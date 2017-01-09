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
package ca.ualberta.entitylinking.utils;

import java.util.Arrays;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopAnalyzer;

public class StringUtils {
	/**
	 * Use the StopAnalyzer.ENGLISH_STOP_WORDS_SET to check if the given term is a stop word.
	 * 
	 * @return
	 */
	public static boolean isStopWord(String word) {
		if (word == null || word.isEmpty())
			return true;
		
		CharArraySet stopWords = (CharArraySet)StopAnalyzer.ENGLISH_STOP_WORDS_SET;
		return stopWords.contains(word.toLowerCase());
	}
	
	/**
	 * Only works for ascii characters.
	 * 
	 * @param str
	 * @return
	 */
	public static String sort(String str) {
		if (str == null || str.isEmpty())
			return str;
		
		char[] chars = str.toCharArray();
		Arrays.sort(chars);
		
		return new String(chars);
	}
	
//	/**
//	 * Convert a word in plural format to its singular.
//	 *
//	 * @param word
//	 * @return
//	 */
//	public static String p2s(String word) {
//		return PlingStemmer.stem(word);
//	}
//
//	public static boolean isPlural(String word) {
//		return PlingStemmer.isPlural(word);
//	}
//
//	public static void main(String[] args) {
//		System.out.println(p2s("men"));
//	}
}