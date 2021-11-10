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

package com.android.build.gradle.internal.lint

import com.android.SdkConstants
import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.BuildMapping
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.ide.dependencies.getBuildId
import com.android.build.gradle.internal.ide.dependencies.getVariantName
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import com.android.tools.lint.model.DefaultLintModelAndroidLibrary
import com.android.tools.lint.model.DefaultLintModelJavaLibrary
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File
import java.util.Collections

/**
 * An artifact handler that makes project dependencies into [LintModelExternalLibrary]
 *
 * This means that lint does not need to parse dependency sources when checkLibrary is disabled,
 * and the lint integration need not handle local projects that are not analyzed but are needed
 * to resolve symbols.
 */
class ExternalLintModelArtifactHandler private constructor(
    private val localJarCache: CreatingCache<File, List<File>>,
    mavenCoordinatesCache: CreatingCache<ResolvedArtifact, MavenCoordinates>,
    private val projectExplodedAarsMap: Map<ProjectKey, File>,
    private val projectJarsMap: Map<ProjectKey, File>
) : ArtifactHandler<LintModelLibrary>(localJarCache, mavenCoordinatesCache) {

    private data class ProjectKey(val buildId: String, val projectPath: String, val variantName: String?)

    override fun handleAndroidLibrary(
        aarFile: File,
        folder: File,
        localJavaLibraries: List<File>,
        isProvided: Boolean,
        variantName: String?,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): LintModelLibrary =
        DefaultLintModelAndroidLibrary(
            jarFiles = listOf(
                FileUtils.join(
                    folder,
                    SdkConstants.FD_JARS,
                    SdkConstants.FN_CLASSES_JAR
                )
            ) + localJavaLibraries,
            artifactAddress = addressSupplier(),
            manifest = File(folder, SdkConstants.FN_ANDROID_MANIFEST_XML),
            folder = folder,
            resFolder = File(folder, SdkConstants.FD_RES),
            assetsFolder = File(folder, SdkConstants.FD_ASSETS),
            lintJar = File(folder, SdkConstants.FN_LINT_JAR),
            publicResources = File(folder, SdkConstants.FN_PUBLIC_TXT),
            symbolFile = File(folder, SdkConstants.FN_RESOURCE_TEXT),
            externalAnnotations = File(folder, SdkConstants.FN_ANNOTATIONS_ZIP),
            proguardRules = File(folder, SdkConstants.FN_PROGUARD_TXT),
            provided = isProvided,
            resolvedCoordinates = coordinatesSupplier().toMavenName()
        )

    override fun handleAndroidModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        aarFile: File,
        lintJar: File?,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): LintModelLibrary {
        val key = ProjectKey( buildId = buildId, projectPath = projectPath, variantName = variantName)
        val folder = projectExplodedAarsMap[key] ?: throw IllegalStateException("unable to find project exploded aar for $buildId $projectPath")
        return DefaultLintModelAndroidLibrary(
            jarFiles = listOf(
                FileUtils.join(
                    folder,
                    SdkConstants.FD_JARS,
                    SdkConstants.FN_CLASSES_JAR
                )
            ) + (localJarCache[folder] ?: listOf()),
            artifactAddress = addressSupplier(),
            manifest = File(folder, SdkConstants.FN_ANDROID_MANIFEST_XML),
            folder = folder,
            resFolder = File(folder, SdkConstants.FD_RES),
            assetsFolder = File(folder, SdkConstants.FD_ASSETS),
            lintJar = File(folder, SdkConstants.FN_LINT_JAR),
            publicResources = File(folder, SdkConstants.FN_PUBLIC_TXT),
            symbolFile = File(folder, SdkConstants.FN_RESOURCE_TEXT),
            externalAnnotations = File(folder, SdkConstants.FN_ANNOTATIONS_ZIP),
            proguardRules = File(folder, SdkConstants.FN_PROGUARD_TXT),
            provided = isProvided,
            resolvedCoordinates = coordinatesSupplier().toMavenName()
        )
    }

    override fun handleJavaLibrary(
        jarFile: File,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): LintModelLibrary =
        DefaultLintModelJavaLibrary(
            artifactAddress = addressSupplier(),
            jarFiles = listOf(jarFile),
            resolvedCoordinates = coordinatesSupplier().toMavenName(),
            provided = isProvided
        )

    override fun handleJavaModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        addressSupplier: () -> String
    ): LintModelLibrary {
        val artifactAddress = addressSupplier()
        val key = ProjectKey(buildId, projectPath, variantName)
        val jar = projectJarsMap[key] ?: error("Could not find jar for project $key")
        return DefaultLintModelJavaLibrary(
            artifactAddress = artifactAddress,
            jarFiles = listOf(jar),
            resolvedCoordinates = LintModelMavenName.NONE,
            provided = false
        )
    }

    private fun MavenCoordinates.toMavenName(): LintModelMavenName = DefaultLintModelMavenName(
        groupId,
        artifactId,
        version
    )

    companion object {
        fun create(
            localJarCache: CreatingCache<File, List<File>>,
            mavenCoordinatesCache: CreatingCache<ResolvedArtifact, MavenCoordinates>,
            projectExplodedAars: ArtifactCollection?,
            testedProjectExplodedAars: ArtifactCollection?,
            projectJars: ArtifactCollection,
            buildMapping: BuildMapping
        ) : ExternalLintModelArtifactHandler {
            var projectExplodedAarsMap =
                projectExplodedAars?.asProjectKeyedMap(buildMapping) ?: emptyMap()
            testedProjectExplodedAars?.let {
                projectExplodedAarsMap = projectExplodedAarsMap + it.asProjectKeyedMap(buildMapping)
            }
            return ExternalLintModelArtifactHandler(
                localJarCache,
                mavenCoordinatesCache,
                Collections.unmodifiableMap(projectExplodedAarsMap),
                Collections.unmodifiableMap(projectJars.asProjectKeyedMap(buildMapping))
            )

        }

        private fun ArtifactCollection.asProjectKeyedMap(buildMapping: BuildMapping): Map<ProjectKey, File> {
            return artifacts.asSequence().map { artifact ->
                val id = artifact.id.componentIdentifier as ProjectComponentIdentifier
                ProjectKey(id.getBuildId(buildMapping)!!, id.projectPath, artifact.getVariantName()) to artifact.file
            }.toMap()
        }
    }
}
