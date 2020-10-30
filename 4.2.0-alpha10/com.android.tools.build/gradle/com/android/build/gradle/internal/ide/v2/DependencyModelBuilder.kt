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

package com.android.build.gradle.internal.ide.v2

import com.android.build.gradle.internal.ide.dependencies.DependencyModelBuilder
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.GraphItem
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import java.io.File

class DependencyModelBuilder(
    private val globalLibraryBuilderService: GlobalLibraryBuildService
): DependencyModelBuilder<ArtifactDependencies> {

    private val compileItems = mutableListOf<GraphItem>()
    private val runtimeItems = mutableListOf<GraphItem>()

    override fun createModel(): ArtifactDependencies {
        return ArtifactDependenciesImpl(
            compileItems.toList(),
            runtimeItems.toList()
        )
    }

    override val needFullRuntimeClasspath: Boolean
        get() = true

    override val needRuntimeOnlyClasspath: Boolean
        get() = false

    override fun addArtifact(
        artifact: ResolvedArtifact,
        isProvided: Boolean,
        lintJarMap: Map<ComponentIdentifier, File>?,
        type: DependencyModelBuilder.ClasspathType
    ) {
        // create the GraphItem
        val graphItem = GraphItemImpl(
            artifactAddress = artifact.computeModelAddress(),
            requestedCoordinates = null,
            dependencies = listOf()
        )

        when (type) {
            DependencyModelBuilder.ClasspathType.COMPILE -> compileItems.add(graphItem)
            DependencyModelBuilder.ClasspathType.RUNTIME -> runtimeItems.add(graphItem)
        }

        // add the artifact to the global library instance
        globalLibraryBuilderService.addArtifact(artifact)
    }

    override fun setRuntimeOnlyClasspath(files: ImmutableList<File>) {
        throw RuntimeException("DependencyModelBuilder does not support runtimeOnlyClasspath")
    }
}