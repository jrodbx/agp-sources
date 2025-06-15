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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File

/**
 * Abstract class to handle [ResolvedArtifact] and create model class that can be used by AGP, Lint
 * or the IDe.
 *
 * The entry point is [handleArtifact], and the logic figures out whether this is a sub-project,
 * or an external library (android or java).
 * This then calls the abstract method to handle each case: [handleAndroidLibrary],
 * [handleAndroidModule], [handleJavaLibrary] and [handleJavaModule].
 */
abstract class ArtifactHandler<DependencyItemT> protected constructor(
    private val localJarCache: CreatingCache<File, List<File>>,
    private val mavenCoordinatesCache: MavenCoordinatesCacheBuildService
) {

    /**
     * Handles an artifact.
     *
     * This optionally returns the model item that represents the artifact in case something needs
     * use the return
     */
    fun handleArtifact(
        artifact: ResolvedArtifact,
        isProvided: Boolean,
        lintJarMap: Map<ComponentIdentifier, File>?
    ) : DependencyItemT {
        val id = artifact.componentIdentifier

        val coordinatesSupplier = {
            mavenCoordinatesCache.cache.get(artifact)
                ?: throw RuntimeException("unable to compute coordinates for $artifact")
        }
        val modelAddressSupplier = { artifact.computeModelAddress(mavenCoordinatesCache) }

        return if (id !is ProjectComponentIdentifier || artifact.isWrappedModule) {
            if (artifact.dependencyType === ResolvedArtifact.DependencyType.ANDROID) {
                if (artifact.extractedFolder == null) {
                    throw RuntimeException("Null extracted folder for artifact: $artifact")
                }
                val extractedFolder = artifact.extractedFolder
                    ?: throw RuntimeException("Null extracted folder for artifact: $artifact")

                handleAndroidLibrary(
                    artifact.artifactFile!!,
                    extractedFolder,
                    localJarCache.get(extractedFolder) ?: listOf(),
                    isProvided,
                    artifact.variantName,
                    coordinatesSupplier,
                    modelAddressSupplier
                )
            } else {
                handleJavaLibrary(
                    artifact.artifactFile!!,
                    isProvided,
                    coordinatesSupplier,
                    modelAddressSupplier
                )
            }
        } else {
            if (artifact.dependencyType === ResolvedArtifact.DependencyType.ANDROID) {
                val lintJar = lintJarMap?.get(id)

                handleAndroidModule(
                    id.projectPath,
                    id.build.buildPath,
                    artifact.variantName,
                    artifact.isTestFixturesArtifact,
                    artifact.artifactFile,
                    lintJar,
                    isProvided,
                    coordinatesSupplier,
                    modelAddressSupplier
                )
            } else if (artifact.dependencyType == ResolvedArtifact.DependencyType.ANDROID_SANDBOX_SDK) {
                handleJavaLibrary(
                    artifact.extractedFolder!!,
                    isProvided,
                    coordinatesSupplier,
                    modelAddressSupplier
                )
            } else {
                handleJavaModule(
                    id.projectPath,
                    id.build.buildPath,
                    artifact.variantName,
                    artifact.isTestFixturesArtifact,
                    modelAddressSupplier
                )
            }
        }
    }

    protected abstract fun handleAndroidLibrary(
        aarFile: File,
        folder: File,
        localJavaLibraries: List<File>,
        isProvided: Boolean,
        variantName: String?,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ) : DependencyItemT

    // FIXME once we get rid of v1 L1, there should be single type of module
    protected abstract fun handleAndroidModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        aarFile: File?,
        lintJar: File?,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ) : DependencyItemT

    protected abstract fun handleJavaLibrary(
        jarFile: File,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ) : DependencyItemT

    // FIXME once we get rid of v1 L1, there should be single type of module
    protected abstract fun handleJavaModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        addressSupplier: () -> String
    ) : DependencyItemT
}

private val logger = LoggerWrapper.getLogger(ArtifactHandler::class.java)
