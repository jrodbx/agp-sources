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

import com.android.build.gradle.internal.ide.level2.AndroidLibraryImpl
import com.android.build.gradle.internal.ide.level2.JavaLibraryImpl
import com.android.build.gradle.internal.ide.level2.ModuleLibraryImpl
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.level2.Library
import com.android.ide.common.caching.CreatingCache
import java.io.File

/**
 * Implementation of [ArtifactHandler] for Level2 Dependency model, for sync.
 */
class Level2ArtifactHandler(
    localJarCache: CreatingCache<File, List<File>>,
    mavenCoordinatesCache: MavenCoordinatesCacheBuildService
) : ArtifactHandler<Library>(
    localJarCache,
    mavenCoordinatesCache
) {

    override fun handleAndroidLibrary(
        aarFile: File,
        folder: File,
        localJavaLibraries: List<File>,
        isProvided: Boolean,
        variantName: String?,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): Library {
        // the localJavaLibraries are full path as File, but level 2 requires String that
        // are relative to the folder.
        // This extract work is not a problem because we're going to delete L2 and move directly
        // to v2 instead so this is temporary to keep some tests working.
        val rootLen = folder.toString().length + 1
        val localJarsAsString = localJavaLibraries.asSequence().map { it.toString().substring(rootLen) }.toMutableList()

        return AndroidLibraryImpl(
            addressSupplier(),
            aarFile,
            folder,
            null, /* resStaticLibrary */
            localJarsAsString
        )
    }

    override fun handleAndroidModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        aarFile: File?,
        lintJar: File?,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): Library = ModuleLibraryImpl(
        addressSupplier(),
        buildId,
        projectPath,
        variantName
    )

    override fun handleJavaLibrary(
        jarFile: File,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): Library = JavaLibraryImpl(addressSupplier(), jarFile)

    override fun handleJavaModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        addressSupplier: () -> String
    ): Library = ModuleLibraryImpl(
        addressSupplier(),
        buildId,
        projectPath,
        variantName
    )
}
