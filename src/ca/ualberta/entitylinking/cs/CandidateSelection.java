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
package ca.ualberta.entitylinking.cs;

import java.util.Map;
import java.util.List;
import java.util.Set;

import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;

public abstract class CandidateSelection {
	public abstract Map<Entity, Double> selectCandidates(String name);
	
	public Map<String, Map<Entity, Double>> selectCandidatesFuzzy(String name) {return null;}
	
	public abstract Map<Entity, Double> selectCandidatesName(Set<String> names);
	
	public abstract Map<Mention, Map<Entity, Double>> selectCandidatesMention(List<Mention> mentions);
}