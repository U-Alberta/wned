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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import ca.ualberta.entitylinking.graph.DirectedGraph;
import ca.ualberta.entitylinking.graph.WeightedGraph;
import ca.ualberta.entitylinking.graph.UndirectedGraph;
import ca.ualberta.entitylinking.graph.similarity.measure.RelatednessMeasure;

public class RelatednessMeasureWebGraph implements RelatednessMeasure {
	/**
	 * Data used to generate the entity relatedness measure.
	 */
	public enum DataDependency {
		// Use in-links for relatedness measure.
		pageLinksIn,
		
		// Use in-links for relatedness measure, with the link count considered.
		pageCountLinksIn,

		//use out-links for relatedness measure.
		pageLinksOut,
		
		//Use out-links for relatedness measure, with the link count considered.
		pageCountLinksOut,

		//Use both in- and out-links for relatedness measure.
		pageLinks,

		//Use both in- and out-links for relatedness measure, with link count considered.
		pageCountLinks,
		
		//Use the link counts as the relatedness measure.(directed graph)
		directLinkCounts,
		
		//Use the link counts as the relatedness measure. (undirected graph)
		undirectLinkCounts,

		//Use links in undirected graph for relatedness measure.
		undirectPageLinks,

		//Use links in undirected graph for relatedness measure, with link count considered.
		undirectPageCountLinks

	};
	
	private WeightedGraph graph = null;
	private DataDependency dependency = DataDependency.pageLinksIn;

	public RelatednessMeasureWebGraph(String basename, boolean direct) {
		if (direct)
			graph = new DirectedGraph(basename);
		else
			graph = new UndirectedGraph(basename);
	}
	
	public RelatednessMeasureWebGraph(WeightedGraph graph) {
		this.graph = graph;
	}
	
	public void setDependency(DataDependency dependency) {
		this.dependency = dependency;
	}
	
	@Override
	public double semanticRelatedness(String e1, String e2) {
		if (e1.equals(e2))
			return 1.0;
		
		switch (dependency) { 
			case pageLinksIn:
				return semanticRelatednessInLinks(e1, e2);
			case pageCountLinksIn:
				return semanticRelatednessInLinksCount(e1, e2);
			case pageLinksOut:
				return semanticRelatednessOutLinks(e1, e2);
			case pageCountLinksOut:
				return semanticRelatednessOutLinksCount(e1, e2);
			case pageLinks:
				return semanticRelatednessLinks(e1, e2);
			case pageCountLinks:
				return semanticRelatednessLinksCount(e1, e2);
			case directLinkCounts:
				return 0.0;
			case undirectLinkCounts:
				return undirectLinkCounts(e1, e2);
			case undirectPageLinks:
				return semanticRelatednessUndirectPageLinks(e1, e2);
			case undirectPageCountLinks:
				return semanticRelatednessUndirectLinkCounts(e1, e2);
			default:
				return 0.0;
		}
	}
	
	/**
	 * sr(a,b) = (log(max(|A|,|B|)) - log(|AB|)) / (log(|W|)-log(min(|A|,|B|)))
	 */
	public double computeSemanticRelatedness(int[] links1, int[] links2) {
		if (links1 == null || links2 == null || links1.length == 0 || links2.length == 0)
			return 1.0;
		
		HashSet<Integer> set = new HashSet<Integer>();
		for (int i = 0; i < links1.length; i++)
			set.add(links1[i]);
		
		int common = 0;
		for (int i = 0; i < links2.length; i++)
			if (set.contains(links2[i]))
				common++;
		
		if (common == 0)
			return 1.0;
		
		double sr = (Math.log(Math.max(links1.length, links2.length)) - Math.log(common)) / 
				(Math.log(graph.numNodes()) - Math.log(Math.min(links1.length, links2.length)));
		
		if (Double.isNaN(sr)) {
			System.out.println("++++++++++++++");
			System.out.println("links1: " + links1.length + "\tlinks2: " +
					links2.length + "\tcommon: " + common + "\tnumNodes: " + graph.numNodes());
		}
		return sr;
	}

	public double semanticRelatednessInLinks(String e1, String e2) {
		int[] inLinks1 = graph.inLinks(e1);
		int[] inLinks2 = graph.inLinks(e2);

		double sr = computeSemanticRelatedness(inLinks1, inLinks2);
		if (sr < 0.0 || sr >= 1.0)
			return 0.0;
		
		return 1 - sr;
	}
	
	public double semanticRelatednessOutLinks(String e1, String e2) {
		int[] outLinks1 = graph.outLinks(e1);
		int[] outLinks2 = graph.outLinks(e2);

		double sr = computeSemanticRelatedness(outLinks1, outLinks2);
		if (sr < 0.0 || sr >= 1.0)
			return 0.0;
		
		return 1 - sr;
	}
	
	private double computeSemanticRelatednessCount(Map<String, Integer> links1, 
			Map<String, Integer> links2) {
		if (links1 == null || links1.isEmpty()|| 
				links2 == null || links2.isEmpty()) {
			return 1.0;
		}
		
		int numLink1 = 0, numLink2 = 0, common = 0;
		for (String name : links1.keySet())
			numLink1 += links1.get(name);

		for (String name : links2.keySet()) {
			numLink2 += links2.get(name);
			if (links1.containsKey(name)) {
				if (links1.get(name) > links2.get(name))
					common += links2.get(name);
				else
					common += links1.get(name);
			}
		}
		
		if (common == 0 || numLink1 == 0 || numLink2 == 0)
			return 1.0;
		
		double sr = (Math.log(Math.max(numLink1, numLink2)) - Math.log(common)) / 
				(Math.log(graph.numNodes()) - Math.log(Math.min(numLink1, numLink2)));
		
		return sr;
	}
	
