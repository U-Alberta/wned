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
package ca.ualberta.entitylinking.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import ca.ualberta.entitylinking.graph.similarity.measure.ParallelRelatednessComputation;
import ca.ualberta.entitylinking.config.WNEDConfig;

public class SubGraphGenerator {
	private int in_threshold = 200;
	private int undirect_threshold = 600;
	
	private int nodeID = 0;
	
	private WeightedGraph graph = null;
	private ParallelRelatednessComputation measurer = null;
	
	public SubGraphGenerator(WeightedGraph graph) {
		this.graph = graph;
		this.measurer = new ParallelRelatednessComputation(graph);
		
		if (!graph.isDirected())
			in_threshold = undirect_threshold;
	}
	
	public SubGraphGenerator(String basename) {
		this(new DirectedGraph(basename));
	}

	private boolean addWeightedEdges(List<Triple> g,
			Set<String> expandedSet, 
			Map<String, Integer> nameIDMap,
			String name1, boolean inlink) {

        if (name1 == null || name1.isEmpty()) return false;

		Map<String, Integer> links = null;
		if (inlink)
			links = graph.inLinks2(name1);
		else
			links = graph.outLinks2(name1);
		
		if (links == null || links.isEmpty())
			return false;

		boolean ret = false;
		int id1 = -1, id2 = -1;
		if (nameIDMap.containsKey(name1))
			id1 = nameIDMap.get(name1);

		for (String name2 : links.keySet()) {
            if (name2 == null || name2.isEmpty()) continue;

			// the same entity.
			if (name1.compareTo(name2) == 0)
				continue;

			// not in the entity set.
			if (!expandedSet.contains(name2))
				continue;

			if (id1 < 0) {
				id1 = nodeID++;
				nameIDMap.put(name1, id1);
			}

			if (nameIDMap.containsKey(name2)) {
				id2 = nameIDMap.get(name2);
			} else {
				id2 = nodeID++;
				nameIDMap.put(name2, id2);
			}

			if (inlink)
				g.add(new Triple(id2, id1, links.get(name2)));
			else
				g.add(new Triple(id1, id2, links.get(name2)));
			
			ret = true;
		}
		
		return ret;
	}

	private List<Triple> generateWeightedTargetGraphImpl(Set<String> entities,
			Set<String> expandedSet, Map<String, Integer> nameIDMap) {
		List<Triple> g = new ArrayList<Triple>();

		nodeID = 0;

		//now build the graph out of the entities in the expanded set.
		for (String name : entities) {
			//inlinks
			addWeightedEdges(g, expandedSet, nameIDMap, name, true);
			//outlinks
			addWeightedEdges(g, expandedSet, nameIDMap, name, false);
		}

		return g;
	}

	private List<Triple> generateExpandedWeightedGraphImpl(Set<String> entities, 
			Map<String, Integer> nameIDMap, int level) {

		List<Triple> g = new ArrayList<Triple>();
		Set<String> expandedSet = new HashSet<String>();
		expandedSet.addAll(entities);
		
		long start = System.currentTimeMillis();

		while (level > 0) {
			String[] names = expandedSet.toArray(new String[1]);
			for (String name1 : names) {
				boolean expanded = false;

				// Expand from the inlinks
				Map<String, Integer> inlinks = graph.inLinks2(name1);
				if (inlinks != null) {
					for (String name2 : inlinks.keySet()) {
						// get rid of unpopular entity (with small number of inlinks)
						if (graph.inDegree(name2) < in_threshold)
							continue;
						expandedSet.add(name2);
						expanded = true;
					}
				}
				
				//Just to make sure all entities in the original set are connected.
				if (!expanded && entities.contains(name1) && inlinks != null)
					expandedSet.addAll(inlinks.keySet());

				if (!graph.isDirected())
					continue;
				
				//expand from the outlinks.
				//This only happens when we are handling a directed graph.
				expanded = false;
				Map<String, Integer> outlinks = graph.outLinks2(name1);
				if (outlinks != null) {
					for (String name2 : outlinks.keySet()) {
						// get rid of unpopular entity (with small number of inlinks)
						if (graph.inDegree(name2) < in_threshold)
							continue;

						expandedSet.add(name2);
						expanded = true;
					}
				}
				
				//Just to make sure all entities in the original set are connected.
				if (!expanded && entities.contains(name1) && outlinks != null)
					expandedSet.addAll(outlinks.keySet());
			}
			
			level--;
		}
		
		long end = System.currentTimeMillis();
		System.out.println("\ttime1: " + (end - start));
		start = end;

		g = generateWeightedTargetGraphImpl(entities, expandedSet, nameIDMap);

		end = System.currentTimeMillis();
		System.out.println("\ttime2: " + (end - start));

		return g;
	}

	private static String genKey(int s, int t) {
		return new String(Integer.toString(s) + "-" + Integer.toString(t));
	}
	
