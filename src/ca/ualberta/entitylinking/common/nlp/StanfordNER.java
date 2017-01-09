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
package ca.ualberta.entitylinking.common.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import ca.ualberta.entitylinking.common.data.Document;
import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.data.Sentence;
import ca.ualberta.entitylinking.common.data.Token;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
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

public class StanfordNER {

	private StanfordCoreNLP pipeline;
	private String orlandoModel = "";

	Set<String> allowedEntityTypes;

	/**
	 * Creates a StanfordCoreNLP object, with POS tagging, lemmatization and NER .
	 */
	public StanfordNER(){
		this(Entity.defaultAllowedEntityTypes);
	}

	/**
	 * Creates a StanfordCoreNLP object, with POS tagging, lemmatization and NER .
	 */
	public StanfordNER(Set<String> allowedEntityTypes){
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
		pipeline = new StanfordCoreNLP(props);
		this.allowedEntityTypes = allowedEntityTypes;
	}

	/**
	 * Creates a StanfordCoreNLP object, with POS tagging, lemmatization and custom
	 * NER using Orlando model.
	 * @param orlandoModel The filename of the custom model.  Place it in the parent directory of your bin, data and lib folders.
	 */
	public StanfordNER(String orlandoModel){
		this.orlandoModel = orlandoModel;

		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma");
		pipeline = new StanfordCoreNLP(props);
	}


	/**
	 * Annotated text that has been pre-split into sentences.
	 */
	public Document annotateSentences(List<String> sentences) {

		// TODO: the original document has a bunch of metadata associated, which...
		// this document does not have at all.
		Document document = new Document();

		for (String text : sentences) {
			List<Sentence> processed = annotateText(text);
            for (Sentence sentence : processed) {
                document.addSentence(sentence);
            }
		}

		return document;
	}

	public List<Sentence> annotateText(String text){

		if (!(orlandoModel.isEmpty())) {
			try {
				@SuppressWarnings("rawtypes")
				AbstractSequenceClassifier orlandoClassifier = CRFClassifier.getClassifierNoExceptions(orlandoModel);
				text = orlandoClassifier.classifyWithInlineXML(text);
			} catch (Exception e) {
				System.err.println("[WARNING] Stanford NER was unable to classify the following: ");
				System.out.println("\t" + text + "\n");
				e.printStackTrace();
			}
		}

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);
		List<Sentence> mySentences = new ArrayList<Sentence>();

		try{
			// run all Annotators on this text
			pipeline.annotate(document);
		} catch(Exception e){
			System.out.println("[WARNING] Stanford NER was unable to annotate the following text (more details in the stack trace): ");
			System.out.println("\t\t" + text);
			e.printStackTrace();
			return mySentences;
		}

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);


		for(CoreMap sentence: sentences) {

			List<Token> tokens = new ArrayList<Token>();
			List<Mention> mentions = new ArrayList<Mention>();
			int position=0;
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
				// this is the POS tag of the token
				String pos = token.get(PartOfSpeechAnnotation.class);
				// this is the NER label of the token
				String ne = token.get(NamedEntityTagAnnotation.class);
				// this is the token offset
				int bPos = token.beginPosition();
				int ePos = token.endPosition();

				Token mytoken = new Token(word, position, bPos, ePos-1);
				mytoken.addAnnotation(Token.LEMMA_ANNOTATION, lemma);
				mytoken.addAnnotation(Token.POS_ANNOTATION, pos);
				mytoken.addAnnotation(Token.ENTITY_ANNOTATION, ne);
				tokens.add(mytoken);

				// keep track of mentions
				if(lastNe.equals("O")){
					if(!ne.equals("O")){
						startEntity = position;
						name = word;
					}
				}else{
					if(ne.equals("O")){
						int endEntity = position-1;
						createMention(name, lastNe, startEntity, endEntity, mentions);
					}else{
						if(ne.equals(lastNe)){
							name += " " + word;
						}
					}

					if(!ne.equals(lastNe) && !ne.equals("O")){
						int endEntity = position-1;
						createMention(name, lastNe, startEntity, endEntity, mentions);

						startEntity=position;
						name = word;
					}

				}

//				System.out.println(word + "\t" + lemma + "\t" + pos + "\t" + ne);
				lastNe = ne;
				position++;

			}

			// verify mention ending at the last token
			if(!lastNe.equals("O") && !lastNe.equals(".")){
				int endEntity = position-1;
				createMention(name, lastNe, startEntity, endEntity, mentions);
			}

			Sentence mySentence  = new Sentence(tokens);
			for(Mention mention : mentions){
				mySentence.addMention(mention);

			}
			mySentences.add(mySentence);


		}

		return mySentences;
	}

	private void createMention(String name, String stanfordType, int startEntity,
			int endEntity, List<Mention> mentions) {

		Entity entity = createEntity(name, stanfordType);
		//Entity is null when type is not allowed.
		if(entity != null){
			Mention mention = new Mention(entity, Mention.NONE, startEntity, endEntity);
			mentions.add(mention);
		}

	}

	/**
	 * public interface for the create Mention method.
	 * designed to recover a document from the nlptools output
	 * @param name
	 * @param stanfordType
	 * @param startEntity
	 * @param endEntity
	 * @return
	 */
	public void createMentions(String name, String stanfordType, int startEntity, int endEntity, List<Mention> mentions) {
		createMention(name, stanfordType, startEntity, endEntity, mentions);
	}



	private Entity createEntity(String name, String stanfordType){

		//System.out.println("Entity: " + name + "(" + stanfordType + ")");

		String myType=Entity.NONE;

		if(stanfordType.equals("PERSON"))
			myType = Entity.PERSON;

		if(stanfordType.equals("ORGANIZATION"))
			myType = Entity.ORGANIZATION;

		if(stanfordType.equals("LOCATION"))
			myType = Entity.LOCATION;

		if(stanfordType.equals("DATE"))
			myType = Entity.DATE;

		if(stanfordType.equals("GPE"))
			myType = Entity.GPE;

		if(stanfordType.equals("MISC"))
			myType = Entity.MISC;

		if(stanfordType.equals("MONEY"))
			myType = Entity.MONEY;

		if(stanfordType.equals("PERCENT"))
			myType = Entity.PERCENT;

		if(stanfordType.equals("TIME"))
			myType = Entity.TIME;

		if(stanfordType.equals("NUMBER"))
			myType = Entity.NUMBER;

		Entity entity = null;

		if(allowedEntityTypes.contains(myType)){
			entity = new Entity("", name, myType);
			String id = entity.generateId();
			entity.setId(id);
		}

		return entity;
	}

}
