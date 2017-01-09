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

public class Pair<S, T> {
	public S value1;
	public T value2;
	
	public Pair(S value1, T value2) {
		this.value1 = value1;
		this.value2 = value2;
	}
	
	public S getValue1() {
		return this.value1;
	}
	
	public T getValue2() {
		return this.value2;
	}
	
	public void setValue1(S value) {
		this.value1 = value;
	}
	
	public void setValue2(T value) {
		this.value2 = value;
	}
}
