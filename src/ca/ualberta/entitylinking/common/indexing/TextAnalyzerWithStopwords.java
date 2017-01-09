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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

public class TextAnalyzerWithStopwords extends Analyzer {
	  /** Default maximum allowed token length */
	  public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;
	  protected Version matchVersion = null;

	  private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

	  /** Builds an analyzer with the default stop words.
	   * @param matchVersion Lucene version to match See {@link
	   * <a href="#version">above</a>}
	   */
	  public TextAnalyzerWithStopwords(Version matchVersion) {
		  this.matchVersion = matchVersion;
	  }

	  /**
	   * Set maximum allowed token length.  If a token is seen
	   * that exceeds this length then it is discarded.  This
	   * setting only takes effect the next time tokenStream or
	   * tokenStream is called.
	   */
	  public void setMaxTokenLength(int length) {
	    maxTokenLength = length;
	  }

	  /**
	   * @see #setMaxTokenLength
	   */
	  public int getMaxTokenLength() {
	    return maxTokenLength;
	  }

	  public TokenStream tokenStream(String fieldName, Reader reader) {
	    final StandardTokenizer src = new StandardTokenizer(matchVersion, reader);
	    TokenStream tok = new StandardFilter(matchVersion, src);
	    return new LowerCaseFilter(matchVersion, tok);
	  }
}