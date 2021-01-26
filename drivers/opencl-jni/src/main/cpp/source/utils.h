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
 */

#ifndef utils_h
#define utils_h

#ifdef __cplusplus
extern "C" {
#endif

#define CL_TARGET_OPENCL_VERSION 120

#ifdef __APPLE__
    #include <OpenCL/cl.h>
#else
    #include <CL/cl.h>
#endif

#include <jni.h>

char *getOpenCLError(char *, cl_int);

jint throwError(JNIEnv *env, const char *message);

jint throwNoClassDefFoundError(JNIEnv *env, const char *message);

jint throwNoSuchMethodError(JNIEnv *env, const char *className, const char *methodName, const char *signature );
/*
jint throwNoSuchFieldError(JNIEnv *env, const char *message);

jint throwOutOfMemoryError(JNIEnv *env, const char *message);
*/
#ifdef __cplusplus
}
#endif
#endif