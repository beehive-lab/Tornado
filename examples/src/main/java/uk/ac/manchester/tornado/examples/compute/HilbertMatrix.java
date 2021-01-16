/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

public class HilbertMatrix {

    private static final boolean CHECK_RESULT = true;
    private static int NROWS = 1024;
    private static int NCOLS = 1024;

    /**
     * Computes a Hilbert Matrix
     * @param output array where the output should be stored
     * @param rows number of rows
     * @param cols number of columns
     */
    public static void hilberComputation(float[] output, int rows, int cols) {
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                output[i * rows + j] = (float) 1 / ((i + 1) + (j + 1) - 1);
            }
        }
    }

    public static void main(String[] args) {
        float[] output = new float[NROWS * NCOLS];

        // @formatter:off
        TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", HilbertMatrix::hilberComputation, output, NROWS, NCOLS)
                .streamOut(output);
        // @formatter:on

        s0.execute();

        if (CHECK_RESULT) {
            float[] seq = new float[NROWS * NCOLS];
            hilberComputation(seq, NROWS, NCOLS);
            for (int i = 0; i < NROWS; i++) {
                for (int j = 0; j < NCOLS; j++) {
                    if (Math.abs(output[i * NROWS + j] - seq[i * NROWS + j]) > 0.01f) {
                        System.out.println("Result is not correct: <" + i + "," + j + ">  -> " + output[i * NROWS + j] + " vs " + seq[i * NROWS + j]);
                        break;
                    }
                }
            }
        }

    }
}
