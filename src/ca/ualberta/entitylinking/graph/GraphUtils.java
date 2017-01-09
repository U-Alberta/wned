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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import ca.ualberta.entitylinking.config.WNEDConfig;


import it.unimi.dsi.webgraph.ASCIIGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;

public class GraphUtils {
	private static Logger LOGGER = LogManager.getLogger(GraphUtils.class);

	/** Default window size. */
	public final static int DEFAULT_WINDOW_SIZE = 7;
	/** Default value of <var>k</var>. */
	public final static int DEFAULT_ZETA_K = 3;
	/** Default backward reference maximum length. */
	public final static int DEFAULT_MAX_REF_COUNT = 3;
	/** Default minimum interval length. */
	public final static int DEFAULT_MIN_INTERVAL_LENGTH = 4;


	public static BVGraph createBVGraph(InputStream input) {
		String basename = "tempDest";

		//Load the graph file, and then convert it to the BVGraph.
		try {
			final ImmutableGraph graph = new ASCIIGraph(input);
			BVGraph.store( graph, basename, DEFAULT_WINDOW_SIZE, 
					DEFAULT_MAX_REF_COUNT, DEFAULT_MIN_INTERVAL_LENGTH, DEFAULT_ZETA_K, 1);
		} catch (Exception e) {e.printStackTrace();}

		//load the BVGraph.
		BVGraph g = null;
		try {
			g = BVGraph.load(basename, 1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return g;
	}
	
	public static BVGraph createBVGraph(String graphStr) {
		InputStream input = new ByteArrayInputStream(graphStr.getBytes(StandardCharsets.UTF_8));
		return createBVGraph(input);
	}
	
	public static void testGraph(BVGraph graph) {
		System.out.println("numArcs: " + graph.numArcs());
		System.out.println("numNodes: " + graph.numNodes());
		
		for (int i = 0; i < graph.numNodes(); i++) {
			System.out.println("node " + i + " : " + graph.outdegree(i));
			int[] succs = graph.successorArray(i);
			for (int j = 0; j < succs.length; j++)
				System.out.print(" " + succs[j]);
			System.out.println();
		}
	}
	
    /**
     * Build the subgraph around entities from the knowledge base entity graph.
     *
     * @param entities The core component of the subgraph
     * @param e2id A name to id mapping for entities.
     * @return The graphs consisting of edges, each triple is a source, target and weight.
     */
	public static List<Triple> buildWeightedGraph(SubGraphGenerator gg, Set<String> entities,
			Map<String, Integer> e2id) {
		
		List<Triple> graph = null;
		if (WNEDConfig.directedGraph)
			graph = gg.generateExpandedWeightedDirectedGraph(entities, e2id, WNEDConfig.expandLevel);
		else
			graph = gg.generateExpandedWeightedUndirectedGraph(entities, e2id, WNEDConfig.expandLevel);

		if (entities.isEmpty() || graph == null || graph.isEmpty())
			return null;
		
        LOGGER.info("Interested entities: " + entities.size());
        LOGGER.info("Graph nodes: " + e2id.size());
        LOGGER.info("Graph edges: " + graph.size());
        LOGGER.info("edge / node: " + graph.size()*1.0/e2id.size());

		return graph;
	}
	
	public static BVGraph buildUnweightedGraph(SubGraphGenerator gg, Set<String> entities,
			Map<String, Integer> e2id) {
		String graphStr = null;
		if (WNEDConfig.directedGraph)
			graphStr = gg.generateExpandedDirectedGraph(entities, e2id, WNEDConfig.expandLevel);
		else
			graphStr = gg.generateExpandedUndirectedGraph(entities, e2id, WNEDConfig.expandLevel);

		BVGraph graph = createBVGraph(graphStr); 
        LOGGER.info("Interested entities: " + entities.size());
        LOGGER.info("Graph nodes: " + graph.numNodes());
        LOGGER.info("Graph edges: " + graph.numArcs());
        LOGGER.info("edge / node: " + graph.numArcs()*1.0/e2id.size());
        
        return graph;
	}


	
	public static void main(String[] args) {
		String eol = System.getProperty("line.separator");
		System.out.println(eol);
		String graphStr = "6" + eol +
				"1 2 3" + eol +
				"4 3 2" + eol +
				"5 4 3" + eol +
				"" + eol +
				"" + eol +
				"" + eol;
		
		
//		InputStream input = new ByteArrayInputStream(graphStr.getBytes(StandardCharsets.UTF_8));
		InputStream input = null;
		try {
			input = new FileInputStream("sample.dat");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BVGraph graph = createBVGraph(input);
		testGraph(graph);
	}
}
