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

import com.android.build.gradle.internal.ide.dependencies.LibraryService
import com.android.build.gradle.internal.ide.dependencies.LibraryServiceImpl
import com.android.build.gradle.internal.ide.dependencies.LocalJarCacheImpl
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.ide.dependencies.StringCacheImpl
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.models.GlobalLibraryMap
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build Service used to aggregate all instances of [Library], across all sub-projects, during sync.
 */
abstract class GlobalLibraryBuildService : BuildService<GlobalLibraryBuildService.Parameters>,
        AutoCloseable,
        LibraryService {

    interface Parameters: BuildServiceParameters {
        val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>
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

    internal fun createModel(): GlobalLibraryMap {
        return GlobalLibraryMapImpl(libraryService.getAllLibraries().associateBy { it.key })
    }

    override fun getLibrary(artifact: ResolvedArtifact): Library = libraryService.getLibrary(artifact)

    private val stringCache = StringCacheImpl()
    private val localJarCache = LocalJarCacheImpl()
    private val libraryService = LibraryServiceImpl(stringCache, localJarCache)

    override fun close() {
        libraryService.clear()
        stringCache.clear()
        localJarCache.clear()
    }
}

