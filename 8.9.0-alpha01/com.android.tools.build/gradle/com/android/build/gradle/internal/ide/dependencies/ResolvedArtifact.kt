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

package com.android.build.gradle.internal.ide.dependencies

import com.android.SdkConstants.EXT_AAR
import com.android.SdkConstants.EXT_ASAR
import com.android.SdkConstants.EXT_JAR
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.internal.StringCachingService
import com.android.builder.model.MavenCoordinates
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import java.io.File
import java.util.regex.Pattern

/**
 * Artifact information relevant about the computation of the dependency model sent to the IDE.
 *
 * This is generally computed from a [ResolvedArtifactResult] (which is not usable as a [Map]
 * key) plus additional information.
 */
data class ResolvedArtifact internal constructor(
    val componentIdentifier: ComponentIdentifier,
    val variant: ResolvedVariantResult,
    val variantName: String?,
    val artifactFile: File?,
    val isTestFixturesArtifact: Boolean,
    /**
     * An optional sub-result, it can
     * - represent the bundle file, when the current result represents an exploded aar
     * - represent the asar jar, when the current result represent an asar artifact
     */
    val extractedFolder: File?,
    /** optional published lint jar */
    val publishedLintJar: File?,
    val dependencyType: DependencyType,
    val isWrappedModule: Boolean,
)  {

    constructor(
        mainArtifactResult: ResolvedArtifactResult,
        artifactFile: File?,
        extractedFolder: File?,
        publishedLintJar: File?,
        dependencyType: DependencyType,
        isWrappedModule: Boolean,
    ) :
            this(
                mainArtifactResult.id.componentIdentifier,
                mainArtifactResult.variant,
                mainArtifactResult.getVariantName(),
                artifactFile,
                mainArtifactResult.hasProjectTestFixturesCapability() ||
                        mainArtifactResult.hasLibraryTestFixturesCapability(),
                extractedFolder,
                publishedLintJar,
                dependencyType,
                isWrappedModule,
            )

    enum class DependencyType constructor(val extension: String) {
        JAVA(EXT_JAR),
        ANDROID(EXT_AAR),
        ANDROID_SANDBOX_SDK(EXT_ASAR),
        RELOCATED_ARTIFACT(""),
        // An artifact without file, but it may contain dependencies.
        NO_ARTIFACT_FILE(""),
    }

    /**
     * Computes Maven Coordinate for a given artifact result.
     */
    fun computeMavenCoordinates(
        stringCachingService: StringCachingService
    ): MavenCoordinates {
        return when (componentIdentifier) {
            is ModuleComponentIdentifier -> {
                val module = componentIdentifier.module
                val version = componentIdentifier.version
                val extension = dependencyType.extension
                var classifier: String? = null

                if (!artifactFile!!.isDirectory) {
                    // attempts to compute classifier based on the filename.
                    val pattern = "^$module-$version-(.+)\\.$extension$"

                    val p = Pattern.compile(pattern)
                    val m = p.matcher(artifactFile.name)
                    if (m.matches()) {
                        classifier = m.group(1)
                    }
                }

                MavenCoordinatesImpl.create(
                    stringCachingService = stringCachingService,
                    groupId = componentIdentifier.group,
                    artifactId = module,
                    version = version,
                    packaging = extension,
                    classifier = classifier
                )
            }

            is ProjectComponentIdentifier -> {
                MavenCoordinatesImpl.create(
                    stringCachingService = stringCachingService,
                    groupId = "artifacts",
                    artifactId = componentIdentifier.getIdString(),
                    version = "unspecified"
                )
            }

            is OpaqueComponentArtifactIdentifier, is OpaqueComponentIdentifier -> {
                MavenCoordinatesCacheBuildService.getMavenCoordForLocalFile(
                    artifactFile!!,
                    stringCachingService
                )
            }

            else -> {
                throw RuntimeException(
                    "Don't know how to compute maven coordinate for artifact '"
                            + componentIdentifier.displayName
                            + "' with component identifier of type '"
                            + componentIdentifier.javaClass
                            + "'."
                )
            }
        }
    }

    /**
     * Computes a unique address to use in the level 4 model
     */
    fun computeModelAddress(
        mavenCoordinatesCache: MavenCoordinatesCacheBuildService
    ): String = when (componentIdentifier) {
        is ProjectComponentIdentifier -> {

            StringBuilder(100)
                .append(componentIdentifier.build.buildPath)
                .append("@@")
                .append(componentIdentifier.projectPath)
                .also { sb ->
                    this.variantName?.let{ sb.append("::").append(it) }
                    if (this.isTestFixturesArtifact) {
                        sb.append("::").append("testFixtures")
                    }
                }
                .toString().intern()
        }
        is ModuleComponentIdentifier,
        is OpaqueComponentArtifactIdentifier,
        is OpaqueComponentIdentifier -> {
            (mavenCoordinatesCache.getMavenCoordinates(this).toString() +
                    if (this.isTestFixturesArtifact) {
                        "::testFixtures"
                    } else "").intern()
        }
        else -> {
            throw RuntimeException(
                "Don't know how to handle ComponentIdentifier '"
                        + componentIdentifier.displayName
                        + "'of type "
                        + componentIdentifier.javaClass)
        }
    }
}
