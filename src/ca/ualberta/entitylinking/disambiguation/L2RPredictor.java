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
package ca.ualberta.entitylinking.disambiguation;

import java.util.List;
import java.util.ArrayList;

import ciir.umass.edu.learning.RankerFactory;
import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankList;
import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.DenseDataPoint;
import ciir.umass.edu.features.SumNormalizor;
import ciir.umass.edu.features.Normalizer;

import ca.ualberta.entitylinking.utils.Feature;

public class L2RPredictor {
	protected Ranker ranker = null;
	protected Normalizer nml = null;

	public L2RPredictor(String modelFile) {
		RankerFactory rFact = new RankerFactory();
		ranker = rFact.loadRankerFromFile(modelFile);
		nml = new SumNormalizor();
		
		System.out.println("Done with loading the prediction model");
	}

	public int predict(List<Feature> instances) {
		RankList rankList = readSample(instances);
		nml.normalize(rankList);
		
		return predict(rankList);
	}
	
	private int predict(RankList testInstances) {
		double maxScore = Double.NEGATIVE_INFINITY, score;
		int maxInst = -1;
		for (int i = 0; i < testInstances.size(); i++) {
			score = ranker.eval(testInstances.get(i));
			if (maxScore < score) {
				maxScore = score;
				maxInst = i;
			}
		}

		return maxInst;
	}
	
	public static RankList readSample(List<Feature> instances) {
		String content = "";
		
		List<DataPoint> rl = new ArrayList<DataPoint>();
		for (Feature inst : instances) {
			//generate content
			content = new String("0 qid:1" + 
								" 1:" + inst.prior + 
								" 2:" + inst.local + 
								" 3:" + inst.semSim) + 
								" 4:" + inst.nameSim;
			DataPoint qp = new DenseDataPoint(content);
			rl.add(qp);
		}

		if(rl.size() <= 0)
			return null;

		return (new RankList(rl));
	}
}