	public double semanticRelatednessInLinksCount(String e1, String e2) {
		Map<String, Integer> inLinks1 = graph.inLinks2(e1);
		Map<String, Integer> inLinks2 = graph.inLinks2(e2);
		
		double sr = computeSemanticRelatednessCount(inLinks1, inLinks2);
		if (sr < 0 || sr >= 1.0)
			return 0.0;
		
		return 1- sr;
	}

	public double semanticRelatednessOutLinksCount(String e1, String e2) {
		Map<String, Integer> outLinks1 = graph.outLinks2(e1);
		Map<String, Integer> outLinks2 = graph.outLinks2(e2);
		
		double sr = computeSemanticRelatednessCount(outLinks1, outLinks2);
		if (sr < 0 || sr >= 1.0)
			return 0.0;
		
		return 1- sr;
	}
	
	public double semanticRelatednessLinks(String e1, String e2) {
		int[] inLinks1 = graph.inLinks(e1);
		int[] inLinks2 = graph.inLinks(e2);
		int[] outLinks1 = graph.outLinks(e1);
		int[] outLinks2 = graph.outLinks(e2);

		HashSet<Integer> set1 = new HashSet<Integer>();
		if (inLinks1 != null)
			for (int i = 0; i < inLinks1.length; i++)
				set1.add(inLinks1[i]);
		if (outLinks1 != null)
			for (int i = 0; i < outLinks1.length; i++)
				set1.add(outLinks1[i]);
		
		if (set1.isEmpty())
			return 0.0;

		HashSet<Integer> set2 = new HashSet<Integer>();
		if (inLinks2 != null)
			for (int i = 0; i < inLinks2.length; i++)
				set2.add(inLinks2[i]);
		if (outLinks2 != null)
			for (int i = 0; i < outLinks2.length; i++)
				set2.add(outLinks2[i]);
		
		if (set2.isEmpty())
			return 0.0;

		int count1 = set1.size();
		int count2 = set2.size();
		int common = 0;
		for (Integer i : set1)
			if (set2.contains(i))
				common++;
		
		if (common == 0 || count1 == 0 || count2 == 0)
			return 0.0;
		
		double sr = (Math.log(Math.max(count1, count2)) - Math.log(common)) / 
				(Math.log(graph.numNodes()) - Math.log(Math.min(count1, count2)));
		
		if (sr < 0.0 || sr >= 1.0)
			return 0.0;
		
		return 1- sr;
	}

	public double semanticRelatednessLinksCount(String e1, String e2) {
		Map<String, Integer> inLinks1 = graph.inLinks2(e1);
		Map<String, Integer> inLinks2 = graph.inLinks2(e2);
		Map<String, Integer> outLinks1 = graph.outLinks2(e1);
		Map<String, Integer> outLinks2 = graph.outLinks2(e2);

		Map<String, Integer> links1 = new HashMap<String, Integer>();
		Map<String, Integer> links2 = new HashMap<String, Integer>();
		links1.putAll(inLinks1);
		for (String name : outLinks1.keySet())
			if (links1.containsKey(name))
				links1.put(name, links1.get(name) + outLinks1.get(name));
			else
				links1.put(name, outLinks1.get(name));
		
		if (links1.isEmpty())
			return 0.0;

		links2.putAll(inLinks2);
		for (String name : outLinks2.keySet())
			if (links2.containsKey(name))
				links2.put(name, links2.get(name) + outLinks2.get(name));
			else
				links2.put(name, outLinks2.get(name));
		
		if (links2.isEmpty())
			return 0.0;
		
		int numLink1 = 0, numLink2 = 0, common = 0;
		for (String name : links1.keySet())
			numLink1 += links1.get(name);

		for (String name : links2.keySet()) {
			numLink2 += links2.get(name);
			if (links1.containsKey(name)) {
				if (links1.get(name) > links2.get(name))
					common += links2.get(name);
				else
					common += links1.get(name);
			}
		}
		
		if (common == 0 || numLink1 == 0 || numLink2 == 0)
			return 0.0;
		
		double sr = (Math.log(Math.max(numLink1, numLink2)) - Math.log(common)) / 
				(Math.log(graph.numNodes()) - Math.log(Math.min(numLink1, numLink2)));

		if (sr < 0.0 || sr >= 1.0)
			return 0.0;

		return 1 - sr;
	}
	
	public double inLinkCounts(String e1, String e2) {
		Map<String, Integer> inLinks1 = graph.inLinks2(e1);
		Map<String, Integer> inLinks2 = graph.inLinks2(e2);

		return 0;
	}
	
	public double semanticRelatednessUndirectPageLinks(String e1, String e2) {
		int[] links1 = graph.outLinks(e1);
		int[] links2 = graph.outLinks(e2);
		
		double sr = computeSemanticRelatedness(links1, links2);
		if (sr < 0.0 || sr >= 1.0)
			return 0.0;
		
		return 1 - sr;
	}
	
	public double semanticRelatednessUndirectLinkCounts(String e1, String e2) {
		Map<String, Integer> links1 = graph.outLinks2(e1);
		Map<String, Integer> links2 = graph.outLinks2(e2);
		
		double sr = computeSemanticRelatednessCount(links1, links2);
		if (sr < 0 || sr >= 1.0)
			return 0.0;

		return 1- sr;
	}
	
	public double undirectLinkCounts(String e1, String e2) {
		Map<String, Integer> links = graph.outLinks2(e1);
		if (links == null || !links.containsKey(e2))
			return 0.0;
		
		return links.get(e2);
	}

}
