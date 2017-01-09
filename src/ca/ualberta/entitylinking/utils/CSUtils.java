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
package ca.ualberta.entitylinking.utils;

import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.cs.CandidateSelection;
import ca.ualberta.entitylinking.cs.MentionExpansion;
import ca.ualberta.entitylinking.graph.similarity.context.EntityContextCache;
import ca.ualberta.entitylinking.graph.similarity.context.MentionContextCache;
import ca.ualberta.entitylinking.utils.similarity.StringSim;
import ca.ualberta.entitylinking.utils.similarity.VectorSimilarity;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.*;

public class CSUtils {
    private static Logger LOGGER = LogManager.getLogger(CSUtils.class);

    public final static int PRUNE_LIMIT = 3;

    public static Map<Entity, Double> selectCandidatesNormalize(
            Mention mention, CandidateSelection cser) {
        Map<Entity, Double> map = null;

        String name = mention.getEntity().getName();
        map = cser.selectCandidates(name);

        if (map == null || map.isEmpty()) {
            //try a normalized version.
            String normName = MentionExpansion.normalizeName(name);
            if (!normName.equalsIgnoreCase(normName))
                map = cser.selectCandidates(normName);
        }

        return map;
    }

    public static Map<Entity, Double> selectCandidatesFuzzy(
            Mention mention, CandidateSelection cser) {
        String name = mention.getEntity().getName();
        Map<String, Map<Entity, Double>> ret =
                cser.selectCandidatesFuzzy(name);
        if (ret == null || ret.isEmpty())
            return null;

        Map<Entity, Double> candidates = null;
        double sim = 0.0, maxSim = 0.0;
        for (String alias : ret.keySet()) {
            //ignore the name itself.
            if (alias.equalsIgnoreCase(name))
                continue;
            sim = StringSim.jaro_winkler_score(name.toLowerCase(), alias);
            if (maxSim < sim) {
                maxSim = sim;
                candidates = ret.get(alias);
            }
        }

        if (maxSim < 0.8)
            return null;

        return candidates;
    }

    public static Map<Mention, Map<Entity, Double>> selectCandidatesFuzzy(
            List<Mention> mentions, CandidateSelection cser) {
        Map<Mention, Map<Entity, Double>> ret = new HashMap<Mention, Map<Entity, Double>>();
        Map<Entity, Double> candidates = null;

        if (mentions == null || mentions.isEmpty())
            return null;

        for (Mention m : mentions) {
            //try normalized name then;
            candidates = selectCandidatesNormalize(m, cser);

            if (candidates == null || candidates.isEmpty())
                candidates = selectCandidatesFuzzy(m, cser);

            ret.put(m, candidates);
        }

        return ret;
    }

    public static Map<Entity, Double> pruneCandidatesWithContext(
            Map<String, Float> docContext,
            EntityContextCache entCtxCache,
            Map<Entity, Double> candidates) {
    	return pruneCandidatesWithContext(docContext, entCtxCache, candidates, PRUNE_LIMIT);
    }

    public static Map<Entity, Double> pruneCandidatesWithContext(
            Map<String, Float> docContext,
            EntityContextCache entCtxCache,
            Map<Entity, Double> candidates, int K) {

        //keep the top K candidates (sorted by the probability)
        Rank<Double, Entity> rank = null;
        List<Rank<Double,Entity>> rankList = new ArrayList<Rank<Double, Entity>>();

        for (Entity ent : candidates.keySet()) {
            //get entity context.
            Map<String, Float> entContext = entCtxCache.getContext(ent);
            double sim = VectorSimilarity.vectorSim(docContext, entContext);

            rank = new Rank<Double, Entity>(sim, ent);
            rankList.add(rank);
        }

        Collections.sort(rankList);

        Map<Entity, Double> ret = new HashMap<Entity, Double>();
        int len = (rankList.size() > K ? K : rankList.size());
        for (int i = 0; i < len; i++) {
            rank = rankList.get(i);
            ret.put(rank.obj, candidates.get(rank.obj));
        }

        return ret;
    }

    public static Map<Entity, Double> pruneCandidatesWithPriorProb(
            Map<Entity, Double> candidates) {
    	return pruneCandidatesWithPriorProb(candidates, PRUNE_LIMIT);
    }
    
    public static Map<Entity, Double> pruneCandidatesWithPriorProb(
            Map<Entity, Double> candidates, int K) {

        //keep the top K candidates (sorted by the probability)
        Rank<Double, Entity> rank = null;
        List<Rank<Double,Entity>> rankList = new ArrayList<Rank<Double, Entity>>();

        for (Entity ent : candidates.keySet()) {
            double prior = candidates.get(ent);
            rank = new Rank<Double, Entity>(prior, ent);
            rankList.add(rank);
        }

        Collections.sort(rankList);

        Map<Entity, Double> ret = new HashMap<Entity, Double>();
        int len = (rankList.size() > K ? K : rankList.size());
        for (int i = 0; i < len; i++) {
            rank = rankList.get(i);
            ret.put(rank.obj, candidates.get(rank.obj));
        }

        return ret;
    }

    public static Map<Entity, Double> selectCandidatesPruning(
            Mention mention,
            MentionContextCache menCtxCache,
            EntityContextCache entCtxCache,
            CandidateSelection myCs) {

        if (mention == null)
            return null;

        Map<Entity, Double> candidates = null, temp1 = null, temp2 = null;
        String name = mention.getEntity().getName();
        candidates = myCs.selectCandidates(name);
        if (candidates == null || candidates.isEmpty()) {
            LOGGER.info("\t" + mention.getName() + " : No candidates!");
            return null;
        }

        temp1 = pruneCandidatesWithContext(menCtxCache.getContext(mention), entCtxCache, candidates);
        temp2 = pruneCandidatesWithPriorProb(candidates);

        temp1.putAll(temp2);
        candidates.clear();
        double priorThreshold = 0.00002;
        for (Entity ent : temp1.keySet()) {
            double prior = temp1.get(ent);
            //prune candidates with prior probability < 0.00
            if (prior < priorThreshold) {
            	LOGGER.info(ent.getName() + "[prior=" + prior + "] is pruned");
                continue;
            }

            candidates.put(ent, prior);
        }

        return candidates;
    }

    public static Map<Mention, Map<Entity, Double>> selectCandidatesMention(
            List<Mention> mentions,
            MentionContextCache menCtxCache,
            EntityContextCache entCtxCache,
            CandidateSelection myCs) {

        if (mentions == null || mentions.isEmpty())
            return null;

        Map<Mention, Map<Entity, Double>> ret = new HashMap<Mention, Map<Entity, Double>>();
        Map<Entity, Double> candidates = null;
        for (Mention m : mentions) {
            candidates = selectCandidatesPruning(m, menCtxCache, entCtxCache, myCs);
            if (candidates == null || candidates.isEmpty()) {
            	LOGGER.info("No candidates!");
                continue;
            }

            ret.put(m, candidates);
        }

        return ret;
    }


}
