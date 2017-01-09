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

import it.unimi.dsi.webgraph.labelling.ArcLabelledImmutableGraph;
import it.unimi.dsi.webgraph.labelling.ArcLabelledNodeIterator;
import it.unimi.dsi.webgraph.labelling.BitStreamArcLabelledImmutableGraph;
import it.unimi.dsi.webgraph.labelling.Label;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import es.yrbcn.graph.weighted.WeightedArc;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class DirectedGraph extends WeightedGraph {
    private static Logger LOGGER = LogManager.getLogger(DirectedGraph.class);

    String graphPath2 = null;
	ArcLabelledImmutableGraph bitgraph2 = null;
	
	public DirectedGraph(String graphPath) {
		super(graphPath);
		this.graphPath2 = graphPath + "2";
	}
	
	@Override
	public void storeGraph(String graphFile) {
		int nodeId = 0;
		WeightedArc[] arcs1 = null, arcs2 = null;
		
		if (name2id == null)	name2id = new HashMap<String, Integer>();
		if (id2name == null)	id2name = new HashMap<Integer, String>();
		try {
			int sid = -1, tid = -1;
			int narcs = 0, nline = 0;
			
			//Read the arscii file.
			String line = null;
			BufferedReader r = new BufferedReader(new FileReader(graphFile));
			while (r.readLine() != null)
				narcs++;
			r.close();
			
			arcs1 = new WeightedArc[narcs];
			arcs2 = new WeightedArc[narcs];
			r = new BufferedReader(new FileReader(graphFile));
			while ((line = r.readLine()) != null) {
				String toks[] = line.split("\t");
				String s = toks[0];
				String t = toks[1];
				float w = Float.parseFloat(toks[2]);

				if (!name2id.containsKey(s)) {
					name2id.put(s, nodeId);
					nodeId++;
				}
				sid = name2id.get(s);
				
				if (!name2id.containsKey(t)) {
					name2id.put(t, nodeId);
					nodeId++;
				}
				tid = name2id.get(t);

				arcs1[nline] = new WeightedArc(sid, tid, w);
				arcs2[nline] = new WeightedArc(tid, sid, w);
				nline++;
			}
			r.close();
			
			//write the graph into files.
			writeGraph(graphPath, arcs1);
			writeGraph(graphPath2, arcs2);

			//generate the id2name mapping.
			for (String name : name2id.keySet())
				id2name.put(name2id.get(name), name);
			
			//write the mapping into a file.
			ObjectOutputStream output = new ObjectOutputStream(
					new FileOutputStream(graphPath + ".map.name2id"));
			output.writeObject(name2id);
			output.close();
			
			output = new ObjectOutputStream(
					new FileOutputStream(graphPath + ".map.id2name"));
			output.writeObject(id2name);
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void loadImpl(boolean offline) {
		try {
			if (offline) {
				bitgraph = BitStreamArcLabelledImmutableGraph.loadOffline(graphPath);
				bitgraph2 = BitStreamArcLabelledImmutableGraph.loadOffline(graphPath2);
			} else {
				bitgraph = BitStreamArcLabelledImmutableGraph.load(graphPath);
				bitgraph2 = BitStreamArcLabelledImmutableGraph.load(graphPath2);
			}

			if (bitgraph.randomAccess())
				LOGGER.info("Support random access!");
			else
				LOGGER.info("Not support random access!");
			
			loadNameIDMap();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean isDirected() {
		return true;
	}

	/**
	 * A generic function to find the in-degree or out-degree of a node.
	 * @param entName
	 * @param out
	 * @return in or out-degree of the given node.
	 */
	private int degree(String entName, boolean out) {
		if (!name2id.containsKey(entName))
			return 0;
			
		int id = name2id.get(entName);
		
		ArcLabelledImmutableGraph g = null;
		if (out)
			g = bitgraph;
		else
			g = bitgraph2;

		return g.outdegree(id);
	}
	
	@Override
	public int outDegree(String entName) {
		return degree(entName, true);
	}
	
	@Override
	public int inDegree(String entName) {
		return degree(entName, false);
	}

	private int[] links(String entName, boolean out) {
		if (!name2id.containsKey(entName))
			return null;
			
		int id = name2id.get(entName);
		
		ArcLabelledImmutableGraph g = null;
		if (out)
			g = bitgraph;
		else
			g = bitgraph2;

		int outDegree = g.outdegree(id);
		int[] ret = new int[outDegree];
		int[] succ = g.successorArray(id);
		for (int i = 0; i < outDegree; i++)
			ret[i] = succ[i];
			
		return ret;
	}
	
	public int[] outLinks(String entName) {
		return links(entName, true);
	}

	public int[] inLinks(String entName) {
		return links(entName, false);
	}

	private Map<String, Integer> links2(String entName, boolean out) {
		if (!name2id.containsKey(entName))
			return null;
		
		int src = name2id.get(entName);

		ArcLabelledImmutableGraph g = null;
		if (out)
			g = bitgraph;
		else
			g = bitgraph2;
		
		ArcLabelledNodeIterator.LabelledArcIterator iter = g.successors(src);
		Map<String, Integer> map = new HashMap<String, Integer>();

		int tgt = -1;
		while ((tgt = iter.nextInt()) >= 0) {
			Label label = iter.label();
			map.put(id2name.get(tgt), label.getInt());
		}

		return map;
	}

	public Map<String, Integer> outLinks2(String entName) {
		return links2(entName, true);
	}
	
	public Map<String, Integer> inLinks2(String entName) {
		return links2(entName, false);
	}

	public static void main(String[] argv) {
		DirectedGraph graph = new DirectedGraph(argv[0]);
		graph.storeGraph(argv[1]);
	}
}