	private List<Triple> expandFromWeightedDirectedGraph(
			Set<String> entities, Map<String, Integer> nameIDMap, int level) {

		List<Triple> g = generateExpandedWeightedDirectedGraph(entities, nameIDMap, level);
		if (g == null || g.isEmpty())
			return g;
		
		//Change the directed graph to an undirected graph.
		Map<String, Double> map = new HashMap<String, Double>(); 
		for (Triple triple : g) {
			String key = null;
			if (triple.s < triple.t)
				key = genKey(triple.s, triple.t);
			else
				key = genKey(triple.t, triple.s);
			
			if (map.containsKey(key)) {
				if (map.get(key) < triple.w)
					map.put(key, triple.w);
			} else {
				map.put(key, triple.w);
			}
		}
		
		List<Triple> ug = new ArrayList<Triple>();
		for (String key : map.keySet()) {
			String[] toks = key.split("-");
			int s = Integer.parseInt(toks[0]);
			int t = Integer.parseInt(toks[1]);
			ug.add(new Triple(s, t, map.get(key)));
			ug.add(new Triple(t, s, map.get(key)));
		}
		
		return ug;

	}

	private List<Triple> expandFromWeightedUndirectedGraph(
			Set<String> entities, Map<String, Integer> nameIDMap, int level) {
		List<Triple> g = generateExpandedWeightedGraphImpl(entities, nameIDMap, level);
		
		return g;
	}
	
	public List<Triple> generateExpandedWeightedUndirectedGraph(Set<String> entities, 
			Map<String, Integer> nameIDMap, int level) {
		
		if (graph.isDirected())
			return expandFromWeightedDirectedGraph(entities, nameIDMap, level);
		else
			return expandFromWeightedUndirectedGraph(entities, nameIDMap, level);
	}

	/**
	 * Generate an expanded graph using the inlinks and outlinks of the given entities.
	 * @param entities
	 * @param nameIDMap
	 * @param level The number of expansions.
	 * 
	 * @return
	 */
	public List<Triple> generateExpandedWeightedDirectedGraph(Set<String> entities, 
			Map<String, Integer> nameIDMap, int level) {

		//we cannot generate directed graph from an undirected graph.
		if (!graph.isDirected())
			return null;
		
		return generateExpandedWeightedGraphImpl(entities, nameIDMap, level);
	}

	private boolean addEdges(Map<Integer, Set<Integer>> g,
			Set<String> expandedSet, 
			Map<String, Integer> nameIDMap,
			String name1, boolean inlink) {
		
		Map<String, Integer> links = null;
		if (inlink)
			links = graph.inLinks2(name1);
		else
			links = graph.outLinks2(name1);
		
		if (links == null || links.isEmpty())
			return false;

		boolean ret = false;
		int id1 = -1, id2 = -1;
		if (nameIDMap.containsKey(name1))
			id1 = nameIDMap.get(name1);

		for (String name2 : links.keySet()) {
			// the same entity.
			if (name1.compareTo(name2) == 0)
				continue;

			// not in the entity set.
			if (!expandedSet.contains(name2))
				continue;

			if (id1 < 0) {
				id1 = nodeID++;
				nameIDMap.put(name1, id1);
			}

			if (nameIDMap.containsKey(name2)) {
				id2 = nameIDMap.get(name2);
			} else {
				id2 = nodeID++;
				nameIDMap.put(name2, id2);
			}

			Set<Integer> neighbors = null;
			if (inlink) {
				neighbors = g.get(id2);
				if (neighbors == null)
					neighbors = new TreeSet<Integer>();
				neighbors.add(id1);
				g.put(id2, neighbors);
			} else {
				neighbors = g.get(id1);
				if (neighbors == null)
					neighbors = new TreeSet<Integer>();
				neighbors.add(id2);
				g.put(id1, neighbors);
			}
			
			ret = true;
		}
		
		return ret;
	}

	private Map<Integer, Set<Integer>> generatedTargetGraphImpl(Set<String> entities,
			Set<String> expandedSet, Map<String, Integer> nameIDMap) {
		Map<Integer, Set<Integer>> g = new HashMap<Integer, Set<Integer>>();

		nodeID = 0;

		//now build the graph out of the entities in the expanded set.
		for (String name : entities) {
			//inlinks
			addEdges(g, expandedSet, nameIDMap, name, true);
			//outlinks
			addEdges(g, expandedSet, nameIDMap, name, false);
		}

		return g;
	}
	
