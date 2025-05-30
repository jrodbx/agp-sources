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

package com.android.build.gradle.internal.ide.dependencies

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import java.io.File

/**
 * Builder for a dependency Model.
 *
 * This is passed the artifacts one by one to [addArtifact], and then the model is created with
 * [createModel]
 *
 * This handles the artifacts for both compile and runtime classpath and can create the
 * top level dependency model depending on the selected level/version.
 */
interface DependencyModelBuilder<DependencyModelT> {
    enum class ClasspathType {
        COMPILE, RUNTIME
    }

    /**
     * Returns the model
     */
    fun createModel(): DependencyModelT

    val needFullRuntimeClasspath: Boolean
    val needRuntimeOnlyClasspath: Boolean

    /**
     * Adds new artifact to the model.
     *
     * @param artifact the artifact to add
     * @param isProvided pre-computed value for whether the artifact is compileOnly. This is only
     * valid for COMPILE.
     * @param lintJarMap a map from component id to file to find the lintJar for a dependency
     * @param type which graph this artifact belongs to (compile or runtime)
     */
    fun addArtifact(
        artifact: ResolvedArtifact,
        isProvided: Boolean,
        lintJarMap: Map<ComponentIdentifier, File>?,
        type: ClasspathType
    )

    fun setRuntimeOnlyClasspath(files: ImmutableList<File>)
}
