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
 * Configure: Invokes CMake or ndk-build to create a configuration, including compile_commands.json
 * Build: Invokes ninja or ndk-build to build .so files for a single ABI.
 * VariantBuild: A referring build used to depend on single ABI build tasks.
 * Anchor: A placeholder task that exists to depend on working tasks.
 */
sealed class CxxGradleTaskModel {
    data class Configure(val representatives: List<CxxAbiModel>) : CxxGradleTaskModel()
    data class Build(val representatives: List<CxxAbiModel>) : CxxGradleTaskModel()
    data class VariantBuild(val variantName: String, val isRepublishOnly:Boolean, val representatives: List<CxxAbiModel>) : CxxGradleTaskModel()
    data class Anchor(val variantName: String) : CxxGradleTaskModel()
}

/**
 * A collection of tasks along with a list of edges that represent dependencies between tasks.
 */
data class CxxTaskDependencyModel(
        val tasks : Map<String, CxxGradleTaskModel>, // Key is task name
        val edges : List<Pair<String, String>>   // first dependsOn second
)
