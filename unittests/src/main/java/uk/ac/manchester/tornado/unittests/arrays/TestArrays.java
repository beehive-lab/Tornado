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

package uk.ac.manchester.tornado.unittests.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestArrays extends TornadoTestBase {

    public static void addAccumulator(int[] a, int value) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += value;
        }
    }

    public static void vectorAddDouble(double[] a, double[] b, double[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddFloat(float[] a, float[] b, float[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddInteger(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddLong(long[] a, long[] b, long[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void vectorAddShort(short[] a, short[] b, short[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = (short) (a[i] + b[i]);
        }
    }

    public static void vectorChars(char[] a, char[] b, char[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = 'f';
        }
    }

    public static void vectorAddByte(byte[] a, byte[] b, byte[] c) {
        for (@Parallel int i = 0; i < c.length; i++) {
            c[i] = (byte) (a[i] + b[i]);
        }
    }

    public static void addChars(char[] a, int[] b) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] += b[i];
        }
    }

    public static void initializeSequentialByte(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 21;
        }
    }

    public static void initializeSequential(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    public static void initializeToOneParallel(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
    }

    @Test
    public void testWarmUp() {

        final int N = 128;
        int numKernels = 16;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestArrays::addAccumulator, data, 1);
        }

        s0.streamOut(data).warmup();

        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data[i]);
        }
    }

    @Test
    public void testInitByteArray() {
        final int N = 128;
        byte[] data = new byte[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeSequentialByte, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(21, data[i]);
        }
    }

    @Test
    public void testInitNotParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeSequential, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    @Test
    public void testInitParallel() {
        final int N = 128;
        int[] data = new int[N];

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        s0.task("t0", TestArrays::initializeToOneParallel, data);
        s0.streamOut(data).warmup();
        s0.execute();

        for (int i = 0; i < N; i++) {
            assertEquals(1, data[i], 0.0001);
        }
    }

    @Test
    public void testAdd() {

        final int N = 128;
        int numKernels = 8;

        int[] data = new int[N];

        IntStream.range(0, N).parallel().forEach(idx -> {
            data[idx] = idx;
        });

        TaskSchedule s0 = new TaskSchedule("s0");
        assertNotNull(s0);

        for (int i = 0; i < numKernels; i++) {
            s0.task("t" + i, TestArrays::addAccumulator, data, 1);
        }

        s0.streamOut(data).execute();

        for (int i = 0; i < N; i++) {
            assertEquals(i + numKernels, data[i], 0.0001);
        }
    }

    @Test
    public void testVectorAdditionDouble() {
        final int numElements = 4096;
        double[] a = new double[numElements];
        double[] b = new double[numElements];
        double[] c = new double[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddDouble, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.01);
        }
    }

    @Test
    public void testVectorAdditionFloat() {
        final int numElements = 4096;
        float[] a = new float[numElements];
        float[] b = new float[numElements];
        float[] c = new float[numElements];

        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        });

        new TaskSchedule("s0") //
                .streamIn(a, b) //
                .task("t0", TestArrays::vectorAddFloat, a, b, c) //
                .streamOut(c) //
                .execute(); //

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i], 0.01f);
        }
    }

    @Test
    public void testVectorAdditionInteger() {
        final int numElements = 4096;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int[] c = new int[numElements];

        Random r = new Random();
        IntStream.range(0, numElements).sequential().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddInteger, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testVectorAdditionLong() {
        final int numElements = 4096;
        long[] a = new long[numElements];
        long[] b = new long[numElements];
        long[] c = new long[numElements];

        IntStream.range(0, numElements).parallel().forEach(i -> {
            a[i] = i;
            b[i] = i;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddLong, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testVectorAdditionShort() {
        final int numElements = 4096;
        short[] a = new short[numElements];
        short[] b = new short[numElements];
        short[] c = new short[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 20;
            b[idx] = 34;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorAddShort, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (int i = 0; i < c.length; i++) {
            assertEquals(a[i] + b[i], c[i]);
        }
    }

    @Test
    public void testVectorChars() {
        final int numElements = 4096;
        char[] a = new char[numElements];
        char[] b = new char[numElements];
        char[] c = new char[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 'a';
            b[idx] = '0';
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::vectorChars, a, b, c)
            .streamOut(c)
            .execute();
        //@formatter:on

        for (char value : c) {
            assertEquals('f', value);
        }
    }

    @Test
    public void testVectorBytes() {
        final int numElements = 4096;
        byte[] a = new byte[numElements];
        byte[] b = new byte[numElements];
        byte[] c = new byte[numElements];

        IntStream.range(0, numElements).parallel().forEach(idx -> {
            a[idx] = 10;
            b[idx] = 11;
        });

        //@formatter:off
        new TaskSchedule("s0")
                .streamIn(a, b)
                .task("t0", TestArrays::vectorAddByte, a, b, c)
                .streamOut(c)
                .execute();
        //@formatter:on

        for (byte value : c) {
            assertEquals(21, value);
        }
    }

    /**
     * Inspired by the CUDA Hello World from Computer Graphics:
     * 
     * 
     * @see <a href=
     *      "http://computer-graphics.se/hello-world-for-cuda.html">http://computer-graphics.se/hello-world-for-cuda.html</a>
     */
    @Test
    public void testVectorCharsMessage() {
        char[] a = new char[] { 'h', 'e', 'l', 'l', 'o', ' ', '\0', '\0', '\0', '\0', '\0', '\0' };
        int[] b = new int[] { 15, 10, 6, 0, -11, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b)
            .task("t0", TestArrays::addChars, a, b)
            .streamOut(a)
            .execute();
        //@formatter:on

        assertEquals('w', a[0]);
        assertEquals('o', a[1]);
        assertEquals('r', a[2]);
        assertEquals('l', a[3]);
        assertEquals('d', a[4]);
        assertEquals('!', a[5]);
    }
}
