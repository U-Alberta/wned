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
package ca.ualberta.entitylinking.graph.similarity.measure;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.wikipedia.miner.comparison.ArticleComparer;
import org.wikipedia.miner.model.Wikipedia;

import ca.ualberta.entitylinking.graph.WeightedGraph;

public class ParallelRelatednessComputation {
	private int numThreads = 1; 
	private long totalNumCalcs = 0;
	private RelatednessMeasure measurer = null;
	
	public ParallelRelatednessComputation(WeightedGraph graph) {
		this.measurer = new RelatednessMeasureWebGraph(graph);
	}

	public static class ComputationThread extends Thread {
		private Set<String> partition;
		private Set<String> entities;
		private Map<String, Map<String, Double>> eeMap = null;
		private CountDownLatch cdl;
		private RelatednessMeasure measurer = null;
		private int numCalcs = 0;
		
		public ComputationThread(Set<String> partition, Set<String> entities, 
				Map<String, Map<String, Double>> eeMap, CountDownLatch cdl,
				RelatednessMeasure measurer) {
			this.partition = partition;
			this.entities = entities;
			this.eeMap = eeMap;
			this.cdl = cdl;
			this.measurer = measurer;
		}
		
		@Override
		public void run() {
			for (String e1 : partition) {
				for (String e2 : entities) {
					// only calculate and add if e1 < e2
					if (e1.compareTo(e2) < 0) {
						double sim = 0.0;
						
						try {
							sim = measurer.semanticRelatedness(e1, e2);
							numCalcs++;
							
							if (sim < 0.0)
								sim = 0.0;
						} catch (Exception e) {
							e.printStackTrace();
						}

						Map<String, Double> sims = eeMap.get(e1);
						if (sims == null) {
							sims = new HashMap<String, Double>();
							eeMap.put(e1, sims);
						}
						sims.put(e2, sim);
					}
				}
			}
			
			cdl.countDown();
		}
		
		public int getNumCalcs() {
			return numCalcs;
		}
	}
	
	public Map<String, Map<String, Double>>
		computeRelatedness(Set<String> entities) {
		
		Map<String, Map<String, Double>> ret = 
				Collections.synchronizedMap(new HashMap<String, Map<String, Double>>());
		
		List<Set<String>> entityPartitions = new LinkedList<Set<String>>();
		String[] allEntities = entities.toArray(new String[1]);
		
		int overall = 0;
		Set<String> part = null;
		int partSize = entities.size() / numThreads;
		
		for (int curPart = 0; curPart < numThreads; curPart++) {
			part = new HashSet<String>();
			entityPartitions.add(part);
			
			for (int j = 0; j < partSize; j++) {
				int total = (curPart*partSize) +j;
				part.add(allEntities[total]);
				
				overall++;
			}
		}
		
		// add rest to last part
		for (; overall < allEntities.length; overall++) {
			part.add(allEntities[overall]);
		}
		
		// create threads and run
		CountDownLatch cdl = new CountDownLatch(numThreads);
		
		List<ComputationThread> scs = new LinkedList<ComputationThread>();
		for (int i = 0; i < numThreads; i++) {
			ComputationThread sc = new ComputationThread(entityPartitions.get(i), entities, ret, cdl, measurer);
			scs.add(sc);
			sc.start();
		}
		
		// wait for calculation to finish
		try {
			cdl.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// sum up total number of calculations
		for (ComputationThread sc : scs) {
			totalNumCalcs += sc.getNumCalcs();
		}
		
		return ret;
	}
	
	public long getTotalNumCalcs() {
		return this.totalNumCalcs;
	}
}
