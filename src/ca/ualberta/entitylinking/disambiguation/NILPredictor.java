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


import java.util.StringTokenizer;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

public class NILPredictor {
	svm_model model = null;
	public NILPredictor(String modelFile) {
        try {
            model = svm.svm_load_model(modelFile);
            if(model == null)
                System.err.println("Cannot open model file " + modelFile);
            else
            	System.err.println("Done with NILModel loading!");
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

	/**
	 * Predict if the given instance is a NIL or not.
	 * @param instance e.g. "1:0.954209 2:0.035177 3:0.431931 4:0.250000"
	 * @return True if the instance is linked to NIL, otherwise False.
	 */
	public boolean predict(String instance) {
		if (instance == null || instance.isEmpty())
			return true;

		StringTokenizer st = new StringTokenizer(instance, " \t\n\r\f:");

		int m = st.countTokens()/2;
		svm_node[] x = new svm_node[m];
		for (int j = 0; j < m; j++) {
			x[j] = new svm_node();
			x[j].index = Integer.parseInt(st.nextToken());
			x[j].value = Double.valueOf(st.nextToken()).doubleValue();
		}

		double v = svm.svm_predict(model, x);
        if (Double.compare(v, 1.0) == 0)
            return false;

        return true;
	}
}
