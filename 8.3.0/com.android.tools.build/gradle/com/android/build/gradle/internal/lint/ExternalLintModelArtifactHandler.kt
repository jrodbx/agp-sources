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
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.builder.model.MavenCoordinates
import com.android.ide.common.caching.CreatingCache
import com.android.tools.lint.model.DefaultLintModelAndroidLibrary
import com.android.tools.lint.model.DefaultLintModelJavaLibrary
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModuleLibrary
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
    mavenCoordinatesCache: MavenCoordinatesCacheBuildService,
    private val projectExplodedAarsMap: Map<ProjectSourceSetKey, File>,
    private val projectJarsMap: Map<ProjectSourceSetKey, File>,
    private val baseModuleModelFileMap: Map<ProjectKey, File>,
    private val lintModelMetadataMap: Map<ProjectKey, File>,
    private val lintPartialResultsMap: Map<ProjectKey, File>
) : ArtifactHandler<LintModelLibrary>(localJarCache, mavenCoordinatesCache) {

    override fun handleAndroidLibrary(
        aarFile: File,
        folder: File,
        localJavaLibraries: List<File>,
        isProvided: Boolean,
        variantName: String?,
        coordinatesSupplier: () -> MavenCoordinates,
        identitySupplier: () -> String
    ): LintModelLibrary =
        DefaultLintModelAndroidLibrary(
            jarFiles = listOf(
                FileUtils.join(
                    folder,
                    SdkConstants.FD_JARS,
                    SdkConstants.FN_CLASSES_JAR
                )
            ) + localJavaLibraries,
            identifier = identitySupplier(),
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
            resolvedCoordinates = coordinatesSupplier().toMavenName(),
            partialResultsDir = null
        )

    override fun handleAndroidModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        aarFile: File?,
        lintJar: File?,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        identitySupplier: () -> String
    ): LintModelLibrary {
        val sourceSetKey = ProjectSourceSetKey(
            buildTreePath = (buildId + projectPath).replace("::", ":"), // TODO (b/298662354)
            variantName = variantName,
            isTestFixtures = isTestFixtures
        )
        val mainKey = ProjectKey(
                buildTreePath = (buildId + projectPath).replace("::", ":"), // TODO (b/298662354)
                variantName = variantName
        )
        if (mainKey in baseModuleModelFileMap || (sourceSetKey !in projectExplodedAarsMap && sourceSetKey in projectJarsMap)) {
            return DefaultLintModelModuleLibrary(
                identifier = identitySupplier(),
                projectPath = projectPath,
                lintJar = null,
                provided = false
            )
        }
        val folder = projectExplodedAarsMap[sourceSetKey] ?:
            throw IllegalStateException("unable to find project exploded aar for $sourceSetKey")


        val resolvedCoordinates: LintModelMavenName =
            lintModelMetadataMap[mainKey]?.let { file ->
                val properties = Properties()
                file.inputStream().use {
                    properties.load(it)
                }
                DefaultLintModelMavenName(
                    groupId = properties.getProperty(MAVEN_GROUP_ID_PROPERTY),
                    artifactId = properties.getProperty(MAVEN_ARTIFACT_ID_PROPERTY),
                    version = "unspecified"
                )
            } ?: coordinatesSupplier().toMavenName()
        return DefaultLintModelAndroidLibrary(
            jarFiles = listOf(
                FileUtils.join(
                    folder,
                    SdkConstants.FD_JARS,
                    SdkConstants.FN_CLASSES_JAR
                )
            ) + (localJarCache[folder] ?: listOf()),
            identifier = identitySupplier(),
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
            resolvedCoordinates = resolvedCoordinates,
            partialResultsDir = lintPartialResultsMap[mainKey]
        )
    }

    override fun handleJavaLibrary(
        jarFile: File,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        identitySupplier: () -> String
    ): LintModelLibrary =
        DefaultLintModelJavaLibrary(
            identifier = identitySupplier(),
            jarFiles = listOf(jarFile),
            resolvedCoordinates = coordinatesSupplier().toMavenName(),
            provided = isProvided,
            partialResultsDir = null
        )

    override fun handleJavaModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        identitySupplier: () -> String
    ): LintModelLibrary {
        val sourceSetKey = ProjectSourceSetKey(
            (buildId + projectPath).replace("::", ":"), // TODO (b/298662354)
            variantName,
            isTestFixtures
        )
        val mainKey = ProjectKey(
            (buildId + projectPath).replace("::", ":"), // TODO (b/298662354)
            variantName
        )
        if (mainKey in baseModuleModelFileMap) {
            return DefaultLintModelModuleLibrary(
                    identifier = identitySupplier(),
                    projectPath = projectPath,
                    lintJar = null,
                    provided = false
            )
        }
        val jar = getProjectJar(sourceSetKey)
        val resolvedCoordinates: LintModelMavenName =
            lintModelMetadataMap[mainKey]?.let { file ->
                val properties = Properties()
                file.inputStream().use {
                    properties.load(it)
                }
                DefaultLintModelMavenName(
                    groupId = properties.getProperty(MAVEN_GROUP_ID_PROPERTY),
                    artifactId = properties.getProperty(MAVEN_ARTIFACT_ID_PROPERTY),
                    version = "unspecified"
                )
            } ?: LintModelMavenName.NONE
        return DefaultLintModelJavaLibrary(
            identifier = identitySupplier(),
            jarFiles = listOf(jar),
            resolvedCoordinates = resolvedCoordinates,
            provided = false,
            partialResultsDir = lintPartialResultsMap[mainKey]
        )
    }

    private fun MavenCoordinates.toMavenName(): LintModelMavenName = DefaultLintModelMavenName(
        groupId,
        artifactId,
        version
    )

    private fun getProjectJar(key: ProjectSourceSetKey): File {
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
            compileLintModelMetadata: ArtifactCollection,
            runtimeLintModelMetadata: ArtifactCollection,
            compileLintPartialResults: ArtifactCollection?,
            runtimeLintPartialResults: ArtifactCollection?,
        ): ExternalLintModelArtifactHandler {
            var projectExplodedAarsMap =
                projectCompileExplodedAars?.asProjectSourceSetKeyedMap() ?: emptyMap()
            projectRuntimeExplodedAars?.let {
                projectExplodedAarsMap = projectExplodedAarsMap + it.asProjectSourceSetKeyedMap()
            }
            testedProjectExplodedAars?.let {
                projectExplodedAarsMap = projectExplodedAarsMap + it.asProjectSourceSetKeyedMap()
            }
            val projectJarsMap =
                compileProjectJars.asProjectSourceSetKeyedMap() + runtimeProjectJars.asProjectSourceSetKeyedMap()
            val baseModuleModelFileMap =
                baseModuleModelFile?.asProjectKeyedMap() ?: emptyMap()
            val lintModelMetadataMap =
                compileLintModelMetadata.asProjectKeyedMap() +
                        runtimeLintModelMetadata.asProjectKeyedMap()
            var lintPartialResultsMap =
                compileLintPartialResults?.asProjectKeyedMap() ?: emptyMap()
            runtimeLintPartialResults?.let {
                lintPartialResultsMap = lintPartialResultsMap + it.asProjectKeyedMap()
            }
            return ExternalLintModelArtifactHandler(
                dependencyCaches.localJarCache,
                dependencyCaches.mavenCoordinatesCache,
                Collections.unmodifiableMap(projectExplodedAarsMap),
                Collections.unmodifiableMap(projectJarsMap),
                Collections.unmodifiableMap(baseModuleModelFileMap),
                Collections.unmodifiableMap(lintModelMetadataMap),
                Collections.unmodifiableMap(lintPartialResultsMap)
            )

        }
    }
}
