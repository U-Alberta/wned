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
package ca.ualberta.entitylinking.config;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.util.Properties;

public class WNEDConfig {
	public enum PrefStrategy {
		RANDOM,
		UNIFORM,
		TFIDF,
		PRIOR_PROB,
		CTX_SIM
	};
    public static int DOC_CTX = 1;  //use the document text as context.
    public static int SEN_CTX = 2;  //use the surrounding sentence as context.
    
    //Strategies to combine the features when ranking candidates.
    public final static int LINEAR = 1;
    public final static int MULTIPLY = 2;
    public final static int VOTE = 3;
    public final static int PRIOR_SEM = 4;
    public final static int LOCAL_SEM = 5;
    public final static int NOR = 6;
    public final static int SEMANTIC = 7;
    
	public static String wikiConfigFile = 
			"wikipedia-miner-1.2.0/configs/wikipedia-20130604.xml";
    public static String systemDataPath = "";
    public static String linkGraphLoc = "";
    public static String cooccurrenceGraphLoc = "";
    public static String a2eIndexDir = "lucene-a2e";
    public static String tfidfIndexDir = "tfidfIndex";
    public static String gateHome = "gate8.1";
    public static String gateConfigPath="gate8.1/gate.xml";
    public static String DATASET_DIR = null;

	public static boolean loaded = false;

	//preference strategy for mention: random, uniform, tfidf
	public static PrefStrategy mPrefStreg = PrefStrategy.UNIFORM;
	//preference strategy for entity: random ,uniform, prior, context_sim
	public static PrefStrategy ePrefStreg = PrefStrategy.UNIFORM;
    //weight on the prior probability when computing the mention-entity similarity.
    public static double priorWeight = 0.2;
    //weigh on the local context similarity.
    public static double localWeight = 0.2;
    //rank scheme for candidate ranking.
    public static int rankScheme = LINEAR;
    //perform the disambiguation supervised or unsupervised.
    public static boolean supervised = false;
    //prediction model file.
    public static String modelFile = null;
    //if we use the entities of unambiguous mentions to represent a document.
    public static boolean useUnambigEntity = false;
    //if we use an iterative process.
    public static boolean useIterative = false;
    //the target file to be linked.
    public static String targetFile = null;
    //if we use directed entity graph or undirected one.
    public static boolean directedGraph = false;
    //if we are using weighted or unweighted pagerank.
    public static boolean weighted = true;    
    //define the levels of expansion when building the entity graph.
    public static int expandLevel = 1;
    //which context (e.g. whole document or surrounding sentences) are we using?
    public static int contextOption = DOC_CTX;
    //if we do NIL prediction or not
    public static boolean NILPrediction = false;
    //NIL prediction model file
    public static String nilModel = null;

    public static void disable3rdPartyLibLogging() {
		Logger.getLogger("es.yrbcn.graph.weighted.WeightedPageRankPowerMethod").setLevel(Level.OFF);
		Logger.getLogger("it.unimi.dsi.law.rank.PageRankParallelGaussSeidel").setLevel(Level.OFF);
		Logger.getLogger("gate.creole.orthomatcher.OrthoMatcher").setLevel(Level.OFF);
		Logger.getLogger("it.unimi.dsi.law.rank.PageRankParallelGaussSeidel").setLevel(Level.OFF);
    }
    
