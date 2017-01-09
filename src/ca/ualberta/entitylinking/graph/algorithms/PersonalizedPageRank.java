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
package ca.ualberta.entitylinking.graph.algorithms;

import java.util.List;
import java.util.Map;
import java.util.Set;

import es.yrbcn.graph.weighted.WeightedPageRank;
import it.unimi.dsi.webgraph.ImmutableGraph;

public abstract class PersonalizedPageRank {
	protected ImmutableGraph g = null;
	protected double threshold = 0.00001;	//stopping threshold
	protected int maxIter = 3; 			//maximum iterations.
	protected double alpha = 0.85;
	protected boolean stronglyPreferential = true;
	
	public abstract void init();
	public abstract void setAlpha(double alpha);
	public abstract void setPreference(double[] p);
	public abstract double[] computeRank();
	public abstract Map<Integer, List<Double>> computePageRankParallel(Set<Integer> entities);
	
	public void setStart(double[] s) {}
	
	public static boolean isStochastic(double[] v ) {
		double normL1 = 0.0, c = 0.0, t, y;

		//Kahan method to minimize the round errors in doubles sum.
		for (int i = 0; i < v.length; i++) {
			y = v[i] - c;
			t = ( normL1 + y );
			c = ( t - normL1 ) - y;
			normL1 = t;
		}
		
		return (Math.abs( normL1 - 1.0 ) <=  WeightedPageRank.STOCHASTIC_TOLERANCE );
	}
	

}
