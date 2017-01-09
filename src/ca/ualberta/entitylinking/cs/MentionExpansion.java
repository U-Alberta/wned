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
package ca.ualberta.entitylinking.cs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ualberta.entitylinking.config.WNEDConfig;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopAnalyzer;

import ca.ualberta.entitylinking.common.data.Document;
import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.data.Sentence;
import ca.ualberta.entitylinking.common.data.Token;
import ca.ualberta.entitylinking.utils.Pair;
import ca.ualberta.entitylinking.utils.Rank;
import ca.ualberta.entitylinking.utils.similarity.StringSim;
import ca.ualberta.entitylinking.utils.StringUtils;
import ca.ualberta.entitylinking.common.nlp.StanfordNER;
import ca.ualberta.entitylinking.common.nlp.OrthoMatcherCoref;

public class MentionExpansion {
	private final static String redirectFile = WNEDConfig.systemDataPath + "/wikipedia-20130604/redirectOut.txt";
	private final static double abbrevThreshold = 0.3;
	private final static boolean useFuzzyMatch = false;
	
	private StanfordNER ner = null;
	private OrthoMatcherCoref orthoMatcher = null;
	private CandidateSelection cs = null;
	private CharArraySet stopwords = null;
	private Map<String, List<String>> redirectMap = null;

	public MentionExpansion() {
		Set<String> allowedEntityTypes = new HashSet<String>();
		allowedEntityTypes.add(Entity.PERSON);
		allowedEntityTypes.add(Entity.ORGANIZATION);
		allowedEntityTypes.add(Entity.LOCATION);
		allowedEntityTypes.add(Entity.MISC);

		ner = new StanfordNER(allowedEntityTypes);
		orthoMatcher = new OrthoMatcherCoref();
		
		cs = new CandidateSelectionLucene(0.001f);
		
		stopwords = (CharArraySet) StopAnalyzer.ENGLISH_STOP_WORDS_SET;
		
		redirectMap = loadRedirect(redirectFile);
	}
	
	public MentionExpansion(
			StanfordNER ner,
			OrthoMatcherCoref orthoMatcher,
			CandidateSelection cs) {
		
		this.ner = ner;
		this.orthoMatcher = orthoMatcher;
		this.cs = cs;
		
		stopwords = (CharArraySet) StopAnalyzer.ENGLISH_STOP_WORDS_SET;
		redirectMap = loadRedirect(redirectFile);
	}

	public Map<String, List<String>> loadRedirect(String redirectFile) {
		Map<String, List<String>> map = new HashMap<String, List<String>>();
		List<String> list = null;
		
		try {
			String line = null;
			BufferedReader r = new BufferedReader(new FileReader(redirectFile));
			while ((line = r.readLine()) != null) {
				line = line.trim();
				String toks[] = line.split("\t");
				if (map.containsKey(toks[1]))
					list = map.get(toks[1]);
				else
					list = new ArrayList<String>();
				
				list.add(toks[0]);
				map.put(toks[1], list);
			}
			
			r.close();
		} catch (Exception e) {
			
		}
		
		return map;
	}
	
	/**
	 * Verify if a given name is an abbreviation according to the following rules:
	 *  - Has only one word.
	 *  - First letter is alphabetic or numeric.
	 *  - Its length is between 2 and 10.
	 *  - Contains at least 2 capital letter.
	 *  - Not a member of a predefined set of person and location names[TODO]
	 *  - Not a member of user-defined list of stopwords[TODO]
	 *  
	 * @param mention
	 * @return
	 */
	private boolean isAbbrev(Mention mention) {
		//Many location are spelled with all capital letters.
		if (mention == null || mention.getEntity().getType() == Entity.LOCATION) 
			return false;
		
		String name = mention.getName();
		if (name == null || name.isEmpty())
			return false;
		
		//Abbreviation consists of only one word.(i.e. no space)
		if (name.contains(" "))
			return false;

		//First letter is alphabetic or numeric.
		if (!Character.isLetterOrDigit(name.charAt(0)))
			return false;
		
		//length should be between 2 and 10
		if (name.length() < 2 || name.length() > 10)
			return false;
		
		//A abbreviation should contain at least two capital letters.
		int capCount = 0;
		for (int i = 0; i < name.length(); i++) {
			if (Character.isUpperCase(name.charAt(i)))
				capCount++;
			
			if (capCount > 1)
				return true;
		}
		
		return false;
	}
	
	public static String normalizeName(String name) {
		//person titles, and organization.
		String prefix[] = {"mrs.", "mrs ", "mr.", "mr ", "ms.", "ms ", "prof.", "prof ", 
				"dr.", "dr ", "gen.", "gen ", "rep.", "rep ", "sen.", "sen ",
				"st.", "st ", "sr.", "sr ", "jr.", "jr ", "capt.", "capt "};

		String suffix[] = {" jr.", " jr ", " ltd.", " ltd", 
				" co.", " co", " corp.", " corp", " dept.", " dept", " div.", " div", 
				" gen.", " gen", " gov.", " gov", " inc.", " inc"};

		String low_name = name.toLowerCase();
		for (String abbrev : prefix) {
			if (!low_name.startsWith(abbrev))
				continue;
			
			name = name.substring(abbrev.length()).trim();
			low_name = name.toLowerCase();
		}
		
		low_name = name.toLowerCase();
		for (String abbrev : suffix) {
			if (!low_name.endsWith(abbrev))
				continue;
			
			name = name.substring(0, name.length() - abbrev.length()).trim();
			low_name = name.toLowerCase();
		}

		if (name.endsWith("."))
			name = name.substring(0, name.length()-1);
		
		return name.trim();
	}
	
