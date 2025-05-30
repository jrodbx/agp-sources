/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("MavenCoordinatesUtils")

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.internal.StringCachingService
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

const val LOCAL_AAR_GROUPID = "__local_aars__"
const val WRAPPED_AAR_GROUPID = "__wrapped_aars__"
const val LOCAL_ASAR_GROUPID = "__local_asars__"

/** Build service used to cache maven coordinates for libraries. */
abstract class MavenCoordinatesCacheBuildService :
    BuildService<MavenCoordinatesCacheBuildService.Parameters>, AutoCloseable {

    interface Parameters: BuildServiceParameters {
        val stringCache: Property<StringCachingBuildService>
    }

    val cache =
        CreatingCache(
            CreatingCache.ValueFactory<ResolvedArtifact, MavenCoordinates> {
                it.computeMavenCoordinates(parameters.stringCache.get())
            })

    companion object {
        @JvmStatic
        @JvmOverloads
        fun getMavenCoordForLocalFile(artifactFile: File, stringCache: StringCachingService? = null): MavenCoordinatesImpl {
            return MavenCoordinatesImpl.create(
                stringCache,
                LOCAL_AAR_GROUPID, artifactFile.path,
                "unspecified"
            )
        }
    }


    fun getMavenCoordinates(resolvedArtifact: ResolvedArtifact): MavenCoordinates {
        return cache.get(resolvedArtifact)
            ?: throw RuntimeException("Failed to compute maven coordinates for $this")
    }

    override fun close() {
        cache.clear()
    }

    class RegistrationAction(
        project: Project,
        private val stringCache: Provider<StringCachingBuildService>
    ) : ServiceRegistrationAction<MavenCoordinatesCacheBuildService, Parameters>(
        project,
        MavenCoordinatesCacheBuildService::class.java
    ) {
        override fun configure(parameters: Parameters) {
            parameters.stringCache.set(stringCache)
        }
    }
}
