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

import ca.ualberta.entitylinking.config.WNEDConfig;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.creole.ANNIEConstants;
import gate.creole.ExecutionException;
import gate.creole.orthomatcher.OrthoMatcher;
import gate.creole.tokeniser.DefaultTokeniser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import ca.ualberta.entitylinking.common.data.Document;
import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.data.Sentence;
import ca.ualberta.entitylinking.common.data.Token;


public class OrthoMatcherCoref {
	private static Logger LOGGER = LogManager.getLogger(OrthoMatcher.class);
//	LOGGER.setLevel(Level.OFF);
	protected OrthoMatcher orthomatcher;
	protected DefaultTokeniser tokenizer;

	protected HashMap<Integer,Mention> mentionMap;

	public OrthoMatcherCoref(){

		try{
			String gateHomePath = WNEDConfig.gateHome;
			String gateConfigPath = gateHomePath + "/gate.xml";
			String gatePluginHome = gateHomePath + "/plugins";

			System.setProperty("gate.home", gateHomePath);
			System.setProperty("gate.site.config", gateConfigPath);
			System.setProperty("gate.user.config", gateConfigPath);
			System.setProperty("gate.plugins.home", gatePluginHome);

			//initializing Gate
			Gate.init();

			// Load ANNIE plugin
			File gateHome = Gate.getGateHome();
			File pluginsHome = new File(gateHome, "plugins");
			Gate.getCreoleRegister().registerDirectories(new File(pluginsHome, "ANNIE").toURI().toURL());

			//create a tokeniser
			tokenizer = (DefaultTokeniser)Factory.createResource(
			"gate.creole.tokeniser.DefaultTokeniser");


			//create an orthomatcher
			orthomatcher = (OrthoMatcher) Factory.createResource(
			"gate.creole.orthomatcher.OrthoMatcher");


			orthomatcher.setProcessUnknown(true);

		} catch (Exception e){
			/* Re-throw, but marked as unchecked to preserve API. */
			throw new IllegalStateException("Could not load Gate.", e);
		}
	}


	private gate.Document createGateDocument(Document document){

		// maps dummy ids (for orthomatcher) to mentions
		mentionMap = new HashMap<Integer,Mention>();
		Map<Mention, Integer> invertedMentionMap = new HashMap<Mention, Integer>();

		int id=0;
		StringBuilder docText = new StringBuilder();

		long startCurrentMention=-1L;
		List<Long> starts = new ArrayList<Long>();
		List<Long> ends = new ArrayList<Long>();
		List<String> types = new ArrayList<String>();
		List<FeatureMap> paramList = new ArrayList<FeatureMap>();

		//TODO: Count the types for each entity and choose the most popular one.
		//Save the types for each entity name (except misc, which is seen as unknown by the OrthoMatcher).
		//We use this index to make sure mentions with the same name get the same type.
		//If more than one non-Misc type exists, we choose the last one seen.
		Map<String, String> typeByName = new HashMap<String, String>();
		for(Sentence sentence : document.getSentences()){
			for(Mention mention : sentence.getMentions()){
				if(!mention.getEntity().getType().equals(Entity.MISC)){
					typeByName.put(mention.getEntity().getName().toLowerCase(), mention.getEntity().getType());
				}
			}
		}

		for(Sentence sentence : document.getSentences()){

			Map<Integer,Mention> start = new HashMap<Integer,Mention>();
			Map<Integer,Mention> end = new HashMap<Integer,Mention>();

			//For every mention
			for(Mention mention : sentence.getMentions()){

				//Create internal ids
				id++;
				mentionMap.put(id, mention);
				invertedMentionMap.put(mention, id);

				//Save start and end offsets
				start.put(mention.getStartToken(), mention);
				end.put(mention.getEndToken(), mention);

			}

			//Build the Gate Document by iterating token by token
			for(Token token : sentence.getTokens()){
				if(start.containsKey(token.getPosition())){
					// Save start offset for the mention
					startCurrentMention = docText.length();
				}
				//Aggregate text
				docText.append(token.getText());

				//Save all information about the mention ending in this token
				if(end.containsKey(token.getPosition())){
					Mention mention = end.get(token.getPosition());

					FeatureMap annotParams = Factory.newFeatureMap();

					String type = "Unknown";

					//Make sure mentions with the same name get the name type.
					//This is a workaround for a OrthoMatcher's bug.
					String myType = mention.getEntity().getType();
					String storedType = typeByName.get(mention.getEntity().getName().toLowerCase());
					if(storedType != null && !myType.equals(storedType)){
						myType = storedType;
						//TODO: Changing the type at this point is a bad idea.
						// Many entities have a type in the id that is different from the type in the entity object.
						mention.getEntity().setType(myType);
					}

					if(myType.equals(Entity.PERSON)) type = "Person";
					if(myType.equals(Entity.ORGANIZATION)) type = "Organization";
					if(myType.equals(Entity.LOCATION)) type = "Location";

					annotParams.put("entity_name", mention.getEntity().getName().toLowerCase());
					annotParams.put("entity_id", invertedMentionMap.get(mention));

//					System.out.println("adding " + startCurrentMention + " " + docText.length() + " " + type + " " + annotParams);

					starts.add(startCurrentMention);
					ends.add((long) docText.length());
					types.add(type);
					paramList.add(annotParams);
//					System.out.println("sub : " + docText.substring((int) startCurrentMention, docText.length()));

				}

				docText.append(" ");

			}
		}


		//Create the document
		FeatureMap params = Factory.newFeatureMap(); // params list for new doc
		params.put(gate.Document.DOCUMENT_STRING_CONTENT_PARAMETER_NAME, docText);
		// use the platform's default encoding rather than GATE's
		params.put(gate.Document.DOCUMENT_ENCODING_PARAMETER_NAME, "UTF-8");
		gate.Document gateDocument = null;

		try{
			gateDocument = (gate.Document) Factory.createResource(
					"gate.corpora.DocumentImpl", params
			);

			//Add the mention annotations
			AnnotationSet annotations = gateDocument.getAnnotations();
			for (int i = 0; i < starts.size(); i++) {
				annotations.add(starts.get(i), ends.get(i), types.get(i), paramList.get(i));

			}
		} catch (Exception e){
			e.printStackTrace();
			System.out.println("Error Details: ");
			System.out.println("Document Id: " + document.getId());

		}

		return gateDocument;

	}

