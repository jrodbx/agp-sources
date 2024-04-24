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

package com.android.build.gradle.internal.component.features

import com.android.build.gradle.internal.PostprocessingFeatures
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Creation config for components that support minification/shrinking.
 *
 * To use this in a task that requires minification support, use
 * [com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationAction].
 * Otherwise, access the nullable property on the component
 * [com.android.build.gradle.internal.component.ConsumableCreationConfig.optimizationCreationConfig].
 */
interface OptimizationCreationConfig {

    val proguardFiles: ListProperty<RegularFile>

    val consumerProguardFiles: List<File>

    /**
     * Returns the component ids of those library dependencies whose keep rules are ignored when
     * building the project.
     */
    val ignoredLibraryKeepRules: Provider<Set<String>>

    /**
     * Returns whether to ignore all keep rules from external library dependencies.
     */
    val ignoreAllLibraryKeepRules: Boolean

    /**
     * Returns the external dependencies to ignore in baseline profiles.
     */
    val ignoreFromInBaselineProfile: Provider<Set<String>>

    /**
     * Returns whether to ignore all external dependencies in baseline profiles.
     */
    val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean

    val minifiedEnabled: Boolean
    val resourcesShrink: Boolean

    val postProcessingFeatures: PostprocessingFeatures?
}
