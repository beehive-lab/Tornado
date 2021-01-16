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

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class InputScanner {

    public static ArrayList<String> getString(String fileName, String delimiter) throws IOException {

        ArrayList<String> list = new ArrayList<>();
        Scanner scanner = null;

        try {
            scanner = new Scanner(new BufferedReader(new FileReader(fileName)));
            scanner.useDelimiter(delimiter);

            while (scanner.hasNext()) {
                list.add(scanner.next());
                scanner.nextLine();
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return list;
    }

    /**
     * Reads strings from a file (divided by a specific delimiter)
     * @param fileName Path to the file to read from
     * @param delimiter Delimiter between two strings
     * @param StrPosA First string
     * @param StrPosB Second string
     * @param UniqueOccur
     * @return HashMap
     * @throws IOException
     */
    public static HashMap<String, ArrayList<String>> readString(String fileName, String delimiter, int StrPosA, int StrPosB, boolean UniqueOccur) throws IOException {

        HashMap<String, ArrayList<String>> map = new HashMap<>();
        Scanner scanner = null;

        try {
            scanner = new Scanner(new BufferedReader(new FileReader(fileName)));

            while (scanner.hasNextLine()) {
                String token = scanner.nextLine();
                String key = token.split(delimiter)[StrPosA];
                String value = token.split(delimiter)[StrPosB];

                if (!map.containsKey(key)) {
                    map.put(key, new ArrayList<String>());
                }

                if (UniqueOccur) { // special case for goCategories data structure
                    if (!(map.get(key).contains(value))) {
                        map.get(key).add(value);
                    }
                } else {
                    map.get(key).add(value);
                }
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return map;
    }

    /**
     * Writes all items of an ArrayList into a file
     * @param fileName Path to the file where the list should be written in
     * @param text ArrayList to write into the file
     */
    public static void writeString(String fileName, ArrayList<String> text) {

        try {
            FileWriter writer = new FileWriter(fileName);
            for (String token : text) {
                writer.write(token);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}