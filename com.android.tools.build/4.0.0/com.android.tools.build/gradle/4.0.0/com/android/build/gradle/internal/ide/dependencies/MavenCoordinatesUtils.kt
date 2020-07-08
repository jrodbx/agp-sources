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

import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import java.io.File

fun ResolvedArtifact.getMavenCoordinates(): MavenCoordinates {
    return mavenCoordinatesCache.get(this) ?: throw RuntimeException("Failed to compute maven coordinates for $this")
}

fun getMavenCoordForLocalFile(artifactFile: File): MavenCoordinatesImpl {
    return MavenCoordinatesImpl(LOCAL_AAR_GROUPID, artifactFile.path, "unspecified")
}


fun clearMavenCaches() {
    mavenCoordinatesCache.clear()
}

private const val LOCAL_AAR_GROUPID = "__local_aars__"

private val mavenCoordinatesCache =
    CreatingCache<ResolvedArtifact, MavenCoordinates>(
        CreatingCache.ValueFactory<ResolvedArtifact, MavenCoordinates> {
            it.computeMavenCoordinates()
        })
