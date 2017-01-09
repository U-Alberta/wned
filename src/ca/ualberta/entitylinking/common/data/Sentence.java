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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Sentence implements Serializable, Cloneable{

	private static final long serialVersionUID = 1;

	/** The document containing this sentence */
	protected Document document;

	/** The sentence number in the document. Starts at zero. 
	 * Must match the index in {@link Document.getSentences()}. */
	protected int number;

	/** The tokens in a sentence */
	protected List<Token> tokens;

	/** Entity mentions*/
	protected LinkedList<Mention> mentions;

	/** Constructor of empty sentences (containing no tokens). */
	public Sentence(){
		this(new ArrayList<Token>());
	}

	/** Complete constructor. 
	 * @param tokens A list of tokens.
	 * */
	public Sentence(List<Token> tokens){
		this.tokens = tokens;
		mentions = new LinkedList<Mention>();
	}

	@Override
	public Sentence clone() throws CloneNotSupportedException{

		Sentence newSentence = new Sentence();

		for(Token token : this.getTokens()){
			tokens.add((Token) token.clone());
		}

		if(this.getMentions() != null){
			for(Mention mention : this.getMentions()){
				Mention newMention = (Mention) mention.clone();
				newMention.setSentence(newSentence);
				mentions.add(newMention);
			}
		}

		return newSentence;
	}

	/**
	 * toPlainTextSquareBracketFormat
	 * 
	 * @return this sentence in plain text, where mentions are annotated with square brackets and relations with curly brackets. 
	 * For example: "[[[PER Barack Obama]]] was {{{seen in}}} the [[[LOC White House]]] ."
	 */
	public String toPlainTextBracketFormat(){

		Map<Integer, Mention> startMention = new HashMap<Integer, Mention>();
		Map<Integer, Mention> endMention= new HashMap<Integer, Mention>();

		if(this.getMentions() != null){
			for(Mention mention : this.getMentions()){
				startMention.put(mention.getStartToken(), mention);
				endMention.put(mention.getEndToken(), mention);
			}
		}

		StringBuffer sentence = new StringBuffer();

		for(int i=0; i< this.getTokens().size(); i++){
			Token token = this.getTokens().get(i);

			if(startMention.containsKey(i)){
				sentence.append("[[[" + startMention.get(i).getEntity().getType() + " ");
			}

			sentence.append(token.getText());

			if(endMention.containsKey(i)){
				sentence.append("]]]");
			}

			sentence.append(" ");

		}

		return sentence.toString().trim();
	}

	public String getAnnotatedText(){
		StringBuffer buffer = new StringBuffer();

		Map<Integer, Mention> startMention = new HashMap<Integer, Mention>();
		Map<Integer, Mention> endMention = new HashMap<Integer, Mention>();

		if(this.getMentions() != null){
			for(Mention mention : this.getMentions()){
				startMention.put(mention.getStartToken(), mention);
				endMention.put(mention.getEndToken(), mention);
			}
		}
		
		for(int i=0; i < tokens.size(); i++){

			Token token = tokens.get(i);

			if(startMention.containsKey(i))
				buffer.append("<entity type=\"" + startMention.get(i).getEntity().getType() + "\" id=\""+ startMention.get(i).getEntity().getId() + "\">");
			
			if(token.getAnnotations().get(Token.POS_ANNOTATION) == null)
				buffer.append(token.getText());
			else
				buffer.append(token.getText() + "/" + token.getAnnotations().get(Token.POS_ANNOTATION));

			if(endMention.containsKey(i))
				buffer.append("</entity>");
			
			if(i != tokens.size()-1)
				buffer.append(" ");
		}

		return buffer.toString();
	}

	/**
	 * Returns the list of mentions in this sentence.
	 * @return mentions List of mentions.
	 */
	public List<Mention> getMentions() {
		return mentions;
	}

	/**
	 * Adds a mention into this sentence (and sets the sentence attribute of the mention).
	 * The mention is inserted as to keep the mention list in order of startToken (first) and endToken (second). 
	 * @param mention A mention object.
	 */
	public void addMention(Mention mention) {
		mention.setSentence(this);
		// Empty list
		if(mentions.size()==0){
			mentions.add(mention);
			return;
		}

		// Add in the end
		if(mention.compareTo(mentions.getLast())>0){
			mentions.addLast(mention);
			return;
		}

		// Add mention in the middle of the list
		for(int i=0;i<mentions.size();i++){
			if(mention.compareTo(mentions.get(i)) < 0){
				mentions.add(i, mention);
				return;
			}

			// In case of tie, don't add this mention since it already exists.
			if(mention.compareTo(mentions.get(i))==0)
				return;

		}

	}

	public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	public String getText(){
		StringBuffer buffer = new StringBuffer(100);

		for(Token token : getTokens()){
			buffer.append(" " + token);
		}

		return buffer.toString().trim();
	}


	public String toString() {
		StringBuffer buffer = new StringBuffer(100);

		buffer.append("Sentence: ");
		buffer.append(this.getText());
		buffer.append("\n");

		buffer.append("Mentions:");
		for(Mention mention : getMentions()){
			buffer.append(" [" + mention + "]");
		}
		buffer.append("\n");


		return buffer.toString();
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public void setTokens(List<Token> tokens) {
		this.tokens = tokens;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

}
