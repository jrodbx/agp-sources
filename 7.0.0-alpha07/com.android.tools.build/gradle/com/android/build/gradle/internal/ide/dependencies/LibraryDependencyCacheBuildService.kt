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

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_AAR_LIBS
import com.android.SdkConstants.FD_JARS
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.model.AndroidProject
import com.android.builder.model.level2.DependencyGraphs
import com.android.builder.model.level2.Library
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

/** Build service used to cache library dependencies used in the model builder. */
abstract class LibraryDependencyCacheBuildService
    : BuildService<LibraryDependencyCacheBuildService.Parameters>, AutoCloseable {

    interface Parameters: BuildServiceParameters {
        val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>
    }

    val libraryCache =
        CreatingCache(CreatingCache.ValueFactory<ResolvedArtifact, Library> {
            artifactHandler.handleArtifact(
                artifact = it,
                isProvided = false, // not needed for level2
                lintJarMap = null // not needed for level2
            ).also {
                synchronized(globalLibrary) {
                    globalLibrary[it.artifactAddress] = it
                }
            }
        })

    private val globalLibrary = Maps.newHashMap<String, Library>()

    val localJarCache = CreatingCache<File, List<File>>(CreatingCache.ValueFactory {
        val localJarRoot = FileUtils.join(it, FD_JARS, FD_AAR_LIBS)

        if (!localJarRoot.isDirectory) {
            ImmutableList.of()
        } else {
            val jarFiles = localJarRoot.listFiles { _, name -> name.endsWith(DOT_JAR) }
            if (jarFiles != null && jarFiles.isNotEmpty()) {
                ImmutableList.copyOf(jarFiles)
            } else ImmutableList.of()
        }
    })

    private val artifactHandler =
        Level2ArtifactHandler(localJarCache, parameters.mavenCoordinatesCache.get().cache)

    fun getGlobalLibMap(): Map<String, Library> {
        return ImmutableMap.copyOf(globalLibrary)
    }

    fun clone(
        dependencyGraphs: DependencyGraphs,
        modelLevel: Int,
        modelWithFullDependency: Boolean
    ): DependencyGraphs {
        if (modelLevel < AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            return EmptyDependencyGraphs.EMPTY
        }

        Preconditions.checkState(dependencyGraphs is ConfigurationDependencyGraphs)
        val cdg = dependencyGraphs as ConfigurationDependencyGraphs

        // these items are already ready for serializable, all we need to clone is
        // the DependencyGraphs instance.

        val libs = cdg.libraries
        synchronized(globalLibrary) {
            for (library in libs) {
                globalLibrary[library.artifactAddress] = library
            }
        }

        val nodes = cdg.compileDependencies

        return if (modelWithFullDependency) {
            FullDependencyGraphsImpl(
                nodes, nodes, ImmutableList.of(), ImmutableList.of()
            )
        } else SimpleDependencyGraphsImpl(nodes, cdg.providedLibraries)

        // just need to register the libraries in the global libraries.
    }

    override fun close() {
        libraryCache.clear()
        globalLibrary.clear()
        localJarCache.clear()
    }

    class RegistrationAction(
        project: Project,
        private val mavenCoordinatesCache: Provider<MavenCoordinatesCacheBuildService>
    ) : ServiceRegistrationAction<LibraryDependencyCacheBuildService, Parameters>(
        project,
        LibraryDependencyCacheBuildService::class.java
    ) {
        override fun configure(parameters: Parameters) {
            parameters.mavenCoordinatesCache.set(mavenCoordinatesCache)
        }
    }
}