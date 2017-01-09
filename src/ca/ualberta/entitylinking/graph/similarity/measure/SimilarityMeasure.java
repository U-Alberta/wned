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
package ca.ualberta.entitylinking.graph.similarity.measure;

import java.util.Map;

import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.graph.similarity.context.EntityContextCache;
import ca.ualberta.entitylinking.graph.similarity.context.MentionContextCache;
import ca.ualberta.entitylinking.utils.similarity.VectorSimilarity;

public class SimilarityMeasure {
    public static double mentionEntitySimilarity(Mention m, Entity e,
                                          MentionContextCache mentionCache,
                                          EntityContextCache entityCache) {
        //get mention context.
        Map<String, Float> mentionCtx = mentionCache.getContext(m);

        //get entity context.
        Map<String, Float> entityCtx = entityCache.getContext(e);

        return VectorSimilarity.vectorSim(mentionCtx, entityCtx);
    }
}
