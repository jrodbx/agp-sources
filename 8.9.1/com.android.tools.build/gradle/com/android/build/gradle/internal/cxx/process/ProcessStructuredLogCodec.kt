/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.process

import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder

/**
 * Methods in this class convert between decoded and encoded versions
 * of C/C++ build cache protocol buffers.
 * Encoded means strings and lists of strings have been converted to
 * their int counterparts.
 */

/**
 * String-encode an [ExecuteProcess] structure into an [EncodedExecuteProcess]
 * structure.
 */
fun ExecuteProcess.encode(encoder : StringEncoder) : EncodedExecuteProcess {
    return EncodedExecuteProcess.newBuilder()
        .setExecutableId(encoder.encode(executable))
        .setArgsId(encoder.encodeList(argsList))
        .setDescriptionId(encoder.encode(description))
        .setEnvironmentKeysId(encoder.encodeList(environmentKeysList))
        .setEnvironmentValuesId(encoder.encodeList(environmentValuesList))
        .setJvmClassPathId(encoder.encode(jvmClassPath))
        .setJvmMainClassId(encoder.encode(jvmMainClass))
        .setJvmArgsId(encoder.encodeList(jvmArgsList))
        .setExitCode(exitCode)
        .build()
}

/**
 * String-decode an [EncodedExecuteProcess] structure from an [ExecuteProcess]
 * structure.
 */
fun EncodedExecuteProcess.decode(decoder : StringDecoder) : ExecuteProcess {
    val result = ExecuteProcess.newBuilder()
        .setExecutable(decoder.decode(executableId))
        .addAllArgs(decoder.decodeList(argsId))
        .setDescription(decoder.decode(descriptionId))
        .addAllEnvironmentKeys(decoder.decodeList(environmentKeysId))
        .addAllEnvironmentValues(decoder.decodeList(environmentValuesId))
        .setExitCode(exitCode)
    if (jvmClassPathId != 0) result.jvmClassPath = decoder.decode(jvmClassPathId)
    if (jvmMainClassId != 0) result.jvmMainClass = decoder.decode(jvmMainClassId)
    if (jvmArgsId != 0) result.addAllJvmArgs(decoder.decodeList(jvmArgsId))
    return result.build()
}

/**
 * Call EncodedExecuteProcess#decode in a pattern that can be
 * used directly with [readStructuredLogs].
 */
fun decodeExecuteProcess(
    encoded : EncodedExecuteProcess,
    decoder : StringDecoder) = encoded.decode(decoder)
