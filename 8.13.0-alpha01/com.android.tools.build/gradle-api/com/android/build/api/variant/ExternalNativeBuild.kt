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

package com.android.build.api.variant

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty

/**
 * Parameters to use when building native components.
 *
 * Warning: Due to current limitations in how Android Gradle Plugin and native build interacts,
 * it is not possible to wire a [org.gradle.api.Task] mapped [org.gradle.api.provider.Provider] to
 * any of the collection [ListProperty] or [SetProperty] below. These properties must be resolved at
 * configuration time and therefore cannot have results based on Task execution. This limitation
 * might be lifted in the future.
 */
interface ExternalNativeBuild {

    /**
     * Specifies the Application Binary Interfaces (ABI) that Gradle should build outputs for. The
     * ABIs that Gradle packages into your APK are determined by {@link
     * com.android.build.gradle.internal.dsl.NdkOptions#abiFilter
     * android.defaultConfig.ndk.abiFilter}
     */
    val abiFilters: SetProperty<String>

    /**
     * Specifies arguments for CMake.
     */
    val arguments: ListProperty<String>

    /**
     * Specifies flags for the C compiler.
     */
    val cFlags: ListProperty<String>

    /**
     * Specifies flags for the CPP compiler.
     */
    val cppFlags: ListProperty<String>

    /**
     * Specifies the library and executable targets from your CMake project that Gradle should
     * build.
     */
    val targets: SetProperty<String>
}
