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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ca.ualberta.entitylinking.graph.Triple;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.Pair;

import es.yrbcn.graph.weighted.WeightedArc;
import es.yrbcn.graph.weighted.WeightedBVGraph;
import es.yrbcn.graph.weighted.WeightedPageRank;
import es.yrbcn.graph.weighted.WeightedPageRankPowerMethod;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.webgraph.labelling.ArcLabelledImmutableGraph;

public class WeightedPersonalizedPageRank extends PersonalizedPageRank{
	ArcLabelledImmutableGraph g = null;
	WeightedPageRankPowerMethod ranker = null;

	//Each input is a task consisting of a taskID, and the task.
	BlockingQueue<Pair<Integer, Map<Integer, Double>>> inputQueue = 
			new LinkedBlockingQueue<Pair<Integer, Map<Integer, Double>>>();
	BlockingQueue<Pair<Integer, List<Double>>> outputQueue = 
			new LinkedBlockingQueue<Pair<Integer, List<Double>>>();
	
	public class PageRankThread extends Thread {
		WeightedPageRankPowerMethod rankerT = null;
		double[] s = null;
		
		public PageRankThread(ArcLabelledImmutableGraph g) {
			rankerT = new WeightedPageRankPowerMethod(g);
			rankerT.alpha = alpha;
			rankerT.stronglyPreferential = true;
			
			s = new double[g.numNodes()];
		}
		
		private double[] computeRankT() {
			try {
				rankerT.stepUntil( WeightedPageRank.or(
					new WeightedPageRank.NormDeltaStoppingCriterion(threshold),
					new WeightedPageRank.IterationNumberStoppingCriterion(maxIter)));
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			return rankerT.rank;
		}

		public void run() {
			//get preference vector from user.
			Pair<Integer, Map<Integer, Double>> pair = null;
			Map<Integer, Double> prefVector = null;
			while (true) {
				try {
					pair = inputQueue.take();
				} catch (Exception e) {
					e.printStackTrace();
				}

				prefVector = pair.getValue2();
				if (prefVector == null)
					break;
				
				Arrays.fill(s,  0.0);
				for (Integer eid : prefVector.keySet())
					s[eid.intValue()] = prefVector.get(eid);

				rankerT.preference = new DoubleArrayList(s);
				double[] rank = computeRankT();

				List<Double> rankList = new ArrayList<Double>();
				if (rank != null) {
					for (int i = 0; i < rank.length; i++)
						rankList.add(rank[i]);
				}

				Pair<Integer, List<Double>> ret = new Pair<Integer, List<Double>>(
						pair.getValue1(), rankList);

				try {
					outputQueue.put(ret);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public WeightedPersonalizedPageRank() {
	}
	
	/**
	 * 
	 * @param graph  The matrix representation of the graph.
	 */
	public WeightedPersonalizedPageRank(double[][] graph) {
		//Create the ImmutableGraph g
		g = createGraph(graph);
		ranker = new WeightedPageRankPowerMethod(g);
		init();
	}
	
	/**
	 * Create the graph using triple representation. 
	 * @param graph
	 */
	public WeightedPersonalizedPageRank(List<Triple> graph) {
		if (graph != null && !graph.isEmpty())
			g = createGraph(graph);
		if (g != null)
			ranker = new WeightedPageRankPowerMethod(g);
		init();
	}

	public void setAlpha(double alpha) {
		ranker.alpha = alpha;
	}
	
	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}
	
	public void init() {
		ranker.alpha = alpha;
		ranker.stronglyPreferential = true;
	}
	
	/**
	 * 
	 * @param g N x N matrix in which N is the number of nodes, and g[i][j] is the weight on edge (i->j) 
	 * @return
	 */
	protected ArcLabelledImmutableGraph createGraph(double[][] g) {
		int numNode = g.length;
		List<WeightedArc> list = new ArrayList<WeightedArc>();
		
		for (int i = 0; i < numNode; i++) {
			for (int j = 0; j < numNode; j++) {
				if (g[i][j] == 0.0)
					continue;
				
				WeightedArc arc = new WeightedArc(i, j, (float)g[i][j]);
				list.add(arc);
			}
		}
		
		WeightedArc[] arcList = list.toArray(new WeightedArc[1]);
		final ArcLabelledImmutableGraph graph = new WeightedBVGraph(arcList);
		
		return graph;
	}
	
	protected ArcLabelledImmutableGraph createGraph(List<Triple> g) {
		if (g == null || g.isEmpty())
			return null;
		
		List<WeightedArc> list = new ArrayList<WeightedArc>();

		for (Triple edge : g) {
			if (edge.w == 0.0)
				continue;
			
			WeightedArc arc = new WeightedArc(edge.s, edge.t, (float)edge.w);
			list.add(arc);
		}

		if (list.isEmpty())
			return null;
		WeightedArc[] arcList = list.toArray(new WeightedArc[1]);
		final ArcLabelledImmutableGraph graph = new WeightedBVGraph(arcList);
		
		return graph;
	}

	public void setPreference(double[] s) {
		ranker.preference = new DoubleArrayList(s);
	}
	
	/**
	 * Set the start vector.
	 * 
	 * @param s
	 */
	public void setStart(double[] s) {
		ranker.start = new DoubleArrayList(s);
	}
	
	public double[] computeRank() {
		try {
			ranker.stepUntil( WeightedPageRank.or(
				new WeightedPageRank.NormDeltaStoppingCriterion(threshold),
				new WeightedPageRank.IterationNumberStoppingCriterion(maxIter)));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return ranker.rank;
	}

	public void addTask(int taskNum, int eid, double weight) {
		Map<Integer, Double> task = new HashMap<Integer, Double>();
		task.put(eid, weight);
		addTask(taskNum, task);
	}
	
	public void addTask(int taskNum, Map<Integer, Double> task) {
		inputQueue.add(new Pair<Integer, Map<Integer, Double>>(taskNum, task));
	}
	
	public Map<Integer, List<Double>> computePageRankParallel(Set<Integer> entities) {
        //we simply use the id of entity as the taskNum.
		for (Integer eid : entities)
			addTask(eid, eid, 1.0);
		
		int threadNum = 32;
		for (int i = 0; i < threadNum; i++)
			addTask(0, null);
		
		Thread[] threads = new Thread[threadNum];
		for (int i = 0; i < threadNum; i++) {
			threads[i] = new PageRankThread(g);
			threads[i].start();
		}
		
		try {
			for (int i = 0; i < threadNum; i++)
				threads[i].join();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Map<Integer, List<Double>> ranks = new HashMap<Integer, List<Double>>();
		Pair<Integer, List<Double>> ret = null;
		while (!outputQueue.isEmpty()) {
			try {
				ret = outputQueue.take();
			} catch (Exception e) { e.printStackTrace(); }
			
			ranks.put(ret.getValue1(), ret.getValue2());
		}
		
		return ranks;
	}
}