/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.compute;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

/**
 * Original version from Introduction to Computer Science by Princeton
 * University: https://introcs.cs.princeton.edu/java/14array/Euler.java.html
 * 
 * Adapted to TornadoVM
 * 
 */

/*
 * Tests whether there are any five positive integers that satisfy a^5 + b^5 +
 * c^5 + d^5 = e^5. In 1769 Euler conjectured that no such solutions exists, but
 * his conjecture was disproved in 1966 using a computational approach like the
 * one we take here.
 *
 * The program reads in an integer command-line argument n and prints all
 * solutions with a <= b <= c <= d <= e <= n. To speed things up by roughly a
 * factor of 3 on my system, we pre-compute an array of fifth powers.
 *
 *
 */
public class Euler {

    private static final int ITERATIONS = 10;

    private static long[] init(int size) {
        long[] input = new long[size];
        for (int i = 0; i < size; i++) {
            input[i] = (long) i * i * i * i * i;
        }
        return input;
    }

    /**
     * Initial version running with TornadoVM. It can be further optimized by
     * supporting break statements within loops.
     * 
     * @param size
     *            input size
     * @param five
     *            input array
     * @param outputA
     *            output for A
     * @param outputB
     *            output for B
     * @param outputC
     *            output for C
     * @param outputD
     *            output for D
     * @param outputE
     *            output for E
     */
    private static void solve(int size, long[] five, long[] outputA, long[] outputB, long[] outputC, long[] outputD, long[] outputE) {
        for (@Parallel int e = 1; e < five.length; e++) {
            long e5 = five[e];
            for (@Parallel int a = 1; a < five.length; a++) {
                long a5 = five[a];
                for (int b = a; b < size; b++) {
                    long b5 = five[b];
                    for (int c = b; c < size; c++) {
                        long c5 = five[c];
                        for (int d = c; d < size; d++) {
                            long d5 = five[d];
                            if (a5 + b5 + c5 + d5 == e5) {
                                outputA[e] = a;
                                outputB[e] = b;
                                outputC[e] = c;
                                outputD[e] = d;
                                outputE[e] = e;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Method to run
     * @param size
     */
    private static void runSequential(int size) {
        long[] input = init(size);
        long[] outputA = new long[size];
        long[] outputB = new long[size];
        long[] outputC = new long[size];
        long[] outputD = new long[size];
        long[] outputE = new long[size];

        // Sequential
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            solve(size, input, outputA, outputB, outputC, outputD, outputE);
            long end = System.nanoTime();
            System.out.println("SEQ: " + (end - start));
        }

        for (int i = 0; i < outputA.length; i++) {
            if (outputA[i] != 0) {
                long a = outputA[i];
                long b = outputB[i];
                long c = outputC[i];
                long d = outputD[i];
                long e = outputE[i];
                System.out.println(a + "^5 + " + b + "^5 + " + c + "^5 + " + d + "^5 = " + e + "^5");
            }
        }
    }

    private static void runParallel(int size) {
        long[] input = init(size);
        long[] outputA = new long[size];
        long[] outputB = new long[size];
        long[] outputC = new long[size];
        long[] outputD = new long[size];
        long[] outputE = new long[size];

        TaskSchedule ts = new TaskSchedule("s0") //
                .task("s0", Euler::solve, size, input, outputA, outputB, outputC, outputD, outputE) //
                .streamOut(outputA, outputB, outputC, outputD, outputE);

        // Sequential
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            ts.execute();
            long end = System.nanoTime();
            System.out.println("Parallel: " + (end - start));
        }

        for (int i = 0; i < outputA.length; i++) {
            if (outputA[i] != 0) {
                long a = outputA[i];
                long b = outputB[i];
                long c = outputC[i];
                long d = outputD[i];
                long e = outputE[i];
                System.out.println(a + "^5 + " + b + "^5 + " + c + "^5 + " + d + "^5 = " + e + "^5");
            }
        }
    }

    public static void main(String[] args) {
        int size = 100;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }
        runSequential(size);
        runParallel(size);
    }
}
