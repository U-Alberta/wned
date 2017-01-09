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

public class Rank<T extends Comparable<T>, V> implements Comparable<Rank<T, V>> {
	public T sim;
	public V obj;

	public Rank(T sim, V obj) {
		this.sim = sim;
		this.obj = obj;
	}

	@Override
	public int compareTo(Rank<T, V> target) {
		// TODO Auto-generated method stub
		if (sim.compareTo(target.sim) < 0)
			return 1;
		else if (sim.compareTo(target.sim) == 0)
			return 0;
		else
			return -1;
	}
}
