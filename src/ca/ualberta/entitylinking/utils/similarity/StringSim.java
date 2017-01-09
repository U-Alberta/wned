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
package ca.ualberta.entitylinking.utils.similarity;

import com.wcohen.ss.JaroWinkler;
import com.wcohen.ss.MongeElkan;
import com.wcohen.ss.SoftTFIDF;
import com.wcohen.ss.Level2Levenstein;

import org.apache.lucene.search.spell.NGramDistance;

public class StringSim {
	public static class Levenshtein
	{
	    public Levenshtein()
	    {
	        super();
	    }

	    public static double compare(final String s1, final String s2)
	    {
	        double retval = 0.0;
	        final int n = s1.length();
	        final int m = s2.length();
	        if (0 == n)
	        {
	            retval = m;
	        }
	        else if (0 == m)
	        {
	            retval = n;
	        }
	        else
	        {
	            retval = 1.0 - (editDistance(s1, n, s2, m) *1.0/ (Math.max(n, m)));
	        }
	        return retval;
	    }

	    public static int editDistance(final String s1, final String s2) {
	    	return editDistance(s1, s1.length(), s2, s2.length());
	    }

	    private static int editDistance(final String s1, final int n,
	                           final String s2, final int m)
	    {
	        int matrix[][] = new int[n + 1][m + 1];
	        for (int i = 0; i <= n; i++)
	        {
	            matrix[i][0] = i;
	        }
	        for (int i = 0; i <= m; i++)
	        {
	            matrix[0][i] = i;
	        }

	        for (int i = 1; i <= n; i++)
	        {
	            int s1i = s1.codePointAt(i - 1);
	            for (int j = 1; j <= m; j++)
	            {
	                int s2j = s2.codePointAt(j - 1);
	                final int cost = s1i == s2j ? 0 : 1;
	                matrix[i][j] = min3(matrix[i - 1][j] + 1,
	                                    matrix[i][j - 1] + 1,
	                                    matrix[i - 1][j - 1] + cost);
	            }
	        }
	        return matrix[n][m];
	    }

	    private static int min3(final int a, final int b, final int c)
	    {
	        return Math.min(Math.min(a, b), c);
	    }
	}

	public static double jaro_winkler_score(String s, String t) {
		JaroWinkler obj = new JaroWinkler();

		return obj.score(obj.prepare(s), obj.prepare(t));
	}

	public static double edit_distance_score(String s, String t) {
		return Levenshtein.compare(s, t);
	}

	public static int edit_distance(String s, String t) {
		return Levenshtein.editDistance(s, t);
	}

	public static double monge_elkan_score(String s, String t) {
		MongeElkan obj = new MongeElkan();

		return obj.score(obj.prepare(s), obj.prepare(t));
	}

	public static double soft_tfidf_score(String s, String t) {
		SoftTFIDF obj = new SoftTFIDF(new Level2Levenstein(), 0.8);

		return obj.score(obj.prepare(s), obj.prepare(t));
	}
	/**
	 * Compute the n-gram distance between two strings
	 * @param s source
	 * @param t target
	 * @param n number of characters in each gram.
	 * @return A similarity between 0 and 1.
	 */
	public static double ngram_distance(String s, String t, int n) {
		NGramDistance measure = new NGramDistance(n);

		return measure.getDistance(s, t);

	}

	/**
	 * Compute the longest common substring of two strings, normalized by the longer length of s and t.
	 * @param s
	 * @param t
	 * @return
	 */
	public static double lcs_distance(String s, String t) {
	    if (s == null || t == null || s.length() == 0 || t.length() == 0)
	        return 0;

	    int maxLen = 0;
	    int sl = s.length();
	    int tl = t.length();
	    int[][] table = new int[sl][tl];

	    for (int i = 0; i < sl; i++) {
	        for (int j = 0; j < tl; j++) {
	            if (s.charAt(i) == t.charAt(j)) {
	                if (i == 0 || j == 0)
	                    table[i][j] = 1;
	                else
	                    table[i][j] = table[i - 1][j - 1] + 1;

	                if (table[i][j] > maxLen)
	                    maxLen = table[i][j];
	            }
	        }
	    }

	    return maxLen;
	}
}