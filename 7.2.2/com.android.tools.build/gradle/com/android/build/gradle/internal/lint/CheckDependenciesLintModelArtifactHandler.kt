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
import com.android.builder.model.MavenCoordinates
import com.android.tools.lint.model.DefaultLintModelAndroidLibrary
import com.android.tools.lint.model.DefaultLintModelJavaLibrary
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModuleLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.logging.Logging
import java.io.File

/**
 * An artifact handler that preserves the distinction between project and remote dependencies.
 *
 * This allows lint with check dependencies enabled to analyze all projects together.
 *
 * Compare with [ExternalLintModelArtifactHandler].
 *
 * Note that project dependencies on java libraries that do not have the custom lint plugin applied
 * will be treated as remote dependencies with a warning emitted that those sources will not be
 * analyzed. This will hopefully help smooth the transition to AGP 7.0 for build authors who enable
 * checkDependencies.
 */
internal class CheckDependenciesLintModelArtifactHandler(
    dependencyCaches: DependencyCaches,
    private val thisProject: ProjectKey,
    projectRuntimeLintModels: ArtifactCollection,
    projectCompileLintModels: ArtifactCollection,
    compileProjectJars: ArtifactCollection,
    runtimeProjectJars: ArtifactCollection,
    buildMapping: BuildMapping,
    private val warnIfProjectTreatedAsExternalDependency: Boolean
) : ArtifactHandler<LintModelLibrary>(
    dependencyCaches.localJarCache,
    dependencyCaches.mavenCoordinatesCache
) {

    private val projectDependencyLintModels =
        projectCompileLintModels.asProjectKeyedMap(buildMapping).keys +
                projectRuntimeLintModels.asProjectKeyedMap(buildMapping).keys
    private val compileProjectJars = compileProjectJars.asProjectSourceSetKeyedMap(buildMapping)
    private val runtimeProjectJars = runtimeProjectJars.asProjectSourceSetKeyedMap(buildMapping)

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
            identifier = addressSupplier(),
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
        isTestFixtures: Boolean,
        aarFile: File,
        lintJar: File?,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        identitySupplier: () -> String
    ): LintModelLibrary =
        DefaultLintModelModuleLibrary(
            identifier = identitySupplier(),
            projectPath = projectPath,
            lintJar = lintJar,
            provided = isProvided
        )

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
            provided = isProvided
        )

    override fun handleJavaModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        isTestFixtures: Boolean,
        identitySupplier: () -> String
    ): LintModelLibrary {
        val sourceSetKey = ProjectSourceSetKey(buildId, projectPath, variantName, isTestFixtures)
        val mainKey = ProjectKey(buildId, projectPath, variantName)
        val hasLintModel = (mainKey.buildId == thisProject.buildId && mainKey.projectPath == thisProject.projectPath) || projectDependencyLintModels.contains(mainKey)
        if (hasLintModel) {
            return DefaultLintModelModuleLibrary(
                identifier = identitySupplier(),
                projectPath = projectPath,
                lintJar = null,
                provided = false
            )
        } else {
            // Fallback for java or java-library project dependencies that do not apply the
            // standalone android lint plugin, treat them as external.
            if(warnIfProjectTreatedAsExternalDependency) {
                Logging.getLogger(CheckDependenciesLintModelArtifactHandler::class.java)
                    .warn(
                        "Warning: Lint will treat ${mainKey.projectPath} as an external dependency and not analyze it.\n" +
                                " * Recommended Action: Apply the 'com.android.lint' plugin to java library project ${mainKey.projectPath}. to enable " +
                                "lint to analyze those sources.\n"
                    )
            }
            val jar = compileProjectJars[sourceSetKey] ?: runtimeProjectJars[sourceSetKey] ?: errorJarNotFound(sourceSetKey)
            return DefaultLintModelJavaLibrary(
                identifier = identitySupplier(),
                jarFiles = listOf(jar),
                resolvedCoordinates = LintModelMavenName.NONE,
                provided = false
            )
        }
    }

    private fun errorJarNotFound(key: ProjectSourceSetKey) : Nothing {
        error("Internal errorr: Could not find jar for project $key\n" +
                "Project jars reachable from the compile classpath:" +
                compileProjectJars.keys.joinToString("\n - ", prefix="\n - ") +
                "\n\nProject jars reachable from the runtime classpath:" +
                runtimeProjectJars.keys.joinToString("\n - ", prefix="\n - ")
        )
    }

    private fun MavenCoordinates.toMavenName(): LintModelMavenName = DefaultLintModelMavenName(
        groupId,
        artifactId,
        version
    )
}

