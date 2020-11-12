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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.DependencyResourcesComputer
import com.android.build.gradle.internal.tasks.IncrementalTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class ResourceAwareTask : IncrementalTask() {

    @get:Internal
    protected val resourcesComputer = DependencyResourcesComputer()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getRenderscriptResOutputDir() = resourcesComputer.renderscriptResOutputDir

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getGeneratedResOutputDir() = resourcesComputer.generatedResOutputDir

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    fun getMicroApkResDirectory() = resourcesComputer.microApkResDirectory

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    fun getExtraGeneratedResFolders() = resourcesComputer.extraGeneratedResFolders

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getLibraries() = resourcesComputer.libraries?.artifactFiles

    /** Returns resources in the current sub-project. */
    @Internal // Should be annotated programmatically in sub-classes
    fun getLocalResources() = resourcesComputer.resources.values
}