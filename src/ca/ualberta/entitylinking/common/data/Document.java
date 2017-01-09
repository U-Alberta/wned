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
import java.util.Date;
import java.util.List;

public class Document implements Serializable{
	
	private static final long serialVersionUID = 1; 

	private String id; 
	private String fileName;
	
	/* metadata */
	private String url;
	private String title;
	private Date publishedDate;

	/* document contents */
	private transient String originalText; //original text
	private transient String asciiText; //ascii text

	private List<Sentence> sentences;
	
	public Document() {
		sentences = new ArrayList<Sentence>();
	}
	
	public Document(String id, String url, String title, String originalText){

		this.id = id;
		this.url = url;
		this.title = title;
		this.originalText = originalText;

		sentences = new ArrayList<Sentence>();

	}
	
	public String toPlainTextBracketFormat(){
		StringBuilder builder = new StringBuilder(10000);
		builder.append("D\tfile=");
		if(this.getFileName() != null) builder.append(this.getFileName());
		builder.append("\tid=");
		if(this.getId() != null) builder.append(this.getId());
		builder.append("\ttitle=");
		if(this.getTitle() != null) builder.append(this.getTitle());
		builder.append("\tdate=");
		if( this.getPublishedDate() != null) builder.append(this.getPublishedDate());
		builder.append("\turl=");
		if(this.getUrl() != null) builder.append(this.getUrl());
		builder.append("\n");
		
		for(Sentence sentence : this.getSentences()){
			builder.append(sentence.toPlainTextBracketFormat());
			builder.append("\n");
		}
		return builder.toString();
	}
	
	public void addSentence(Sentence sentence){
		int number = sentences.size();
		sentences.add(sentence);
		sentence.setNumber(number);
		sentence.setDocument(this);
	}

	public String toString(){
		StringBuffer buffer = new StringBuffer();
		buffer.append(title + " (" + url + ")\n");
		for(Sentence sentence : sentences){
			buffer.append(sentence.toString());
		}
		return buffer.toString();
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getFileName() {
		return fileName;
	}
	
	public void setFileName(String fileName) {
		this.fileName = fileName;
//		System.out.println("[File Name]: " + this.fileName);
	}
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Date getPublishedDate() {
		return publishedDate;
	}

	public void setPublishedDate(Date publishedDate) {
		this.publishedDate = publishedDate;
	}

	public String getOriginalText() {
		return originalText;
	}

	public void setOriginalText(String originalText) {
		this.originalText = originalText;
	}

	public String getAsciiText() {
		return asciiText;
	}

	public void setAsciiText(String asciiText) {
		this.asciiText = asciiText;
	}

	public List<Sentence> getSentences() {
		return sentences;
	}

	public void setSentences(List<Sentence> sentences) {
		this.sentences = sentences;
	}
	
}
