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
package ca.ualberta.entitylinking.utils.similarity;

import java.util.List;
import java.util.Map;

public class VectorSimilarity {
	public static float vectorSim(Map<String, Float> v1, Map<String, Float> v2){
		double sim = 0.0;
		double sum1 = 0.0, sum2 = 0.0;
		
		if (v1 == null || v2 == null)
			return 0.0f;
		
		for (String str : v1.keySet()) {
			if (v2.containsKey(str))
				sim += v1.get(str) * v2.get(str);
			
			sum1 += Math.pow(v1.get(str), 2);
		}
		
		
		for (String str : v2.keySet())
			sum2 += Math.pow(v2.get(str), 2);
		
		sim = sim / (Math.sqrt(sum1) * Math.sqrt(sum2));
		
		return (float)sim;
	}

	/**
	 * Assume that v1 and v2 are the representation of the same feature set
	 * v1.length = v2.length. both v1[i] and v2[i] refer to the same feature. 
	 * 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static double vectorSim(double[] v1, double[] v2) {
		if (v1.length != v2.length)
			return 0.0;
		
		double prod = 0.0, norm1 = 0.0, norm2 = 0.0;
		for (int i = 0; i < v1.length; i++) {
			prod += v1[i]*v2[i];
			norm1 += v1[i]*v1[i];
			norm2 += v2[i]*v2[i];
		}
		
		if (norm1 == 0 || norm2 == 0)
			return 0;

		return prod / (Math.sqrt(norm1)* Math.sqrt(norm2));
	}
	
	public static double vectorSim(List<Double> v1, List<Double> v2) {
		if (v1.size() != v2.size())
			return 0.0;
		
		double prod = 0.0, norm1 = 0.0, norm2 = 0.0;
		for (int i = 0; i < v1.size(); i++) {
			prod += v1.get(i)*v2.get(i);
			norm1 += v1.get(i)*v1.get(i);
			norm2 += v2.get(i)*v2.get(i);
		}
		
		if (norm1 == 0 || norm2 == 0)
			return 0;

		return prod / (Math.sqrt(norm1)* Math.sqrt(norm2));
	}

	public static double ZeroKLDivergence(List<Double> v1, List<Double> v2) {
		if (v1 == null || v2 == null || v1.size() != v2.size())
			return 0.0;
		
		double gamma = 10;
		double div = 0.0;
		for (int i = 0; i < v1.size(); i++) {
			if (v1.get(i) == 0.0)
				continue;
			
			if (v2.get(i) <= 0.000001)
				div += v1.get(i) * gamma;
			else
				div += v1.get(i) * Math.log(v1.get(i)/v2.get(i)) / Math.log(2.0);
		}
		
		return div;
	}
}
