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

import ca.ualberta.entitylinking.common.data.Entity;
import ca.ualberta.entitylinking.common.data.Mention;
import ca.ualberta.entitylinking.graph.algorithms.PersonalizedPageRank;
import ca.ualberta.entitylinking.graph.algorithms.WeightedPersonalizedPageRank;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

public class ELUtils {
    public static Map<Integer, List<Double>> computePageRankParallel(
            Set<String> entities,
            Map<String, Integer> e2id,
            PersonalizedPageRank ranker) {

    	Set<Integer> set = new HashSet<Integer>();
        for (String name : entities) {
            if (!e2id.containsKey(name))
                continue;

            set.add(e2id.get(name));
        }
        
        if (set == null || set.isEmpty() || ranker == null)
        	return null;
        
        return ranker.computePageRankParallel(set);
    }

    /**
     * compute the pagerank by restarting from the nodes in entities.
     * @param prefMap A map mapping each entity to its preference weight.
     *
     * @param avoidSet The set of entities that should not have their preference vector bit set.
     * @param e2id
     * @param prefMap The set of entities with preference vector set.
     * @param ranker Algorithm for computing pagerank.
     * @return
     */
    public static List<Double> computePageRank(Map<String, Double> prefMap,
                                               Set<String> avoidSet,
                                               Map<String, Integer> e2id,
                                               PersonalizedPageRank ranker) {


        List<Double> ranks = new ArrayList<Double>();

        double[] s = new double[e2id.size()];
        Arrays.fill(s, 0.0);

        for (String name : prefMap.keySet()) {
            if (!e2id.containsKey(name))
                continue;

            if (avoidSet!= null && avoidSet.contains(name))
                continue;

            int id = e2id.get(name);
            s[id] = prefMap.get(name);
        }

        s = normalize(s);
        if (!PersonalizedPageRank.isStochastic(s))
            Arrays.fill(s, 1.0/s.length);

        ranker.setPreference(s);
        double[] rank = ranker.computeRank();
        if (rank == null || rank.length != e2id.size())
            return ranks;

        for (int i = 0; i < rank.length; i++)
            ranks.add(rank[i]);

        return ranks;
    }

    /**
     * Normalize the weight of entities so the sum of the weight equals to 1.
     * @param simMap
     */
    public static void normalize(Map<Entity, Double> simMap) {
        double sum = 0.0;
        if (simMap == null || simMap.isEmpty())
            return;

        for (Entity e : simMap.keySet())
            sum += simMap.get(e);
        for (Entity e : simMap.keySet())
            simMap.put(e, simMap.get(e)/sum);
    }

    public static double[] normalize(double[] v) {
        double sum = 0;
        for (int i = 0; i < v.length; i++)
            sum += v[i];

        if (Double.compare(sum, 0) == 0)
            return v;

        for (int i = 0; i < v.length; i++)
            v[i] = v[i]/sum;

        return v;
    }

    /**
     * Choose the candidate with the highest probability.
     *
     * @param candidates List of candidates with their probability.
     * @return The target entity.
     *
     */
    public static Entity linkingProb(Map<Entity, Double> candidates) {
        if (candidates == null || candidates.isEmpty())
            return null;

        List<Rank<Double, Entity>> ranks = new ArrayList<Rank<Double, Entity>>();
        for (Entity e : candidates.keySet()) {
            double sim = candidates.get(e);

            Rank<Double, Entity> r = new Rank<Double, Entity>(sim, e);
            ranks.add(r);
        }

        if (ranks == null || ranks.isEmpty())
            return null;

        Collections.sort(ranks);
        Entity e = ranks.get(0).obj;
        return e;

    }

    public static String readFile(String fileName) {
        StringBuffer strBuf = new StringBuffer();

        try {
            int count = 0;
            char[] buf = new char[2048];
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(fileName), "UTF8"));
            while ((count = r.read(buf, 0, 2048)) > 0)
                strBuf.append(buf, 0, count);

            r.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return strBuf.toString();
    }

    public static String currentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return "[" + sdf.format(Calendar.getInstance().getTime()) + "]";
    }

    public static void resolve(List<Mention> mentions) {
        if (mentions == null || mentions.isEmpty())
            return;

        for (int i = 0; i < mentions.size(); i++) {
            Mention m1 = mentions.get(i);
            String name1 = m1.getEntity().getName();
            //Already perform the co-reference resolution.
            if (m1.getName().compareTo(name1) != 0)
                continue;

            for (int j = i; j < mentions.size(); j++) {
                Mention m2 = mentions.get(j);
                if (m2 == null)
                    continue;

                String name2 = m2.getEntity().getName();

                if (name1.contains(name2) && name1.length() > name2.length()) {
                    //fullname: name1, shortname: name2
                    if (m1.getEntity().getType() == Entity.PERSON)
                        m2.getEntity().setName(name1);
                } else if (name2.contains(name1) && name1.length() < name2.length()) {
                    //fullname: name2, shortname: name1
                    if (m2.getEntity().getType() == Entity.PERSON)
                        m1.getEntity().setName(name2);
                    break;
                }
            }
        }
    }
}