	//transfer gate co-reference annotations to the original document

	private void processCoreferences(Document document, gate.Document gateDocument){

		AnnotationSet annotations = gateDocument.getAnnotations();
		HashMap<Annotation,Boolean> annotationSeen = new HashMap<Annotation,Boolean>();
		HashMap<Mention,Mention> coreferenceMap = new HashMap<Mention,Mention>();
		for(Annotation annot : annotations){
			if(annotationSeen.containsKey(annot)) //TODO check if necessary
				continue;

			@SuppressWarnings("rawtypes")
			ArrayList matches = (ArrayList) annot.getFeatures().get(ANNIEConstants.ANNOTATION_COREF_FEATURE_NAME);
			if(matches == null) continue;

			String longestMatchedEntity = "";
			int longestEntityId = -1;

			for(Object match : matches){
				Integer matchId = (Integer) match;
				Annotation matchedAnnot = annotations.get(matchId);
				String entityName = (String) matchedAnnot.getFeatures().get("entity_name");
				Integer entityId = (Integer) matchedAnnot.getFeatures().get("entity_id");

				if(entityName.length() > longestMatchedEntity.length()){
					longestMatchedEntity = entityName;
					longestEntityId = entityId;
				}

				annotationSeen.put(matchedAnnot, true);

			}

			Mention longestMention = mentionMap.get(longestEntityId);
			//Map every other entity in the chain to the longest entity
			for(Object match : matches){

				Integer matchId = (Integer) match;
				Annotation matchedAnnot = annotations.get(matchId);
				Integer entityId = (Integer) matchedAnnot.getFeatures().get("entity_id");



				if(entityId != longestEntityId){
					Mention mention = mentionMap.get(entityId);
					coreferenceMap.put(mention, longestMention);
					//					System.out.println("---" + mention.getEntity().getName() + " --> " + longestMention.getEntity().getName());
				}
			}
		}

		for (Sentence sentence : document.getSentences()){
			for (Mention mention : sentence.getMentions()){
				// Store the the longest entity equivalent to the mention in the chunk.
				if(coreferenceMap.containsKey(mention)){
					mention.setEntity(coreferenceMap.get(mention).getEntity());
				}
			}
		}
	}

	public void findCoreferences(Document document){

		//If document has no mentions, skip it.
		boolean containsNoMentions = true;
		for(Sentence sentence : document.getSentences()){
			if(sentence.getMentions().size() > 0){
				containsNoMentions = false;
				break;
			}
		}

		if(containsNoMentions) return;

		gate.Document gateDocument = createGateDocument(document);

		tokenizer.setDocument(gateDocument);
		orthomatcher.setDocument(gateDocument);

		//execute tokenizer and orthomatcher
		try {
			tokenizer.execute();

			// A workaround for orthomatcher bug
			AnnotationSet annotations = gateDocument.getAnnotations();
			for( Annotation annot : annotations){
				if(annot.getFeatures().get(gate.creole.orthomatcher.OrthoMatcher.TOKEN_CATEGORY_FEATURE_NAME) == null){
					annot.getFeatures().put(gate.creole.orthomatcher.OrthoMatcher.TOKEN_CATEGORY_FEATURE_NAME, "");
				}
			}

			orthomatcher.execute();

			} catch (ExecutionException e) {
				/* Throw the exception unchecked, preserving same API. */
				throw new IllegalStateException("Could not find corefs", e);
			}

		processCoreferences(document, gateDocument);
		Factory.deleteResource(gateDocument);
	}

	/**
	 * Returns the same document with all coreferences annotated.
	 */
	public Document annotateCoreferences(Document document) {
		findCoreferences(document);
		return document;
	}

}
