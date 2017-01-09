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
package ca.ualberta.entitylinking.common.indexing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.BlockingQueue;
import java.lang.Runnable;

import org.wikipedia.miner.model.Label;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.annotation.Disambiguator;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import ca.ualberta.entitylinking.utils.StringUtils;

public class Tokenizer implements Runnable {
	/** The input queue	*/
	private BlockingQueue<DocumentIndexer.Input> inputQueue;
	private BlockingQueue<DocumentIndexer.Output> outputQueue;

	private StanfordCoreNLP pipeline = null;
	private Wikipedia wikipedia = null;
	private Disambiguator disambiguator = null;

	public static class Token {
		public String text = null;
		public TYPE type = TYPE.UNKNOWN;
		public int startPos = -1, endPos = -1;

		public enum TYPE {
			UNKNOWN, TOKEN, NE, WIKI_LABEL
		}

		public Token() {
		}

		public Token(String text, int startPos, int endPos) {
			this.text = text;
			this.startPos = startPos;
			this.endPos = endPos;
		}

		public Token(String text, TYPE type, int startPos, int endPos) {
			this.text = text;
			this.type = type;
			this.startPos = startPos;
			this.endPos = endPos;
		}

		public boolean equals(Token token) {
			if (token == null) return false;

			if (text == null && token.text != null)
				return false;
			if (text != null && token.text == null)
				return false;

			if (!text.equals(token.text) || type != token.type ||
					startPos != token.startPos || endPos != token.endPos)
				return false;

			return true;
		}

		public boolean equalsIgnoreType(Token token) {
			if (token == null) return false;

			if (text == null && token.text != null)
				return false;
			if (text != null && token.text == null)
				return false;

			if (!text.equals(token.text) ||
					startPos != token.startPos || endPos != token.endPos)
				return false;

			return true;
		}

		public String toString() {
			String str = new String("====text: " + text + "\n" +
						"\t type: " + type + "\n" +
						"\t sPos: " + startPos + "\n" +
						"\t ePos: " + endPos);
			return str;
		}
	}

	public Tokenizer(Wikipedia wikipedia, Disambiguator disambiguator) {
		init(wikipedia, disambiguator);
	}


	public Tokenizer(Wikipedia wikipedia, Disambiguator disambiguator,
			BlockingQueue<DocumentIndexer.Input> inputQueue,
			BlockingQueue<DocumentIndexer.Output> outputQueue) {

		init(wikipedia, disambiguator);

		this.inputQueue = inputQueue;
		this.outputQueue = outputQueue;
	}

	public Tokenizer() {
		init(null, null);
	}

	private void init(Wikipedia wikipedia, Disambiguator disambiguator) {
		this.wikipedia = wikipedia;
		this.disambiguator = disambiguator;

		//Configure the Stanford NLP pipeline.
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
		this.pipeline = new StanfordCoreNLP(props);
	}

	public List<Token> extractNE(String text) {
		List<Token> temp = tokenizeNER(text);
		if (temp == null || temp.isEmpty())
			return null;

		List<Token> ret = new ArrayList<Token>();
		for (Token tok : temp) {
			if (tok.type == Token.TYPE.NE)
				ret.add(tok);
		}

		return ret;
	}

