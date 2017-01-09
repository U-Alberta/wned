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

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import ca.ualberta.entitylinking.config.WNEDConfig;
import ca.ualberta.entitylinking.utils.XmlProcessor;

public class Evaluation {
    public static Logger LOGGER = LogManager.getLogger(Evaluation.class);

	/**
	 * String matching.
	 * 
	 * @param ent1
	 * @param ent2
	 * @return
	 */
	private static boolean matchString(String ent1, String ent2) {
		if (ent1 == null && ent2 == null)
			return true;
		else if (ent1 == null || ent2 == null)
			return false;
		else
			return ent1.equals(ent2);
	}
	
	/**
	 * Evaluate the entity linking results using a fuzzy measure.
	 * 
	 * @param resultFile
	 */
	public static void accuracy(String resultFile) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		org.w3c.dom.Document dom = null;
		
		try {
			//Using factory get an instance of document builder
			db = dbf.newDocumentBuilder();
			
			//parse using builder to get DOM representation of the XML file
			dom = db.parse(resultFile);
		}catch(ParserConfigurationException pce) {
			pce.printStackTrace();
		}catch(SAXException se) {
			se.printStackTrace();
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}

		//get the root element
		Element rootEle = dom.getDocumentElement();
		
		//get a nodelist of <document>
		NodeList docNL = rootEle.getElementsByTagName("document");
		if (docNL == null || docNL.getLength() <= 0) {
			System.out.println("docNL empty");
			return;
		}

		int totalQueries = 0;
		int totalCorrect = 0;
		int totalFound = 0;
		int totalDocs = 0;
		double precision = 0.0, recall = 0.0, f1 = 0.0;
		
		for (int i = 0; i < docNL.getLength(); i++) {
			//get the annotations of each document.
			Element docEle = (Element)docNL.item(i);
			
			//get a nodelist of <annotation>
			NodeList annoteNL = docEle.getElementsByTagName("annotation");
			if (annoteNL == null || annoteNL.getLength() <= 0)
				continue;

			int correct = 0, query = 0, found = 0;
			
			for (int j = 0; j < annoteNL.getLength(); j++) {
				Element annoteEle = (Element) annoteNL.item(j);

				String wikiName = XmlProcessor.getTextValue(annoteEle, "wikiName");
				String entity = XmlProcessor.getTextValue(annoteEle, "entity");

				if (wikiName != null && (wikiName.equals("NIL")||wikiName.isEmpty()))
					wikiName = null;
				if (entity!= null && (entity.equals("NIL")||entity.isEmpty()))
					entity = null;
				
				//Does not consider mentions with no true entity in the KB.
				if (wikiName == null && !WNEDConfig.NILPrediction)
					continue;

				totalQueries++;
				query++;
				if (entity != null || entity == wikiName) {
					totalFound++;
					found++;
				}
				
				if (matchString(wikiName, entity)) {
					totalCorrect++;
					correct++;
				}
			}
			
			if (query == 0)	continue;
			if (found == 0)
				precision += 0;
			else
				precision += correct * 1.0 / found;
			
			recall += correct * 1.0 / query;
			
			totalDocs++;
			String docName = docEle.getAttribute("docName");
			LOGGER.info(docName + ": " + correct*1.0/query);
		}

        LOGGER.info("Acuracy: " + (totalCorrect*1.0/totalQueries));

		precision = precision / totalDocs;
		recall = recall / totalDocs;
		f1 = 2 * precision * recall / (precision + recall);

        LOGGER.info("MA#Precision: " + precision);
        LOGGER.info("MA#Recall: " + recall);
        LOGGER.info("MA#F1: " + f1);
		
		precision = totalCorrect * 1.0 / totalFound;
		recall = totalCorrect * 1.0 / totalQueries;
		f1 = 2 * precision * recall / (precision + recall);

        LOGGER.info("MI#Precision: " + precision);
        LOGGER.info("MI#Recall: " + recall);
        LOGGER.info("MI#F1: " + f1);
	}
	
	public static void main(String[] args) {
		Evaluation.accuracy(args[0]);
	}
}