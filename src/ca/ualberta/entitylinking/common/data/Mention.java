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
import java.util.Set;
import java.util.HashSet;

public class Mention implements Comparable<Mention>, Serializable, Cloneable{

	private static final long serialVersionUID = 1;

	public static final String NAME = "NAM";
	public static final String NOMINAL = "NOM";
	public static final String PROMONIAL = "PRO";
	public static final String PRENOMINAL = "PRE";
	public static final String NONE = "NONE";

	/** The entity referred by this mention */
	protected Entity entity;

	protected Chunk chunk;

	protected String type; 

	protected String name;
	
	protected Set<String> alternatives = null;

	/** 
	 * Creates a mention with no sentence. Its sentence will be set when this mention is added into a sentence.   
	 * 
	 * @param entity The entity referred by this mention.
	 * @param type The mention type (e.g., NAME, PRONOUN).
	 * @param startToken The position of the first token of this mention in the sentence above.
	 * @param endToken The position of the last token of this mention in the sentence above.
	 * 
	 */
	public Mention(Entity entity, String type, int startToken, int endToken){
		this.entity = entity;
		this.type = type;
		this.chunk = new Chunk(null, startToken, endToken);
		if (entity != null)
			this.name = entity.getName();
	}


	/** 
	 * Complete constructor. Creates a mention to an entity in a sentence. 
	 * 
	 * @param entity The entity referred by this mention.
	 * @param type The mention type (e.g., NAME, PRONOUN).
	 * @param sentence The sentence containg this mention.
	 * @param startToken The position of the first token of this mention in the sentence above.
	 * @param endToken The position of the last token of this mention in the sentence above.
	 * 
	 */
	public Mention(Entity entity, String type, Sentence sentence, int startToken, int endToken){
		this.entity = entity;
		this.type = type;
		this.chunk = new Chunk(sentence, startToken, endToken);
		if (entity != null)
			this.name = entity.getName();
	}

	@Override
	public Mention clone() throws CloneNotSupportedException{
		Mention newMention = null;
		if(this.getEntity() == null){
			newMention = new Mention(null, this.getType(), null, this.getStartToken(), this.getEndToken());
		}else{
			newMention = new Mention((Entity) this.getEntity().clone(), this.getType(), null, this.getStartToken(), this.getEndToken());
		}
		
		return newMention;
	}
	
	public String toString(){
		return chunk.toString() + " : " + entity.getId();
	}

	public int getStartToken(){
		return chunk.getStartToken();
	}

	public int getEndToken(){
		return chunk.getEndToken();
	}

	public Sentence getSentence() {
		return chunk.getSentence();
	}

	public void setSentence(Sentence sentence) {
		chunk.setSentence(sentence);
	}

	public Entity getEntity() {
		return entity;
	}
	public void setEntity(Entity entity) {
		this.entity = entity;
	}

	public Chunk getChunk() {
		return chunk;
	}

	public void setChunk(Chunk chunk) {
		this.chunk = chunk;
	}


	public String getType() {
		return type;
	}


	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return this.name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public void addAlternative(String alterName) {
		if (alternatives == null)
			alternatives = new HashSet<String>();
		
		alternatives.add(alterName);
	}
	
	public void setAlternatives(Set<String> alterNames) {
		alternatives = alterNames;
	}
	
	public Set<String> getAlternatives() {
		return alternatives;
	}
	
	@Override
	public int hashCode() {

		if(this.getChunk()==null){
			return 0;
		}

		return this.getChunk().hashCode();
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
		if ( !(aThat instanceof Mention) ) return false;
		//Alternative to the above line :
		//if ( aThat == null || aThat.getClass() != this.getClass() ) return false;

		//cast to native object is now safe
		Mention that = (Mention) aThat;

		// in order keep hashCode, equals and compareTo consistent... 
		if(this.getChunk()==null && that.getChunk()==null){
			return true;
		}

		if(this.getChunk()==null || that.getChunk()==null){
			return false;
		}

		return this.getChunk().equals(that.getChunk());
	}

	public int compareTo(Mention that) {
		final int BEFORE = -1;
		final int EQUAL = 0;
		final int AFTER = 1;

		if(this == that) return EQUAL;
		if(that == null) return BEFORE;

		// in order keep hashCode, equals and compareTo consistent...
		if(this.getChunk()==null && that.getChunk()==null) return EQUAL;
		if((this.getChunk()==null) && !(that.getChunk()==null)) return AFTER;
		if(!(this.getChunk()==null) && (that.getChunk()==null)) return BEFORE;

		return this.getChunk().compareTo(that.getChunk());
	}



}
