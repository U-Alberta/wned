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
package ca.ualberta.entitylinking.graph.algorithms;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.law.rank.PageRank;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.law.rank.SpectralRanking.StoppingCriterion;
import it.unimi.dsi.law.rank.SpectralRanking.NormStoppingCriterion;
import it.unimi.dsi.law.rank.SpectralRanking.IterationNumberStoppingCriterion;
import it.unimi.dsi.webgraph.BVGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;

public class UnweightedPersonalizedPageRank extends PersonalizedPageRank {
	final static Logger LOGGER = (new Log4jLoggerFactory()).getLogger("it.unimi.dsi.law.rank.PageRankParallelGaussSeidel");
    PageRank ranker = null;
	double[] pref = null;

	public UnweightedPersonalizedPageRank(BVGraph graph) {
		ranker = new PageRankParallelGaussSeidel(graph, 20, LOGGER);
		pref = new double[graph.numNodes()];
	}
	
	public void init() {
		ranker.alpha = alpha;
		ranker.stronglyPreferential = true;
	}
	
	public void setAlpha(double alpha) {
		ranker.alpha = alpha;
	}
	
	public void setPreference(double[] p) {
		ranker.preference = new DoubleArrayList(p);
	}
	
	public double[] computeRank() {
		try {
			ranker.init();
			ranker.stepUntil(or(new NormStoppingCriterion(threshold),
								new IterationNumberStoppingCriterion(maxIter)));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return ranker.rank;
	}
	
	public Map<Integer, List<Double>> computePageRankParallel(Set<Integer> entities) {
		Map<Integer, List<Double>> results = new HashMap<Integer, List<Double>>();
		List<Double> list = null;
		
		for (Integer eid : entities) {
			Arrays.fill(pref, 0.0);
			pref[eid] = 1.0;
			setPreference(pref);
			
			double[] rank = computeRank();
			if (rank == null)	continue;
			
			list = new ArrayList<Double>();
			for (int i = 0; i < rank.length; i++)
				list.add(rank[i]);
			
			results.put(eid, list);
		}
		
		return results;	
	}
	
	private static StoppingCriterion or( final StoppingCriterion stop1, final StoppingCriterion stop2 ) {
		return new StoppingCriterion() {
			public boolean shouldStop( final SpectralRanking p ) {
				return stop1.shouldStop( p ) || stop2.shouldStop( p );
			}
		};
	}

}
