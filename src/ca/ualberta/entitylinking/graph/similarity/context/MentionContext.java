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
package ca.ualberta.entitylinking.graph.similarity.context;

import ca.ualberta.entitylinking.common.data.Document;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.data.Sentence;
import ca.ualberta.entitylinking.common.data.Token;
import ca.ualberta.entitylinking.common.indexing.TFIDF3x;
import ca.ualberta.entitylinking.common.indexing.Tokenizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MentionContext {
    public static int CTX_WIN_SIZE = 25;

    public static Map<String, Float> getContext(String ctxText,
                                                Tokenizer tokener, TFIDF3x index) {
        Map<String, Float> ret = new HashMap<String, Float>();

        List<Tokenizer.Token> contexts = tokener.extractContext(ctxText);
        Map<String, Integer> termFreq = new HashMap<String, Integer>();

        for (Tokenizer.Token tok : contexts) {
            String term = tok.text.toLowerCase();

            int freq = 1;
            if (termFreq.containsKey(term))
                freq += termFreq.get(term);
            termFreq.put(term, freq);
        }

        for (String term : termFreq.keySet()) {
            int tf = termFreq.get(term);
            float termScore = index.computeTFIDF(term, tf);
            ret.put(term, termScore);
        }

        return ret;
    }
    /**
     * Simply use the sentence containing the mention as the context text.
     * @param m
     * @return Context text.
     */
    public static String mentionContextText(Mention m) {
        Document doc = m.getChunk().getSentence().getDocument();
        List<Sentence> sentences = doc.getSentences();
        Sentence sentence = m.getChunk().getSentence();

        int sentNum = sentence.getNumber();
        List<Token> tokens = sentence.getTokens();

        int sTok = m.getChunk().getStartToken();
        int eTok = m.getChunk().getEndToken();

        String context = "";
        if (sTok < CTX_WIN_SIZE) {
            //previous sentence
            if (sentNum == 0) // first sentence
                context = "";
            else
                context = sentences.get(sentNum-1).getText();
        }

        // current sentence.
        context = context + " " + sentence.getText();

        if (eTok + CTX_WIN_SIZE > tokens.size()) {
            //next sentence
            if (sentNum < sentences.size()-1) //ignore if this is the last sentence
                context = context + " " + sentences.get(sentNum+1).getText();
        }

        return context;
    }


}
