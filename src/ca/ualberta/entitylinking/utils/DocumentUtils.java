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
package ca.ualberta.entitylinking.utils;

import ca.ualberta.entitylinking.common.data.*;
import ca.ualberta.entitylinking.common.nlp.OrthoMatcherCoref;
import ca.ualberta.entitylinking.common.nlp.StanfordNER;
import ca.ualberta.entitylinking.common.indexing.TFIDF3x;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentUtils {

    public static List<Mention> getMentions(Document doc) {
        List<Mention> mentions = new ArrayList<Mention>();
        for (Sentence sentence : doc.getSentences()) {
            for (Mention mention : sentence.getMentions()) {
                mentions.add(mention);
            }
        }

        return mentions;
    }

    public static Document annotateDocument(String content,
                                            StanfordNER myNer,
                                            OrthoMatcherCoref myOrthoMatcher) {

        //1. Create a Document.
        Document doc = new Document();
        doc.setOriginalText(content);

        //2. Text annotation.
        List<Sentence> sentences = myNer.annotateText(content);
        for (Sentence sentence : sentences)
            doc.addSentence(sentence);

        //3. Co-reference resolution.
        if (myOrthoMatcher != null)
            myOrthoMatcher.findCoreferences(doc);

        return doc;
    }

    /**
     * Collect the index information to improve the mention creation process.
     *
     * @param doc
     */
    public static Map<Integer, Mention> getIndex(Document doc) {
        Map<Integer, Mention> tokMenMap = new HashMap<Integer, Mention>();

        for (Sentence sentence : doc.getSentences()) {
            List<Token> tokens = sentence.getTokens();
            List<Mention> mentions = sentence.getMentions();

            for (Mention mention : mentions) {
                int sTok = mention.getChunk().getStartToken();
                int eTok = mention.getChunk().getEndToken();
                for (int idx = sTok; idx <= eTok; idx++) {
                    Token token = tokens.get(idx);
                    tokMenMap.put(token.getbPosition(), mention);
                    tokMenMap.put(token.getePosition(), mention);
                }
            }
        }

        return tokMenMap;
    }

    public static Mention createMention(String mentionName,
                                        int offset, Document doc,
                                        Map<Integer, Mention> tokMenMap) {
        int bPos = offset, ePos = offset + mentionName.length() - 1;

        Mention mention = null;
        if (tokMenMap.containsKey(bPos))
            mention = tokMenMap.get(bPos);
        else if (tokMenMap.containsKey(ePos))
            mention = tokMenMap.get(ePos);

        if (mention != null && mentionName.equals(mention.getName()))
            return mention;

        //first locate the sentence containing the mentionName.
        Mention m = null;
        for (Sentence sentence : doc.getSentences()) {
            List<Token> tokens = sentence.getTokens();
            int bIdx = tokens.get(0).getbPosition();
            int eIdx = tokens.get(tokens.size()-1).getePosition();

            //we have two cases:
            //case 1: the mention is in one sentence.
            //case 2: the mention is in two sentences due to an error of the sentence splitter.
            if (bPos >= bIdx && bPos < eIdx) {
                int startToken = 0, endToken = 0;
                for (int i = 0; i < tokens.size(); i++) {
                    Token token = tokens.get(i);
                    if (token.getbPosition() == bPos)
                        startToken = i;

                    if (token.getePosition() == ePos) {
                        endToken = i;
                        break;
                    }
                }

                //case 2: the sentence splitter made an error.
                if (endToken == 0)
                    endToken = tokens.size() - 1;

                m = new Mention(
                        new Entity(mentionName, mentionName),
                        Entity.NONE, sentence, startToken, endToken);

                break;
            }
        }

        //The mentionName is not recognized as a mention by a NER.
        if (mention == null || m == null)
            return m;

        m.getEntity().setType(mention.getEntity().getType());

        return m;
    }


    public static double computeTFIDF(String name, String docContent, TFIDF3x tfidf) {
        int oldIdx = 0, newIdx = 0;

        String newName = name.toLowerCase();
        String content = docContent.toLowerCase();
        oldIdx = content.indexOf(newName);
        if (oldIdx < 0)
            return 0;

        int freq = 1;
        while ((newIdx = content.indexOf(newName, oldIdx+1)) > oldIdx) {
            oldIdx = newIdx;
            freq++;
        }

        return tfidf.computeTFIDF(name, freq);
    }
}
