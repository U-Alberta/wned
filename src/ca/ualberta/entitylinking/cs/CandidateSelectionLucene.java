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
package ca.ualberta.entitylinking.cs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.indexing.AliasLuceneIndex;
import ca.ualberta.entitylinking.config.WNEDConfig;
import ca.ualberta.entitylinking.utils.Rank;

public class CandidateSelectionLucene extends CandidateSelection {
	private static Logger LOGGER = LogManager.getLogger(CandidateSelectionLucene.class);
	private final static double THRESHOLD = 0.005f;
	
	private AliasLuceneIndex a2eIndex = null;
	private double prob_threshold = THRESHOLD;
	private int K = 20;
	private Map<String, String> entityType = null;
	
	public CandidateSelectionLucene() {
		this(THRESHOLD);
	}

	public CandidateSelectionLucene(double threshold) {
		this.prob_threshold = threshold;

		a2eIndex = new AliasLuceneIndex();
		if (!a2eIndex.loadIndex(WNEDConfig.a2eIndexDir))
			LOGGER.info("Alias to entity index is not available");
		else
			LOGGER.info("Load a2eIndex Done!!!");

		//Entity type does not help so far, so disable it for now.
//		loadEntityType(WNEDConfig.entityTypeFile);
		LOGGER.info("Load entity type done!!");
	}