	/**
	 * Tokenize the given text using a Stanford NLP toolkit.
	 * The tokenized results are a mix of non-overlapping tokens and named entities.
	 * e.g. University of Alberta is a great university in Canada.
	 * tokens: "University of Alberta", "is", "a", "great", "university", "in", "Canada".
	 *
	 * @param text
	 * @return
	 */
	public List<Token> tokenizeNER(String text) {
		List<Token> ret = new ArrayList<Token>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		try{
			// run all Annotators on this text
			pipeline.annotate(document);
		} catch(Exception e){
			System.out.println("[WARNING] Stanford NER was unable to annotate the following text (more details in the stack trace): ");
			System.out.println("\t\t" + text);
			e.printStackTrace();
			return null;
		}

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
		int position=0;
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for(CoreMap sentence: sentences) {
			String name = "";

			String lastNe = "O";
			int startEntity = 0;

			// traversing the words in the current sentence
			// a CoreLabel is a CoreMap with additional token-specific methods
			for (CoreLabel token: sentence.get(TokensAnnotation.class)) {

				// this is the text of the token
				String word = token.get(TextAnnotation.class);
				// this is the lemma of the token
				String lemma = token.get(LemmaAnnotation.class);
				// this is the POS tag of the token: disabled here since we do not need it now.
				String pos = token.get(PartOfSpeechAnnotation.class);
				// this is the NER label of the token
				String ne = token.get(NamedEntityTagAnnotation.class);

				int bPos = token.beginPosition();
				int ePos = token.endPosition();

				// keep track of mentions
				if(lastNe.equals("O")){
					if(!ne.equals("O")){
						startEntity = bPos;
						name = word;
					}
				}else{
					if(ne.equals("O")){
						int endEntity = position;
						//create mention.
						Token tok = new Token(name, Token.TYPE.NE, startEntity, endEntity);
						ret.add(tok);
					//	System.out.println(tok);
					} else {
						if(ne.equals(lastNe)){
							name += " " + word;
						}
					}

					if(!ne.equals(lastNe) && !ne.equals("O")){
						int endEntity = position;
						//create mention.
						Token tok = new Token(name, Token.TYPE.NE, startEntity, endEntity);
						ret.add(tok);
					//	System.out.println(tok);

						startEntity = bPos;
						name = word;
					}
				}

				if (ne.equals("O")) {
					//filter out the punctuations and stop words.
					if (!word.equals(pos) && !StringUtils.isStopWord(word)) {
						// create token.
						Token tok = new Token(word, Token.TYPE.TOKEN, bPos, ePos);
						ret.add(tok);
						// System.out.println(tok);
					}
				}

				lastNe = ne;
				position = ePos;
			}

			// verify mention ending at the last token
			if(!lastNe.equals("O") && !lastNe.equals(".")){
				int endEntity = position;
				//create mention.
				Token tok = new Token(name, Token.TYPE.NE, startEntity, endEntity);
				ret.add(tok);
			}
		}

		return ret;
	}

	/**
	 * This one does not use the NER.
	 * @param text
	 * @return
	 */
	public List<Token> tokenize(String text) {
		List<Token> ret = new ArrayList<Token>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		try{
			// run all Annotators on this text
			pipeline.annotate(document);
		} catch(Exception e){
			System.out.println("[WARNING] Stanford NER was unable to annotate the following text (more details in the stack trace): ");
			System.out.println("\t\t" + text);
			e.printStackTrace();
			return null;
		}

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for(CoreMap sentence: sentences) {
			// traversing the words in the current sentence
			// a CoreLabel is a CoreMap with additional token-specific methods
			for (CoreLabel token: sentence.get(TokensAnnotation.class)) {

				// this is the text of the token
				String lemma = token.get(LemmaAnnotation.class);

				int bPos = token.beginPosition();
				int ePos = token.endPosition();

				// filter out the punctuations and stop words.
				if (useful(lemma) && !StringUtils.isStopWord(lemma)) {
					// create token.
					Token tok = new Token(lemma, Token.TYPE.TOKEN, bPos, ePos);
					ret.add(tok);
				}
			}
		}

		return ret;
	}

	private static boolean useful(String token) {
		if (token == null || token.length() < 2)
			return false;

		if (token.equals("\\*"))
			return false;

		return true;
	}