	/**
	 * Convert name in form F.M. Last to F. M. Last
	 * @param name
	 * @return
	 */
	private static String normalizePersonName(String name) {
		int index = name.indexOf('.');
		while (index>=0 && index < name.length()-1) {
			if (name.charAt(index +1) != ' ')
				name = name.substring(0, index+1) + " " + name.substring(index+1);
			index = name.indexOf('.', index+1);
		}
		
		return name;
	}

	/**
	 * Normalize the name by removing extra dot (.) from the short form of a location name.
	 * @param name
	 * @return
	 */
	private static String normalizeLocationName(String name) {
		return name.replace(".", "");
	}
	
	/**
	 * Check if the token is a punctuation.
	 * 
	 * @param token
	 * @return
	 */
	private boolean isPunctuation(Token token) {
		String word = token.getText();
		int bPos = token.getbPosition();
		int ePos = token.getePosition()+1;

		//due to the Stanford tokenizer, some punctuations are marked by their defined symbols.
		//e.g. ( to -LBR-, ) to -RBR-, we identify them by comparing the length of the symbol
		//with the actual length of the word.
		if (word.compareTo("&") == 0)
			return false;
		if (word.length() != (ePos-bPos))
			return true;
		
		//check if every letter in word is a punctuation.
		for (int i = 0; i < word.length(); i++) {
			if (Character.isLetterOrDigit(word.charAt(i)) || word.charAt(i) == '\'')
				return false;
		}
		
		return true;
	}
	
	/**
	 * 
	 * @param word
	 * @return True if word is a stopword.
	 */
	private boolean isStopword(String word) {
		if (stopwords.contains(word.toLowerCase()))
			return true;
		
		return false;
	}

	private boolean isStopword(Token token) {
		String pos = token.getAnnotations().get(Token.POS_ANNOTATION);
		if (pos.equals("IN") || pos.equals("DT") || pos.equals("TO") ||
			pos.equals("CD") || pos.equals("CC") || pos.equals("MD") ||
			pos.equals("PDT") || pos.equals("PRP"))
				return true;
		
		String word = token.getText();
		if (word.contains("<") || word.contains(">") || word.contains("?"))
			return true;
		
		return false;
	}
	
	/**
	 * Check if the word contains an uppercase.
	 */
	private boolean containUppercase(String word) {
		for (int i = 0; i < word.length(); i++)
			if (Character.isUpperCase(word.charAt(i)))
				return true;
		
		return false;
	}
	
	/**
	 * Check if the shortName and the longName are potential coreference.
	 * The shortName has to be only one word, and the longName has to be at least
	 * two terms, and has at least two terms with uppercase.
	 * @param shortName
	 * @param longName
	 * @return
	 */
	private static boolean matchName(String shortName, String longName) {
		if (longName.length() <= shortName.length())
			return false;
		if (shortName.contains(" "))
			return false;
		
		String tokens[] = longName.split(" ");
		for (String token : tokens) {
			if (StringSim.jaro_winkler_score(shortName, token) > 0.9)
				return true;
		}
		
		return false;
	}
	
	/**
	 * Check if the longName contains the shortName in terms of word.
	 * e.g. "Washington US" contains "US" while "School Bus" does not contains "us"
	 * @param longName
	 * @param shortName
	 * @return
	 */
	private static boolean contains(String longName, String shortName) {
		longName = longName.toLowerCase();
		shortName = shortName.toLowerCase();
		Pattern pattern = Pattern.compile("\\b" + shortName + "$");
		
		return pattern.matcher(longName).find();
	}
	
