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
import com.android.SdkConstants.MAVEN_ARTIFACT_ID_PROPERTY
import com.android.SdkConstants.MAVEN_GROUP_ID_PROPERTY
import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.BuildMapping
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.build.gradle.internal.tasks.LintModelMetadataTask.Companion.LINT_MODEL_METADATA_ENTRY_PATH
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import com.android.tools.lint.model.DefaultLintModelAndroidLibrary
import com.android.tools.lint.model.DefaultLintModelJavaLibrary
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModuleLibrary
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import java.io.File
import java.util.Collections
import java.util.Properties

/**
 * An artifact handler that makes project dependencies into [LintModelExternalLibrary]
 *
 * This means that lint does not need to parse dependency sources when checkLibrary is disabled,
 * and the lint integration need not handle local projects that are not analyzed but are needed
 * to resolve symbols.
 *
 * Note: If [baseModuleModelFileMap] contains the appropriate entry, any corresponding base module
 * project dependency will be handled as a [DefaultLintModelModuleLibrary] instead of a
 * [LintModelExternalLibrary]
 */
class ExternalLintModelArtifactHandler private constructor(
    private val localJarCache: CreatingCache<File, List<File>>,
    mavenCoordinatesCache: CreatingCache<ResolvedArtifact, MavenCoordinates>,
    private val projectExplodedAarsMap: Map<ProjectKey, File>,
    private val projectJarsMap: Map<ProjectKey, File>,
    private val baseModuleModelFileMap: Map<ProjectKey, File>
) : ArtifactHandler<LintModelLibrary>(localJarCache, mavenCoordinatesCache) {

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
        val folder = projectExplodedAarsMap[key] ?: throw IllegalStateException("unable to find project exploded aar for $key")
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
            resolvedCoordinates =
                if (File(folder, LINT_MODEL_METADATA_ENTRY_PATH).isFile) {
                    val properties = Properties()
                    File(folder, LINT_MODEL_METADATA_ENTRY_PATH).inputStream().use {
                        properties.load(it)
                    }
                    DefaultLintModelMavenName(
                        groupId = properties.getProperty(MAVEN_GROUP_ID_PROPERTY),
                        artifactId = properties.getProperty(MAVEN_ARTIFACT_ID_PROPERTY),
                        version = "unspecified"
                    )
                } else {
                    coordinatesSupplier().toMavenName()
                }
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
        if (key in baseModuleModelFileMap) {
            return DefaultLintModelModuleLibrary(
                artifactAddress = addressSupplier(),
                projectPath = projectPath,
                lintJar = null,
                provided = false
            )
        }
        val jar = getProjectJar(key)
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

    private fun getProjectJar(key: ProjectKey): File {
        return projectJarsMap[key] ?: error("Could not find jar for project $key\n" +
                "${projectJarsMap.keys.size} known projects: \n" +
                projectJarsMap.entries.joinToString("\n") { "  ${it.key}=${it.value}\n" })
    }

    companion object {

        internal fun create(
            dependencyCaches: DependencyCaches,
            projectRuntimeExplodedAars: ArtifactCollection?,
            projectCompileExplodedAars: ArtifactCollection?,
            testedProjectExplodedAars: ArtifactCollection?,
            compileProjectJars: ArtifactCollection,
            runtimeProjectJars: ArtifactCollection,
            baseModuleModelFile: ArtifactCollection?,
            buildMapping: BuildMapping
        ): ExternalLintModelArtifactHandler {
            var projectExplodedAarsMap =
                projectCompileExplodedAars?.asProjectKeyedMap(buildMapping) ?: emptyMap()
            projectRuntimeExplodedAars?.let {
                projectExplodedAarsMap = projectExplodedAarsMap + it.asProjectKeyedMap(buildMapping)
            }
            testedProjectExplodedAars?.let {
                projectExplodedAarsMap = projectExplodedAarsMap + it.asProjectKeyedMap(buildMapping)
            }
            val projectJarsMap =
                compileProjectJars.asProjectKeyedMap(buildMapping) + runtimeProjectJars.asProjectKeyedMap(buildMapping)
            val baseModuleModelFileMap =
                baseModuleModelFile?.asProjectKeyedMap(buildMapping) ?: emptyMap()
            return ExternalLintModelArtifactHandler(
                dependencyCaches.localJarCache,
                dependencyCaches.mavenCoordinatesCache,
                Collections.unmodifiableMap(projectExplodedAarsMap),
                Collections.unmodifiableMap(projectJarsMap),
                Collections.unmodifiableMap(baseModuleModelFileMap)
            )

        }
    }
}
