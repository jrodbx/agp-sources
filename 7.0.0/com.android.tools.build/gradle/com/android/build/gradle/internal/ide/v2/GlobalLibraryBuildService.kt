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

import com.android.SdkConstants
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

/**
 * Build Service used to aggregate all instances of [Library], across all sub-projects, during sync.
 */
abstract class GlobalLibraryBuildService: BuildService<GlobalLibraryBuildService.Parameters>, AutoCloseable {

    interface Parameters: BuildServiceParameters {
        val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>
    }

    fun addArtifact(artifact: ResolvedArtifact) {
        libraryCache.get(artifact)
    }

    fun createModel(): GlobalLibraryMap {
        return GlobalLibraryMapImpl(libraryCache.values().associateBy { it.artifactAddress })
    }

    /**
     * The [CreatingCache] that converts [ResolvedArtifact] into [Library]
     */
    private val libraryCache =
        CreatingCache(CreatingCache.ValueFactory<ResolvedArtifact, Library> {
            artifactHandler.handleArtifact(
                artifact = it,
                isProvided = false, // not needed for v2
                lintJarMap = null // not needed for v2
            )
        })

    /**
     * a [CreatingCache] that computes, and cache the list of jars in a extracted AAR folder
     */
    private val jarFromExtractedAarCache = CreatingCache<File, List<File>> {
        val localJarRoot = FileUtils.join(it, SdkConstants.FD_JARS, SdkConstants.FD_AAR_LIBS)

        if (!localJarRoot.isDirectory) {
            ImmutableList.of()
        } else {
            val jarFiles = localJarRoot.listFiles { _, name -> name.endsWith(SdkConstants.DOT_JAR) }
            if (!jarFiles.isNullOrEmpty()) {
                // Sort by name, rather than relying on the file system iteration order
                ImmutableList.copyOf(jarFiles.sortedBy(File::getName))
            } else ImmutableList.of()
        }
    }

    private val artifactHandler = ArtifactHandlerImpl(
        jarFromExtractedAarCache,
        parameters.mavenCoordinatesCache.get().cache)

    override fun close() {
        libraryCache.clear()
        jarFromExtractedAarCache.clear()
    }

    class RegistrationAction(
        project: Project,
        private val mavenCoordinatesCache: Provider<MavenCoordinatesCacheBuildService>
    ) : ServiceRegistrationAction<GlobalLibraryBuildService, Parameters>(
        project,
        GlobalLibraryBuildService::class.java
    ) {
        override fun configure(parameters: Parameters) {
            parameters.mavenCoordinatesCache.set(mavenCoordinatesCache)
        }
    }
}