	public void loadEntityType(String file) {
		entityType = new HashMap<String, String>();
		try {
			String line = null;
			BufferedReader r = new BufferedReader(new FileReader(file));
			while ((line = r.readLine()) != null) {
				String toks[] = line.split("\t");
				if (toks[0].equals("LOC"))
					entityType.put(toks[1], Entity.LOCATION);
				else if (toks[0].equals("ORG"))
					entityType.put(toks[1], Entity.ORGANIZATION);
				else if (toks[0].equals("PER"))
					entityType.put(toks[1], Entity.PERSON);
				else
					entityType.put(toks[1], Entity.MISC);
			}
			
			r.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public AliasLuceneIndex getAliasIndex() {
		return a2eIndex;
	}
	
	public Map<Entity, Double> selectCandidatesPruning(String name) {
		if (name == null || name.isEmpty())
			return null;

		//remove extra space.
		name = name.replaceAll("\\s+",  " ").trim().toLowerCase();
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		Map<String, Integer> map = new HashMap<String, Integer>();
		Map<String, String> srcMap = new HashMap<String, String>();

		//find entity from alias2entity index.
		List<String> candidates = a2eIndex.queryAlias(name);
		if (candidates == null || candidates.isEmpty())
			return null;
		
		//String format: Entity	Alias	source	freq
		for (String entStr : candidates) {
			String toks[] = entStr.split("\t");
			if (toks.length != 4)
				continue;
			
			String entName = toks[0];
			String src = toks[2];
			int freq = Integer.parseInt(toks[3]);
			
			map.put(entName, freq);
			srcMap.put(entName, src);
		}
		
		//convert the frequency to probability.
		double sum = 0.0, prob = 0.0;
		for (String entName : map.keySet())
			sum += map.get(entName);
		for (String entName : map.keySet()) {
			prob = map.get(entName) / sum;
			String type = Entity.NONE;
			if (entityType != null && entityType.containsKey(entName))
				type = entityType.get(entName);
			if (!srcMap.get(entName).equals("link") && prob > 0.001)
				ret.put(new Entity(entName, entName, type), prob);
			else if (srcMap.get(entName).equals("link") && prob > prob_threshold)
				ret.put(new Entity(entName, entName, type), prob);
		}

		//keep the top K candidates (sorted by the probability)
		Rank<Double, Entity> rank = null;
		List<Rank<Double,Entity>> rankList = new ArrayList<Rank<Double, Entity>>();
		for (Entity ent : ret.keySet()) {
			rank = new Rank<Double, Entity>(ret.get(ent), ent);
			rankList.add(rank);
		}
		
		Collections.sort(rankList);
		
		Map<Entity, Double> ret2 = new HashMap<Entity, Double>();
		int len = (rankList.size() > K ? K : rankList.size());
		for (int i = 0; i < len; i++) {
			rank = rankList.get(i);
			ret2.put(rank.obj, rank.sim);
		}

		return ret2;
	}

	public Map<Entity, Double> selectCandidatesFull(String name) {
		if (name == null || name.isEmpty()) {
			LOGGER.info("Empty name!");
			return null;
		}

		//remove extra space.
		name = name.replaceAll("\\s+",  " ").trim().toLowerCase();
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		Map<String, Integer> map = new HashMap<String, Integer>();
		Map<String, String> srcMap = new HashMap<String, String>();

		//find entity from alias2entity index.
		List<String> candidates = a2eIndex.queryAlias(name);
		if (candidates == null || candidates.isEmpty()) {
			LOGGER.info("No candidates returned from a2eIndex!");
			return null;
		}
		
		//String format: Entity	Alias	source	freq
		for (String entStr : candidates) {
			String toks[] = entStr.split("\t");

			if (toks.length != 4)
				continue;
			
			String entName = toks[0];
			String src = toks[2];
			int freq = Integer.parseInt(toks[3]);
			
			map.put(entName, freq);
			srcMap.put(entName, src);
		}

		//convert the frequency to probability.
		double sum = 0.0, prob = 0.0;
		for (String entName : map.keySet())
			sum += map.get(entName);
		for (String entName : map.keySet()) {
			String type = Entity.NONE;
			if (entityType != null && entityType.containsKey(entName))
				type = entityType.get(entName);

			prob = map.get(entName) * 1.0 / sum;
			ret.put(new Entity(entName, entName, type), prob);
			
//			if (!srcMap.get(entName).equals("link") && prob > 0.001)
//			if (!srcMap.get(entName).equals("link"))
//				ret.put(new Entity(entName, entName, type), prob);
//			else if (srcMap.get(entName).equals("link") && prob > prob_threshold)
//				ret.put(new Entity(entName, entName, type), prob);
		}
		
		return ret;
	}

	public Map<Entity, Double> selectCandidates(String name) {
			return selectCandidatesFull(name);
	}

	public Map<String, Map<Entity, Double>> selectCandidatesFuzzy(String name) {
		if (name == null || name.isEmpty())
			return null;

		name = name.toLowerCase();
		Map<String, Map<Entity, Double>> ret = new HashMap<String, Map<Entity, Double>>();
		Map<String, Integer> map = new HashMap<String, Integer>();
		Map<String, String> srcMap = new HashMap<String, String>();
		Map<Entity, Double> probMap = null;

		//find entity from alias2entity index.
		Map<String, List<String>> candidates = a2eIndex.queryAlias(name,2);
		if (candidates == null || candidates.isEmpty())
			return null;
		
		//String format: Entity	Alias	source	freq
		for (String alias : candidates.keySet()) {
			List<String> entStrList = candidates.get(alias);
			if (entStrList == null || entStrList.isEmpty())
				continue;
			
			LOGGER.info("\t" + alias);
			
			for (String entStr : entStrList) {
				String toks[] = entStr.split("\t");
				if (toks.length != 4)
					continue;

				String entName = toks[0];
				String src = toks[2];
				int freq = Integer.parseInt(toks[3]);

				map.put(entName, freq);
				srcMap.put(entName, src);
			}

			//convert the frequency to probability.
			double sum = 0.0, prob = 0.0;
			for (String entName : map.keySet())
				sum += map.get(entName);

			probMap = new HashMap<Entity, Double>();
			for (String entName : map.keySet()) {
				String type = Entity.NONE;
				if (entityType != null && entityType.containsKey(entName))
					type = entityType.get(entName);

				prob = map.get(entName) / sum;
				probMap.put(new Entity(entName, entName, type), prob);
			}
			
			ret.put(alias, probMap);
		}

		return ret;
	}

	@Override
	public Map<Entity, Double> selectCandidatesName(
			Set<String> names) {
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		Map<Entity, Double> map = null;
		
		if (names == null || names.isEmpty())
			return null;
		
		for (String name : names) {
			map = selectCandidates(name);
			if (map == null || map.isEmpty())
				continue;

			for (Entity ent : map.keySet()) {
				if (ret.containsKey(ent))
					ret.put(ent, ret.get(ent) + map.get(ent));
				else
					ret.put(ent, map.get(ent));
			}
		}
		
		return ret;
	}

	@Override
	public Map<Mention, Map<Entity, Double>> selectCandidatesMention(
			List<Mention> mentions) {
		Map<Mention, Map<Entity, Double>> ret = new HashMap<Mention, Map<Entity, Double>>();
		Map<Entity, Double> map = null;
		
		if (mentions == null || mentions.isEmpty())
			return null;
		
		for (Mention m : mentions) {
			String name = m.getEntity().getName();
			map = selectCandidates(name);
			if (map == null || map.isEmpty()) {
				LOGGER.info(m.getName() + " : No candidates!");
				continue;
			}

			ret.put(m, map);
		}

		return ret;
	}
}
