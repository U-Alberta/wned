/*
 * Copyright 2017 Zhaochen Guo
 *
 * This file is part of WNED.
 * WNED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * WNED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with WNED.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.ualberta.entitylinking.tuning;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ca.ualberta.entitylinking.disambiguation.L2RPredictor;
import ca.ualberta.entitylinking.experiment.Evaluation;
import ca.ualberta.entitylinking.graph.similarity.context.EntityContextCache;
import ca.ualberta.entitylinking.graph.similarity.context.MentionContextCache;
import ca.ualberta.entitylinking.graph.similarity.measure.SimilarityMeasure;
import ca.ualberta.entitylinking.utils.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import ca.ualberta.entitylinking.graph.GraphUtils;
import ca.ualberta.entitylinking.graph.WeightedGraph;
import ca.ualberta.entitylinking.graph.DirectedGraph;
import ca.ualberta.entitylinking.graph.UndirectedGraph;
import ca.ualberta.entitylinking.graph.SubGraphGenerator;
import ca.ualberta.entitylinking.graph.algorithms.PersonalizedPageRank;
import ca.ualberta.entitylinking.graph.algorithms.UnweightedPersonalizedPageRank;
import ca.ualberta.entitylinking.graph.algorithms.WeightedPersonalizedPageRank;
import ca.ualberta.entitylinking.common.data.Document;
import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.nlp.OrthoMatcherCoref;
import ca.ualberta.entitylinking.common.nlp.StanfordNER;
import ca.ualberta.entitylinking.common.indexing.TFIDF3x;
import ca.ualberta.entitylinking.common.indexing.Tokenizer;
import ca.ualberta.entitylinking.cs.CandidateSelection;
import ca.ualberta.entitylinking.cs.CandidateSelectionLucene;
import ca.ualberta.entitylinking.config.WNEDConfig;
import ca.ualberta.entitylinking.utils.similarity.StringSim;
import ca.ualberta.entitylinking.utils.similarity.VectorSimilarity;

public class SemanticSignatureEL {
	private static Logger LOGGER = LogManager.getLogger(SemanticSignatureEL.class);
    public static DecimalFormat df = new DecimalFormat("#.###");

	protected StanfordNER ner = null;
	protected OrthoMatcherCoref orthoMatcher = null;
	protected TFIDF3x tfidfIndex = null;
	protected Tokenizer toker = null;
	protected CandidateSelection cs = null;

    private SubGraphGenerator gg = null;
    private WeightedGraph g = null;

    private boolean weighted = false;
    
    private L2RPredictor predictor = null;

	public double alpha = 0.0;
	public double beta = 0.3;

	//cache the context of mentions for efficiency.
    MentionContextCache mentionCtxCache = null;
    //cache the context of entities for efficiency.
    EntityContextCache entityCtxCache = null;

	private Map<Mention, String> truth = new HashMap<Mention, String>();

    private PrintStream output = null;

	public SemanticSignatureEL(String configFile) {
		WNEDConfig.loadConfiguration(configFile);
		//Just for feature generation.
		WNEDConfig.supervised = false;

        //Load the Knowledge base graph.
		if (WNEDConfig.directedGraph)
			g = new DirectedGraph(WNEDConfig.linkGraphLoc);
		else
			g = new UndirectedGraph(WNEDConfig.cooccurrenceGraphLoc);

		g.load();
		gg = new SubGraphGenerator(g);
        LOGGER.info(ELUtils.currentTime() + "Done with loading graph");

        //Candidate selection
		cs = new CandidateSelectionLucene();
        LOGGER.info(ELUtils.currentTime() + "Done with loading lucene index");
        //load the prediction model.
        if (WNEDConfig.supervised)
        	predictor = new L2RPredictor(WNEDConfig.modelFile);

        //Create the NER and co-reference resolution components.
		Set<String> allowedEntityTypes = new HashSet<String>();
		allowedEntityTypes.add(Entity.PERSON);
		allowedEntityTypes.add(Entity.ORGANIZATION);
		allowedEntityTypes.add(Entity.LOCATION);
		allowedEntityTypes.add(Entity.MISC);
		ner = new StanfordNER(allowedEntityTypes);
		orthoMatcher = new OrthoMatcherCoref();
        LOGGER.info(ELUtils.currentTime() + "Done with loading StanfordNER and GATE OrthoMatcher");

		try {
            tfidfIndex = new TFIDF3x();
            LOGGER.info(ELUtils.currentTime() + "Done with loading the TFIDF index");
			toker = new Tokenizer();
            mentionCtxCache = new MentionContextCache(WNEDConfig.contextOption, toker, tfidfIndex);
            entityCtxCache = new EntityContextCache(tfidfIndex);

            output = new PrintStream("training-data.txt");
        } catch (Exception e) {
			e.printStackTrace();
		}
	}
    /**
     * Collect the entities of unambiguous mentions.
     *
     * @param candMap
     * @return The list of entities of unambiguous mentions.
     */
    private Map<String, Double> getUnambiguousEntities(
            Map<Mention, Map<Entity, Double>> candMap, WeightCache weightCache) {

        Map<String, Double> ret = new HashMap<String, Double>();
        Map<Entity, Double> candidates = null;

        for (Mention m : candMap.keySet()) {
            LOGGER.info(m.getEntity().getName());

            candidates = candMap.get(m);
            if (candidates == null || candidates.isEmpty())
                continue;

            //get the weight
            double mWeight = weightCache.getMentionWeight(m, WNEDConfig.mPrefStreg);
            if (candidates.size() == 1) {
                LOGGER.info("\t1: " + candidates.keySet().iterator().next().getName());
                ret.put(candidates.keySet().iterator().next().getName(), mWeight);
                continue;
            }

            //we select entities with both maximum prior probability
            //and local compatibility with mentions.
            double prior, sim, maxPrior = 0.0, maxSim = 0.0;
            Entity maxPriorEnt = null, maxSimEnt = null;
            for (Entity ent : candidates.keySet()) {
                prior = candidates.get(ent);
                sim = SimilarityMeasure.mentionEntitySimilarity(m, ent, mentionCtxCache, entityCtxCache);

                if (prior > maxPrior) {
                    maxPrior = prior;
                    maxPriorEnt = ent;
                }
                if (sim > maxSim) {
                    maxSim = sim;
                    maxSimEnt = ent;
                }
            }

            if (maxPriorEnt == maxSimEnt) {
                ret.put(maxPriorEnt.getName(), mWeight);
                LOGGER.info("\t2: " + maxPriorEnt.getName());

                // remove the rest candidates.
                prior = candidates.get(maxSimEnt);
                candidates.clear();
                candidates.put(maxSimEnt, prior);
            }
        }

        return ret;
    }

	/**
	 * Collect all the entities in the candidates of mentions as the nodes of the graph,
	 * Also we could expand the entity set with incoming links and outgoing links.
	 *
	 * @param candMap
	 * @return
	 */
	private Set<String> collectNodes(Set<Mention> mentions,
			Map<Mention, Map<Entity, Double>> candMap) {

		if (candMap == null || candMap.isEmpty())
			return null;

		Set<String> ret = new HashSet<String>();
		Map<Entity, Double> candidates = null;
		for (Mention m : mentions) {
			candidates = candMap.get(m);
			if (candidates == null || candidates.isEmpty())
				continue;

			for (Entity e : candidates.keySet()) {
				String entName = e.getName();
				// check if the entName is in the Wikipedia Graph.
				if (!g.containsNode(entName))
					continue;

				ret.add(entName);
			}
		}

		return ret;
	}

    /**
     * Clear the candidates by removing entities not in the graph.
     * @param candidates
     * @param entities
     * @param e2id
     * @param ranks
     */
    private void cleanupCandidates(Map<Entity, Double> candidates, Set<String> entities,
                                   Map<String, Integer> e2id,
                                   Map<Integer, List<Double>> ranks) {

        if (candidates == null || candidates.size() <= 1)
            return;

        Entity[] candEntities = candidates.keySet().toArray(new Entity[1]);
        for (int j = 0; j < candEntities.length; j++) {
            Entity ent = candEntities[j];
            String entName = ent.getName();
            if (!entities.contains(entName) ||
                    !e2id.containsKey(entName) ||
                    !ranks.containsKey(e2id.get(entName)))

                candidates.remove(ent);
        }
    }

    private class WeightCache {
        Map<Entity, Double> randomWeightCache = new HashMap<Entity, Double>();
        Map<Mention, Double> prefWeightCache = new HashMap<Mention, Double>();
        Map<Mention, Map<Entity, Double>> localSimMap =
                new HashMap<Mention, Map<Entity, Double>>();
        Map<Mention, Map<Entity, Double>> priorProbMap = null;

        public void prepareWeightCache(WNEDConfig.PrefStrategy pref,
                                       List<Mention> mentions,
                                       Map<Mention, Map<Entity, Double>> candMap) {
            if (pref == WNEDConfig.PrefStrategy.RANDOM) {
                Map<Entity, Double> candidates = null;
                randomWeightCache = new HashMap<Entity, Double>();
                Random rand = new Random(System.currentTimeMillis());
                for (Mention m : mentions) {
                    candidates = candMap.get(m);
                    if (candidates == null || candidates.isEmpty())
                        continue;

                    for (Entity e : candidates.keySet()) {
                        double weight = rand.nextDouble();
                        randomWeightCache.put(e, weight);
                    }
                }
            } else if (pref == WNEDConfig.PrefStrategy.TFIDF) {
                Document doc = mentions.get(0).getSentence().getDocument();
                String content = doc.getOriginalText();
                for (Mention m : mentions) {
                    String name = m.getName();
                    double tfidf = DocumentUtils.computeTFIDF(name, content, tfidfIndex);
                    prefWeightCache.put(m, tfidf);
                }
            }
        }

        public void prepareContextSimCache(List<Mention> mentions,
                                           Map<Mention, Map<Entity, Double>> candMap) {
            Map<Entity, Double> candidates = null;
            Map<Entity, Double> simMap = null;

            for (Mention m : mentions) {
                candidates = candMap.get(m);
                if (candidates == null || candidates.isEmpty())
                    continue;

                simMap = localSimMap.get(m);
                if (simMap == null)
                    simMap = new HashMap<Entity, Double>();

                if (candidates.size() == 1) {
                    simMap.put(candidates.keySet().iterator().next(), 1.0);
                } else {
                    for (Entity e : candidates.keySet()) {
                        //compute the context similarity with the candidate.
                        double local = SimilarityMeasure.mentionEntitySimilarity(m, e, mentionCtxCache, entityCtxCache);
                        simMap.put(e,  local);
                    }
                }

                localSimMap.put(m, simMap);
            }
        }

        public void preparePriorProbCache(List<Mention> mentions,
                                          Map<Mention, Map<Entity, Double>> candMap) {

            priorProbMap = candMap;
        }

        public double getMentionWeight(Mention m, WNEDConfig.PrefStrategy mPref) {
            if (mPref == WNEDConfig.PrefStrategy.UNIFORM)
                return 1.0;
            if (mPref == WNEDConfig.PrefStrategy.TFIDF)
                return prefWeightCache.get(m);

            return 1.0;
        }

        public double getEntityWeight(Mention m, Entity e, WNEDConfig.PrefStrategy ePref) {
            if (ePref == WNEDConfig.PrefStrategy.PRIOR_PROB)
                return priorProbMap.get(m).get(e);
            else if (ePref == WNEDConfig.PrefStrategy.CTX_SIM)
                return localSimMap.get(m).get(e);
            else if (ePref == WNEDConfig.PrefStrategy.RANDOM)
                return randomWeightCache.get(e);

            return 1.0;
        }
    }

    /**
     * Use an iterative approach for the entity linking task.
     *
     * @param mentions
     * @return
     */
    private List<String> linkingImplUnifiedUniterative(List<Mention> mentions) {
        //Select candidates.
        Map<Mention, Map<Entity, Double>> candMap =
                CSUtils.selectCandidatesMention(mentions, mentionCtxCache, entityCtxCache, cs);
        if (candMap == null || candMap.isEmpty())
            return null;

        WeightCache weightCache = new WeightCache();
        //Cache the importance of mentions, context similarity and prior probability between mention and entity.
        weightCache.prepareWeightCache(WNEDConfig.mPrefStreg, mentions, candMap);
        weightCache.prepareContextSimCache(mentions, candMap);
        weightCache.preparePriorProbCache(mentions, candMap);

        //Use unambiguous mentions as the initial representation of the document.
        //This step has to be here, since we do some cleanup when we collect the unambiguous entities.
        Map<String, Double> unambigEntities = null;
        if (WNEDConfig.useUnambigEntity)
            unambigEntities = getUnambiguousEntities(candMap, weightCache);

        // Collect all entities for graph construction.
        Set<String> entities = collectNodes(candMap.keySet(), candMap);
        if (entities == null)
            entities = new HashSet<String>();

        // 2. Construct a graph including all candidate entities and the target Entities.
        Map<String, Integer> e2id = new HashMap<String, Integer>();
        PersonalizedPageRank ranker = null;
        
        if (weighted)
        	ranker = new WeightedPersonalizedPageRank(GraphUtils.buildWeightedGraph(gg, entities, e2id));
        else
        	ranker = new UnweightedPersonalizedPageRank(GraphUtils.buildUnweightedGraph(gg, entities, e2id));

        //3. Compute the semantic signature, and perform the disambiguation.
        //3.1. Compute the semantic signature of all entities.
        Map<Integer, List<Double>> entSemSigs =
                ELUtils.computePageRankParallel(entities, e2id, ranker);

        //clear the candidates by removing entities not in the graph.
        for (Mention m : mentions)
            cleanupCandidates(candMap.get(m), entities, e2id, entSemSigs);

        //compute the semantic signature of the document using targetEntities.
        Map<Entity, Double> candidates = null;

        //4. Entity disambiguation.
        //find unambiguous mentions.
        for (Mention m : mentions) {
            candidates = candMap.get(m);
            if (candidates == null || candidates.isEmpty())
                continue;

            if (candidates.size() == 1) {
                double mWeight = weightCache.getMentionWeight(m, WNEDConfig.mPrefStreg);
                Entity finalEnt = candidates.keySet().iterator().next();
                unambigEntities.put(finalEnt.getName(), mWeight);
            }
        }

        List<Double> docSemSig = null;
        List<String> ret = new ArrayList<String>();

        //Update the semantic signature of the document.
        if (unambigEntities == null || unambigEntities.isEmpty())
            unambigEntities = getApproximateEntities(candMap, weightCache);

        //Start disambiguation.
        for (Mention m : mentions) {
            candidates = candMap.get(m);
            if (candidates == null || candidates.isEmpty()) {
                ret.add(null);
            } else if (candidates.size() == 1) {
                ret.add(candidates.keySet().iterator().next().getName());
            } else {
                // If there is any candidate in the unambigEntities,remove them and recompute the docSemSig.
                // The reason is that candidate in the unambigEntities will get higher semantic similarity with the doc.
                Set<String> avoidSet = new HashSet<String>();
                for (Entity ent : candidates.keySet()) {
                    String name = ent.getName();
                    if (unambigEntities.containsKey(name)) {
                        LOGGER.info("Candidate in the representative entities: " + name);
                        avoidSet.add(name);
                    }
                }

                docSemSig = ELUtils.computePageRank(unambigEntities, avoidSet, e2id, ranker);

                Entity ent = disambiguateMention(m, candidates, docSemSig, entSemSigs, e2id, weightCache);
                ret.add(ent.getName());
            }
        }

        return ret;
    }


    /**
	 * Use an iterative approach for the entity linking task.
	 *
	 * @param mentions
	 * @return
	 */
	private List<String> linkingImplUnifiedIterative(List<Mention> mentions) {
		long begin = 0, end = 0;

		begin = System.currentTimeMillis();
        //Select candidates.
        Map<Mention, Map<Entity, Double>> candMap =
				CSUtils.selectCandidatesMention(mentions, mentionCtxCache, entityCtxCache, cs);
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]selectCandidatesMention: " + (end - begin) + "ms");
		if (candMap == null || candMap.isEmpty())
			return null;

		begin = System.currentTimeMillis();
		WeightCache weightCache = new WeightCache();
        //Cache the importance of mentions, context similarity and prior probability between mention and entity.
        weightCache.prepareWeightCache(WNEDConfig.mPrefStreg, mentions, candMap);
        weightCache.prepareContextSimCache(mentions, candMap);
        weightCache.preparePriorProbCache(mentions, candMap);
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]prepareCache: " + (end - begin) + "ms");

        //Use unambiguous mentions as the initial representation of the document.
        //This step has to be here, since we do some cleanup when we collect the unambiguous entities.
		begin = System.currentTimeMillis();
        Map<String, Double> unambigEntities = null;
        if (WNEDConfig.useUnambigEntity)
            unambigEntities = getUnambiguousEntities(candMap, weightCache);
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]getUnambiguousEntities: " + (end - begin) + "ms");

        // Collect all entities for graph construction.
		begin = System.currentTimeMillis();
		Set<String> entities = collectNodes(candMap.keySet(), candMap);
		if (entities == null)
            entities = new HashSet<String>();
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]collectNodes: " + (end - begin) + "ms");

		// 2. Construct a graph including all candidate entities and the target Entities.
		begin = System.currentTimeMillis();
		Map<String, Integer> e2id = new HashMap<String, Integer>();
        PersonalizedPageRank ranker = null;
        
        if (weighted)
        	ranker = new WeightedPersonalizedPageRank(GraphUtils.buildWeightedGraph(gg, entities, e2id));
        else
        	ranker = new UnweightedPersonalizedPageRank(GraphUtils.buildUnweightedGraph(gg, entities, e2id));

        end = System.currentTimeMillis();
		LOGGER.info("[profiling]new PersonalizedPageRank: " + (end - begin) + "ms");

        //3. Compute the semantic signature, and perform the disambiguation.
        //3.1. Compute the semantic signature of all entities.
		begin = System.currentTimeMillis();
		Map<Integer, List<Double>> entSemSigs =
                ELUtils.computePageRankParallel(entities, e2id, ranker);
		end = System.currentTimeMillis();
		LOGGER.info("computePageRankParallel[" + entities.size() + "]: " + (end - begin) + "ms");

		//clear the candidates by removing entities not in the graph.
		begin = System.currentTimeMillis();
        for (Mention m : mentions)
            cleanupCandidates(candMap.get(m), entities, e2id, entSemSigs);
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]cleanupCandidates: " + (end - begin) + "ms");

		//compute the semantic signature of the document using targetEntities.
        Map<Entity, Double> candidates = null;

        //4. Iterative entity disambiguation.
		begin = System.currentTimeMillis();
        List<Mention> sortedMentions = sortMentionByAmbiguity(mentions, candMap);
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]sortMentionByAmbiguity: " + (end - begin) + "ms");

        //find unambiguous mentions.
        for (Mention m : sortedMentions) {
            candidates = candMap.get(m);
            if (candidates == null || candidates.isEmpty()) {
            	LOGGER.info("[result]" + m.getName() + "[" + truth.get(m) + "]" + " : " + "NIL");
                continue;
            }

            if (candidates.size() == 1) {
                double mWeight = weightCache.getMentionWeight(m, WNEDConfig.mPrefStreg);
                Entity finalEnt = candidates.keySet().iterator().next();
                unambigEntities.put(finalEnt.getName(), mWeight);
            	LOGGER.info("[result]" + m.getName() + "[" + truth.get(m) + "]" + " : " + finalEnt.getName());
            }
        }

        List<Double> docSemSig = null;
        Map<String, Double> tempEntities = null;

        //Start disambiguation.
        for (Mention m : sortedMentions) {
            candidates = candMap.get(m);
            if (candidates == null || candidates.size() < 2)
                continue;

            //Update the semantic signature of the document.
            if (unambigEntities != null && unambigEntities.size() > 0)
                tempEntities = unambigEntities;
            else
                //This case only happens for the first time when all mentions are ambiguous.
                tempEntities = getApproximateEntities(candMap, weightCache);

            // If there is any candidate in the unambigEntities,remove them and recompute the docSemSig.
            // The reason is that candidate in the unambigEntities will get higher semantic similarity with the doc.
            Set<String> avoidSet = new HashSet<String>();
            for (Entity ent : candidates.keySet()) {
                String name = ent.getName();
                if (tempEntities.containsKey(name)) {
                    LOGGER.info("Candidate in the representative entities: " + name);
                    avoidSet.add(name);
                }
            }

    		begin = System.currentTimeMillis();
            docSemSig = ELUtils.computePageRank(tempEntities, avoidSet, e2id, ranker);
    		end = System.currentTimeMillis();
    		LOGGER.info("[profiling]ELUtils.computePageRank: " + (end - begin) + "ms");

    		begin = System.currentTimeMillis();
            Entity ent = disambiguateMention(m, candidates, docSemSig, entSemSigs, e2id, weightCache);
            unambigEntities.put(ent.getName(), weightCache.getMentionWeight(m, WNEDConfig.mPrefStreg));

        	LOGGER.info("[result]" + m.getName() + "[" + truth.get(m) + "]" + " : " + ent.getName());

    		end = System.currentTimeMillis();
    		LOGGER.info("[profiling]disambiguateMention: " + (end - begin) + "ms");
        }


        List<String> ret = new ArrayList<String>();
		for (Mention m : mentions) {
			candidates = candMap.get(m);
			if (candidates == null || candidates.isEmpty())
				ret.add(null);
            else
	    		ret.add(candidates.keySet().iterator().next().getName());
		}

		return ret;
	}

	private static void normalizeFeatures(List<Feature> rankList) {
		Feature total = new Feature(0.0, 0.0, 0.0);
		for (Feature rank : rankList) {
			total.prior += rank.prior;
			total.local += rank.local;
			total.semSim += rank.semSim;
			total.nameSim += rank.nameSim;
		}
		
		for (Feature rank : rankList) {
			if (total.prior > 0)
				rank.prior /= total.prior;
			if (total.local > 0)
				rank.local /= total.local;
			if (total.semSim > 0)
				rank.semSim /= total.semSim;
			if (total.nameSim > 0)
				rank.nameSim /= total.nameSim;
		}
	}
	
	private Entity disambiguateSupervised(List<Entity> candList, List<Feature> features) {
		int index = predictor.predict(features);
		
		if (index < 0)	return null;
		
		return candList.get(index);
	}
	
	private Entity disambiguateUnsupervised(List<Entity> candList, List<Feature> features) {
		int maxIdx = 0;
		Feature max = features.get(0), f= null;
		for (int i = 1; i < features.size(); i++) {
			f = features.get(i);
			if (max.compareTo(f) < 0) {
				max = f;
				maxIdx = i;
			}
		}
		
		return candList.get(maxIdx);
	}

    private Entity disambiguateMention(Mention m, Map<Entity, Double> candidates,
                                       List<Double> docSemSig,
                                       Map<Integer, List<Double>> entSemSigs,
                                       Map<String, Integer> e2id,
                                       WeightCache weightCache) {
        if (candidates == null || candidates.isEmpty())
            return null;

        if (candidates.size() == 1)
            return candidates.keySet().iterator().next();

        List<Entity> entities = new ArrayList<Entity>();
        List<Feature> features = new ArrayList<Feature>();

        output.println("[mention]" + m.getName());
        for (Entity ent : candidates.keySet()) {
            String entName = ent.getName();
            int eid = e2id.get(entName);

            double prior = candidates.get(ent);
            double local = weightCache.getEntityWeight(m, ent, WNEDConfig.PrefStrategy.CTX_SIM);
            double semSim = 1.0 / VectorSimilarity.ZeroKLDivergence(entSemSigs.get(eid), docSemSig);
            double nameSim = StringSim.ngram_distance(m.getName().toLowerCase(), entName.toLowerCase(), 2);

            entities.add(ent);
            features.add(new Feature(prior, local, semSim, nameSim));
        }

        //dump the features. 
        for (int i = 0; i < entities.size(); i++) {
        	String entName = entities.get(i).getName();
        	Feature f = features.get(i);
        	if (truth.containsKey(m) && truth.get(m).equals(entName))
                output.println("[entity]" + entName + ":" + 
                				f.prior + "\t" + f.local + "\t" + 
                				f.semSim + "\t" + f.nameSim + "\t1");
            else
                output.println("[entity]" + entName + ":" + 
        				f.prior + "\t" + f.local + "\t" + 
        				f.semSim + "\t" + f.nameSim + "\t0");
        }

        //disambiguate
        //normalize features first
        normalizeFeatures(features);
        Entity ret = null;
        if (WNEDConfig.supervised)
        	ret = disambiguateSupervised(entities, features);
        else
        	ret = disambiguateUnsupervised(entities, features);
        
        //Choose the entity with the highest ranking.
        //Remove the rest candidates.
        for (int i = 0; i < entities.size(); i++) {
        	Entity ent = entities.get(i);
        	Feature f = features.get(i);
        	if (ent == ret) {
                LOGGER.info("" + ent.getName()
                        + "\t" + df.format(f.prior) + "\t"
                        + df.format(f.local) + "\t"
                        + df.format(f.semSim));

        		continue;
        	}

            LOGGER.info("[removed4]:" + ent.getName()
                    + "\t" + df.format(f.prior) + "\t"
                    + df.format(f.local) + "\t"
                    + df.format(f.semSim));

            candidates.remove(ent);
        }

        return ret;
    }

    private Map<String, Double> getApproximateEntities(
                                    Map<Mention, Map<Entity, Double>> candMap, WeightCache weightCache) {

        Map<String, Double> ret = new HashMap<String, Double>();
        Map<Entity, Double> candidates = null;

        for (Mention m : candMap.keySet()) {
            candidates = candMap.get(m);
            if (candidates == null || candidates.isEmpty())
                continue;

            double mWeight = weightCache.getMentionWeight(m, WNEDConfig.mPrefStreg);

            // We assume the only one candidate is the final true entity.
            if (candidates.size() == 1) {
                Entity finalEnt = candidates.keySet().iterator().next();
                ret.put(finalEnt.getName(), mWeight);
                continue;
            }

            Map<Entity, Double> tempMap = new HashMap<Entity, Double>();
            for (Entity e : candidates.keySet()) {
                double eWeight = weightCache.getEntityWeight(m, e, WNEDConfig.ePrefStreg);
                tempMap.put(e, eWeight);
            }

            ELUtils.normalize(tempMap);

            for (Entity e : tempMap.keySet()) {
                String entName = e.getName();
                if (WNEDConfig.mPrefStreg == WNEDConfig.PrefStrategy.RANDOM ||
                        WNEDConfig.ePrefStreg == WNEDConfig.PrefStrategy.RANDOM)
                    ret.put(entName, weightCache.getEntityWeight(m, e, WNEDConfig.PrefStrategy.RANDOM));
                else
                    ret.put(entName, tempMap.get(e) * mWeight);
            }
        }

        return ret;
    }

    /**
     * Sort the mentions by their ambiguity so that the least ambiguous mention
     * is disambiguated first.
     * Here we simply use the number of candidates to measure the ambiguity of a mention.
     * In the future work, we may explore other measures such as Entropy of the mention.
     *
     * @param mentions
     * @param candMap
     * @return
     */
    private List<Mention> sortMentionByAmbiguity(List<Mention> mentions,
                                                 Map<Mention, Map<Entity, Double>> candMap) {

        Map<Entity, Double> candidates = null;
        List<Rank<Integer, Mention>> rankList = new ArrayList<Rank<Integer, Mention>>();
        for (Mention m : mentions) {
            int ambiguity = 0;
            candidates = candMap.get(m);
            if (candidates != null)
                ambiguity = candidates.size();

            rankList.add(new Rank<Integer, Mention>(ambiguity, m));
        }

        Collections.sort(rankList);

        List<Mention> ret = new ArrayList<Mention>();
        for (Rank<Integer, Mention> rank : rankList)
            ret.add(rank.obj);

        return ret;
    }

	/**
	 * Use an iterative approach for the entity linking task.
	 *
	 * @param mentions
	 * @return
	 */
	private List<String> linkingImplUnified(List<Mention> mentions) {
		if (WNEDConfig.useIterative)
			return linkingImplUnifiedIterative(mentions);
		else
			return linkingImplUnifiedUniterative(mentions);
	}

	public String linking(String file) {
		//1. Load the query file into a DOM tree.
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		org.w3c.dom.Document dom = null;

		try {
			//Using factory get an instance of document builder
			db = dbf.newDocumentBuilder();
			//parse using builder to get DOM representation of the XML file
			dom = db.parse(file);
		}catch(ParserConfigurationException pce) {
			pce.printStackTrace();
		}catch(SAXException se) {
			se.printStackTrace();
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}

		//get the root element, and its nodes.
		Element rootEle = dom.getDocumentElement();
		NodeList docNL = rootEle.getElementsByTagName("document");
		if (docNL == null || docNL.getLength() <= 0) {
			LOGGER.warn("docNL empty");
			return null;
		}

		long begin = 0, end = 0;
		begin = System.currentTimeMillis();
		end = System.currentTimeMillis();
		LOGGER.info("[profiling]" + (end - begin) + "ms");

		List<Mention> mentions = new ArrayList<Mention>();
		List<Element> elements = new ArrayList<Element>();
		Map<Element, Mention> e2m = new HashMap<Element, Mention>();

		for (int i = 0; i < docNL.getLength(); i++) {
			//get the annotations of each document.
			Element docEle = (Element)docNL.item(i);
			//get the attribute <docName> of each document
			String docName = docEle.getAttribute("docName");
			//get a node list of <annotation>
			NodeList annoteNL = docEle.getElementsByTagName("annotation");
			if (annoteNL == null || annoteNL.getLength() <= 0)
				continue;

			LOGGER.info("[doc]: " + docName);
			//Tokenize the document and get the index of each term.
			String content = ELUtils.readFile(WNEDConfig.DATASET_DIR + "/" + docName);
			// Annotate document.
			begin = System.currentTimeMillis();
			Document doc = DocumentUtils.annotateDocument(content, ner, orthoMatcher);
			end = System.currentTimeMillis();
			LOGGER.info("[profiling]annotateDocument: " + (end - begin) + " ms");

			Map<Integer, Mention> idxMenMap = DocumentUtils.getIndex(doc);

			//clear the data for re-use.
			mentions.clear();;
			elements.clear();
			e2m.clear();
			truth.clear();
            mentionCtxCache.clear();
            entityCtxCache.clear();

			begin = System.currentTimeMillis();
			for (int j = 0; j < annoteNL.getLength(); j++) {
				Element annoteEle = (Element) annoteNL.item(j);

				String mentionName = XmlProcessor.getTextValue(annoteEle, "mention");
				String wikiName = XmlProcessor.getTextValue(annoteEle, "wikiName");
				int offset = XmlProcessor.getIntValue(annoteEle, "offset");

				if (mentionName == null || mentionName.isEmpty())
					continue;
				
				if (wikiName != null && (wikiName.equals("NIL") || wikiName.isEmpty()))
					wikiName = null;

				if (!WNEDConfig.NILPrediction && wikiName == null)
					continue;

				Mention m = DocumentUtils.createMention(mentionName, offset, doc, idxMenMap);
				if (m == null)	continue;
				mentions.add(m);
				elements.add(annoteEle);
				e2m.put(annoteEle, m);
				if (wikiName != null)	truth.put(m, wikiName);
			}
			
			end = System.currentTimeMillis();
			LOGGER.info("[profiling]createMention: " + (end - begin) + " ms");

			if (elements == null || elements.isEmpty())
				continue;

			for (Mention m : mentions) {
				String name = m.getName();
				LOGGER.info(name + "[" + m.getEntity().getType() + "]" + ":" + m.getEntity().getName());
			}

            ELUtils.resolve(mentions);

            output.println("[docname]" + docName);
			begin = System.currentTimeMillis();
            List<String> results = linkingImplUnified(mentions);
			end = System.currentTimeMillis();
			LOGGER.info("[profiling]linkingImplUnified: " + (end - begin) + " ms");

			for (int j = 0; j < elements.size(); j++) {
				Element ele = elements.get(j);
				Element newE = dom.createElement("entity");

				if (results != null &&
						!results.isEmpty() &&
						results.get(j) != null)
					newE.appendChild(dom.createTextNode(results.get(j)));
				else
					newE.appendChild(dom.createTextNode("NIL"));

				ele.appendChild(newE);
			}
		}

		//output to a file.
		String outFile = null;
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

			outFile = new String(file + ".semantic-signature-feature-generation-" + System.currentTimeMillis());

			DOMSource source = new DOMSource(dom);
			StreamResult result = new StreamResult(new File(outFile));

			transformer.transform(source, result);
		} catch (Exception ie) {
			ie.printStackTrace();
		}

		return outFile;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SemanticSignatureEL obj = new SemanticSignatureEL(args[0]);

		String outFile = obj.linking(WNEDConfig.targetFile);

		// report the accuracy of the entity linking.
		Evaluation.accuracy(outFile);
	}
}
