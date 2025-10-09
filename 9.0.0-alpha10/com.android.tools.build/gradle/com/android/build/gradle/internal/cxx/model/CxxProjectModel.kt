/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.model

import java.io.File

/**
 * Holds immutable project-level information for C/C++ build and sync, see README.md
 */
data class CxxProjectModel(
    /**
     * Folder of project-level build.gradle file
     *   ex, source-root/
     */
    val rootBuildGradleFolder: File = File("."),

    /**
     * Install folder of SDK
     *   ex, sdk.dir=/path/to/sdk
     */
    val sdkFolder: File = File("."),

    /**
     * Whether to build a single ABI for IDE
     *   default -pandroid.buildOnlyTargetAbi=true
     */
    val isBuildOnlyTargetAbiEnabled: Boolean = false,

    /** The ABIs to build for IDE
     *   example -pandroid.injected.build.abi="x86,x86_64"
     */
    val ideBuildTargetAbi: String? = null,

    /**
     * When true, CMake Build Cohabitation is turned on.
     */
    val isCmakeBuildCohabitationEnabled: Boolean = false,

    /**
     * Directory containing all build attribution file in Chrome trace format. See
     * [com.android.builder.profile.ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY].
     * If null, it means user has not requested generation of build attribution file.
     */
    val chromeTraceJsonFolder: File? = null,

    /**
     * Feature flag enabling prefab for the project.
     */
    val isPrefabEnabled: Boolean = false,
)
