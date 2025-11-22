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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.DependencyModelBuilder
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.tools.lint.model.DefaultLintModelDependencies
import com.android.tools.lint.model.DefaultLintModelDependency
import com.android.tools.lint.model.DefaultLintModelDependencyGraph
import com.android.tools.lint.model.DefaultLintModelLibraryResolver
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelModuleLibrary
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File

class LintDependencyModelBuilder(
    private val artifactHandler: ArtifactHandler<LintModelLibrary>,
    private val libraryMap: MutableMap<String, LintModelLibrary> = mutableMapOf(),
    private val mavenCoordinatesCache: MavenCoordinatesCacheBuildService
) : DependencyModelBuilder<LintModelDependencies> {

    private val libraryResolver = DefaultLintModelLibraryResolver(libraryMap)

    private val compileRoots = mutableListOf<LintModelDependency>()
    private val runtimeRoots = mutableListOf<LintModelDependency>()

    override fun createModel(): LintModelDependencies = DefaultLintModelDependencies(
        compileDependencies = DefaultLintModelDependencyGraph(
            compileRoots,
            libraryResolver
        ),
        packageDependencies = DefaultLintModelDependencyGraph(
            runtimeRoots,
            libraryResolver
        ),
        libraryResolver = libraryResolver
    )

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
        // TODO(b/198449627) Handle java libraries with external Android library dependencies.
        if ((artifact.componentIdentifier !is ProjectComponentIdentifier
                    || artifact.isWrappedModule)
            && artifact.dependencyType === ResolvedArtifact.DependencyType.ANDROID
            && artifact.extractedFolder == null) {
            return
        }

        // check if this particular artifact was created before, if not we create it and record it
        // Even though we are not yet handling full graph, and only flat list, this will happen
        // because most artifacts are in both compile and runtime
        val lintModelLibrary = libraryMap.computeIfAbsent(artifact.computeModelAddress(mavenCoordinatesCache)) {
            artifactHandler.handleArtifact(artifact, isProvided, lintJarMap)
        }

        val artifactName =
            when (lintModelLibrary) {
                is LintModelExternalLibrary ->
                    "${lintModelLibrary.resolvedCoordinates.groupId}:${lintModelLibrary.resolvedCoordinates.artifactId}"
                is LintModelModuleLibrary -> "artifacts:${lintModelLibrary.projectPath}"
                else -> throw RuntimeException("Not supported library type")
            }

        // create a graph node with no transitive dependencies (at the moment)
        val dependency = DefaultLintModelDependency(
            identifier = lintModelLibrary.identifier,
            artifactName = artifactName,
            requestedCoordinates = null, // FIXME
            dependencies = listOf(),
            libraryResolver = libraryResolver
        )

        when (type) {
            DependencyModelBuilder.ClasspathType.COMPILE -> compileRoots.add(dependency)
            DependencyModelBuilder.ClasspathType.RUNTIME -> runtimeRoots.add(dependency)
        }
    }

    override fun setRuntimeOnlyClasspath(files: ImmutableList<File>) {
        throw RuntimeException("LintModel does not support runtimeOnlyClasspath")
    }
}
