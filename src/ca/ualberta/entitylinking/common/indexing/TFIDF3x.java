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
import java.io.StringReader;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.Term;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.document.Document;
import org.apache.lucene.util.Version;

import ca.ualberta.entitylinking.config.WNEDConfig;
import ca.ualberta.entitylinking.utils.StringUtils;

public class TFIDF3x {
	private IndexReader reader = null;
	private Map<String, Integer> name2id = null;
	
	public TFIDF3x() {
		loadIndex(WNEDConfig.tfidfIndexDir);
	}
	
	public int numDocs() {
		return reader.numDocs();
	}
	
	public Document document(String docName) {
		if (!name2id.containsKey(docName))
			return null;
		
		return document(name2id.get(docName));
	}
	
	public Document document(int docId) {
		Document ret = null;
		try {
			ret = reader.document(docId);
		} catch (Exception e) {e.printStackTrace();}
		
		return ret;
	}
	
	public void loadIndex(String indexDir) {
		try {
			reader = IndexReader.open(FSDirectory.open(new File(indexDir)));
			
			String[] stringArray = FieldCache.DEFAULT.getStrings(reader, "name");
			
			// build a map from string to its document id.
			name2id = new HashMap<String, Integer>();
			for (int i = 0; i < stringArray.length; i++)
				name2id.put(stringArray[i], i);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean containsDoc(String docName) {
		return name2id.containsKey(docName);
	}
	
	/**
	 * Filter the string with StandardAnalyzer.
	 * @param str
	 * @param removeStopWords	Indicate if the stop words should be removed.
	 * @return
	 */
	public static String processString(String str, boolean removeStopWords) {
		StringBuffer strBuf = new StringBuffer();
		
		try {
			Analyzer analyzer = null;
			if (removeStopWords)
				analyzer = new StandardAnalyzer(Version.LUCENE_34);
			else
				analyzer = new TextAnalyzerWithStopwords(Version.LUCENE_34);
			
			TokenStream tokenStream = analyzer.tokenStream("string",
					new StringReader(str));
			CharTermAttribute charTermAttribute = tokenStream
					.addAttribute(CharTermAttribute.class);

			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				String term = charTermAttribute.toString();
				strBuf.append(term + " ");
			}
			
			analyzer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return strBuf.toString().trim();
	}
	
	/**
	 * Compute the tf-idf value of the given term in a document.
	 *  
	 * @param term
	 * @return
	 */
	public float computeTFIDF(String term, String docName) {
		float tfidf = 0;
		try {
			int docId = name2id.get(docName);

			TermFreqVector terms = reader.getTermFreqVector(docId, "contents");

			// TF - term frequency.
			term = processString(term, false);
			int tf = 0;
			int idx = terms.indexOf(term);
			if (idx >= 0) {
				int[] freq = terms.getTermFrequencies();
				tf = freq[idx];
			}

			// IDF
			// 1. docFreq
			int df = reader.docFreq(new Term("contents", term));
			// 2. numDocs
			int numDocs = reader.numDocs();

			DefaultSimilarity simObj = new DefaultSimilarity();

			tfidf = simObj.tf(tf) * simObj.idf(df, numDocs);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return tfidf;
	}
	
	public float computeTFIDF(String term, int tf) {
		float tfidf = 0;
		
		try {
			// IDF
			// 1. docFreq
			int df = reader.docFreq(new Term("contents", term));
			// 2. numDocs
			int numDocs = reader.numDocs();

			DefaultSimilarity simObj = new DefaultSimilarity();

			tfidf = simObj.tf(tf) * simObj.idf(df, numDocs);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return tfidf;
	}
	
	/**
	 * Retrieve the term vector of the text weighted by their tf-idf.
	 * Note that a map is used to represent the vector for saving space and also speedup
	 * the similarity computation.
	 * 
	 * @param text
	 * @return
	 */
	public Map<String, Float> TFIDFVector(String text, String docName) {
		return null;
	}
	
	/**
	 * Get the TFIDF vector representation of the given document.
	 * @param docName
	 * @return
	 */
	public Map<String, Float> DocTFIDFVector(String docName) {
		if (!containsDoc(docName))
			return null;

		Map<String, Float> map = new HashMap<String, Float>();
		DefaultSimilarity simObj = new DefaultSimilarity();

		try {
			int docId = name2id.get(docName);
			
			TermFreqVector termVector = reader.getTermFreqVector(docId, "contents");
			int numDocs = reader.numDocs();

			int[] termFreq = termVector.getTermFrequencies();
			String[] terms = termVector.getTerms();
			for (int i = 0; i < terms.length; i++) {
				//avoid stop words
				if (StringUtils.isStopWord(terms[i]))
					continue;
				
				int tf = termFreq[i];
				int df = reader.docFreq(new Term("contents", terms[i]));
				float tfidf = simObj.tf(tf) * simObj.idf(df, numDocs);
				map.put(terms[i], tfidf);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return map;		
	}
	
	/**
	 * This function assumes that the TFIDF vector of the document containing text is already
	 * given. We simply build a tfidf-vector of the text out of the docVector. 
	 * The purpose of doing this is to save the time computing the tf-idf value for words in
	 * the same document.
	 * 
	 * @param text
	 * @param docVector
	 * @return
	 */
	public Map<String, Float> TextTFIDFVector(String text, Map<String, Float> docVector) {
		Map<String, Float> map = new HashMap<String, Float>();
		
		//preprocess the text using StandardAnalyzer (StandardAnalyzer2 + StopAnalyzer).
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_34);
		TokenStream tokenStream = analyzer.tokenStream("string",
				new StringReader(text));
		CharTermAttribute charTermAttribute = tokenStream
				.addAttribute(CharTermAttribute.class);

		try {
			tokenStream.reset();
			while (tokenStream.incrementToken()) {
				String term = charTermAttribute.toString();

				if (docVector.containsKey(term))
					map.put(term, docVector.get(term));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		analyzer.close();
		
		return map;
	}
}