	private Map<Integer, Set<Integer>> generateExpandedGraphImpl(Set<String> entities,
			Map<String, Integer> nameIDMap, int level) {

		Set<String> expandedSet = new HashSet<String>();
		expandedSet.addAll(entities);
		
		long start = System.currentTimeMillis();

		while (level > 0) {
			String[] names = expandedSet.toArray(new String[1]);
			for (String name1 : names) {
				boolean expanded = false;

				// Expand from the inlinks
				Map<String, Integer> inlinks = graph.inLinks2(name1);
				if (inlinks != null) {
					for (String name2 : inlinks.keySet()) {
						// get rid of unpopular entity (with small number of inlinks)
						if (graph.inDegree(name2) < in_threshold)
							continue;
						expandedSet.add(name2);
						expanded = true;
					}
				}
				
				//Just to make sure all entities in the original set are connected.
				if (!expanded && entities.contains(name1) && inlinks != null)
					expandedSet.addAll(inlinks.keySet());

				if (!graph.isDirected())
					continue;
				
				//expand from the outlinks.
				//This only happens when we are handling a directed graph.
				expanded = false;
				Map<String, Integer> outlinks = graph.outLinks2(name1);
				if (outlinks != null) {
					for (String name2 : outlinks.keySet()) {
						// get rid of unpopular entity (with small number of inlinks)
						if (graph.inDegree(name2) < in_threshold)
							continue;

						expandedSet.add(name2);
						expanded = true;
					}
				}
				
				//Just to make sure all entities in the original set are connected.
				if (!expanded && entities.contains(name1) && outlinks != null)
					expandedSet.addAll(outlinks.keySet());
			}
			
			level--;
		}
		
		long end = System.currentTimeMillis();
		System.out.println("\ttime1: " + (end - start));
		start = end;

		Map<Integer, Set<Integer>> g = generatedTargetGraphImpl(entities, expandedSet, nameIDMap);

		end = System.currentTimeMillis();
		System.out.println("\ttime2: " + (end - start));

		return g;
	}

	private Map<Integer, Set<Integer>> expandFromDirectedGraph(
			Set<String> entities, Map<String, Integer> nameIDMap, int level) {

		if (!graph.isDirected()) return null;

		Map<Integer, Set<Integer>> g = generateExpandedGraphImpl(entities, nameIDMap, level);
		if (g == null || g.isEmpty())
			return g;
		
		//Change the directed graph to an undirected graph.
		Set<Integer> set1 = null, set2= null;
		int nodeNum = nameIDMap.size();
		for (int i = 0; i < nodeNum; i++) {
			set1 = g.get(i);
			if (set1== null || set1.isEmpty())	continue;
			
			for (Integer j : set1) {
				set2 = g.get(j);
				if (set2 == null)
					set2 = new TreeSet<Integer>();
				set2.add(i);
			}
		}
		
		return g;
	}

	private Map<Integer, Set<Integer>> expandFromUndirectedGraph(
			Set<String> entities, Map<String, Integer> nameIDMap, int level) {
		return generateExpandedGraphImpl(entities, nameIDMap, level);
	}
	
	public String generateExpandedUndirectedGraph(Set<String> entities, 
			Map<String, Integer> nameIDMap, int level) {
		
		Map<Integer, Set<Integer>> g = null;
		if (graph.isDirected())
			g = expandFromDirectedGraph(entities, nameIDMap, level);
		else
			g = expandFromUndirectedGraph(entities, nameIDMap, level);
		
		return toString(g, nameIDMap.size());
	}

	/**
	 * Generate an expanded graph using the inlinks and outlinks of the given entities.
	 * @param entities
	 * @param nameIDMap
	 * @param level The number of expansions.
	 * 
	 * @return	String representation of the graph.
	 */
	public String generateExpandedDirectedGraph(Set<String> entities, 
			Map<String, Integer> nameIDMap, int level) {

		//we cannot generate directed graph from an undirected graph.
		if (!graph.isDirected())
			return null;
	
		Map<Integer, Set<Integer>> g = generateExpandedGraphImpl(entities, nameIDMap, level);
		return toString(g, nameIDMap.size());
	}

	private static String toString(Set<Integer> set) {
		if (set == null || set.isEmpty())
			return "";
		
		StringBuilder sb = new StringBuilder();
		for (Integer i : set)
			sb.append(i + " ");
		
		return sb.toString().trim();
	}

	private static String toString(Map<Integer, Set<Integer>> g, int nodeNum) {
		System.out.println("nodeNum: " + nodeNum);
		
		String eol = System.getProperty("line.separator");
		StringBuilder sb = new StringBuilder();
		sb.append(nodeNum + eol);
		
		Set<Integer> set = null;
		for (int i = 0; i < nodeNum; i++) {
			set = g.get(i);
			
//			System.out.println(i + ": " + (set != null ? set.size() : 0));
			
			sb.append(toString(set) + eol);
		}
		
		return sb.toString();
	}
	

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		WNEDConfig.loadConfiguration("el.config");

        //Load the Knowledge base graph.
		WeightedGraph g = null;
		if (WNEDConfig.directedGraph)
			g = new DirectedGraph(WNEDConfig.linkGraphLoc);
		else
			g = new UndirectedGraph(WNEDConfig.cooccurrenceGraphLoc);

		g.load();
		SubGraphGenerator gg = new SubGraphGenerator(g);

		Set<String> entities = new HashSet<String>();
		entities.add("University of Alberta");
		Map<String, Integer> map = new HashMap<String, Integer>();
		
		List<Triple> graph = gg.generateExpandedWeightedUndirectedGraph(entities, map, 1);
		System.out.println("nodeNum: " + map.size());
		for (Triple t : graph) {
			System.out.println(t.s + "\t" + t.t + "\t" + t.w);
		}
	}
}
