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
package ca.ualberta.entitylinking.common.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Entity  implements Serializable, Cloneable {
	
	private static final long serialVersionUID = 1;
	
	public static final String NONE = "NONE";
	public static final String PERSON = "PER";
	public static final String ORGANIZATION = "ORG";
	public static final String LOCATION = "LOC";
	public static final String DATE = "DATE";
	public static final String MONEY = "MNY";
	public static final String PERCENT = "PRC";
	public static final String GPE = "GPE";
	public static final String MISC = "MISC";
	public static final String TIME = "TIME";
	public static final String NUMBER = "NUM";
	
	
	public static Set<String> defaultAllowedEntityTypes = new HashSet<String>();
	static {
		defaultAllowedEntityTypes.add(Entity.PERSON);
		defaultAllowedEntityTypes.add(Entity.ORGANIZATION);
		defaultAllowedEntityTypes.add(Entity.LOCATION);
		defaultAllowedEntityTypes.add(Entity.MISC);
	}
	
	public static Set<String> allEntityTypes = new HashSet<String>(11);
	static {
		allEntityTypes.add(Entity.DATE);
		allEntityTypes.add(Entity.GPE);
		allEntityTypes.add(Entity.LOCATION);
		allEntityTypes.add(Entity.MISC);
		allEntityTypes.add(Entity.MONEY);
		allEntityTypes.add(Entity.NONE);
		allEntityTypes.add(Entity.NUMBER);
		allEntityTypes.add(Entity.ORGANIZATION);
		allEntityTypes.add(Entity.PERCENT);
		allEntityTypes.add(Entity.PERSON);
		allEntityTypes.add(Entity.TIME);
	}
	
	/** The id of this entity */
	protected String id;
	
	/** The name of this entity */
	protected String name;
	
	protected List<String> alternativeNames;
	
	/** The type of this entity */
	protected String type;
	
	public Entity(){
		
	}
	
	/** Creates a new entity with type NONE.
	 * @param id A unique identification for this entity.
	 * @param name The name of this entity. 
	 */
	public Entity(String id, String name){
		this(id, name, NONE);
	}
	
	/** Complete constructor. Creates a new entity with id, name and type.
	 * @param id A unique identification for this entity.
	 * @param name The name of this entity.
	 * @param type THe type of this entity. 
	 */
	public Entity(String id, String name, String type){
		this.id = id;
		this.name = name;
		this.type = type;
		this.alternativeNames = new ArrayList<String>();
	}
	
	@Override
	public Entity clone() throws CloneNotSupportedException{
		Entity newEntity = (Entity)super.clone();
		return newEntity;
	}
	
	@Override
	public String toString(){
		return this.getId();
	}
	
	public String detailedToString(){
		return this.getId() + "\t" + this.getName() + printAlternatives() + "\t" + this.getType();
	}
	
	private String printAlternatives() {
		StringBuilder result = new StringBuilder();
		for (String a : this.alternativeNames) {
			result.append("||" + a);
		}
		return result.toString();
	}

	/**
	 * Generate an id based on the name and type the entity.
	 * This function does not set the entity id, just returns one.
	 * @return the default id for an entity.
	 */
	public String generateId(){
		String normName = name.toUpperCase();
		normName = normName.replaceAll("[^\\w ]", "");
		normName = normName.trim();
		
		return normName + "#" + type;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		if(type==null){
			return Entity.NONE;
		}else{
			return type;
		}
		
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public List<String> getAlternativeNames() {
		return alternativeNames;
	}

	public void setAlternativeNames(List<String> names) {
		this.alternativeNames = names;
	}
	
	@Override
	public int hashCode() {
		int result = this.name.hashCode();
		if(this.type != null) {
				result = result + this.type.hashCode();
		}
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		Entity that = (Entity)obj;
		return this.name.equals(that.name) && ((this.type == null && that.type == null) || this.type.equals(that.type));
	}
	
	

}
