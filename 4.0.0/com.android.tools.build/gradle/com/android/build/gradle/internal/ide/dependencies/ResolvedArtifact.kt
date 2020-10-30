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
import com.android.SdkConstants.EXT_JAR
import com.android.builder.dependency.MavenCoordinatesImpl
import com.android.builder.model.MavenCoordinates
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File
import java.util.regex.Pattern

/**
 * Artifact information relevant about the computation of the dependency model sent to the IDE.
 *
 * This is generally computed from a [ResolvedArtifactResult] (which is not usable as a [Map]
 * key) plus additional information.
 */
data class ResolvedArtifact(
    val componentIdentifier: ComponentIdentifier,
    val variantName: String?,
    val artifactFile: File,
    /**
     * An optional sub-result that represents the bundle file, when the current result
     * represents an exploded aar
     */
    val extractedFolder: File?,
    val dependencyType: DependencyType,
    val isWrappedModule: Boolean,
    val buildMapping: ImmutableMap<String, String>
)  {

    constructor(
        mainArtifactResult: ResolvedArtifactResult,
        secondaryArtifactResult: ResolvedArtifactResult?,
        dependencyType: DependencyType,
        isWrappedModule: Boolean,
        buildMapping: ImmutableMap<String, String>
    ) :
            this(
                mainArtifactResult.id.componentIdentifier,
                mainArtifactResult.getVariantName(),
                mainArtifactResult.file,
                secondaryArtifactResult?.file,
                dependencyType,
                isWrappedModule,
                buildMapping
            )

    enum class DependencyType constructor(val extension: String) {
        JAVA(EXT_JAR),
        ANDROID(EXT_AAR)
    }

    /**
     * Computes Maven Coordinate for a given artifact result.
     */
    fun computeMavenCoordinates(): MavenCoordinates {
        return when (componentIdentifier) {
            is ModuleComponentIdentifier -> {
                val module = componentIdentifier.module
                val version = componentIdentifier.version
                val extension = dependencyType.extension
                var classifier: String? = null

                if (!artifactFile.isDirectory) {
                    // attempts to compute classifier based on the filename.
                    val pattern = "^$module-$version-(.+)\\.$extension$"

                    val p = Pattern.compile(pattern)
                    val m = p.matcher(artifactFile.name)
                    if (m.matches()) {
                        classifier = m.group(1)
                    }
                }

                MavenCoordinatesImpl(componentIdentifier.group, module, version, extension, classifier)
            }

            is ProjectComponentIdentifier -> {
                MavenCoordinatesImpl("artifacts", componentIdentifier.projectPath, "unspecified")
            }

            is OpaqueComponentArtifactIdentifier -> {
                // We have a file based dependency
                if (dependencyType == DependencyType.JAVA) {
                    getMavenCoordForLocalFile(
                        artifactFile
                    )
                } else {
                    // local aar?
                    assert(artifactFile.isDirectory)
                    getMavenCoordForLocalFile(
                        artifactFile
                    )
                }
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
    fun computeModelAddress(): String = when (componentIdentifier) {
        is ProjectComponentIdentifier -> {

            StringBuilder(100)
                .append(componentIdentifier.getBuildId(buildMapping))
                .append("@@")
                .append(componentIdentifier.projectPath)
                .also { sb ->
                    this.variantName?.let{ sb.append("::").append(it) }
                }
                .toString().intern()
        }
        is ModuleComponentIdentifier, is OpaqueComponentArtifactIdentifier -> {
            this.getMavenCoordinates().toString().intern()
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
