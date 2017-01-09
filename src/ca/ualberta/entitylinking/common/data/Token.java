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
import java.util.HashMap;
import java.util.Map;

public class Token implements Serializable, Cloneable{
	
	private static final long serialVersionUID = 1;

	public static final String ENTITY_ANNOTATION = "NER"; //e.g., ORGANIZATION, PERSON, etc.
	public static final String LEMMA_ANNOTATION = "LEM"; // lemma
	public static final String POS_ANNOTATION = "POS"; //part-of-speech tags
	public static final String RELATION_ANNOTATION = "REL"; //relational tokens
	public static final String PHRASE_ANNOTATION = "PCK"; //phrase chunker

	/** Annotations for each token (e.g. entities, part-of-speech tags, relational terms) */
	protected Map<String, String> annotations;
	
	/** The actual token */
	protected String text;
	
	/** The position of the token in the sentence */
	protected int position;
	
	/** Token offset **/
	protected int bPosition;
	protected int ePosition;
	
	/** 
	 * Half constructor.
	 * @param text Token substring.
	 * @param position Position of the token in the sentence, starting at 0.  The position must coincide with the index in the sentence list of tokens.
	 */
	public Token(String text, int position){
		this.text = text;
		this.position = position;
		
		annotations = new HashMap<String, String>(3);
	}

	/**
	 * Complete constructor
	 * @param text Token substring.
	 * @param position Position of the token in the sentence, starting at 0.  The position must coincide with the index in the sentence list of tokens.
	 * @param bPosition Token offset begin
	 * @param ePosition Token offset end
	 */
	public Token(String text, int position, int bPosition, int ePosition) {
		this.text = text;
		this.position = position;
		this.bPosition = bPosition;
		this.ePosition = ePosition;
		
		annotations = new HashMap<String, String>(3);
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		Token newToken = new Token(this.getText(), this.getPosition());
		newToken.getAnnotations().putAll(this.getAnnotations());
		return newToken;
	}
	
	public String toString(){
		return getText();
	}
	
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public int getPosition() {
		return position;
	}
	public void setPosition(int position) {
		this.position = position;
	}
	
	public int getbPosition() {
		return bPosition;
	}

	public void setbPosition(int bPosition) {
		this.bPosition = bPosition;
	}

	public int getePosition() {
		return ePosition;
	}

	public void setePosition(int ePosition) {
		this.ePosition = ePosition;
	}

	public Map<String, String> getAnnotations() {
		return annotations;
	}

	public void addAnnotation(String annotationType, String annotation) {
		this.annotations.put(annotationType, annotation);
	}
	

}
