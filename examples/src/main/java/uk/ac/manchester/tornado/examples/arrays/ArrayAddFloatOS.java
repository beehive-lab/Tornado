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

package uk.ac.manchester.tornado.examples.arrays;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.math.SimpleMath;

public class ArrayAddFloatOS {

    public static void main(final String[] args) {
        final boolean useTornado = Boolean.parseBoolean(System.getProperty("useTornado", "False"));
        final boolean warmup = Boolean.parseBoolean(System.getProperty("warmup", "False"));
        final int numElements = (args.length == 1) ? Integer.parseInt(args[0]) : 8192;
        final int iterations = Integer.parseInt(System.getProperty("iterations", "1"));
        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", SimpleMath::vectorAdd, a, b, c)
                .streamOut(c);
        //@formatter:on

        if (useTornado && warmup) {
            s0.warmup();
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {

            if (useTornado) {
                s0.execute();
            } else {
                SimpleMath.vectorAdd(a, b, c);
            }
        }
        long stop = System.nanoTime();

        /*
         * Check to make sure result is correct
         */
        int errors = 0;
        for (final float value : c) {
            if (value != 5f) {
                errors++;
            }
        }

        /**
         * Checks if the result throws an error
         */
        if (errors > 0) {
            System.out.printf("Invalid result: %d errors\n", errors);
            return;
        }

        final double elapsed = (stop - start) * 1e-9;
        final long bytesTransfered = ((numElements * 4)) * 3 * iterations;

        System.out.printf("result: %f, %d\n", elapsed, bytesTransfered);

        if (useTornado) {
            s0.dumpEvents();
        }
    }
}
