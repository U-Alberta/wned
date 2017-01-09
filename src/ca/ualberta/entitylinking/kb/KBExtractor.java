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
package ca.ualberta.entitylinking.kb;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.PageCallbackHandler;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.Pair;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiPage;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiTextParser;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiXMLSAXParser;

public class KBExtractor implements PageCallbackHandler {
	PrintStream nameIDOut = null;
	PrintStream aliasOut = null;
	PrintStream redirectOut = null;
	

	public KBExtractor() {
	}

	public void init() {
		try {
			nameIDOut = new PrintStream("nameIDOut.txt");
			aliasOut = new PrintStream("aliasOut.txt");
			redirectOut = new PrintStream("redirectOut.txt");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String getAlterName(String title) {
		String ret = null;
		
		//name (appositive)
		if (title.contains("(")) {
			ret = title.substring(0, title.indexOf('('));
		//name, appositive
		} else if (title.contains(",")) {
			ret = title.substring(0, title.indexOf(','));
		//name - appositive
		} else if (title.contains(" - ")) {
			ret = title.substring(0, title.indexOf(" - "));
		} else {
			ret = title;
		}
		
		if (ret != null)
			ret = ret.trim();
		
		return ret;
	}

	/**
	 * Check if the longName is referred to by the shortName.
	 * To include as many cases as possible, we approximate the matching
	 * by checking if every character in shortname is in the longName.
	 * 
	 * @param shortName
	 * @param longName
	 * @return
	 */
	public static boolean isDisambigName(String shortName, String longName) {
		shortName = shortName.toLowerCase();
		longName = longName.toLowerCase();
		int curIdx = 0;
		for (int i = 0; i < shortName.length(); i++) {
			char c = shortName.charAt(i);
			curIdx = longName.indexOf(c);
			if (curIdx < 0)
				return false;
		}
		
		return true;
	}
	
	@Override
	public void process(WikiPage page) {
		// TODO Auto-generated method stub
		//extract title, id, alias, context, and categories.
		
		if (page == null) return;
		String title = page.getTitle().trim();
		String id = page.getID();
		
		try {
			// special pages and stub page.
			//a stub page is a page with not enough text to describe an entity.
			if (page.isSpecialPage())// || page.isStub())
				return;
			
			System.out.println(title);
			// redirect page.
			if (page.isRedirect()) {
				// redirect
				redirectOut.println(title + "\t" + page.getRedirectPage());
				
				//also alias.
				aliasOut.println(page.getRedirectPage() + "\t" + title + "\t" + "redirect");
				
				return;
			}
			
			// disambiguation page.
			if (page.isDisambiguationPage()) {
				// extract potential entities.
				Vector<String> links = page.getLinks();
				if (links == null || links.isEmpty())
					return; 
				
				String alterName = getAlterName(title);
				if (alterName == null || alterName.isEmpty())
					return;
				
				alterName = alterName.trim();
				for (String link : links) {
					link = link.trim();
					if (isDisambigName(alterName, link))
						aliasOut.println(link + "\t" + alterName + "\t" + "disambig");
				}
				
				return;
			}
			
			// entity page.
			// alias in the arch text of hyperlink.
			Vector<Pair<String, String>> links = page.getLinkPairs();
			for (Pair<String, String> linkPair : links) {
				String entity = linkPair.getValue1().trim();
				String mention = linkPair.getValue2().trim();
				if (mention.contains("more..."))
					continue;
				
				if (entity == null || entity.isEmpty() || mention == null || mention.isEmpty())
					continue;
				
				aliasOut.println(WikiTextParser.formatName(entity) + "\t" + mention + "\t" + "link");
			}
			
			// title name.
			aliasOut.println(title + "\t" + title + "\t" + "name");
			
			nameIDOut.println(title + "\t" + id);
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
	
	/**
	 * Some names are redirected to a disambiguation page, for example S.D. -> SD
	 * However, only SD is the alias of entities in the disambiguation page, and S.D.
	 * is not. This function is used to add S.D. into the alias name of their entities.
	 * 
	 * Redirect: S.D.	SD
	 * AliasFile: San Diego	SD	disambig
	 * === TO ADD: San Diego	S.D.	disambig
	 * 
	 * @param aliasFile
	 * @param redirectFile
	 */
	public void resolveRedirect(String aliasFile, String redirectFile) {
		//load redirectMap: <entity, List<redirects>>
		Map<String, List<String>> redirectMap = new HashMap<String, List<String>>();
		List<String> list = null;
		
		try {
			//load redirectFile
			String line = null;
			BufferedReader r = new BufferedReader(new FileReader(redirectFile));
			while ((line = r.readLine()) != null) {
				String toks[] = line.split("\t");
				if (redirectMap.containsKey(toks[1]))
					list = redirectMap.get(toks[1]);
				else
					list = new ArrayList<String>();
				list.add(toks[0]);
				redirectMap.put(toks[1], list);
			}
			
			r.close();
			
			//resolve redirects in the aliasFile.
			PrintStream output = new PrintStream(aliasFile + ".new");
			r = new BufferedReader(new FileReader(aliasFile));
			while ((line = r.readLine()) != null) {
				String toks[] = line.split("\t");
				if (toks.length != 3) {
					continue;
				}
				
				output.println(line);
				if (!toks[2].equals("disambig") || !redirectMap.containsKey(toks[1]))
					continue;

				list = redirectMap.get(toks[1]);
				for (String name : list) {
					output.println(toks[0] + "\t" + name + "\t" + "disambig");
				}
			}
			
			r.close();
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		KBExtractor obj = new KBExtractor();

		if (args[0].equalsIgnoreCase("extract")) {
			obj.extract(args[1]);
		} else if (args[0].equalsIgnoreCase("redirect")) {
			obj.resolveRedirect(args[1], args[2]);
		}
	}
}
