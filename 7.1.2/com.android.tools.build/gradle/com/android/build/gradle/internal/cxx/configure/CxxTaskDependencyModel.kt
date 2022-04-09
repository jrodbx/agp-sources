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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.model.CxxAbiModel

/**
 * Four kinds of Gradle task.
 *
 * Configure: Invokes CMake or ndk-build to create a configuration for a single ABI, including
 *   compile_commands.json.
 * ConfigureGroup: A grouping task that depends on all ABIs of a single configuration.
 * VariantConfigure: A per-variant configure task that refers many-to-one with per-configuration
 *   configure.
 * Build: Invokes ninja or ndk-build to build .so files for a single configuration and ABI.
 * BuildGroup: A grouping task that depends on all ABIs of a single build configuration.
 * VariantBuild: A per-variant build that refers many-to-one with per-configuration builds.
 */
sealed class CxxGradleTaskModel {
    data class Configure(val representative: CxxAbiModel) : CxxGradleTaskModel()
    object ConfigureGroup : CxxGradleTaskModel()
    data class VariantConfigure(val representatives: List<CxxAbiModel>) : CxxGradleTaskModel()
    data class Build(val representative: CxxAbiModel) : CxxGradleTaskModel()
    object BuildGroup : CxxGradleTaskModel()
    data class VariantBuild(val representatives: List<CxxAbiModel>) : CxxGradleTaskModel()
}

/**
 * A collection of tasks along with a list of edges that represent dependencies between tasks.
 */
data class CxxTaskDependencyModel(
        val tasks : Map<String, CxxGradleTaskModel>, // Key is task name
        val edges : List<Pair<String, String>>   // first task name dependsOn second
)
