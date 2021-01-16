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
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class ArrayAddIntPrebuilt {

    /**
     * Sums up the (integer) values of two arrays and stores them in a third array (for every index)
     * @param a First array
     * @param b Second array
     * @param c Result array
     */
    public static void add(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Arrays.fill(a, 1);
        Arrays.fill(b, 2);
        Arrays.fill(c, 0);

        String tornadoSDK = System.getenv("TORNADO_SDK");

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        String filePath = tornadoSDK + "/examples/generated/";
        filePath += device.getPlatformName().contains("CUDA") ? "add.ptx" : "add.cl";

        // @formatter:off
        new TaskSchedule("s0")
                .prebuiltTask("t0", "add", filePath,
                        new Object[]{a, b, c},
                        new Access[]{Access.READ, Access.READ, Access.WRITE},
                        device,
                        new int[]{numElements})
                .streamOut(c)
                .execute();
        // @formatter:on

        System.out.println("a: " + Arrays.toString(a));
        System.out.println("b: " + Arrays.toString(b));
        System.out.println("c: " + Arrays.toString(c));
    }

}
