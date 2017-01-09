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

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;

import es.yrbcn.graph.weighted.WeightedArc;
import es.yrbcn.graph.weighted.WeightedBVGraph;

import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.labelling.ArcLabelledImmutableGraph;
import it.unimi.dsi.webgraph.labelling.BitStreamArcLabelledImmutableGraph;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public abstract class WeightedGraph {
    private static Logger LOGGER = LogManager.getLogger(WeightedGraph.class);

    protected String graphPath = null;
	protected ArcLabelledImmutableGraph bitgraph = null;
	protected Map<String, Integer> name2id = null;
	protected Map<Integer, String> id2name = null;

	public WeightedGraph(String graphPath) {
		this.graphPath = graphPath;
	}
	
	abstract public void storeGraph(String graphFile);
	abstract protected void loadImpl(boolean offline);
	abstract public boolean isDirected();
	abstract public int outDegree(String entName);
	abstract public int inDegree(String entName);
	abstract public int[] outLinks(String entName);
	abstract public int[] inLinks(String entName);
	abstract public Map<String, Integer> outLinks2(String entName);
	abstract public Map<String, Integer> inLinks2(String entName);

	public void load() {
		loadImpl(false);
	}

	public void loadOffline() {
		loadImpl(true);
	}

	protected void writeGraph(String bname, WeightedArc[] arcs) {
		WeightedBVGraph graph = new WeightedBVGraph(arcs);
		LOGGER.info( "Compressing graph" );
		
		try {
			BVGraph.store(graph, bname + ArcLabelledImmutableGraph.UNDERLYINGGRAPH_SUFFIX);

			LOGGER.info("Storing labels");
			BitStreamArcLabelledImmutableGraph.store(graph, bname, bname + ArcLabelledImmutableGraph.UNDERLYINGGRAPH_SUFFIX);
		} catch (Exception e) {
			e.printStackTrace();
		}

        LOGGER.info("Graph stored!");
	}
	
	@SuppressWarnings("unchecked")
	protected void loadNameIDMap() {
		try {
			ObjectInputStream input = new ObjectInputStream(
					new FileInputStream(graphPath + ".map.name2id"));
			name2id = (Map<String, Integer>)input.readObject();
			input.close();
			
			input = new ObjectInputStream(
					new FileInputStream(graphPath + ".map.id2name"));
			id2name = (Map<Integer, String>)input.readObject();
			input.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public long numArcs() {
		return bitgraph.numArcs();
	}
	
	public int numNodes() {
		return bitgraph.numNodes();
	}
	
	public boolean containsNode(String entName) {
		return name2id.containsKey(entName);
	}
	
}
