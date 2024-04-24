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

package com.android.build.gradle.internal.core.dsl.features

import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.PostProcessingOptions
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import java.io.File

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by components that support shrinking/minification.
 */
interface OptimizationDslInfo {

    /**
     * Returns the component ids of those external library dependencies whose keep rules are ignored
     * when building the project.
     */
    val ignoredLibraryKeepRules: Set<String>

    /**
     * Returns whether to ignore all keep rules from external library dependencies.
     */
    val ignoreAllLibraryKeepRules: Boolean

    /**
     * Returns the external dependencies to ignore in baseline profiles.
     */
    val ignoreFromInBaselineProfile: Set<String>

    /**
     * Returns whether to ignore all external dependencies in baseline profiles.
     */
    val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean

    val postProcessingOptions: PostProcessingOptions

    fun getProguardFiles(into: ListProperty<RegularFile>)

    fun gatherProguardFiles(type: ProguardFileType): Collection<File>
}
