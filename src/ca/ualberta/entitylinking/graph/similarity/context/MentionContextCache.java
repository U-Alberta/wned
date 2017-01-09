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

import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.indexing.TFIDF3x;
import ca.ualberta.entitylinking.common.indexing.Tokenizer;
import ca.ualberta.entitylinking.config.WNEDConfig;

import java.util.HashMap;
import java.util.Map;

public class MentionContextCache {
    private int ctx_option = WNEDConfig.DOC_CTX;

    private Tokenizer tokener = null;
    private TFIDF3x index = null;

    private Map<String, Float> docCtx = null;
    private Map<Mention, Map<String, Float>> mentionCtxMap = new HashMap<Mention, Map<String, Float>>();

    public MentionContextCache() {
    }

    public MentionContextCache(int option, Tokenizer tokener, TFIDF3x index) {
        this.ctx_option = option;
        this.tokener = tokener;
        this.index = index;
    }

    public Map<String, Float> getContext(Mention m) {
        Map<String, Float> context = null;
        if (ctx_option == WNEDConfig.DOC_CTX) {
            if (docCtx == null) {
                String content = m.getSentence().getDocument().getOriginalText();
                docCtx = MentionContext.getContext(content, tokener, index);
            }

            context = docCtx;
        } else {
            if (mentionCtxMap.containsKey(m)) {
                context = mentionCtxMap.get(m);
            } else {
                String content = MentionContext.mentionContextText(m);
                context = MentionContext.getContext(content, tokener, index);
                mentionCtxMap.put(m, context);
            }
        }

        return context;
    }
    
    public void clear() {
    	docCtx = null;
    	mentionCtxMap.clear();
    }
}
