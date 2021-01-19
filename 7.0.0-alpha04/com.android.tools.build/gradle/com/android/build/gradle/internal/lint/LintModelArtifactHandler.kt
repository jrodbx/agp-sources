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
import com.android.builder.model.MavenCoordinates
import com.android.tools.lint.model.DefaultLintModelAndroidLibrary
import com.android.tools.lint.model.DefaultLintModelJavaLibrary
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.DefaultLintModelModuleLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.utils.FileUtils
import java.io.File

internal class LintModelArtifactHandler(
    dependencyCaches: DependencyCaches
) : ArtifactHandler<LintModelLibrary>(
    dependencyCaches.localJarCache,
    dependencyCaches.mavenCoordinatesCache
) {

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
    ): LintModelLibrary =
        DefaultLintModelModuleLibrary(
            artifactAddress = addressSupplier(),
            projectPath = projectPath,
            lintJar = lintJar,
            provided = isProvided
        )

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
    ): LintModelLibrary =
        DefaultLintModelModuleLibrary(
            artifactAddress = addressSupplier(),
            projectPath = projectPath,
            lintJar = null,
            provided = false
        )

    private fun MavenCoordinates.toMavenName(): LintModelMavenName = DefaultLintModelMavenName(
        groupId,
        artifactId,
        version
    )
}