	/**
	 * Match the given text against the label dictionary of Wikipedia, and return the list
	 * of labels found in the text.
	 *
	 * @param text
	 * @return
	 */
	public List<Token> extractWikiLabel(String text) {
		if (disambiguator == null || wikipedia == null)
			return null;

		List<Token> ret = new ArrayList<Token>();

		String s = "$ " + text + " $" ;

		Pattern p = Pattern.compile("[\\s\\{\\}\\(\\)\"\'\\.\\,\\;\\:\\-\\_]") ;  //would just match all non-word chars, but we don't want to match utf chars
		Matcher m = p.matcher(s) ;

		Vector<Integer> matchIndexes = new Vector<Integer>() ;

		while (m.find())
			matchIndexes.add(m.start()) ;

		for (int i=0 ; i<matchIndexes.size() ; i++) {
			int startIndex = matchIndexes.elementAt(i) + 1 ;
			if (Character.isWhitespace(s.charAt(startIndex)))
				continue ;

			for (int j=Math.min(i + disambiguator.getMaxLabelLength(), matchIndexes.size()-1) ; j > i ; j--) {
				int currIndex = matchIndexes.elementAt(j) ;
				String ngram = s.substring(startIndex, currIndex) ;

				if (! (ngram.length()==1 && s.substring(startIndex-1, startIndex).equals("'"))&& !ngram.trim().equals("") && !wikipedia.getConfig().isStopword(ngram)) {

					//TODO: test if we need escapes here
					Label label = new Label(wikipedia.getEnvironment(), ngram, disambiguator.getTextProcessor()) ;
					if (label.exists() && label.getLinkProbability() >= disambiguator.getMinLinkProbability()) {
						Token tok = new Token(ngram, Token.TYPE.WIKI_LABEL, startIndex-2, currIndex-2);
						ret.add(tok);

					//	System.out.println(tok);
					}
				}
			}
		}

		return ret;
	}

	public List<Token> extractContext(String text) {
		List<Token> tokenList = tokenize(text);
		List<Token> labelList = extractWikiLabel(text);
		List<Token> ret = mergeTokenList(tokenList, labelList);

		return ret;
	}

	/**
	 * Merge token list and the label list, and remove tokens that are identical or substring of label.
	 *
	 * @param tokenList Results from the tokenize().
	 * @param labelList Results from the extractWikiLabel();
	 * @return A merged token list.
	 */
	public static List<Token> mergeTokenList(List<Token> tokenList, List<Token> labelList) {
		if (tokenList == null || tokenList.isEmpty())
			return labelList;
		if (labelList == null || labelList.isEmpty())
			return tokenList;

		List<Token> ret = new ArrayList<Token>();

		Token token = null;
		Token label = null;
		int i, j;
		for (i = 0, j = 0; i < tokenList.size() && j < labelList.size();) {
			token = tokenList.get(i);
			label = labelList.get(j);

			if (token.startPos >= label.endPos) {
				ret.add(label);
				j++;
			} else if (token.endPos <= label.startPos) {
				ret.add(token);
				i++;
			} else {
				if (token.startPos > label.startPos && token.endPos > label.endPos) {
					//overlapping, label is the one behind.
					ret.add(label);
					j++;
				} else if (token.startPos >= label.startPos && token.endPos <= label.endPos) {
					//token is the substring of label.
					i++;
				} else if (token.startPos < label.startPos && token.endPos < label.endPos) {
					//overlapping, token is the one behind.
					ret.add(token);
					i++;
				} else {
					//label is the substring of token.
					ret.add(label);
					j++;
				}
			}
		}

		if (i == tokenList.size())
			for (; j < labelList.size(); j++)
				ret.add(labelList.get(j));
		if (j == labelList.size())
			for (; i < tokenList.size(); i++)
				ret.add(tokenList.get(i));

		return ret;
	}

	public void run() {
		while (true) {
			try {
				DocumentIndexer.Input input = inputQueue.take();
				if (input.id == null && input.content == null)
					break;

				List<Token> tokens = extractContext(input.content);
				outputQueue.put(new DocumentIndexer.Output(input.id, tokens));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("Done with the tokenization!!");
	}
}
