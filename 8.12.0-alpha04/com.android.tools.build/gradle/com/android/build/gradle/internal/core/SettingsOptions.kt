/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core

data class ToolExecutionOptions (val jvmArgs: List<String>, val runInSeparateProcess: Boolean)

data class ExecutionProfileOptions(val name: String, val r8Options: ToolExecutionOptions)

/**
 * Data class containing options specified in the [com.android.build.gradle.internal.plugins.SettingsPlugin]
 *
 * This class is for options that are exclusive to the android settings plugin, such as execution
 * profiles, and not compileSdkVersion and other options that directly translate to DSL options that
 * exist in AGP plugins.
 *
 * executionProfiles is the container of [com.android.build.api.dsl.ExecutionProfile] transformed
 * into a simple map of [ExecutionProfileOptions]
 *
 * defaultProfile is the default profile to use in case no profile is declared.
 */
data class SettingsOptions(val executionProfile: ExecutionProfileOptions?)


val DEFAULT_EXECUTION_PROFILE = ExecutionProfileOptions(
    name = "default",
    r8Options = ToolExecutionOptions(
        jvmArgs = listOf(),
        runInSeparateProcess = false
    )
)
