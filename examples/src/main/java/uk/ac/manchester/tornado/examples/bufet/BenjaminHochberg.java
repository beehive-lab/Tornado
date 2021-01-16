/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.examples.bufet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class BenjaminHochberg {

    private ArrayList<Double> pValueList = new ArrayList<>();
    private ArrayList<Double> indexList = new ArrayList<>();
    private ArrayList<String> fdrList = new ArrayList<>();

    /**
     * Conceptualizes the rate of type-1 errors in null hyptohesis testing
     * @param checkGO
     * @param nocheckGO
     * @param pvalues
     * @param miRNA_groups
     * @see <a href="https://en.wikipedia.org/wiki/False_discovery_rate">False discovery rate (Benjamini-Hochberg)</a>
     */
    public void benjaminHochberg(HashMap<String, ArrayList<String>> checkGO, HashMap<String, ArrayList<String>> nocheckGO, long[] pvalues, int miRNA_groups) {

        int totalGo = checkGO.size();
        int totalNoGo = nocheckGO.size();
        int maxI = 0;
        double star = 0.05,doubleStar = 0.01;

        for (int i = 0; i < totalGo; i++) {
            pValueList.add((double) pvalues[i] / (double) miRNA_groups);
            indexList.add((double) pvalues[i] / (double) miRNA_groups);
            fdrList.add("");
        }

        for (int i = 0; i < totalNoGo; i++) {
            pValueList.add(1.0);
            indexList.add(1.0);
            fdrList.add("");
        }

        Collections.sort(pValueList);
        int pNodeListSize = pValueList.size();

        for (int i = 0; i < pNodeListSize; i++) {
            double starcheck = (double) (((i + 1) * star)) / (double) pNodeListSize;
            if (pValueList.get(i) <= starcheck) {
                maxI = i;
            }
        }

        if (maxI > pNodeListSize) {
            maxI = pNodeListSize;
        }

        for (int i = 0; i < maxI; i++) {
            fdrList.set(i, "*");
        }

        for (int i = 0; i < pNodeListSize; i++) {
            double starcheck = (double) ((i + 1) * doubleStar) / (double) pNodeListSize;
            if (pValueList.get(i) <= starcheck) {
                maxI = i;
            }
        }

        if (maxI > pNodeListSize) {
            maxI = pNodeListSize;
        }

        for (int i = 0; i < maxI; i++) {
            fdrList.set(i, "**");
        }

        pValueList = indexList;
    }

    public ArrayList<Double> returnPvalueList() {

        return pValueList;
    }

    public ArrayList<String> returnFdrList() {

        return fdrList;
    }

}