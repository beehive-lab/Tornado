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

package uk.ac.manchester.tornado.examples.compression;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

public class InputScanner {

    /**
     * Method to read a file and store the content into an ArrayList
     * @param fileName path to the input file
     * @return ArrayList storing the content of the file
     * @throws IOException
     */
    public static ArrayList<Integer> getIntNumbers(String fileName) throws IOException {
        ArrayList<Integer> list = new ArrayList<>();
        Scanner scanner = null;
        try {
            scanner = new Scanner(new BufferedReader(new FileReader(fileName)));
            scanner.useLocale(Locale.US);
            scanner.useDelimiter("[,\\n]");

            while (scanner.hasNext()) {
                if (scanner.hasNextFloat()) {
                    float n = scanner.nextFloat();
                    list.add((int) n);
                } else {
                    scanner.next();
                }
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return list;
    }
}
