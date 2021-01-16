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

public class ArrayAddFloat {

    public static final int ONE_GIGABYTE = 1 * 1024 * 1024 * 1024;
    public static final int ONE_MEGABYTE = 1 * 1024 * 1024;
    public static final int ONE_KILOBYTE = 1 * 1024;

    /**
     * Formats a double value into (giga-/mega-/kilo-)bytes per second
     * @param bytes Double value to format
     * @return
     */
    public static final String formatBytesPerSecond(final double bytes) {
        String out = "";

        if (bytes >= ONE_GIGABYTE) {
            out = String.format("%.2f GB/s", (bytes / ONE_GIGABYTE));
        } else if (bytes >= ONE_MEGABYTE) {
            out = String.format("%.2f MB/s", (bytes / ONE_MEGABYTE));
        } else if (bytes >= ONE_KILOBYTE) {
            out = String.format("%.2f KB/s", (bytes / ONE_KILOBYTE));
        } else {
            out = String.format("%f B/s", bytes);
        }
        return out;
    }

    public static void main(final String[] args) {

        final int numElements = (args.length == 1) ? Integer.parseInt(args[0]) : 8192;
        final int iterations = 1;
        final float[] a = new float[numElements];
        final float[] b = new float[numElements];
        final float[] c = new float[numElements];

        Arrays.fill(a, 3);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        //@formatter:off
        final TaskSchedule schedule = new TaskSchedule("s0")
                .task("t0", SimpleMath::vectorAdd, a, b, c)
                .streamOut(c);
        //@formatter:on

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            schedule.execute();
        }
        long stop = System.nanoTime();

        double elapsed = (stop - start) * 1e-9;
        double megaBytes = (((double) numElements * 4)) * 3 * iterations;
        double bw = megaBytes / elapsed;

        System.out.printf("Overall  : time = %f seconds, bw = %s\n", elapsed, formatBytesPerSecond(bw));

        /*
         * Check results
         */
        int errors = 0;
        for (final float value : c) {
            if (value != 5f) {
                errors++;
            }
        }

        if (errors > 0) {
            System.out.printf("Invalid result: %d errors\n", errors);
        }
    }
}
