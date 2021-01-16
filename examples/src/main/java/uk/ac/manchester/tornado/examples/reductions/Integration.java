/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.examples.reductions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;

public class Integration {

    private static final int LOWER = 1;
    private static final int UPPER = 4;

    public static float f(float x) {
        return (float) (1 / ((x + 1) * Math.sqrt(x * Math.exp(x))));
    }

    /**
     *
     * @param size
     * @param a
     * @param b
     * @return
     */
    public float integrationSequential(int size, final float a, final float b) {
        float sum = 0.0f;
        for (int i = 1; i < (size + 1); i++) {
            sum += f(a + ((i - (1.f / 2.f)) * ((b - a) / size)));
        }
        return ((b - a) / size) * sum;
    }

    public float runIntegrationSequential(int size) {

        System.out.println("Running Sequential version");

        final float a = LOWER;
        final float b = UPPER;
        float finalValue = 0.0f;
        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < ConfigurationReduce.MAX_ITERATIONS; i++) {

            long start = System.nanoTime();
            finalValue = integrationSequential(size, a, b);
            long end = System.nanoTime();
            timers.add((end - start));
        }

        System.out.println("IntegrationValue: " + finalValue);
        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));
        return finalValue;
    }

    public static void integrationTornado(float[] input, @Reduce float[] sum, final float a, final float b) {
        final int size = input.length;
        for (@Parallel int i = 0; i < input.length; i++) {
            float value = f(a + (((i + 1) - (1 / 2)) * ((b - a) / size)));
            sum[0] += input[i] + value;
        }
    }

    public float runTornado(final int size) {

        System.out.println("\nRunning Tornado version");

        float[] input = new float[size];
        float[] result = new float[1];
        Arrays.fill(result, 0.0f);

        final float a = LOWER;
        final float b = UPPER;

        float finalValue = 0.0f;
        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
                .streamIn(input)
                .task("t0", Integration::integrationTornado, input, result, a, b)
                .streamOut(result);
        //@formatter:on

        ArrayList<Long> timers = new ArrayList<>();
        for (int i = 0; i < ConfigurationReduce.MAX_ITERATIONS; i++) {

            IntStream.range(0, size).sequential().forEach(idx -> {
                input[idx] = 0;
            });

            long start = System.nanoTime();
            task.execute();
            long end = System.nanoTime();

            finalValue = ((b - a) / size) * result[0];
            // System.out.println("IntegrationValue: " + finalValue);
            timers.add((end - start));
        }

        System.out.println("IntegrationValue: " + finalValue);
        System.out.println("Median TotalTime: " + Stats.computeMedian(timers));
        return finalValue;
    }

    public static void main(String[] args) {

        int size = 33_554_432;
        if (args.length > 0) {
            try {
                size = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                size = 33_554_432;
            }
        }

        // Run Sequential
        new Integration().runIntegrationSequential(size);

        // Run with Tornado
        new Integration().runTornado(size);
    }
}
