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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class Huffman {

    private static HashMap<Integer, String> dictionary = new HashMap<>();
    private static boolean CHECK_RESULT = true;
    private static boolean CHECK_DECODE_ONLY = false;

    /**
     * Converts a given String to a binary
     * @param binary input String
     * @return computed Binary
     */
    private static BitSet convertStringToBinary(String binary) {
        BitSet bitset = new BitSet(binary.length());
        for (int i = 0; i < binary.length(); i++) {
            if (binary.charAt(i) == '1') {
                bitset.set(i);
            } else {
                bitset.clear(i);
            }
        }
        return bitset;
    }

    public static void printAndAssignCode(HuffmanNode node, String code) {
        if (node.left == null && node.right == null && node.frequency != -1) {
            dictionary.put(node.realData, code);
            return;
        }
        printAndAssignCode(node.left, code + "0");
        printAndAssignCode(node.right, code + "1");
    }

    public static HashMap<Integer, Integer> getFrequencies(ArrayList<Integer> inputData) {
        HashMap<Integer, Integer> frequencies = new HashMap<>();
        for (int i = 0; i < inputData.size(); i++) {
            if (!frequencies.containsKey(inputData.get(i))) {
                frequencies.put(inputData.get(i), 1);
            } else {
                frequencies.put(inputData.get(i), frequencies.get(inputData.get(i)) + 1);
            }
        }
        return frequencies;
    }

    /**
     * Method to decode a given list of Integers with the Huffman-Coding
     * @param inputData List of Integers to decode
     * @throws IOException is thrown if the input could not be read
     * @see <a href="https://en.wikipedia.org/wiki/Huffman_coding">Huffman Coding</a>
     */
    public static void huffmanDecoding(ArrayList<Integer> inputData) throws IOException {
        System.out.println("5. Reading file for decompressing");
        FileInputStream iStream = new FileInputStream("/tmp/huffman.txt");
        @SuppressWarnings("resource") ObjectInputStream inObject = new ObjectInputStream(iStream);

        // 1. Read huffman tree
        int[] frequencies = (int[]) inObject.readObject();
        int[] data = (int[]) inObject.readObject();
        int[] left = (int[]) inObject.readObject();
        int[] right = (int[]) inObject.readObject();

        // 2. Read byte[] compressed data
        byte[] compressedData = null;
        BitSet bitSetCompressed = null;

        compressedData = (byte[]) inObject.readObject();
        bitSetCompressed = BitSet.valueOf(compressedData);

        byte[] bits = new byte[bitSetCompressed.length()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = (byte) (bitSetCompressed.get(i) ? 1 : 0);
        }

        // 3. decode
        System.out.println("6. Decode");
        long s0 = System.nanoTime();
        int size = 5000000;
        if (inputData != null) {
            size = inputData.size();
        }
        int[] result = new int[size];

        decodeTornado(bits, frequencies, data, left, right, result, inputData);
        long s1 = System.nanoTime();

        System.out.println("Decoding time: " + (s1 - s0) + " (ns)");
    }

    /**
     *
     * @param input
     * @param frequencies
     * @param data
     * @param left
     * @param right
     * @param message
     * @param inputData
     */
    private static void decodeTornado(byte[] input, int[] frequencies, int[] data, int[] left, int[] right, int[] message, ArrayList<Integer> inputData) {

        // @formatter:off
        new TaskSchedule("s0") 
              .task("t0", Huffman::decodeTornadoKernel, input, frequencies, data, left, right, message)
              .streamOut(message)
              .execute();
        // @formatter:on

        if (CHECK_RESULT && inputData != null) {
            for (int i = 0; i < inputData.size(); i++) {
                if (message[i] != inputData.get(i)) {
                    System.out.println("Result is not correct: " + inputData.get(i) + " vs " + message[i] + " INDEX: " + i);
                    break;
                }
            }
        }
    }

    /**
     *
     * @param input
     * @param frequencies
     * @param data
     * @param left
     * @param right
     * @param output
     */
    private static void decodeTornadoKernel(byte[] input, int[] frequencies, int[] data, int[] left, int[] right, int[] output) {
        final int rootNode = 0;
        int iteratorNode = 0;
        int outIndex = 0;
        for (int idx = 0; idx < input.length; idx++) {
            byte bitInput = input[idx];

            if (left[iteratorNode] == -1) {
                int realData = data[iteratorNode];
                output[outIndex] = realData;
                iteratorNode = rootNode;
                outIndex++;
                idx--;
                continue;
            } else if (bitInput == 0) {
                iteratorNode = left[iteratorNode];
            } else {
                iteratorNode = right[iteratorNode];
            }
        }
    }

    /**
     * Method to read a given input file
     * @return ArrayList<Integer>: content of the input file
     * @throws IOException is thrown if the given input can not be read
     */
    public static ArrayList<Integer> readInputFile() throws IOException {
        long ss0 = System.nanoTime();
        ArrayList<Integer> inputData = InputScanner.getIntNumbers("/tmp/framesNonCompress.txt");
        long ss1 = System.nanoTime();
        System.out.println("Reading time: " + (ss1 - ss0) + " (ns)");
        return inputData;
    }

    /**
     *
     * @param dataKeyToCompress
     * @param frequencyArray
     * @return
     */
    public static HuffmanNode huffmanEncoding(int[] dataKeyToCompress, int[] frequencyArray) {
        int n = dataKeyToCompress.length;
        PriorityQueue<HuffmanNode> queue = new PriorityQueue<HuffmanNode>(n, new HuffmanTreeComparator());
        for (int i = 0; i < n; i++) {
            HuffmanNode hn = new HuffmanNode();
            hn.realData = dataKeyToCompress[i];
            hn.frequency = frequencyArray[i];
            hn.left = null;
            hn.right = null;
            queue.add(hn);
        }

        HuffmanNode root = null;

        while (queue.size() > 1) {
            HuffmanNode x = queue.peek();
            queue.poll();

            HuffmanNode y = queue.peek();
            queue.poll();

            HuffmanNode f = new HuffmanNode();
            f.frequency = x.frequency + y.frequency;
            f.realData = 0;

            f.left = x;
            f.right = y;

            root = f;
            queue.add(f);
        }
        printAndAssignCode(root, "");
        return root;
    }

    /**
     * Writes the compressed Huffman into a file
     * @param root Node
     * @param inputData ArrayList<Integer> storing the input
     * @throws IOException
     */
    public static void writeCompressedHuffmanIntoFile(HuffmanNode root, ArrayList<Integer> inputData) throws IOException {
        int numNodes = getNumNodes(root);
        System.out.println("NUM NODES: " + numNodes);

        int[] frequencies = new int[numNodes];
        int[] data = new int[numNodes];
        int[] left = new int[numNodes];
        int[] right = new int[numNodes];

        fillArrays(frequencies, data, left, right, root);

        FileOutputStream stream = new FileOutputStream("/tmp/huffman.txt");
        ObjectOutputStream out = new ObjectOutputStream(stream);

        // 4.1 Write huffman tree
        out.writeObject(frequencies);
        out.writeObject(data);
        out.writeObject(left);
        out.writeObject(right);

        StringBuffer compressData = new StringBuffer();
        System.out.println("SIZE: " + inputData.size());
        // 4.2 Write compress data in byte[]
        for (int i = 0; i < inputData.size(); i++) {
            compressData.append(dictionary.get(inputData.get(i)));
        }

        BitSet compressedBits = convertStringToBinary(compressData.toString());
        try {
            out.writeObject(compressedBits.toByteArray());
        } catch (Exception e) {

        }
        stream.close();
        out.close();
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        ArrayList<Integer> inputData = null;
        if (!CHECK_DECODE_ONLY) {
            System.out.println("1. Reading");
            inputData = readInputFile();

            System.out.println("2. Computing frequencies");
            HashMap<Integer, Integer> frequencyHash = getFrequencies(inputData);
            int[] dataKeyToCompress = frequencyHash.keySet().stream().mapToInt(i -> i).toArray();
            int[] frequencyArray = frequencyHash.values().stream().mapToInt(i -> i).toArray();

            System.out.println("3. Compressing");
            HuffmanNode root = huffmanEncoding(dataKeyToCompress, frequencyArray);

            System.out.println("4. Writing");
            writeCompressedHuffmanIntoFile(root, inputData);
        }
        huffmanDecoding(inputData);
    }

    private static void fillArrays(int[] frequencies, int[] data, int[] left, int[] right, HuffmanNode root) {
        if (root == null) {
            return;
        }

        HashSet<HuffmanNode> visited = new HashSet<>();
        HashMap<Integer, HuffmanNode> nodeIndexes = new HashMap<>();
        HashMap<HuffmanNode, Integer> nodeIndexes2 = new HashMap<>();

        Deque<HuffmanNode> f = new LinkedList<>();
        f.push(root);
        visited.add(root);
        int nodeIndex = 0;

        frequencies[0] = root.frequency;
        data[0] = root.realData;
        left[0] = -1;
        right[0] = -1;

        while (!f.isEmpty()) {
            HuffmanNode aux = f.poll();
            nodeIndexes.put(nodeIndex, aux);
            nodeIndexes2.put(aux, nodeIndex++);
            visited.add(aux);

            if (aux.left != null && !visited.contains(aux.left)) {
                f.addLast(aux.left);
            }
            if (aux.right != null && !visited.contains(aux.right)) {
                f.addLast(aux.right);
            }
        }

        for (int i = 0; i < visited.size(); i++) {
            HuffmanNode n = nodeIndexes.get(i);
            frequencies[i] = n.frequency;
            data[i] = n.realData;
            left[i] = (n.left != null) ? nodeIndexes2.get(n.left) : -1;
            right[i] = (n.right != null) ? nodeIndexes2.get(n.right) : -1;
        }
    }

    private static int getNumNodes(HuffmanNode root) {
        if (root == null) {
            return 0;
        }

        HashSet<HuffmanNode> visited = new HashSet<>();
        Deque<HuffmanNode> queue = new LinkedList<>();
        queue.push(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            HuffmanNode aux = queue.poll();
            visited.add(aux);

            if (aux.left != null && !visited.contains(aux.left)) {
                queue.addLast(aux.left);
            }
            if (aux.right != null && !visited.contains(aux.right)) {
                queue.addLast(aux.right);
            }
        }
        return visited.size();
    }
}
