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
package ca.ualberta.entitylinking.experiment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ca.ualberta.entitylinking.common.data.Document;
import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.nlp.OrthoMatcherCoref;
import ca.ualberta.entitylinking.common.nlp.StanfordNER;
import ca.ualberta.entitylinking.config.WNEDConfig;
import ca.ualberta.entitylinking.cs.CandidateSelectionLucene;
import ca.ualberta.entitylinking.utils.DocumentUtils;
import ca.ualberta.entitylinking.utils.ELUtils;
import ca.ualberta.entitylinking.utils.XmlProcessor;

public class PriorProbEL {
	private static Logger LOGGER = LogManager.getLogger(PriorProbEL.class);

	protected StanfordNER ner = null;
	protected OrthoMatcherCoref orthoMatcher = null;
	protected CandidateSelectionLucene cs = null;

	public PriorProbEL(String configFile) {
		WNEDConfig.loadConfiguration(configFile);
		orthoMatcher = new OrthoMatcherCoref();
		Set<String> allowedEntityTypes = new HashSet<String>();
		allowedEntityTypes.add(Entity.PERSON);
		allowedEntityTypes.add(Entity.ORGANIZATION);
		allowedEntityTypes.add(Entity.LOCATION);
		allowedEntityTypes.add(Entity.MISC);
		ner = new StanfordNER(allowedEntityTypes);
        LOGGER.info(ELUtils.currentTime() + "Done with loading StanfordNER and GATE OrthoMatcher");

        //Candidate selection
		cs = new CandidateSelectionLucene();
	}
	
	public String linkingNoCoref(String dataFile) {
		//1. Load the query file into a dom tree.
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		org.w3c.dom.Document dom = null;

		try {
			//Using factory get an instance of document builder
			db = dbf.newDocumentBuilder();
			//parse using builder to get DOM representation of the XML file
			dom = db.parse(dataFile);
		}catch(Exception e) {
			e.printStackTrace();
		}

		//get the root element, and its nodes.
		Element rootEle = dom.getDocumentElement();
		NodeList docNL = rootEle.getElementsByTagName("document");
		if (docNL == null || docNL.getLength() <= 0) {
			LOGGER.warn("docNL empty");
			return null;
		}

		Map<Entity, Double> candidates = null;

		int correct = 0;
		int total = 0;

		for (int i = 0; i < docNL.getLength(); i++) {
			//get the annotations of each document.
			Element docEle = (Element)docNL.item(i);

			//get a node list of <annotation>
			NodeList annoteNL = docEle.getElementsByTagName("annotation");
			if (annoteNL == null || annoteNL.getLength() <= 0)
				continue;

			for (int j = 0; j < annoteNL.getLength(); j++) {
				Element element = (Element) annoteNL.item(j);
				Element newE = dom.createElement("entity");

				String mentionName = XmlProcessor.getTextValue(element, "mention");
				String wikiName = XmlProcessor.getTextValue(element, "wikiName");
				if (wikiName != null && wikiName.equals("NIL"))
					wikiName = null;

				//we ignore the queries whose wikiName is null (linked to NIL).
				if (wikiName == null || wikiName.isEmpty() ||
					mentionName == null || mentionName.isEmpty()) {
					continue;
				}

				total++;
				candidates = cs.selectCandidatesFull(mentionName);
				if (candidates == null || candidates.isEmpty()) {
					newE.appendChild(dom.createTextNode("NIL"));
					element.appendChild(newE);
					continue;
				}

				//Get the highest ranked entity.
				Entity maxEnt = null;
				double maxValue = 0;
				for (Entity ent : candidates.keySet()) {
					double value = candidates.get(ent);
					if (value <= maxValue)
						continue;

					maxValue = value;
					maxEnt = ent;
				}

				if (maxEnt.getName().equals(wikiName))
					correct++;
				
				newE.appendChild(dom.createTextNode(maxEnt.getName()));
				element.appendChild(newE);
			}
		}
		
		double accuracy = (correct * 1.0 / total);
		System.out.println("Accuracy: " + accuracy);

		//output to a file.
		String outFile = null;
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

			outFile = new String(dataFile + ".priorProb-no-Coref");
			
			DOMSource source = new DOMSource(dom);
			StreamResult result = new StreamResult(new File(outFile));
			
			transformer.transform(source, result);
		} catch (Exception ie) {
			ie.printStackTrace();
		}
		
