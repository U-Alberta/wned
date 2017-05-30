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

import ca.ualberta.entitylinking.config.WNEDConfig;
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

public class UndirectedGraph extends WeightedGraph {
    private static Logger LOGGER = LogManager.getLogger(UndirectedGraph.class);

	public UndirectedGraph(String graphPath) {
		super(graphPath);
	}

	@Override
	public void storeGraph(String graphFile) {
		int nodeId = 0;
		WeightedArc[] arcs1 = null;
		
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
			
			arcs1 = new WeightedArc[narcs*2];
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
				nline++;
				arcs1[nline] = new WeightedArc(tid, sid, w);
				nline++;
			}
			r.close();
			
			//write the graph into files.
			writeGraph(graphPath, arcs1);

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
	protected void loadImpl(boolean offline) {
		try {
			if (offline) {
				bitgraph = BitStreamArcLabelledImmutableGraph.loadOffline(graphPath);
			} else {
				bitgraph = BitStreamArcLabelledImmutableGraph.load(graphPath);
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
		return false;
	}

	@Override
	public int outDegree(String entName) {
		if (!name2id.containsKey(entName))
			return 0;
		
		return bitgraph.outdegree(name2id.get(entName));
	}

	@Override
	public int inDegree(String entName) {
		return outDegree(entName);
	}

	@Override
	public int[] outLinks(String entName) {
		if (!name2id.containsKey(entName))
			return null;
			
		int id = name2id.get(entName);
		
		int outDegree = bitgraph.outdegree(id);
		int[] ret = new int[outDegree];
		int[] succ = bitgraph.successorArray(id);
		for (int i = 0; i < outDegree; i++)
			ret[i] = succ[i];
			
		return ret;
	}

	/**
	 * Same as outLinks.
	 */
	@Override
	public int[] inLinks(String entName) {
		return outLinks(entName);
	}

	@Override
	public Map<String, Integer> outLinks2(String entName) {
		if (!name2id.containsKey(entName))
			return null;
		
		int src = name2id.get(entName);

		ArcLabelledNodeIterator.LabelledArcIterator iter = bitgraph.successors(src);
		Map<String, Integer> map = new HashMap<String, Integer>();

		int tgt = -1;
		while ((tgt = iter.nextInt()) >= 0) {
			Label label = iter.label();
			map.put(id2name.get(tgt), label.getInt());
		}

		return map;
	}

	@Override
	public Map<String, Integer> inLinks2(String entName) {
		return outLinks2(entName);
	}

	/**
	 * Graph is transformed and saved under systemDataPath/graph
	 * GraphName is defined in the configuration file.
	 * @param argv
	 * 		argv[0] is the config file: e.g. el.config
	 * 		argv[1] is the graph file to be saved.
	 */
	public static void main(String[] argv) {
		WNEDConfig.loadConfiguration(argv[0]);
		UndirectedGraph graph = new UndirectedGraph(WNEDConfig.cooccurrenceGraphLoc);
		graph.storeGraph(argv[1]);
	}
}
