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

import com.android.build.gradle.internal.ide.DependenciesImpl
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.services.BuildServiceRegistry
import java.io.File

class Level1DependencyModelBuilder(
    buildServiceRegistry: BuildServiceRegistry
): DependencyModelBuilder<DependenciesImpl> {
    private val artifactHandler: Level1ArtifactHandler = Level1ArtifactHandler(buildServiceRegistry)

    private var runtimeClasspath = ImmutableList.of<File>()

    override fun createModel(): DependenciesImpl = DependenciesImpl(
        artifactHandler.androidLibraries,
        artifactHandler.javaLibraries,
        artifactHandler.projects,
        runtimeClasspath
    )

    override fun addArtifact(
        artifact: ResolvedArtifact,
        isProvided: Boolean,
        lintJarMap: Map<ComponentIdentifier, File>?,
        type: DependencyModelBuilder.ClasspathType
    ) {
        // there's not need to check the return value of this handler as the handler itself
        // accumulate the result.
        // This is because unlike the newer dependency model, this model accumulate the different
        // types into separate list, so it's better handler by the artifact handler.
        artifactHandler.handleArtifact(
            artifact,
            isProvided,
            lintJarMap
        )
    }

    override val needFullRuntimeClasspath: Boolean
        get() = false
    override val needRuntimeOnlyClasspath: Boolean
        get() = true

    override fun setRuntimeOnlyClasspath(files: ImmutableList<File>) {
        runtimeClasspath = files
    }
}