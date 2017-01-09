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

import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.indexing.TFIDF3x;

import java.util.Map;

public class EntityContext {
    public static Map<String, Float> getEntityContext(Entity e, TFIDF3x index) {
        return index.DocTFIDFVector(e.getName());
    }
}
