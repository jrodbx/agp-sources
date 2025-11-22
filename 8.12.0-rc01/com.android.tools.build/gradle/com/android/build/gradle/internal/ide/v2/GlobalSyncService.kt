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

import com.android.build.gradle.internal.ide.dependencies.GraphEdgeCache
import com.android.build.gradle.internal.ide.dependencies.GraphEdgeCacheImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryCache
import com.android.build.gradle.internal.ide.dependencies.LibraryCacheImpl
import com.android.build.gradle.internal.ide.dependencies.LocalJarCache
import com.android.build.gradle.internal.ide.dependencies.LocalJarCacheImpl
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.StringCache
import com.android.build.gradle.internal.ide.dependencies.StringCacheImpl
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build Service used to cache objects used across sync of several sub-projects.
 *
 * Right now this caches:
 * - string instances
 * - content of local jar folders so that we only need to do IO once per folder.
 */
@Suppress("UnstableApiUsage")
abstract class GlobalSyncService : BuildService<GlobalSyncService.Parameters>,
    AutoCloseable {

    interface Parameters: BuildServiceParameters {
        val mavenCoordinatesCache: Property<MavenCoordinatesCacheBuildService>
    }

    class RegistrationAction(
        project: Project,
        private val mavenCoordinatesCache: Provider<MavenCoordinatesCacheBuildService>
    ) : ServiceRegistrationAction<GlobalSyncService, Parameters>(
        project,
        GlobalSyncService::class.java
    ) {
        override fun configure(parameters: Parameters) {
            parameters.mavenCoordinatesCache.set(mavenCoordinatesCache)
        }
    }

    val stringCache: StringCache
        get() = _stringCache

    val localJarCache: LocalJarCache
        get() = _localJarCache
    val libraryCache: LibraryCache
        get() = _libraryCache

    val graphEdgeCache: GraphEdgeCache
        get() = _graphEdgeCache

    private val _stringCache = StringCacheImpl()
    private val _localJarCache = LocalJarCacheImpl()
    private val _libraryCache  = LibraryCacheImpl(_stringCache, _localJarCache)
    private val _graphEdgeCache  = GraphEdgeCacheImpl()

    override fun close() {
        _stringCache.clear()
        _localJarCache.clear()
        _libraryCache.clear()
        _graphEdgeCache.clear()
    }
}