	/**
	 * Check if the longName contains the potential candidates of the mentionName.
	 * 
	 * @param longName
	 * @param mentionName
	 * @return
	 */
	private boolean containsCombo(String longName, String mentionName) {
		longName = longName.toLowerCase();
		mentionName = mentionName.toLowerCase();

		Map<Entity, Double> candidates = cs.selectCandidates(mentionName);
		if (candidates == null || candidates.isEmpty())
			return false;
		
		Pattern pattern = null;
		boolean ret = false;
		
		for (Entity ent : candidates.keySet()) {
			String entName = ent.getName().toLowerCase();
			try {
				pattern = Pattern.compile("\\b" + entName + "$");
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			
			if (pattern.matcher(longName).find()) {
				ret = true;
				break;
			}
		}
		
		return ret;
	}
	
	/**
	 * Check if m1 and m2 is adjacent.
	 * @param m1
	 * @param m2
	 * @return
	 */
	private boolean isAdjacent(Mention m1, Mention m2) {
		if (m1 == null || m2 == null)
			return false;
		
		if (m2.getStartToken() - m1.getEndToken() <= 2)
			return true;
		else
			return false;
	}
	
	/**
	 * Given a short person name, find its full name.
	 * We assume that we have already perform the coreference resolution.
	 *  
	 * @param m
	 * @param doc
     * @param mentions
     *
	 * @return
	 */
	public HashSet<String> findNameDefinitions(Mention m, Document doc, List<Mention> mentions) {
		HashSet<String> candidates = new HashSet<String>();
		String name = m.getName();
		String entName = m.getEntity().getName();
		name = normalizeName(name);
		entName = normalizeName(entName);
		
		//use the coreference resolution result.
		if (name.compareTo(entName) != 0) {
			candidates.add(entName);
			
			//find other possible names.
			for (Mention mention : mentions) {
				if (mention == m)
					continue;
				if (mention.getEntity().getType() != m.getEntity().getType())
					continue;
				
				String name1 = mention.getName();
				String entName1 = mention.getEntity().getName();
				name1 = normalizeName(name1);
				entName1 = normalizeName(entName1);
				if (name1.equals(name))
					continue;
				
				if (entName1.equals(entName) || entName1.equals(name))
					candidates.add(name1);
			}
			
			System.out.println("\t====Potential full name");
			for (String cand : candidates)
				System.out.println("\t" + cand);
			System.out.println("\t=======================");

			return candidates;
		}
		
		if (m.getEntity().getType() == Entity.PERSON)
			name = normalizePersonName(name);
		//add the normalized name to the candidates.
		if (!name.equals(m.getName()))
			candidates.add(name);
		
		//Find potential full name from the extracted mentions.
		for (Mention mention : mentions) {
			if (m == mention)
				continue;
			if (mention.getEntity().getType() != m.getEntity().getType())
				continue;
			
			String candName = mention.getName();
			candName = normalizeName(candName);
			if (candName.equals(name))
				continue;
			
			if (matchName(name, candName))
				candidates.add(candName);
			else if (matchName(candName, name) && 
					mention.getEntity().getType() != Entity.PERSON)
				candidates.add(candName);
		}
		
		System.out.println("\t====Potential full name");
		for (String cand : candidates)
			System.out.println("\t" + cand);
		System.out.println("\t=======================");
		
		return candidates;
	}
	
	/**
	 * When we cannot find the name and definitions in the alias dictionary,
	 * we resort to the fuzzy matching.
	 * 
	 * @param name
	 * @param definitions
	 * @return
	 */
	public Map<Entity, Double> fuzzyCandidateSelect(String name, Set<String> definitions) {
		Map<Entity, Double> candidates = null;
		Map<String, Map<Entity, Double>> ret = null;
		if (definitions == null || definitions.isEmpty()) {
			double maxSim = 0.0, sim = 0.0;
			ret = cs.selectCandidatesFuzzy(name);
			if (ret == null || ret.isEmpty())
				return candidates;
			
			for (String alias : ret.keySet()) {
				sim = StringSim.jaro_winkler_score(name.toLowerCase(), alias);
				if (maxSim < sim) {
					maxSim = sim;
					candidates = ret.get(alias);
				}
			}
			
			if (maxSim < 0.93)
				return null;
		} else {
			double maxSim = 0.0, sim = 0.0;
			for (String definition : definitions) {
				ret = cs.selectCandidatesFuzzy(definition);
				if (ret == null || ret.isEmpty())
					return candidates;

				for (String alias : ret.keySet()) {
					sim = StringSim.jaro_winkler_score(definition.toLowerCase(), alias);
					if (maxSim < sim) {
						maxSim = sim;
						candidates = ret.get(alias);
					}
				}
			}
			
			if (maxSim < 0.93)
				return null;
		}
		
		System.out.println("\tFuzzy search");
		for (Entity ent : candidates.keySet())
			System.out.println("\t" + ent.getName());
		return candidates;
	}
	
	public Map<Entity, Double> selectCandidateNameImpl(Mention mention, 
			List<Mention> mentions,
			Set<String> definitions) {
		
		Map<Entity, Double> candidates = null;
		Map<Entity, Double> tempCands = null;
		String name = mention.getName();
		
		candidates = cs.selectCandidates(name);
		if (definitions == null || definitions.isEmpty()) {
			if (candidates != null && !candidates.isEmpty()) {
				tempCands = findNameCandidateByComposition(mention, mentions, candidates);
				if (tempCands != null && !tempCands.isEmpty())
					return tempCands;
				
				return pruneNameCandidate(mention, mentions, candidates);
			}
		}

		if (candidates == null || candidates.isEmpty()) {
			candidates = cs.selectCandidatesName(definitions);
			if (candidates == null || candidates.isEmpty()) {
				if (useFuzzyMatch) {
					candidates = fuzzyCandidateSelect(name, definitions);
				} else {
					return pruneNameCandidate(mention, mentions, candidates);
				}
			}
		}
		
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		
		tempCands = findNameCandidateByComposition(mention, mentions, candidates);
		if (tempCands != null && !tempCands.isEmpty())
			return tempCands;

		//Only add the top candidates when our confidence of the definition is not 100%.
		tempCands = selectTopCandidate(candidates, definitions);
		if (tempCands != null && !tempCands.isEmpty())
			ret.putAll(tempCands);

		//add the candidates of the definition.
		//we go through the longest definition to the shortest definition.
		//whenever candidates are found, then the process stops.
		List<Rank<Integer, String>> list = new ArrayList<Rank<Integer, String>>();
		for (String definition : definitions)
			list.add(new Rank<Integer, String>(definition.length(), definition));
		
		Collections.sort(list);
		for (Rank<Integer, String> rank : list) {
			String definition = rank.obj;
			//only concern about the definition with multiple words.
			if (!definition.contains(" "))
				continue;
			
			candidates = cs.selectCandidates(definition);
			if (candidates != null && !candidates.isEmpty())
				ret.putAll(candidates);
		}

		if (ret == null || ret.isEmpty())
			return ret;
		
		return pruneNameCandidate(mention, mentions, ret);
	}
	
	/**
	 * Find candidates using the mentions right after the given mention.
	 * If a candidate contains the entity referred to by the following mention (the 
	 * one right after the given mention), then we consider this candidate as the true entity.
	 * 
	 * Focus on location entity only.
	 * 
	 * @param mention
	 * @param mentions
	 * @param candidates
	 * @return
	 */

	public Map<Entity, Double> findNameCandidateByComposition(Mention mention, 
			List<Mention> mentions,
			Map<Entity, Double> candidates) {
		
		if (candidates == null || candidates.isEmpty() ||
				mentions == null || mentions.isEmpty())
			return null;

		System.out.println("========before composition=============");
		for (Entity ent : candidates.keySet())
			System.out.println("\t" + ent.getName());
		System.out.println("=======================================");
		
		List<String> redirects = null;
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		for (Entity ent : candidates.keySet()) {
			String entName = ent.getName();
			redirects = redirectMap.get(entName);
		
			entName = entName.toLowerCase();
			for (int i = 0; i < mentions.size(); i++) {
				Mention m = mentions.get(i);
				String menName = m.getName().toLowerCase();
				if (m == mention || menName.equalsIgnoreCase(mention.getName()))
					continue;
				
				if (m.getEntity().getType() != Entity.LOCATION)
					continue;

				menName = normalizeLocationName(menName);
				
				//try to disambiguate the mention using its neibouring mention.
				Mention preMention = null;
				if (i > 0) 
					preMention = mentions.get(i-1);
				
				if (preMention != null && 
						preMention.getName().equals(mention.getName()) &&
						isAdjacent(preMention, m) && 
						containsCombo(entName, menName)) {
					System.out.println("\t" + preMention.getName() + "\t" + menName + "\t" + entName);
					ret.put(ent, candidates.get(ent));
					continue;
				}
				
				if (redirects == null || redirects.isEmpty())
					continue;
				
				for (String redirect : redirects) {
					if (preMention != null && 
							preMention.getName().equals(mention.getName()) &&
							isAdjacent(preMention, m) && 
							containsCombo(redirect, menName)) {
						System.out.println("\t" + preMention.getName() + "\t" + menName + "\t" + redirect);
						ret.put(ent, candidates.get(ent));
						break;
					}
				}
			}
		}
		
		// if we can find the entity in the document, then
		// we assume the entity is the true definition of the mention.
		System.out.println("=======After findNameCandidateByComposition=======");
		if (ret != null && !ret.isEmpty()) {
			for (Entity ent: ret.keySet())
				System.out.println("\t" + ent.getName());
		}
		System.out.println("===========================");
		
		return ret;
	}

    /**
     *
     * @param mention
     * @param mentions
     * @param candidates
     * @return
     */
	public Map<Entity, Double> pruneNameCandidate(Mention mention, List<Mention> mentions,
			Map<Entity, Double> candidates) {
		if (candidates == null || candidates.isEmpty() ||
				mentions == null || mentions.isEmpty())
			return candidates;
		
		System.out.println("========before pruning=============");
		for (Entity ent : candidates.keySet())
			System.out.println("\t" + ent.getName());
		System.out.println("=======================================");

		List<String> redirects = null;
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		for (Entity ent : candidates.keySet()) {
			String entName = ent.getName();
			redirects = redirectMap.get(entName);
		
			entName = entName.toLowerCase();
			for (Mention m : mentions) {
				if (m.getEntity().getType() != Entity.LOCATION)
					continue;
				
				String menName = m.getName().toLowerCase();
				if (m == mention || menName.equalsIgnoreCase(mention.getName()))
					continue;
				
				menName = normalizeLocationName(menName);
				if (contains(entName, menName)) {
					ret.put(ent, candidates.get(ent));
					System.out.println("\t" + ent.getName() + " \t" + m.getName());
					continue;
				}
				
				if (redirects == null || redirects.isEmpty())
					continue;
				
				for (String redirect : redirects) {
					if (contains(redirect, menName)) {
						ret.put(ent, candidates.get(ent));
						System.out.println("\t" + ent.getName() + " \t" + m.getName());
						break;
					}
				}
			}
		}
		
		if (ret.isEmpty())
			return candidates;
		
		System.out.println("=======After pruning=======");
		for (Entity ent: ret.keySet())
			System.out.println("\t" + ent.getName());
		System.out.println("===========================");
		return ret;
	}

	/**
	 * For pattern definition(A), we first identify the position of (A), 
	 * and then search backwards for the definition.
	 * 
	 * @param pos
	 * @param doc
	 * @return
	 */
	public HashSet<String> findDefinitionBackward(String abbrev, int pos, Document doc) {
		
		//locate the sentence containing token.
		Sentence curSent = null;
		int index = -1, endIndex = -1;
		List<Sentence> sentences = doc.getSentences();
		for (Sentence sent : sentences) {
			List<Token> tokens = sent.getTokens();
			for (Token tok : tokens) {
				int bPos = tok.getbPosition();
				if (pos == bPos) {
					curSent = sent;
					endIndex = tok.getPosition()-1;
					break;
				}
			}
		}
		
		if (curSent == null)
			return null;

		// now we check backward from this position to find the full name.
		//The stop condistions are as follows:
		//	- encounter special symbols: (, ), [, ], {, }, =, !, ?.
		//	- encounter two stop words.
		//	- the size of the definition reaches: |D| = min{|A|+5, |A|*2}
		int maxWin = Math.min(abbrev.length()+5, abbrev.length()*2);
		int numStopwords = 0, numTokens = 0;
		
		List<Token> tokens = curSent.getTokens();
		for (index = endIndex; index >= 0; index--) {
			Token token = tokens.get(index);
			numTokens++;
			if (numTokens > maxWin)
				break;
			
			// check punctuation
			if (isPunctuation(token))
				break;

			// check stop word.
			if (isStopword(token))
				numStopwords++;
			
			if (numStopwords > 3)
				break;
		}

		// The current token is not part of the candidate.
		index++;

		// begin position and end position of the candidate.
		// Removing unrelated stating words.e.g. stop words, words with all lowercase.
		for (; index < endIndex; index++) {
			Token token = tokens.get(index);
			String word = token.getText();
			if (isPunctuation(token) || isStopword(token) || !containUppercase(word))
				continue;
			
			break;
		}
		
		if (index >= endIndex)
			return null;
		
		//Add all possible definition in to the result set.
		HashSet<String> ret = new HashSet<String>();
		int bPos = 0;
		int ePos = tokens.get(endIndex).getePosition()+1;
		for (; index < endIndex; index++) {
			Token token = tokens.get(index);
			if (isPunctuation(token) || isStopword(token) || 
					!containUppercase(token.getText()))
				continue;

			bPos = token.getbPosition();
			String candStr = doc.getOriginalText().substring(bPos, ePos);
			if (candStr == null || candStr.isEmpty() || candStr.contains(abbrev))
				continue;

			// remove extra space.
			candStr = candStr.replaceAll("\\s+", " ").trim();
			ret.add(candStr);
		}
		
		return ret;
	}
	
	/**
	 * Find definition from the given starting point.
	 * 
	 * @param abbrev
	 * @param doc
	 * @return
	 */
	private HashSet<String> findDefinitionImpl(String abbrev, 
											 List<Token> tokens, 
											 Document doc, 
											 int beginIndex) {

		if (beginIndex >= tokens.size())
			return null;
		
		//continue to find the rest words in a definition.
		Token token = null;
		int maxWin = Math.min(abbrev.length()+5, abbrev.length()*2);
		int numStopwords = 0, numTokens = 0, endIndex;

		for (endIndex = beginIndex; endIndex < tokens.size(); endIndex++) {
			token = tokens.get(endIndex);
			numTokens++;
			if (numTokens > maxWin)
				break;
			
			// check punctuation
			if (isPunctuation(token))
				break;

			// check stop word.
			if (isStopword(token))
				numStopwords++;
			if (numStopwords > 1)
				break;
		}
		
		// end of the document.
		endIndex--;
		
		//move backwards to remove unrelated words.
		String word = null;
		for (; endIndex > beginIndex; endIndex--) {
			token = tokens.get(endIndex);
			word = token.getText();
			if (isPunctuation(token) || isStopword(token) || 
				!containUppercase(word))
			//	!word.contains(abbrev.substring(abbrev.length()-1)))
				continue;
			
			break;
		}
		
		if (endIndex <= beginIndex)
			return null;
		
		//Further step to identify the ending word
		//check if the word contains the last letter of abbrev
//		for (int i = endIndex; i > beginIndex; i--) {
//			token = tokens.get(i);
//			word = token.getText();
//			if (word.contains(abbrev.substring(abbrev.length()-1))) {
//				endIndex = i;
//				break;
//			}
//		}
//		
//		int bPos = tokens.get(beginIndex).getbPosition();
//		int ePos = tokens.get(endIndex).getePosition()+1;
//		String candStr = doc.getOriginalText().substring(bPos, ePos);
//		if (candStr != null && !candStr.isEmpty()) {
//			// remove extra space.
//			candStr = candStr.replaceAll("\\s+", " ").trim();
//			System.out.println("\t" + candStr);
//		}
		
		// add it to the alterantive list.
//		ret.add(candStr);
		
		HashSet<String> ret = new HashSet<String>();
		int bPos = tokens.get(beginIndex).getbPosition();
		int ePos = 0;
		for (int i = beginIndex + 1; i <= endIndex; i++) {
			token = tokens.get(i);
			if (isPunctuation(token) || isStopword(token) || 
					!containUppercase(token.getText()))
				continue;
			
			ePos = token.getePosition()+1;
			String candStr = doc.getOriginalText().substring(bPos, ePos);
			if (candStr == null || candStr.isEmpty() || candStr.contains(abbrev))
				continue;
			// remove extra space.
			candStr = candStr.replaceAll("\\s+", " ").trim();
			ret.add(candStr);
		}

		
		return ret;
	}

	/**
	 * In the case that the definition is not adjacent from the abbreviation, we need
	 * to search over the whole document from the beginning of the document. 
	 * 
	 * @param abbrev
	 * @param doc
	 * @return
	 */
	public HashSet<String> findDefinitionForward(String abbrev, Document doc) {
		HashSet<String> ret = new HashSet<String>();
		
		List<Sentence> sentences = doc.getSentences();
		for (Sentence sent : sentences) {
			int beginIndex = 0;
			Token token = null;
			String word = null;
			List<Token> tokens = sent.getTokens();

			// Find the starting word first.
			for (beginIndex = 0; beginIndex < tokens.size(); beginIndex++) {
				token = tokens.get(beginIndex);
				word = token.getText();
				if (isPunctuation(token) || isStopword(token))
					continue;
				if (word.charAt(0) == abbrev.charAt(0)) {
					HashSet<String> definitions = findDefinitionImpl(abbrev, tokens, doc, beginIndex);
					if (definitions != null && !definitions.isEmpty())
						ret.addAll(definitions);
				}
			}
		}
		
		return ret;
	}
	
	/**
	 * Find the potential fullname of an abbrev from the given document.
	 * 
	 * @param mention
     * @param doc
	 * @return
	 */
	public Pair<String, Double> findAbbrevDefinitions(Mention mention, Document doc) {
		HashSet<String> ret = new HashSet<String>();
		String content = doc.getOriginalText();
		String name = mention.getName();
		
		String entName = mention.getEntity().getName();
		//1. check if we find the full name already using the co-reference tool.
		if (!name.equals(entName)) {
			//A very simple check to remove unrelated definitions. 
			double sim = abbrevSim(name, entName);
			if (sim <= abbrevThreshold)
				return null;
			
			System.out.println("\t[chosen]" + entName);
			return new Pair<String, Double>(entName, 1.0);
		}
//		
//		//2. check the names from the mentions extracted using NER.
//		for (Mention m : mentions) {
//			String name2 = m.getName();
//			if(isCoref(name, name2))
//				ret.add(name2);
//		}
//		
//		if (!ret.isEmpty())
//			return ret;
		
		//3. From pattern A(fullname)
		Pattern p = Pattern.compile(name + "\\s*\\((.*?)\\)");
		Matcher m = p.matcher(content);
		while (m.find()) {
			String matchStr = m.group(1);
			
			//A very simple check to remove unrelated definitions. 
			double sim = abbrevSim(name, matchStr);
			if (sim <= abbrevThreshold)
				continue;
			
			System.out.println("\t[chosen]" + matchStr);
			
			return new Pair<String, Double>(matchStr.trim(), 1.0);
		}
		
		//4. From pattern fullname(A)
		p = Pattern.compile("\\(\\s*" + name + "\\s*\\)");
		m = p.matcher(content);
		while (m.find()) {
			HashSet<String> candStrs = findDefinitionBackward(name, m.start(), doc);
			if (candStrs == null || candStrs.isEmpty())
				continue;
			
			//A very simple check to remove unrelated definitions. 
			for (String candStr : candStrs) {
				double sim = abbrevSim(name, candStr);
				if (sim < abbrevThreshold)
					continue;

				ret.add(candStr);
			}
		}
		
		if (!ret.isEmpty()) {
			//We assume that there is always a definition in this case.
			Pair<String, Double> definition1 = chooseByWikipediaName(name, ret);
			Pair<String, Double> definition2 = chooseByNameSimilarity(name, ret, false);
			
			if (definition1 != null) {
				System.out.println("\t[chosen]" + definition1.getValue1());
				definition1.setValue2(1.0);
				return definition1;
			} else if (definition2 != null) {
				System.out.println("\t[chosen]" + definition2.getValue1());
				definition2.setValue2(1.0);
				return definition2;
			}
		}
		
		//5. From the document.
		//Iterating from the first word of the document to the end to check if
		//the first letter matches the first letter of the abbreviation, and then
		//starting from that word to extract candidates.
		HashSet<String> candStrs = findDefinitionForward(name, doc);
		if (candStrs == null || candStrs.isEmpty())
			return null;
		
		//A very simple check to remove unrelated definitions. 
		for (String candStr : candStrs) {
			double sim = abbrevSim(name, candStr);
			if (sim <= abbrevThreshold)
				continue;

			ret.add(candStr);
		}

		Pair<String, Double> definition1 = chooseByWikipediaName(name, ret);
		Pair<String, Double> definition2 = chooseByNameSimilarity(name, ret, true);
		if (definition1 != null) {
			System.out.println("\t[chosen]" + definition1.getValue1() + "[" + definition1.getValue2() + "]");
			return definition1;
		} else if (definition2 != null) {
			System.out.println("\t[chosen]" + definition2.getValue1() + "[" + definition2.getValue2() + "]");
			return definition2;
		}
		
		return null;
	}
	
	/**
	 * Prune the definitions using Wikipedia name and redirects.
	 * 
	 * @param name
	 * @param definitions
	 * @return
	 */
	private Pair<String, Double> chooseByWikipediaName(String name, HashSet<String> definitions) {
		System.out.println("\t=======pruneByWikipediaName");

		if (definitions == null || definitions.isEmpty())
			return null;
		
		Map<Entity, Double> candidates = cs.selectCandidates(name.toLowerCase());
		List<String> redirects = null;
		
		if (candidates == null || candidates.isEmpty())
			return null;
		
		double sim = 0, maxSim = 0;
		String maxEnt = null, entName = null, maxDef = null;
		for (Entity ent : candidates.keySet()) {
			entName = ent.getName();
			redirects = redirectMap.get(entName);
			
			for (String def : definitions) {
				sim = StringSim.ngram_distance(entName, def, 2);
				if (sim > maxSim) {
					maxSim = sim;
					maxEnt = entName;
					maxDef = def;
				}
				
				if (redirects == null || redirects.isEmpty())
					continue;
				
				for (String redirect : redirects) {
					sim = StringSim.ngram_distance(redirect, def, 2);
					if (sim > maxSim) {
						maxSim = sim;
						maxEnt = entName;
						maxDef = def;
					}
				}
			}
		}
		
		if (maxSim > 0.95) {
			System.out.println("\t" + maxEnt + " : " + maxDef + "\t" + maxSim);
			return new Pair<String, Double>(maxDef, maxSim);
		}
		
		return null;
	}
	
	/**
	 * Given a name, define its abbreviation by concatenating the uppercases. 
	 * @param name
	 * @return
	 */
	private static String extractAbbrev(String name, boolean useCap) {
		StringBuffer abbrev = new StringBuffer();
		boolean noCap = true;
		String[] tokens = name.split(" ");
		for (String token : tokens) {
			if (token == null || token.isEmpty())
				continue;
			
			noCap = true;
			for (int i = 0; i < token.length(); i++) {
				char c = token.charAt(i);
				if (Character.isUpperCase(c)) {
					abbrev.append(c);
					noCap = false;
				}
			}
			
			if (noCap && useCap)
				abbrev.append(token.charAt(0));
		}
		
		return abbrev.toString();
	}
	
	/**
	 * Measure the similarity of two abbreviations.
	 * This work, we prefer the abbreviation with the same length, thus we need to 
	 * penalize the candidates with longer or shorter size.
	 * sim = 1 - editDistance / (orig.length() + |org.len - new.len|) 
	 * 
	 * @param origAbbr 	The original abbreviation.
	 * @param newAbbr	The proposed 
	 * @return
	 */
	private static double abbrevSimImpl(String origAbbr, String newAbbr) {
		String abbr1 = StringUtils.sort(origAbbr.toLowerCase());
		String abbr2 = StringUtils.sort(newAbbr.toLowerCase());
		
		int maxLen = abbr1.length() > abbr2.length() ? abbr1.length() : abbr2.length();
		int difLen = Math.abs(abbr1.length() - abbr2.length());
		double sim = StringSim.edit_distance_score(abbr1, abbr2);
		
		//return the revised similarity.
		sim = sim * maxLen / (abbr1.length() + difLen);
		return  sim;
	}
	
	private static double abbrevSim(String name, String definition) {
		String abbrev = extractAbbrev(definition, false);
		double sim1 = abbrevSimImpl(name, abbrev);
		abbrev = extractAbbrev(definition, true);
		double sim2 = abbrevSimImpl(name, abbrev);
		double sim = (sim1 > sim2 ? sim1 : sim2);

		return sim;
	}
	
	/**
	 * Prune unrelated definitions by measuring their similarity with the abbreviation.
	 * 
	 * @param name
	 * @param definitions
	 * @return
	 */
	private Pair<String, Double> chooseByNameSimilarity(String name, 
			HashSet<String> definitions,
			boolean useThreshold) {
		
		System.out.println("\t=======pruneByNameSimilarity");
		if (definitions == null || definitions.isEmpty())
			return null;
		
		List<Rank<Double, String>> rankList = new ArrayList<Rank<Double, String>>();
		double sim = 0;
		
		for (String definition : definitions) {
			sim = abbrevSim(name, definition);
			rankList.add(new Rank<Double, String> (sim, definition));
		}
		
		Collections.sort(rankList);
		Rank<Double, String> rank = rankList.get(0);
		
		if (useThreshold) {
			if (rank.sim > 0.95)
				return new Pair<String, Double>(rank.obj, rank.sim);
		} else {
			return new Pair<String, Double>(rank.obj, rank.sim);
		}
		
		return null;
	}
	
	/**
	 * Find potential co-refered names. 
	 * For now we only check the named entities extracted using Stanford NER.
	 * In the future, we will check the whole document for more potential candidates.
	 * For example, identify the occurrence of the given name, and searching surrounding
	 * words to see if they, combined with this name, can form a whole named entity.
	 * 
	 * @param m
	 * @param content
	 * @param mentions
	 * @return
	 */
	public Set<String> findNameAlternatives(Mention m, String content,
		List<Mention> mentions) {
		Set<String> nameCandidates = new HashSet<String>();
		String name = m.getName();
		nameCandidates.add(normalizeName(name));
		nameCandidates.add(normalizeName(m.getEntity().getName()));
		
		for (Mention mention : mentions) {
			String candName = mention.getName();
			if (candName.contains(name))
				nameCandidates.add(normalizeName(candName));
			else if (name.contains(candName) && 
					mention.getEntity().getType() != Entity.PERSON)
				nameCandidates.add(normalizeName(candName));
		}
		
		System.out.println(name);
		for (String cand : nameCandidates)
			System.out.println("\t" + cand);
		
		return nameCandidates;
	}
	
	public Document tokenize(String content) {
		//1. Create a Document.
		Document doc = new Document();
		doc.setOriginalText(content);

		//2. Text annotation.
		List<Sentence> sentences = ner.annotateText(content);
		for (Sentence sentence : sentences)
			doc.addSentence(sentence);

		//3. Co-reference resolution.
		orthoMatcher.findCoreferences(doc);

		return doc;
	}
	
	private Map<Entity, Double> selectTopCandidate(Map<Entity, Double> candidates,
			Set<String> definitions) {
		
		if (definitions == null || definitions.isEmpty())
			return candidates;
		
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		List<Rank<Double, Entity>> rankList1 = new ArrayList<Rank<Double, Entity>>();
		List<Rank<Double, Entity>> rankList2 = new ArrayList<Rank<Double, Entity>>();

		//select the top few candidates by the string similarity and prior popularity.
		for (Entity ent : candidates.keySet()) {
			String entName = ent.getName();
			double priorProb = candidates.get(ent);
			double maxSim = 0.0;
			for (String definition : definitions) {
				if (entName.contains(definition))
					ret.put(ent, priorProb);

				double sim = StringSim.edit_distance_score(definition, entName);
				if (maxSim < sim)
					maxSim = sim;
			}

			rankList1.add(new Rank<Double, Entity>(maxSim, ent));
			rankList2.add(new Rank<Double, Entity>(priorProb, ent));
		}
		
		//sort and pick the top K entity.
		//sort by string similarity
		int K = 3;
		K = K > rankList1.size() ? rankList1.size() : K;
		Collections.sort(rankList1);
		for (int i = 0; i < K; i++) {
			Entity ent = rankList1.get(i).obj;
			ret.put(ent, candidates.get(ent));
		}
		
		//sort by prior probability.
		K = K > rankList2.size() ? rankList2.size() : K;
		Collections.sort(rankList2);
		for (int i = 0; i < K; i++) {
			Entity ent = rankList2.get(i).obj;
			ret.put(ent, candidates.get(ent));
		}
		
		return ret;
	}
	
	public Map<Entity, Double> selectCandidateAbbrevImpl(Mention mention, 
			Pair<String,Double> definition) {
		String name = mention.getName();
		Map<Entity, Double> candidates = cs.selectCandidates(name);
		
		if (definition == null)
			return candidates;
		if (candidates == null || candidates.isEmpty())
			return cs.selectCandidates(definition.getValue1());
		
		Map<Entity, Double> ret = new HashMap<Entity, Double>();
		Map<Entity, Double> tempCands = null;
		
		//add the candidates of the definition.
		tempCands = cs.selectCandidates(definition.getValue1());
		if (tempCands == null || tempCands.isEmpty()) {
			name = normalizeName(definition.getValue1());
			tempCands = cs.selectCandidates(name);
		}
		
		if (tempCands != null && !tempCands.isEmpty())
			ret.putAll(tempCands);

		//Only add the top candidates when our confidence of the definition is not 100%.
		if (definition.getValue2() < 0.99) {
			Set<String> definitions = new HashSet<String>();
			definitions.add(definition.getValue1());
			tempCands = selectTopCandidate(candidates, definitions);
			if (tempCands != null && !tempCands.isEmpty())
				ret.putAll(tempCands);
		}

		return ret;
	}

	public List<Mention> extractMentions(Document doc) {
		//4. Collect mentions.
		List<Mention> mentions = new ArrayList<Mention>();
		for (Sentence sentence : doc.getSentences()) {
			for (Mention mention : sentence.getMentions()) {
				mentions.add(mention);
			}
		}

		return mentions;
	}

	private Map<Entity, Double> selectCandidateAbbrev(Mention mention, 
			Document doc, List<Mention> mentions) {
		
		Pair<String, Double> definition = findAbbrevDefinitions(mention, doc);
		
		return selectCandidateAbbrevImpl(mention, definition);
	}

	private Map<Entity, Double> selectCandidateName(Mention mention, 
			Document doc, List<Mention> mentions) {
		
		HashSet<String> definitions = findNameDefinitions(mention, doc, mentions);
		Map<Entity, Double> candidates = selectCandidateNameImpl(mention, mentions, definitions);
//		return pruneNameCandidate(mention, mentions, candidates, doc);
//		return pruneNameCandidateByComposition(mention, mentions, candidates, doc);
		return candidates;
	}
	
	public Map<Entity, Double> selectCandidateMentionExpansion(String name, String content) {
		//Annotate the content: tokenize, NER.
		Document doc = tokenize(content);

		//Extract the mentions from the document.
		List<Mention> mentions = extractMentions(doc);
		Mention mention = matchMention(name, content, mentions);
		
		//Select the candidates.
		if (isAbbrev(mention))
			return selectCandidateAbbrev(mention, doc, mentions);
		else
			return selectCandidateName(mention, doc, mentions);
	}

	public Map<Entity, Double> selectCandidateMentionExpansion(
			Mention mention, 
			Document doc,
			List<Mention> mentions) {
		//Select the candidates.
		if (isAbbrev(mention))
			return selectCandidateAbbrev(mention, doc, mentions);
		else
			return selectCandidateName(mention, doc, mentions);
	}

	/**
	 * Find the corresponding mention of the name in the mention list.
	 * If no mention is found,create a new one. 
	 * 
	 * @param name
	 * @param content
	 * @param mentions
	 * @return
	 */
	private Mention matchMention(String name, String content, List<Mention> mentions) {
		//find exact match.
		for (Mention m : mentions) {
			String mName = m.getName();
			if (mName.equals(name))
				return m;
		}
		
		//find mentions with partial matches.
		for (Mention m : mentions) {
			String mName = m.getName();
			if (mName.contains(name)) {
				m.setName(name);
				return m;
			}
		}
		
		
		int index = content.indexOf(name);
		Mention m = new Mention(new Entity(name, name), Entity.NONE, index, index+name.length());
		mentions.add(m);
		
		return m;
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}
}