    public static void loadConfiguration(String configFile) {
        if (loaded)
            return;

        //Do some pre-configuration.
        disable3rdPartyLibLogging();
        
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(configFile));
        } catch (Exception e) {
            e.printStackTrace();
        }

        systemDataPath = prop.getProperty("systemDataPath", "entitylinking/data");
        linkGraphLoc = systemDataPath + "/graph/" + prop.getProperty("linkGraph", "pageLinkGraph");
        cooccurrenceGraphLoc = systemDataPath + "/graph/" + prop.getProperty("cooccurGraph", "co-occurGraph");

        a2eIndexDir = systemDataPath + "/" + prop.getProperty("lucene-a2e", "a2eIndex");
        tfidfIndexDir = systemDataPath + "/" + prop.getProperty("tfidfIndex", "tfidfIndex");
        gateHome = systemDataPath + "/" + prop.getProperty("gateHome", "gate8.1");
        gateConfigPath = systemDataPath + "/" + prop.getProperty("gateConfigPath", gateConfigPath);

        String value = null;
        value = prop.getProperty("useUnambigEntity", "1");
        if (value.equals("1"))
            useUnambigEntity = true;
        else
            useUnambigEntity = false;

        value = prop.getProperty("useIterative", "1");
        if (value.equals("1"))
            useIterative = true;
        else
            useIterative = false;

        value = prop.getProperty("weighted", "0");
        if (value.equals("1"))
        	weighted = true;
        else
        	weighted = false;

        value = prop.getProperty("mentionPrefStrategy", "UNIFORM");
        if (value.equals("UNIFORM")) {
            WNEDConfig.mPrefStreg = WNEDConfig.PrefStrategy.UNIFORM;
        } else if (value.equals("RANDOM")) {
            WNEDConfig.mPrefStreg = WNEDConfig.PrefStrategy.RANDOM;
            WNEDConfig.ePrefStreg = WNEDConfig.PrefStrategy.RANDOM;
        } else if (value.equals("TFIDF")) {
            WNEDConfig.mPrefStreg = WNEDConfig.PrefStrategy.TFIDF;
        }

        value = prop.getProperty("entityPrefStrategy", "UNIFORM");
        if (value.equals("UNIFORM")) {
            WNEDConfig.ePrefStreg = WNEDConfig.PrefStrategy.UNIFORM;
        } else if (value.equals("RANDOM")) {
            WNEDConfig.mPrefStreg = WNEDConfig.PrefStrategy.RANDOM;
            WNEDConfig.ePrefStreg = WNEDConfig.PrefStrategy.RANDOM;
        } else if (value.equals("PRIOR_PROB")) {
            WNEDConfig.ePrefStreg = WNEDConfig.PrefStrategy.PRIOR_PROB;
        } else if (value.equals("CTX_SIM")) {
            WNEDConfig.ePrefStreg = WNEDConfig.PrefStrategy.CTX_SIM;
        }

        value = prop.getProperty("contextOption", "document");
        if (value.equals("document"))
        	contextOption = DOC_CTX;
        else if (value.equals("sentence"))
        	contextOption = SEN_CTX;
        
        value = prop.getProperty("priorWeight", "0.2");
        priorWeight = Double.parseDouble(value);
        value = prop.getProperty("localWeight", "0.1");
        localWeight = Double.parseDouble(value);
        
        value = prop.getProperty("rankScheme", "LINEAR");
        if (value.equals("LINEAR"))
            rankScheme = LINEAR;
        else if (value.equals("MULTIPLE"))
        	rankScheme = MULTIPLY;        
        else if (value.equals("VOTE"))
        	rankScheme = VOTE;
        else if (value.equals("PRIOR_SEM"))
        	rankScheme = PRIOR_SEM;
        else if (value.equals("LOCAL_SEM"))
        	rankScheme = LOCAL_SEM;
        else if (value.equals("NOR"))
        	rankScheme = NOR;
        else if (value.equals("SEMANTIC"))
        	rankScheme = SEMANTIC;
        else
            rankScheme = LINEAR;

        value = prop.getProperty("supervised", "0");
        if (value.equals("1"))
        	supervised = true;
        else
        	supervised = false;
        
        value = prop.getProperty("NILPrediction", "0");
        if (value.equals("1"))
        	NILPrediction = true;
        else
        	NILPrediction = false;

        modelFile = systemDataPath + "/" + prop.getProperty("modelFile", "");
        nilModel = systemDataPath + "/" + prop.getProperty("nilModel", "");
        
        targetFile = prop.getProperty("targetFile", "");

        //get the RawText directory.
        if (targetFile != null && !targetFile.isEmpty() && targetFile.contains(("/"))) {
            String dir = targetFile.substring(0, targetFile.lastIndexOf('/'));
            WNEDConfig.DATASET_DIR = dir + "/RawText/";
        }

        loaded = true;
    }
}
