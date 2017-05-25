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
package ca.ualberta.entitylinking.graph.extraction;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.PageCallbackHandler;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.Pair;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiPage;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiTextParser;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiXMLSAXParser;

public class WikiGraphExtractor implements PageCallbackHandler {
	PrintStream pageLinkGraph = null;
	PrintStream cooccurGraph = null;

	public WikiGraphExtractor() {
		
	}
	
	public void init() {
		try {
			this.pageLinkGraph = new PrintStream("pageLinkGraph.txt");
			this.cooccurGraph = new PrintStream("co-occurGraph.txt");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void process(WikiPage page) {
		if (page == null) return;
		String title = page.getTitle().trim();
		
		try {
			//ignore special pages, stub pages, redirect pages, and disambiguation pages.
			if (page.isSpecialPage() ||
				page.isStub() || 
				page.isRedirect() ||
				page.isDisambiguationPage())
				
				return;
			
			//entity page.
			Vector<Pair<String, Integer>> linkPos = page.getLinkPos();
			for (Pair<String, Integer> link1 : linkPos) {
				String name1 = WikiTextParser.formatName(link1.getValue1());
				int pos1 = link1.getValue2();
				for (Pair<String, Integer> link2 : linkPos) {
					String name2 = WikiTextParser.formatName(link2.getValue1());
					int pos2 = link2.getValue2();
					//only keep one pair for the co-occurrance.
					if (name1.compareTo(name2) >= 0)
						continue;
					
					if (Math.abs(pos1-pos2) > 1000)
						continue;
					
					cooccurGraph.println(name1 + "\t" + name2);
				}
				
				//hyperlinks.
				pageLinkGraph.println(title + "\t" + name1);
				
				//also include the hyperlink in the co-occurrance graph.
				cooccurGraph.println(title + "\t" + name1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void extract(String wikiFile) {
		try {
			init();
			WikiXMLSAXParser.parseWikipediaDump(wikiFile, this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void aggregateGraphEdges(String graphFile) {
		try {
			Map<String, Integer> map = new HashMap<>();

			String line = null;
			BufferedReader reader = new BufferedReader(new FileReader((graphFile)));
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				int count = map.getOrDefault(line, 0);
				map.put(line, count+1);
			}
			reader.close();

			PrintStream output = new PrintStream(graphFile + ".new");
			for (String key : map.keySet()) {
				output.println(key + "\t" + map.get(key));
			}

			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		WikiGraphExtractor obj = new WikiGraphExtractor();
		String command = args[0];
		String fileName = args[1];
//		String command = "aggregate";
//		String fileName = "test.txt";
		if (command.equals("extract"))
			obj.extract(fileName);
		else if (command.equals("aggregate"))
			obj.aggregateGraphEdges(fileName);
	}
}
