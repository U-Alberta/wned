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

import ca.ualberta.entitylinking.config.WNEDConfig;

public class Feature implements Comparable<Feature> {
    public double prior;
    public double local;
    public double semSim;
    public double nameSim;

    public Feature(double prior, double local, double semSim) {
        this.prior = prior;
        this.local = local;
        this.semSim = semSim;
        this.nameSim = 0.0;
    }

    public Feature(double prior, double local, double semSim, double nameSim) {
        this.prior = prior;
        this.local = local;
        this.semSim = semSim;
        this.nameSim = nameSim;
    }

    @Override
    public int compareTo(Feature target) {
        	switch (WNEDConfig.rankScheme) {
        		case WNEDConfig.SEMANTIC:
        			return compareToSemantic(target);
        		case WNEDConfig.PRIOR_SEM:
        			return compareToPriorSemantic(target);
        		case WNEDConfig.LOCAL_SEM:
        			return compareToLocalSemantic(target);
        		case WNEDConfig.NOR:
        			return compareToNOR(target);
        		case WNEDConfig.LINEAR:
        			return compareToJoint(target);
        		case WNEDConfig.MULTIPLY:
        			return compareToJoint2(target);
        		default:
            		return compareToSemantic(target);
        	}
    }

    public int compareToVote(Feature target) {
        if (prior == target.prior &&
                local == target.local &&
                semSim == target.semSim)
            return 0;

        int vote = 0;
        if (prior > target.prior)
            vote++;
        if (local > target.local)
            vote++;
        if (semSim > target.semSim)
            vote++;

        if (vote >= 2)
            return 1;

        return -1;
    }

    public int compareToPrior(Feature target) {
        if (prior == target.prior)
            return 0;

        if (prior < target.prior)
            return -1;

        return 1;
    }

    public int compareToLocal(Feature target) {
        if (local == target.local)
            return 0;

        if (local < target.local)
            return -1;

        return 1;
    }

    public int compareToSemantic(Feature target) {
        if (semSim == target.semSim)
            return 0;

        if (semSim < target.semSim)
            return -1;

        return 1;
    }

    public int compareToPriorSemantic(Feature target) {
        if (prior * semSim == target.prior * target.semSim)
            return 0;

        if (prior * semSim < target.prior * target.semSim)
            return -1;

        return 1;
    }

    public int compareToLocalSemantic(Feature target) {
        if (local * semSim == target.local * target.semSim)
            return 0;

        if (local * semSim < target.local * target.semSim)
            return -1;

        return 1;
    }

    public int compareToNOR(Feature target) {
        double nor1 = 1 - (1 - prior) * (1 - local) * (1 - semSim);
        double nor2 = 1 - (1 - target.prior) * (1 - target.local) * (1 - target.semSim);

        if (nor1 == nor2)
            return 0;
        if (nor1 < nor2)
            return -1;

        return 1;
    }

    public int compareToJoint(Feature target) {
        double alpha = WNEDConfig.priorWeight, beta = WNEDConfig.localWeight, gama = 1-alpha-beta;
        double joint1 = alpha * prior + beta * local + gama * semSim;
        double joint2 = alpha * target.prior + beta * target.local + gama * target.semSim;

        if (joint1 == joint2)
            return 0;
        if (joint1 < joint2)
            return -1;
        return 1;
    }

    public int compareToJoint2(Feature target) {
        double alpha = WNEDConfig.priorWeight, beta = 1 - alpha;
        double joint1 = alpha * prior * semSim + beta * local * semSim;
        double joint2 = alpha * target.prior * target.semSim + beta * target.local * target.semSim;

        if (joint1 == joint2)
            return 0;
        if (joint1 < joint2)
            return -1;
        return 1;
    }
}

