/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.gradle.generator

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.tasks.NativeBuildSystem
import com.google.gson.stream.JsonReader
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Action
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.util.concurrent.Callable

/**
 * Abstraction of C/C++ gradle model generation and build.
 */
interface ExternalNativeJsonGenerator {
    @get:Input
    val nativeBuildSystem: NativeBuildSystem
    @get:OutputFiles
    val nativeBuildConfigurationsJsons: List<File>
    @get:Input
    val objFolder: String
    @get:Input
    val soFolder: String
    @get:Internal
    val stats: GradleBuildVariant.Builder
    @get:Internal
    val variant: CxxVariantModel
    @get:Internal
    val abis: List<CxxAbiModel>
    @Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    fun getStlSharedObjectFiles(): Map<Abi, File>

    // Gradle model generator support below here.
    // TODO(153964094) expose gradle model directly here rather than utility functions that allow it
    fun buildForOneAbiName(
        forceJsonGeneration: Boolean,
        abiName: String,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    )
    fun forEachNativeBuildConfiguration(callback: (JsonReader) -> Unit)
    fun parallelBuild(
        forceJsonGeneration: Boolean,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    ): List<Callable<Void?>>
    fun build(
        forceJsonGeneration: Boolean,
        execOperation: (Action<in ExecSpec?>) -> ExecResult,
        javaExecOperation: (Action<in JavaExecSpec?>) -> ExecResult
    )
}