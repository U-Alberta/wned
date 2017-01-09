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

package ca.ualberta.entitylinking.common.data;

import java.io.Serializable;
import java.util.List;

public class Chunk implements Serializable{

	private static final long serialVersionUID = 1;

	/** The sentence containing this chunk */
	protected Sentence sentence;

	/** The position of the first token of this chunk in the sentence */
	protected int startToken;

	/** The position of the last token of this chunk in the sentence */
	protected int endToken;

	public Chunk(Sentence sentence, int startToken, int endToken){
		this.sentence = sentence;
		this.startToken = startToken;
		this.endToken = endToken;
	}

	public String toString(){
		return getText();
	}

	public String getText(){
		StringBuffer buffer = new StringBuffer(10);
		List<Token> tokens = sentence.getTokens();
		for(int i=startToken;i<=endToken;i++){
			buffer.append(tokens.get(i) + " ");
		}
		return buffer.toString().trim();

	}

	public int getStartToken() {
		return startToken;
	}
	public void setStartToken(int startToken) {
		this.startToken = startToken;
	}
	public int getEndToken() {
		return endToken;
	}
	public void setEndToken(int endToken) {
		this.endToken = endToken;
	}

	public Sentence getSentence() {
		return sentence;
	}

	public void setSentence(Sentence sentence) {
		this.sentence = sentence;
	}

	@Override
	public int hashCode() {
		int hash = this.getStartToken() + 31 * 3;
		hash += this.getEndToken() + 31 * 2;
		hash += this.getSentence().hashCode() * 31;
		return hash;
	}

	@Override
	public boolean equals(Object aThat) {
		//check for self-comparison
		if ( this == aThat ) return true;

		//use instanceof instead of getClass here for two reasons
		//1. if need be, it can match any supertype, and not just one class;
		//2. it renders an explict check for "that == null" redundant, since
		//it does the check for null already - "null instanceof [type]" always
		//returns false. (See Effective Java by Joshua Bloch.)
		if ( !(aThat instanceof Chunk) ) return false;
		//Alternative to the above line :
		//if ( aThat == null || aThat.getClass() != this.getClass() ) return false;

		//cast to native object is now safe
		Chunk that = (Chunk) aThat;

		return this.getSentence()==that.getSentence() && this.getStartToken()==that.getStartToken() && this.getEndToken()==that.getEndToken();
	}

	public int compareTo(Chunk that) {
		final int BEFORE = -1;
		final int EQUAL = 0;
		final int AFTER = 1;

		if(this == that) return EQUAL;

		if(this.getSentence() != that.getSentence()) return BEFORE;

		if(this.getStartToken() < that.getStartToken()) return BEFORE;

		if(this.getStartToken() == that.getStartToken()){
			if(this.getEndToken() == that.getEndToken()) return EQUAL;
			if(this.getEndToken() < that.getEndToken()) return BEFORE;
		}

		return AFTER;
	}

}