		return outFile;
	}
	
	public String linkingWithCoref(String dataFile) {
        String dir = dataFile.substring(0, dataFile.lastIndexOf('/'));
        WNEDConfig.DATASET_DIR = dir + "/RawText/";

		//1. Load the query file into a dom tree.
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		org.w3c.dom.Document dom = null;

		try {
			//Using factory get an instance of document builder
			db = dbf.newDocumentBuilder();
			//parse using builder to get DOM representation of the XML file
			dom = db.parse(dataFile);
		}catch(Exception e) {
			e.printStackTrace();
		}

		//get the root element, and its nodes.
		Element rootEle = dom.getDocumentElement();
		NodeList docNL = rootEle.getElementsByTagName("document");
		if (docNL == null || docNL.getLength() <= 0) {
			LOGGER.warn("docNL empty");
			return null;
		}

		Map<Entity, Double> candidates = null;

		for (int i = 0; i < docNL.getLength(); i++) {
			//get the annotations of each document.
			Element docEle = (Element)docNL.item(i);
			//get the attribute <docName> of each document
			String docName = docEle.getAttribute("docName");

			//get a node list of <annotation>
			NodeList annoteNL = docEle.getElementsByTagName("annotation");
			if (annoteNL == null || annoteNL.getLength() <= 0)
				continue;

			LOGGER.info("[doc]: " + docName);
			//Tokenize the document and get the index of each term.
			String content = ELUtils.readFile(WNEDConfig.DATASET_DIR + "/" + docName);
			// Annotate document.
			Document doc = DocumentUtils.annotateDocument(content, ner, orthoMatcher);
			Map<Integer, Mention> idxMenMap = DocumentUtils.getIndex(doc);

			List<Mention> mentions = new ArrayList<Mention>();
			List<Element> elements = new ArrayList<Element>();
			for (int j = 0; j < annoteNL.getLength(); j++) {
				Element element = (Element) annoteNL.item(j);

				String mentionName = XmlProcessor.getTextValue(element, "mention");
				String wikiName = XmlProcessor.getTextValue(element, "wikiName");
				int offset = XmlProcessor.getIntValue(element, "offset");

				if (mentionName == null || mentionName.isEmpty())
					continue;

				if (wikiName != null && (wikiName.equals("NIL") || wikiName.isEmpty()))
					wikiName = null;
				if (wikiName == null)
					continue;

				Mention m = DocumentUtils.createMention(mentionName, offset, doc, idxMenMap);
				mentions.add(m);
				elements.add(element);
			}
			
			if (elements == null || elements.isEmpty())
				continue;

//			ELUtils.resolve(mentions);
			
			List<String> results = new ArrayList<String>();
	        //Select candidates.
			for (Mention m : mentions) {
				candidates = cs.selectCandidates(m.getEntity().getName());
				if (candidates == null || candidates.isEmpty()) {
		            LOGGER.info("\t" + m.getName() + " : No candidates!");
		            results.add(null);
		            continue;
				}
				
				//Get the highest ranked entity.
				Entity maxEnt = null;
				double maxValue = 0;
				for (Entity ent : candidates.keySet()) {
					double value = candidates.get(ent);
					if (value <= maxValue)
						continue;

					maxValue = value;
					maxEnt = ent;
				}
				
				results.add(maxEnt.getName());				
			}

			for (int j = 0; j < elements.size(); j++) {
				Element ele = elements.get(j);
				Element newE = dom.createElement("entity");

				if (results != null && 
						!results.isEmpty() &&
						results.get(j) != null)
					newE.appendChild(dom.createTextNode(results.get(j)));
				else
					newE.appendChild(dom.createTextNode("NIL"));
				
				ele.appendChild(newE);
			}

		}
		
		//output to a file.
		String outFile = null;
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
//			String timestamp = sdf.format(new Date(System.currentTimeMillis()));
			outFile = new String(dataFile + ".priorProb-with-Coref");
			
			DOMSource source = new DOMSource(dom);
			StreamResult result = new StreamResult(new File(outFile));
			
			transformer.transform(source, result);
		} catch (Exception ie) {
			ie.printStackTrace();
		}
		
		return outFile;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		PriorProbEL obj = new PriorProbEL("el.config");
		String outFile = null;
		
		if (args[1].equalsIgnoreCase("noref"))
			outFile = obj.linkingNoCoref(args[0]);
		else
			outFile = obj.linkingWithCoref(args[0]);
		
		// report the accuracy of the entity linking.
		Evaluation.accuracy(outFile);
	}
}
