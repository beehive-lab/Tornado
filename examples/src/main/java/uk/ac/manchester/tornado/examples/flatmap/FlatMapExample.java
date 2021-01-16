/*
 * Copyright (c) 2020, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples.flatmap;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

import java.util.Random;
import java.util.stream.IntStream;

/**
 * Simple example of how to perform a flat-map with TornadoVM. Since we
 * currently don't support dynamic memory allocation, the space for the output
 * has to be allocated before running the method on the target device.
 */
public class FlatMapExample {

    private static final int SIZE = 16;

    /**
     * Simple method to compute the memory allocation of a FlatMap
     * @param input array for the specified input
     * @param output array with the allocated size for the computed output
     * @param size
     */
    private static void computeFlatMap(float[] input, float[] output, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            if (input[i] > 100) {
                for (int j = 0; j < size; j++) {
                    output[i * size + j] = input[i] + j;
                }
            }
        }
    }

    public static void main(String[] args) {

        float[] input = new float[SIZE * SIZE];
        float[] output = new float[SIZE * SIZE];

        Random r = new Random();
        IntStream.range(0, input.length).forEach(i -> {
            input[i] = 50 + r.nextInt(100);
        });

        TaskSchedule ts = new TaskSchedule("s0") //
                .streamIn(input) //
                .task("t0", FlatMapExample::computeFlatMap, input, output, SIZE) //
                .streamOut(output);
        ts.execute();

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                System.out.print(output[i * SIZE + j] + " ");
            }
            System.out.println();
        }
    }
}
