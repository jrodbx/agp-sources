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
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

private const val LOCAL_AAR_GROUPID = "__local_aars__"

/** Build service used to cache maven coordinates for libraries. */
abstract class MavenCoordinatesCacheBuildService : BuildService<BuildServiceParameters.None>,
    AutoCloseable {

    private val mavenCoordinatesCache =
        CreatingCache(
            CreatingCache.ValueFactory<ResolvedArtifact, MavenCoordinates> {
                it.computeMavenCoordinates()
            })

    fun getMavenCoordForLocalFile(artifactFile: File): MavenCoordinatesImpl {
        return MavenCoordinatesImpl(LOCAL_AAR_GROUPID, artifactFile.path, "unspecified")
    }

    fun getMavenCoordinates(resolvedArtifact: ResolvedArtifact): MavenCoordinates {
        return mavenCoordinatesCache.get(resolvedArtifact)
            ?: throw RuntimeException("Failed to compute maven coordinates for $this")
    }

    override fun close() {
        mavenCoordinatesCache.clear()
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<MavenCoordinatesCacheBuildService, BuildServiceParameters.None>(
            project,
            MavenCoordinatesCacheBuildService::class.java
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}