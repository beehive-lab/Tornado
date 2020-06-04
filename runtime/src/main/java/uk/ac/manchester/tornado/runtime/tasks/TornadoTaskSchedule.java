/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.tasks;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.CachedGraph;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.api.AbstractTaskGraph;
import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.TornadoDriver;
import uk.ac.manchester.tornado.api.common.*;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.*;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.TornadoVM;
import uk.ac.manchester.tornado.runtime.analyzer.MetaReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.ReduceCodeAnalysis;
import uk.ac.manchester.tornado.runtime.analyzer.TaskUtils;
import uk.ac.manchester.tornado.runtime.common.*;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.graph.*;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.profiler.TimeProfiler;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.SketchRequest;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitiveClass;
import static uk.ac.manchester.tornado.runtime.common.Tornado.VM_USE_DEPS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.warn;

/**
 * Implementation of the Tornado API for running on heterogeneous devices.
 */
public class TornadoTaskSchedule implements AbstractTaskGraph {

    /**
     * Options for Dynamic Reconfiguration
     */
    private static final boolean EXEPERIMENTAL_MULTI_HOST_HEAP = false;
    private static final int DEFAULT_DRIVER_INDEX = 0;
    private static final int PERFORMANCE_WARMUP = 3;
    private final static boolean TIME_IN_NANOSECONDS = Tornado.TIME_IN_NANOSECONDS;
    private static final String TASK_SCHEDULE_PREFIX = "XXX";
    private static final ConcurrentHashMap<Policy, ConcurrentHashMap<String, HistoryTable>> executionHistoryPolicy = new ConcurrentHashMap<>();
    private static final int HISTORY_POINTS_PREDICTION = 5;
    private static final boolean USE_GLOBAL_TASK_CACHE = false;
    private static ConcurrentHashMap<Integer, TaskSchedule> globalTaskScheduleIndex = new ConcurrentHashMap<>();
    private static int baseGlobalIndex = 0;
    private static AtomicInteger offsetGlobalIndex = new AtomicInteger(0);
    private final CompileInfo COMPILE_ONLY = new CompileInfo(true, false);
    private final CompileInfo COMPILE_AND_UPDATE = new CompileInfo(true, true);
    private final CompileInfo NOT_COMPILE_UPDATE = new CompileInfo(false, false);
    private final Pattern PATTERN_BATCH = Pattern.compile("(\\d+)(MB|mg|gb|GB)");
    MetaReduceCodeAnalysis analysisTaskSchedule;
    private TornadoExecutionContext graphContext;
    private byte[] highLevelCode = new byte[2048];
    private ByteBuffer hlBuffer;
    private TornadoVMGraphCompilationResult result;
    private long batchSizeBytes = -1;
    // One TornadoVM instance per TaskSchedule
    private TornadoVM vm;
    private Event event;
    private String taskScheduleName;
    private ArrayList<TaskPackage> taskPackages = new ArrayList<>();
    private ArrayList<Object> streamOutObjects = new ArrayList<>();
    private ArrayList<Object> streamInObjects = new ArrayList<>();
    private ConcurrentHashMap<Policy, Integer> policyTimeTable = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Object>> multiHeapManagerOutputs = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, ArrayList<Object>> multiHeapManagerInputs = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, TaskSchedule> taskScheduleIndex = new ConcurrentHashMap<>();
    private StringBuffer bufferLogProfiler = new StringBuffer();
    private CachedGraph<?> graph;
    /**
     * Options for new reductions - experimental
     */
    private boolean reduceExpressionRewritten = false;
    private ReduceTaskSchedule reduceTaskScheduleMeta;
    private boolean reduceAnalysis = false;
    private TornadoProfiler timeProfiler;

    /**
     * Task Schedule implementation that uses GPU/FPGA and multi-core backends.
     *
     * @param taskScheduleName Task-Schedule name
     */
    public TornadoTaskSchedule(String taskScheduleName) {
        graphContext = new TornadoExecutionContext(taskScheduleName);
        hlBuffer = ByteBuffer.wrap(highLevelCode);
        hlBuffer.order(ByteOrder.LITTLE_ENDIAN);
        hlBuffer.rewind();
        result = null;
        event = null;
        this.taskScheduleName = taskScheduleName;

        if (TornadoOptions.isProfilerEnabled()) {
            this.timeProfiler = new TimeProfiler();
        } else {
            this.timeProfiler = new EmptyProfiler();
        }
    }

    static void performStreamInThread(TaskSchedule task, ArrayList<Object> inputObjects) {
        int numObjectsCopyIn = inputObjects.size();
        switch (numObjectsCopyIn) {
            case 0:
                break;
            case 1:
                task.streamIn(inputObjects.get(0));
                break;
            case 2:
                task.streamIn(inputObjects.get(0), inputObjects.get(1));
                break;
            case 3:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2));
                break;
            case 4:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3));
                break;
            case 5:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4));
                break;
            case 6:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5));
            case 7:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects.get(6));
                break;
            case 8:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects.get(6), inputObjects.get(7));
                break;
            case 9:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects.get(6), inputObjects.get(7),
                        inputObjects.get(8));
            case 10:
                task.streamIn(inputObjects.get(0), inputObjects.get(1), inputObjects.get(2), inputObjects.get(3), inputObjects.get(4), inputObjects.get(5), inputObjects.get(6), inputObjects.get(7),
                        inputObjects.get(8), inputObjects.get(9));
                break;
            default:
                System.out.println("COPY-IN Not supported yet: " + numObjectsCopyIn);
                break;
        }
    }

    static void performStreamOutThreads(TaskSchedule task, ArrayList<Object> outputArrays) {
        int numObjectsCopyOut = outputArrays.size();
        switch (numObjectsCopyOut) {
            case 0:
                break;
            case 1:
                task.streamOut(outputArrays.get(0));
                break;
            case 2:
                task.streamOut(outputArrays.get(0), outputArrays.get(1));
                break;
            case 3:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2));
                break;
            case 4:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3));
                break;
            case 5:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4));
                break;
            case 6:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5));
                break;
            case 7:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6));
                break;
            case 8:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6), outputArrays.get(7));
                break;
            case 9:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6), outputArrays.get(7),
                        outputArrays.get(8));
                break;
            case 10:
                task.streamOut(outputArrays.get(0), outputArrays.get(1), outputArrays.get(2), outputArrays.get(3), outputArrays.get(4), outputArrays.get(5), outputArrays.get(6), outputArrays.get(7),
                        outputArrays.get(8), outputArrays.get(9));
                break;
            default:
                System.out.println("COPY-OUT Not supported yet: " + numObjectsCopyOut);
                break;
        }
    }

    @Override
    public String getTaskScheduleName() {
        return taskScheduleName;
    }

    @Override
    public SchedulableTask getTask(String id) {
        return graphContext.getTask(id);
    }

    @Override
    public TornadoDevice getDevice() {
        return meta().getDevice();
    }

    @Override
    public void setDevice(TornadoDevice device) {
        meta().setDevice(device);
    }

    @Override
    public TornadoAcceleratorDevice getDeviceForTask(String id) {
        return graphContext.getDeviceForTask(id);
    }

    @Override
    public long getReturnValue(String id) {
        CallStack stack = graphContext.getFrame(id);
        return stack.getReturnValue();
    }

    @Override
    public void addInner(SchedulableTask task) {
        Providers providers = getTornadoRuntime().getDriver(0).getProviders();
        TornadoSuitesProvider suites = getTornadoRuntime().getDriver(0).getSuitesProvider();

        int index = graphContext.addTask(task);

        if (task instanceof CompilableTask) {
            CompilableTask compilableTask = (CompilableTask) task;
            final ResolvedJavaMethod resolvedMethod = getTornadoRuntime().resolveMethod(compilableTask.getMethod());
            new SketchRequest(compilableTask.meta(), resolvedMethod, providers, suites.getGraphBuilderSuite(), suites.getSketchTier()).run();

            Sketch lookup = TornadoSketcher.lookup(resolvedMethod);
            this.graph = lookup.getGraph();
        }

        hlBuffer.put(TornadoGraphBitcodes.CONTEXT.index());
        int globalTaskId = graphContext.getTaskCount();
        hlBuffer.putInt(globalTaskId);
        graphContext.incrGlobalTaskCount();
        hlBuffer.putInt(index);

        // create parameter list
        final Object[] args = task.getArguments();
        hlBuffer.put(TornadoGraphBitcodes.ARG_LIST.index());
        hlBuffer.putInt(args.length);

        for (final Object arg : args) {
            index = graphContext.insertVariable(arg);
            if (arg.getClass().isPrimitive() || isBoxedPrimitiveClass(arg.getClass())) {
                hlBuffer.put(TornadoGraphBitcodes.LOAD_PRIM.index());
            } else {
                hlBuffer.put(TornadoGraphBitcodes.LOAD_REF.index());
            }
            hlBuffer.putInt(index);
        }

        // launch code
        hlBuffer.put(TornadoGraphBitcodes.LAUNCH.index());
    }

    private void updateDeviceContext(TornadoGraph graph) {
        BitSet deviceContexts = graph.filter(ContextNode.class);
        final ContextNode contextNode = (ContextNode) graph.getNode(deviceContexts.nextSetBit(0));
        contextNode.setDeviceIndex(meta().getDeviceIndex());
        graphContext.addDevice(meta().getDevice());
    }

    /**
     * Compile a task-schedule into TornadoVM byte-code
     *
     * @param setNewDevice: boolean that specifies if set a new device or not.
     */
    private void compile(boolean setNewDevice) {
        final ByteBuffer buffer = ByteBuffer.wrap(highLevelCode);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.limit(hlBuffer.position());

        // final long t0 = System.nanoTime();
        final TornadoGraph graph = TornadoGraphBuilder.buildGraph(graphContext, buffer);
        // final long t1 = System.nanoTime();

        if (setNewDevice) {
            updateDeviceContext(graph);
        }

        // TornadoVM byte-code generation
        result = TornadoVMGraphCompiler.compile(graph, graphContext, batchSizeBytes);

        vm = new TornadoVM(graphContext, result.getCode(), result.getCodeSize(), timeProfiler);

        if (meta().shouldDumpSchedule()) {
            graphContext.print();
            graph.print();
            result.dump();
        }
    }

    private boolean compareDevices(HashSet<TornadoAcceleratorDevice> lastDevices, TornadoAcceleratorDevice device2) {
        return lastDevices.contains(device2);
    }

    @Override
    public boolean isLastDeviceListEmpty() {
        return graphContext.getLastDevices().size() == 0;
    }

    /**
     * It queries if the task has to be recompiled. It returns two values:
     * <p>
     * <li>compile: This indicates if it has to be re-compiled</li>
     * <li>updateDevice:This indicates if there is a new device for the same
     * task</li>
     * </p>
     *
     * @return {@link CompileInfo}
     */
    private CompileInfo extractCompileInfo() {
        if (result == null && isLastDeviceListEmpty()) {
            return COMPILE_ONLY;
        } else if (result != null && !isLastDeviceListEmpty() && !(compareDevices(graphContext.getLastDevices(), meta().getDevice()))) {
            return COMPILE_AND_UPDATE;
        }
        return NOT_COMPILE_UPDATE;
    }

    private boolean compileToTornadoVMBytecodes() {
        CompileInfo compileInfo = extractCompileInfo();
        if (compileInfo.compile) {
            timeProfiler.start(ProfilerType.TOTAL_BYTE_CODE_GENERATION);
            graphContext.assignToDevices();
            compile(compileInfo.updateDevice);
            timeProfiler.stop(ProfilerType.TOTAL_BYTE_CODE_GENERATION);
        }
        graphContext.addLastDevice(meta().getDevice());
        graphContext.newStack(compileInfo.updateDevice);
        return compileInfo.compile;
    }

    private void compileTaskToOpenCL() {
        vm.compile();
    }

    /**
     * If current FPGA execution and JIT mode, then run warm-up.
     */
    private void preCompilationForFPGA() {
        boolean compile = false;
        if (Tornado.FPGA_EMULATION) {
            compile = true;
        } else if (graphContext.getDeviceFirtTask() instanceof TornadoAcceleratorDevice) {
            TornadoAcceleratorDevice device = (TornadoAcceleratorDevice) graphContext.getDeviceFirtTask();
            if (device.isFullJITMode(graphContext.getTask(0))) {
                compile = true;
            }
        }

        if (compile) {
            if (Tornado.DEBUG) {
                System.out.println("[DEBUG] JIT compilation for the FPGA");
            }
            compileTaskToOpenCL();
        }
    }

    private void updateProfiler() {
        if (!TornadoOptions.PROFILER_LOGS_ACCUMULATE) {
            timeProfiler.dumpJson(new StringBuffer(), this.getId());
        } else {
            bufferLogProfiler.append(timeProfiler.createJson(new StringBuffer(), this.getId()));
        }

        if (TornadoOptions.isSaveProfilerEnabled()) {
            String jsonFile = timeProfiler.createJson(new StringBuffer(), this.getId());
            try (FileWriter fileWriter = new FileWriter("profiler-app.json", true)) {
                PrintWriter printWriter = new PrintWriter(fileWriter);
                printWriter.println(jsonFile);
            } catch (IOException e) {
                throw new TornadoRuntimeException("JSon profiler file cannot be appened");
            }
        }
    }

    @Override
    public void scheduleInner() {
        boolean compile = compileToTornadoVMBytecodes();
        TornadoAcceleratorDevice deviceForTask = graphContext.getDeviceForTask(0);
        if (compile && deviceForTask.getDeviceContext().isPlatformFPGA()) {
            preCompilationForFPGA();
        }

        event = vm.execute();
        timeProfiler.stop(ProfilerType.TOTAL_TASK_SCHEDULE_TIME);
        updateProfiler();
    }

    @Override
    public void apply(Consumer<SchedulableTask> consumer) {
        graphContext.apply(consumer);
    }

    @Override
    public void mapAllToInner(TornadoDevice device) {
        graphContext.mapAllTo(device);
    }

    @Override
    public void dumpTimes() {
        vm.printTimes();
    }

    @Override
    public void dumpProfiles() {
        vm.dumpProfiles();
    }

    @Override
    public void dumpEvents() {
        vm.dumpEvents();
    }

    @Override
    public void clearProfiles() {
        vm.clearProfiles();
    }

    @Override
    public void waitOn() {
        if (VM_USE_DEPS && event != null) {
            event.waitOn();
        } else {
            graphContext.getDevices().forEach(TornadoDevice::sync);
        }
    }

    @Override
    public void streamInInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            streamInObjects.add(object);
            graphContext.getObjectState(object).setStreamIn(true);
        }
    }

    @Override
    public void forceStreamInInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            streamInObjects.add(object);
            graphContext.getObjectState(object).setForceStreamIn(true);
        }
    }

    @Override
    public void streamOutInner(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                warn("null object passed into streamIn() in schedule %s", graphContext.getId());
                continue;
            }
            streamOutObjects.add(object);
            graphContext.getObjectState(object).setStreamOut(true);
        }
    }

    @Override
    public void dump() {
        final int width = 16;
        System.out.printf("code  : capacity = %s, in use = %s \n", humanReadableByteCount(hlBuffer.capacity(), true), humanReadableByteCount(hlBuffer.position(), true));
        for (int i = 0; i < hlBuffer.position(); i += width) {
            System.out.printf("[0x%04x]: ", i);
            for (int j = 0; j < Math.min(hlBuffer.capacity() - i, width); j++) {
                if (j % 2 == 0) {
                    System.out.print(" ");
                }
                if (j < hlBuffer.position() - i) {
                    System.out.printf("%02x", hlBuffer.get(i + j));
                } else {
                    System.out.print("..");
                }
            }
            System.out.println();
        }
    }

    @Override
    public void warmup() {
        getDevice().getDeviceContext().setResetToFalse();
        timeProfiler.clean();

        compileToTornadoVMBytecodes();
        vm.warmup();

        timeProfiler.dumpJson(new StringBuffer(), this.getId());
    }

    @Override
    public void invalidateObjects() {
        if (vm != null) {
            vm.invalidateObjects();
        }
    }

    @Override
    public void syncObject(Object object) {
        if (vm == null) {
            return;
        }
        graphContext.sync();
    }

    private Event syncObjectInner(Object object) {
        final LocalObjectState localState = graphContext.getObjectState(object);
        final GlobalObjectState globalState = localState.getGlobalState();
        final DeviceObjectState deviceState = globalState.getDeviceState();
        final TornadoAcceleratorDevice device = globalState.getOwner();
        return device.resolveEvent(device.streamOutBlocking(object, 0, deviceState, null));
    }

    @Override
    public void syncObjects() {
        if (vm == null) {
            return;
        }
        graphContext.sync();
    }

    @Override
    public void syncObjects(Object... objects) {
        if (vm == null) {
            return;
        }

        Event[] events = new Event[objects.length];
        for (int i = 0; i < objects.length; i++) {
            Object object = objects[i];
            events[i] = syncObjectInner(object);
        }

        for (Event event : events) {
            event.waitOn();
        }
    }

    public TornadoExecutionContext getGraphContext() {
        return this.graphContext;
    }

    @Override
    public String getId() {
        return meta().getId();
    }

    @Override
    public ScheduleMetaData meta() {
        return graphContext.meta();
    }

    private void runReduceTaskSchedule() {
        this.reduceTaskScheduleMeta.executeExpression();
    }

    private void rewriteTaskForReduceSkeleton(MetaReduceCodeAnalysis analysisTaskSchedule) {
        reduceTaskScheduleMeta = new ReduceTaskSchedule(this.getId(), taskPackages, streamInObjects, streamOutObjects, graph);
        reduceTaskScheduleMeta.scheduleWithReduction(analysisTaskSchedule);
        reduceExpressionRewritten = true;
    }

    private AbstractTaskGraph reduceAnalysis() {
        AbstractTaskGraph graph = null;
        if (analysisTaskSchedule == null && !reduceAnalysis) {
            analysisTaskSchedule = ReduceCodeAnalysis.analysisTaskSchedule(taskPackages);
            reduceAnalysis = true;
            if (analysisTaskSchedule != null && analysisTaskSchedule.isValid()) {
                rewriteTaskForReduceSkeleton(analysisTaskSchedule);
                graph = this;
            }
        }
        return graph;
    }

    private AbstractTaskGraph analyzeSkeletonAndRun() {
        AbstractTaskGraph graph = null;
        if (!reduceExpressionRewritten) {
            graph = reduceAnalysis();
        } else {
            runReduceTaskSchedule();
            graph = this;
        }
        return graph;
    }

    @Override
    public AbstractTaskGraph schedule() {

        if (getTornadoRuntime().getNumDrivers() < 1 || getTornadoRuntime().getDriver(0).getNumPlatforms() < 1) {
            runSequential();
            return this;
        }

        timeProfiler.clean();
        timeProfiler.start(ProfilerType.TOTAL_TASK_SCHEDULE_TIME);

        AbstractTaskGraph executionGraph = null;
        if (TornadoOptions.EXPERIMENTAL_REDUCE && !(getId().startsWith(TASK_SCHEDULE_PREFIX))) {
            executionGraph = analyzeSkeletonAndRun();
        }

        if (executionGraph != null) {
            return executionGraph;
        }
        analysisTaskSchedule = null;
        scheduleInner();
        return this;
    }

    @SuppressWarnings("unchecked")
    private void runSequentialCodeInThread(TaskPackage taskPackage) {
        int type = taskPackage.getTaskType();
        switch (type) {
            case 2:
                @SuppressWarnings("rawtypes") Task2 task2 = (Task2) taskPackage.getTaskParameters()[0];
                task2.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2]);
                break;
            case 3:
                @SuppressWarnings("rawtypes") Task3 task3 = (Task3) taskPackage.getTaskParameters()[0];
                task3.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3]);
                break;
            case 4:
                @SuppressWarnings("rawtypes") Task4 task4 = (Task4) taskPackage.getTaskParameters()[0];
                task4.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4]);
                break;
            case 5:
                @SuppressWarnings("rawtypes") Task5 task5 = (Task5) taskPackage.getTaskParameters()[0];
                task5.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5]);
                break;
            case 6:
                @SuppressWarnings("rawtypes") Task6 task6 = (Task6) taskPackage.getTaskParameters()[0];
                task6.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6]);
                break;
            case 7:
                @SuppressWarnings("rawtypes") Task7 task7 = (Task7) taskPackage.getTaskParameters()[0];
                task7.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7]);
                break;
            case 8:
                @SuppressWarnings("rawtypes") Task8 task8 = (Task8) taskPackage.getTaskParameters()[0];
                task8.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8]);
                break;
            case 9:
                @SuppressWarnings("rawtypes") Task9 task9 = (Task9) taskPackage.getTaskParameters()[0];
                task9.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8],
                        taskPackage.getTaskParameters()[9]);
                break;
            case 10:
                @SuppressWarnings("rawtypes") Task10 task10 = (Task10) taskPackage.getTaskParameters()[0];
                task10.apply(taskPackage.getTaskParameters()[1], taskPackage.getTaskParameters()[2], taskPackage.getTaskParameters()[3], taskPackage.getTaskParameters()[4],
                        taskPackage.getTaskParameters()[5], taskPackage.getTaskParameters()[6], taskPackage.getTaskParameters()[7], taskPackage.getTaskParameters()[8],
                        taskPackage.getTaskParameters()[9], taskPackage.getTaskParameters()[10]);
                break;
            default:
                System.out.println("Sequential Runner not supported yet. Number of parameters: " + type);
                break;
        }
    }

    private int synchronizeWithPolicy(Policy policy, long[] totalTimers) {
        // Set the Performance policy by default;
        if (policy == null) {
            policy = Policy.PERFORMANCE;
        }

        int deviceWinnerIndex = -1;

        switch (policy) {
            case END_2_END:
            case PERFORMANCE:
                int position = 0;
                long min = Long.MAX_VALUE;
                for (int i = 0; i < totalTimers.length; i++) {
                    if (min > totalTimers[i]) {
                        min = totalTimers[i];
                        position = i;
                    }
                }
                deviceWinnerIndex = position;
                break;
            default:
                throw new RuntimeException("Policy " + policy + " not defined yet");
        }

        return deviceWinnerIndex;
    }

    private int syncWinner(Thread[] threads) {
        int winner = 0;
        boolean isAlive = true;
        while (isAlive) {

            for (int i = 0; i < threads.length; i++) {
                isAlive = threads[i].isAlive();
                if (!isAlive) {
                    if (TornadoOptions.DEBUG_POLICY) {
                        System.out.println("Thread " + threads[i].getName() + " finished");
                    }
                    winner = i;
                    // kill the others
                    for (int j = 0; j < threads.length; j++) {
                        if (i != j) {
                            threads[j].interrupt();
                        }
                    }
                    break;
                }
            }
        }
        return winner;
    }

    private void runAllTasksSequentially() {
        for (TaskPackage taskPackage : taskPackages) {
            runSequentialCodeInThread(taskPackage);
        }
    }

    private void runParallelSequential(Policy policy, Thread[] threads, int indexSequential, Timer timer, long[] totalTimers) {
        // Last Thread runs the sequential code
        threads[indexSequential] = new Thread(() -> {
            long start = System.currentTimeMillis();
            if (policy == Policy.PERFORMANCE) {
                for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                    runAllTasksSequentially();
                }
                start = timer.time();
            }
            final long endSequentialCode = timer.time();
            Thread.currentThread().setName("Thread-sequential");
            if (TornadoOptions.DEBUG_POLICY) {
                System.out.println("Seq finished: " + Thread.currentThread().getName());
            }

            totalTimers[indexSequential] = (endSequentialCode - start);
        });
    }

    private void runParallelTaskSchedules(int numDevices, Thread[] threads, Timer timer, Policy policy, long[] totalTimers) {
        for (int i = 0; i < numDevices; i++) {
            final int taskScheduleNumber = i;
            threads[i] = new Thread(() -> {
                String taskScheduleName = TASK_SCHEDULE_PREFIX + taskScheduleNumber;
                TaskSchedule task = new TaskSchedule(taskScheduleName);

                Thread.currentThread().setName("Thread-DEV: " + TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(taskScheduleNumber).getDevice().getDeviceName());

                long start = timer.time();
                performStreamInThread(task, streamInObjects);
                for (int k = 0; k < taskPackages.size(); k++) {
                    String taskID = taskPackages.get(k).getId();
                    TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + taskScheduleNumber);
                    if (Tornado.DEBUG) {
                        System.out.println("SET DEVICE: " + taskScheduleName + "." + taskID + ".device=0:" + taskScheduleNumber);
                    }
                    task.addTask(taskPackages.get(k));
                }
                performStreamOutThreads(task, streamOutObjects);

                if (policy == Policy.PERFORMANCE) {
                    // first warm up
                    for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                        task.execute();
                    }
                    start = timer.time();
                }
                task.execute();
                final long end = timer.time();
                taskScheduleIndex.put(taskScheduleNumber, task);

                if (USE_GLOBAL_TASK_CACHE) {
                    globalTaskScheduleIndex.put(offsetGlobalIndex.get(), task);
                    offsetGlobalIndex.incrementAndGet();
                } else {
                    globalTaskScheduleIndex.put(taskScheduleNumber, task);
                }

                totalTimers[taskScheduleNumber] = end - start;
            });
        }

    }

    private void runScheduleWithParallelProfiler(Policy policy) {

        final Timer timer = (TIME_IN_NANOSECONDS) ? new NanoSecTimer() : new MillesecTimer();
        TornadoDriver tornadoDriver = getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX);
        int numDevices = tornadoDriver.getDeviceCount();
        long masterThreadID = Thread.currentThread().getId();

        // One additional threads is reserved for sequential CPU execution
        final int numThreads = numDevices + 1;
        final int indexSequential = numDevices;
        Thread[] threads = new Thread[numThreads];
        long[] totalTimers = new long[numThreads];

        // Last Thread runs the sequential code
        runParallelSequential(policy, threads, indexSequential, timer, totalTimers);

        // Run all task schedules in parallel
        runParallelTaskSchedules(numDevices, threads, timer, policy, totalTimers);

        // FORK
        for (int i = 0; i < numThreads; i++) {
            threads[i].start();
        }

        // Define the winner, based on the first thread to finish
        if (policy == Policy.LATENCY) {
            int deviceWinnerIndex = syncWinner(threads);
            policyTimeTable.put(policy, deviceWinnerIndex);
        }

        // JOIN
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if ((policy == Policy.PERFORMANCE || policy == Policy.END_2_END) && (masterThreadID == Thread.currentThread().getId())) {
            int deviceWinnerIndex = synchronizeWithPolicy(policy, totalTimers);
            policyTimeTable.put(policy, deviceWinnerIndex);
            if (TornadoOptions.DEBUG_POLICY) {
                System.out.println(getListDevices());
                System.out.println("BEST Position: #" + deviceWinnerIndex + " " + Arrays.toString(totalTimers));
            }
        }
    }

    private void runSequential() {
        for (TaskPackage taskPackage : taskPackages) {
            runSequentialCodeInThread(taskPackage);
        }
    }

    private TaskSchedule taskRecompilation(int deviceWinnerIndex) {
        // Force re-compilation in device <deviceWinnerIndex>
        String taskScheduleName = TASK_SCHEDULE_PREFIX + deviceWinnerIndex;
        TaskSchedule taskToCompile = new TaskSchedule(taskScheduleName);
        performStreamInThread(taskToCompile, streamInObjects);
        for (TaskPackage taskPackage : taskPackages) {
            String taskID = taskPackage.getId();
            TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + deviceWinnerIndex);
            taskToCompile.addTask(taskPackage);
        }
        performStreamOutThreads(taskToCompile, streamOutObjects);
        return taskToCompile;
    }

    private void runTaskScheduleParallelSelected(int deviceWinnerIndex) {
        for (TaskPackage taskPackage : taskPackages) {
            TornadoRuntime.setProperty(this.getTaskScheduleName() + "." + taskPackage.getId() + ".device", "0:" + deviceWinnerIndex);
        }
        if (TornadoOptions.DEBUG_POLICY) {
            System.out.println("Running in parallel device: " + deviceWinnerIndex);
        }
        TaskSchedule task = taskScheduleIndex.get(deviceWinnerIndex);
        if (task == null) {
            if (USE_GLOBAL_TASK_CACHE) {
                // This is only if compilation is not using Partial Evaluation
                task = globalTaskScheduleIndex.get(deviceWinnerIndex);
            } else {
                task = taskRecompilation(deviceWinnerIndex);
                // Save the TaskSchedule in cache
                taskScheduleIndex.put(deviceWinnerIndex, task);
            }
        }
        task.execute();
    }

    @Override
    public AbstractTaskGraph scheduleWithProfile(Policy policy) {
        if (policyTimeTable.get(policy) == null) {
            runScheduleWithParallelProfiler(policy);
        } else {
            // Run with the winner device
            int deviceWinnerIndex = policyTimeTable.get(policy);
            if (deviceWinnerIndex >= TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount()) {
                runSequential();
            } else {
                runTaskScheduleParallelSelected(deviceWinnerIndex);
            }
        }
        return this;
    }

    private Object cloneObject(Object o) {
        if (o instanceof float[]) {
            return ((float[]) o).clone();
        } else if (o instanceof int[]) {
            return ((int[]) o).clone();
        } else {
            throw new RuntimeException("Data type cloning not supported");
        }
    }

    @SuppressWarnings("unused")
    private void cloneInputOutputObjects() {
        final long startSearchProfiler = (TIME_IN_NANOSECONDS) ? System.nanoTime() : System.currentTimeMillis();
        TornadoDriver tornadoDriver = getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX);
        int numDevices = tornadoDriver.getDeviceCount();
        // Clone objects (only outputs) for each device
        for (int deviceNumber = 0; deviceNumber < numDevices; deviceNumber++) {
            ArrayList<Object> newInObjects = new ArrayList<>();
            ArrayList<Object> newOutObjects = new ArrayList<>();

            for (int i = 0; i < streamInObjects.size(); i++) {
                Object in = streamInObjects.get(i);
                boolean outputObjectFound = false;
                for (int j = 0; j < streamOutObjects.size(); j++) {
                    Object out = streamOutObjects.get(j);
                    if (in == out) {
                        outputObjectFound = true;
                        break;
                    }
                }
                if (outputObjectFound) {
                    Object clonedObject = cloneObject(in);
                    newInObjects.add(clonedObject);
                    newOutObjects.add(clonedObject);
                } else {
                    newInObjects.add(in);
                }
            }
            multiHeapManagerInputs.put(deviceNumber, newInObjects);
            multiHeapManagerOutputs.put(deviceNumber, newOutObjects);
        }
    }

    private void runSequentialTaskSchedule(Policy policy, Timer timer, long[] totalTimers, int indexSequential) {
        long startSequential = timer.time();
        if (policy == Policy.PERFORMANCE) {
            for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                runAllTasksSequentially();
            }
            startSequential = timer.time();
        }
        runAllTasksSequentially();
        final long endSequentialCode = timer.time();
        totalTimers[indexSequential] = (endSequentialCode - startSequential);
    }

    private void runAllTaskSchedulesInAcceleratorsSequentually(int numDevices, Timer timer, Policy policy, long[] totalTimers) {
        String[] ignoreTaskNames = System.getProperties().getProperty("tornado.ignore.tasks", "").split(",");

        // Running sequentially for all the devices
        for (int taskNumber = 0; taskNumber < numDevices; taskNumber++) {
            String taskScheduleName = TASK_SCHEDULE_PREFIX + taskNumber;
            TaskSchedule task = new TaskSchedule(taskScheduleName);

            long start = timer.time();
            performStreamInThread(task, streamInObjects);

            boolean ignoreTask = false;
            for (int k = 0; k < taskPackages.size(); k++) {
                String taskID = taskPackages.get(k).getId();

                String name = taskScheduleName + "." + taskID;
                for (String s : ignoreTaskNames) {
                    if (s.equals(name)) {
                        totalTimers[taskNumber] = Long.MAX_VALUE;
                        ignoreTask = true;
                        break;
                    }
                }

                TornadoRuntime.setProperty(taskScheduleName + "." + taskID + ".device", "0:" + taskNumber);
                if (Tornado.DEBUG) {
                    System.out.println("SET DEVICE: " + taskScheduleName + "." + taskID + ".device=0:" + taskNumber);
                }
                task.addTask(taskPackages.get(k));
            }

            if (ignoreTask) {
                continue;
            }
            performStreamOutThreads(task, streamOutObjects);

            if (policy == Policy.PERFORMANCE) {
                for (int k = 0; k < PERFORMANCE_WARMUP; k++) {
                    task.execute();
                }
                start = timer.time();
            }

            task.execute();
            taskScheduleIndex.put(taskNumber, task);

            // TaskSchedules Global
            if (USE_GLOBAL_TASK_CACHE) {
                globalTaskScheduleIndex.put(offsetGlobalIndex.get(), task);
                offsetGlobalIndex.incrementAndGet();
            } else {
                globalTaskScheduleIndex.put(taskNumber, task);
            }

            final long end = timer.time();
            totalTimers[taskNumber] = end - start;
        }
    }

    private void updateHistoryTables(Policy policy, int deviceWinnerIndex) {
        // Matching the name
        for (TaskPackage taskPackage : taskPackages) {
            Object code = taskPackage.getTaskParameters()[0];
            Method m = TaskUtils.resolveMethodHandle(code);
            ConcurrentHashMap<String, HistoryTable> tableSizes = null;

            int dev = baseGlobalIndex + deviceWinnerIndex;

            if (!executionHistoryPolicy.containsKey(policy)) {
                tableSizes = new ConcurrentHashMap<>();
                HistoryTable table = new HistoryTable();
                int size = getMaxInputSize();
                table.getTree().put(size, dev);

                tableSizes.put(m.toGenericString(), table);

            } else {
                tableSizes = executionHistoryPolicy.get(policy);
                if (!tableSizes.containsKey(m.toGenericString())) {
                    HistoryTable table = new HistoryTable();
                    int size = getMaxInputSize();
                    table.getTree().put(size, dev);
                    tableSizes.put(m.toGenericString(), table);
                } else {
                    // update the size
                    HistoryTable table = tableSizes.get(m.toGenericString());
                    int size = getMaxInputSize();
                    table.getTree().put(size, dev);
                    tableSizes.put(m.toGenericString(), table);
                }
            }
            executionHistoryPolicy.put(policy, tableSizes);
            baseGlobalIndex = offsetGlobalIndex.get();
        }
    }

    private String getListDevices() {
        StringBuilder str = new StringBuilder();
        str.append("                  : [");
        int num = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();
        for (int i = 0; i < num; i++) {
            TornadoDeviceType deviceType = TornadoRuntime.getTornadoRuntime().getDriver(0).getDevice(i).getDeviceType();
            String type = "JAVA";
            switch (deviceType) {
                case CPU:
                    type = "CPU  ";
                    break;
                case GPU:
                    type = "GPU  ";
                    break;
                case FPGA:
                    type = "FPGA  ";
                    break;
                case ACCELERATOR:
                    type = "ACCELERATOR";
                    break;
            }
            str.append(type + " ,");
        }
        str.append("JVM]");
        return str.toString();
    }

    private void runWithSequentialProfiler(Policy policy) {
        final Timer timer = (TIME_IN_NANOSECONDS) ? new NanoSecTimer() : new MillesecTimer();
        int numDevices = getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX).getDeviceCount();
        final int totalTornadoDevices = numDevices + 1;
        long[] totalTimers = new long[totalTornadoDevices];

        // Run Sequential
        runSequentialTaskSchedule(policy, timer, totalTimers, numDevices);

        // Run Task Schedules on the accelerator
        runAllTaskSchedulesInAcceleratorsSequentually(numDevices, timer, policy, totalTimers);

        if (policy == Policy.PERFORMANCE || policy == Policy.END_2_END) {
            int deviceWinnerIndex = synchronizeWithPolicy(policy, totalTimers);
            policyTimeTable.put(policy, deviceWinnerIndex);

            updateHistoryTables(policy, deviceWinnerIndex);

            if (TornadoOptions.DEBUG_POLICY) {
                System.out.println(getListDevices());
                System.out.println("BEST Position: #" + deviceWinnerIndex + " " + Arrays.toString(totalTimers));
            }
        }
    }

    /**
     * Experimental method to sync all objects when making a clone copy for all
     * output objects per device.
     *
     * @param policy     input policy
     * @param numDevices number of devices
     */
    private void restoreVarsIntoJavaHeap(Policy policy, int numDevices) {
        if (policyTimeTable.get(policy) < numDevices) {
            // link output
            int deviceWinnerIndex = policyTimeTable.get(policy);
            ArrayList<Object> deviceOutputObjects = multiHeapManagerOutputs.get(deviceWinnerIndex);
            for (int i = 0; i < streamOutObjects.size(); i++) {
                @SuppressWarnings("unused") Object output = streamOutObjects.get(i);
                output = deviceOutputObjects.get(i);
            }

            for (int i = 0; i < numDevices; i++) {
                deviceOutputObjects = multiHeapManagerOutputs.get(i);
            }
        }
    }

    /**
     * It obtains the maximum input size for an input task.
     *
     * @return max size of all input arrays.
     */
    private int getMaxInputSize() {
        Object[] parameters = taskPackages.get(0).getTaskParameters();
        int size = 0;
        for (int i = 1; i < parameters.length; i++) {
            Object o = parameters[i];
            if (o.getClass().isArray()) {
                int currentSize = Array.getLength(o);
                size = Math.max(currentSize, size);
            } else {
                size = Math.max(1, size);
            }
        }
        return size;
    }

    private void runInParallel(int deviceWinnerIndex, int numDevices) {
        // Run with the winner device
        if (deviceWinnerIndex >= numDevices) {
            // Last index corresponds to the sequential in HostVM
            runSequential();
        } else {
            // It runs the parallel in the corresponding device
            runTaskScheduleParallelSelected(deviceWinnerIndex);
        }
    }

    @Override
    public AbstractTaskGraph scheduleWithProfileSequentialGlobal(Policy policy) {
        int numDevices = TornadoRuntime.getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX).getDeviceCount();

        if (!executionHistoryPolicy.containsKey(policy)) {
            runWithSequentialProfiler(policy);

            if (EXEPERIMENTAL_MULTI_HOST_HEAP) {
                restoreVarsIntoJavaHeap(policy, numDevices);
            }

        } else {
            Object codeTask0 = taskPackages.get(0).getTaskParameters()[0];
            String fullMethodName = Objects.requireNonNull(TaskUtils.resolveMethodHandle(codeTask0)).toGenericString();
            // if policy registered but method not explored yet
            ConcurrentHashMap<String, HistoryTable> methodHistory = executionHistoryPolicy.get(policy);
            if (!methodHistory.containsKey(fullMethodName)) {
                // current methods to be compiled are not registered with the
                // current policy.
                runWithSequentialProfiler(policy);
            } else {

                // If current methods are found with the current policy -> match
                // the device, a) exact size is found, b) closest size
                HistoryTable table = methodHistory.get(fullMethodName);

                // 1. Infer sizes
                // We get the first set of parameters for the first task as a
                // reference
                int inputSize = getMaxInputSize();

                // 2. Make decision
                if (table.isKeyInTable(inputSize)) {
                    int deviceWinnerIndex = table.getDeviceNumber(inputSize);
                    runInParallel(deviceWinnerIndex, numDevices);
                } else {
                    // Input size not found
                    if (table.getNumKeys() < HISTORY_POINTS_PREDICTION) {
                        // not enough to make a decision -> run with the whole
                        // profiler
                        runWithSequentialProfiler(policy);
                    } else {
                        // get the closet one to the input history data
                        int closestKey = table.getClosestKey(inputSize);
                        int deviceWinnerIndex = table.getTree().get(closestKey);
                        runInParallel(deviceWinnerIndex, numDevices);
                    }
                }
            }
        }
        return this;
    }

    @Override
    public AbstractTaskGraph scheduleWithProfileSequential(Policy policy) {
        int numDevices = TornadoRuntime.getTornadoRuntime().getDriver(DEFAULT_DRIVER_INDEX).getDeviceCount();

        if (policyTimeTable.get(policy) == null) {
            runWithSequentialProfiler(policy);

            if (EXEPERIMENTAL_MULTI_HOST_HEAP) {
                restoreVarsIntoJavaHeap(policy, numDevices);
            }

        } else {
            // Run with the winner device
            int deviceWinnerIndex = policyTimeTable.get(policy);
            if (deviceWinnerIndex >= numDevices) {
                // if the winner is the last index => it is the sequential
                // (HotSpot)
                runSequential();
            } else {
                // Otherwise, it runs the parallel in the corresponding device
                runTaskScheduleParallelSelected(deviceWinnerIndex);
            }
        }
        return this;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void addTask(TaskPackage taskPackage) {

        taskPackages.add(taskPackage);
        String id = taskPackage.getId();
        int type = taskPackage.getTaskType();
        Object[] parameters = taskPackage.getTaskParameters();

        Method method = TaskUtils.resolveMethodHandle(parameters[0]);
        ScheduleMetaData meta = meta();

        // Set the number of threads to run. If 0, it will execute as many
        // threads as input size (after Tornado analyses the right block-size).
        // Otherwise, we force the number of threads to run. This is the case,
        // for example, when executing reductions in which the input size is not
        // power of two.
        meta.setNumThreads(taskPackage.getNumThreadsToRun());

        switch (type) {
            case 0:
                addInner(TaskUtils.createTask(method, meta, id, (Task) parameters[0]));
                break;
            case 1:
                addInner(TaskUtils.createTask(method, meta, id, (Task1) parameters[0], parameters[1]));
                break;
            case 2:
                addInner(TaskUtils.createTask(method, meta, id, (Task2) parameters[0], parameters[1], parameters[2]));
                break;
            case 3:
                addInner(TaskUtils.createTask(method, meta, id, (Task3) parameters[0], parameters[1], parameters[2], parameters[3]));
                break;
            case 4:
                addInner(TaskUtils.createTask(method, meta, id, (Task4) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4]));
                break;
            case 5:
                addInner(TaskUtils.createTask(method, meta, id, (Task5) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5]));
                break;
            case 6:
                addInner(TaskUtils.createTask(method, meta, id, (Task6) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6]));
                break;
            case 7:
                addInner(TaskUtils.createTask(method, meta, id, (Task7) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7]));
                break;
            case 8:
                addInner(TaskUtils.createTask(method, meta, id, (Task8) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8]));
                break;
            case 9:
                addInner(TaskUtils.createTask(method, meta, id, (Task9) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9]));
                break;
            case 10:
                addInner(TaskUtils.createTask(method, meta, id, (Task10) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9], parameters[10]));
                break;
            case 15:
                addInner(TaskUtils.createTask(method, meta, id, (Task15) parameters[0], parameters[1], parameters[2], parameters[3], parameters[4], parameters[5], parameters[6], parameters[7],
                        parameters[8], parameters[9], parameters[10], parameters[11], parameters[12], parameters[13], parameters[14], parameters[15]));
                break;
            default:
                throw new RuntimeException("Task not supported yet. Type: " + type);
        }
    }

    @Override
    public void addPrebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dimensions) {
        addInner(TaskUtils.createTask(meta(), id, entryPoint, filename, args, accesses, device, dimensions));
    }

    @Override
    public void addScalaTask(String id, Object function, Object[] args) {
        addInner(TaskUtils.scalaTask(id, function, args));
    }

    @Override
    public void batch(String batchSize) {

        // parse value and units
        Matcher matcher = PATTERN_BATCH.matcher(batchSize);
        long value = 0;
        String units = null;
        if (matcher.find()) {
            value = Long.parseLong(matcher.group(1));
            units = matcher.group(2).toUpperCase();
        }

        // compute bytes
        switch (Objects.requireNonNull(units)) {
            case "MB":
                this.batchSizeBytes = value * 1_000_000;
                break;
            case "GB":
                this.batchSizeBytes = value * 1_000_000_000;
                break;
            default:
                throw new TornadoRuntimeException("Units not supported: " + units);
        }
    }

    @Override
    public long getTotalTime() {
        return timeProfiler.getTimer(ProfilerType.TOTAL_TASK_SCHEDULE_TIME);
    }

    @Override
    public long getCompileTime() {
        return timeProfiler.getTimer(ProfilerType.TOTAL_GRAAL_COMPILE_TIME) + timeProfiler.getTimer(ProfilerType.TOTAL_DRIVER_COMPILE_TIME);
    }

    @Override
    public long getTornadoCompilerTime() {
        return timeProfiler.getTimer(ProfilerType.TOTAL_GRAAL_COMPILE_TIME);
    }

    @Override
    public long getDriverInstallTime() {
        return timeProfiler.getTimer(ProfilerType.TOTAL_DRIVER_COMPILE_TIME);
    }

    @Override
    public long getDataTransfersTime() {
        return timeProfiler.getTimer(ProfilerType.COPY_IN_TIME) + timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
    }

    @Override
    public long getWriteTime() {
        return timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
    }

    @Override
    public long getReadTime() {
        return timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
    }

    @Override
    public long getDeviceWriteTime() {
        return timeProfiler.getTimer(ProfilerType.COPY_IN_TIME);
    }

    @Override
    public long getDeviceKernelTime() {
        return timeProfiler.getTimer(ProfilerType.TOTAL_KERNEL_TIME);
    }

    @Override
    public long getDeviceReadTime() {
        return timeProfiler.getTimer(ProfilerType.COPY_OUT_TIME);
    }

    @Override
    public String getProfileLog() {
        return bufferLogProfiler.toString();
    }

    // Timer implementation within the Task Schedule
    private static abstract class Timer {
        abstract long time();
    }

    private static class MillesecTimer extends Timer {
        @Override
        long time() {
            return System.currentTimeMillis();
        }
    }

    private static class NanoSecTimer extends Timer {
        @Override
        long time() {
            return System.nanoTime();
        }
    }

    private static class CompileInfo {

        private boolean compile;
        private boolean updateDevice;

        private CompileInfo(boolean compile, boolean updateDevice) {
            this.compile = compile;
            this.updateDevice = updateDevice;
        }
    }

    /**
     * Class that keeps the history of executions based on their data sizes. It has
     * a sorted map (TreeMap) that keeps the relationship between the input size and
     * the actual Tornado device in which the task was executed based on the
     * profiler for the dynamic reconfiguration.
     */
    private static class HistoryTable {
        /**
         * TreeMap between input size -> device index
         */
        private TreeMap<Integer, Integer> table = new TreeMap<>();

        private int getClosestKey(int goal) {
            Set<Integer> keySet = table.keySet();
            return keySet.stream().reduce((prev, current) -> Math.abs(current - goal) < Math.abs(prev - goal) ? current : prev).get();
        }

        private TreeMap<Integer, Integer> getTree() {
            return table;
        }

        private int getNumKeys() {
            return table.keySet().size();
        }

        private int getDeviceNumber(int key) {
            return table.get(key);
        }

        private boolean isKeyInTable(int key) {
            return table.containsKey(key);
        }
    }
}
