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

package com.android.build.gradle.internal.ide.v2

import com.android.SdkConstants.FD_AIDL
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FD_JNI
import com.android.SdkConstants.FD_RENDERSCRIPT
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import com.android.SdkConstants.FN_API_JAR
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.FN_LINT_JAR
import com.android.SdkConstants.FN_PROGUARD_TXT
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.build.gradle.internal.ide.dependencies.ArtifactHandler
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact
import com.android.builder.model.MavenCoordinates
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.android.ide.common.caching.CreatingCache
import com.android.utils.FileUtils
import java.io.File

class ArtifactHandlerImpl(
    localJarCache: CreatingCache<File, List<File>>,
    mavenCoordinatesCache: CreatingCache<ResolvedArtifact, MavenCoordinates>
): ArtifactHandler<Library>(localJarCache, mavenCoordinatesCache) {

    override fun handleAndroidLibrary(
        aarFile: File,
        folder: File,
        localJavaLibraries: List<File>,
        isProvided: Boolean,
        variantName: String?,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): Library {
        val apiJar = FileUtils.join(folder, FN_API_JAR)
        val runtimeJar = FileUtils.join(folder, FD_JARS, FN_CLASSES_JAR)
        val runtimeJarFiles = listOf(runtimeJar) + localJavaLibraries
        return LibraryImpl(
            type = LibraryType.ANDROID_LIBRARY,
            artifactAddress = addressSupplier(),
            manifest = File(folder, FN_ANDROID_MANIFEST_XML),
            compileJarFiles = if (apiJar.isFile) listOf(apiJar) else runtimeJarFiles,
            runtimeJarFiles = runtimeJarFiles,
            resFolder = File(folder, FD_RES),
            resStaticLibrary = File(folder, FN_RESOURCE_STATIC_LIBRARY),
            assetsFolder = File(folder, FD_ASSETS),
            jniFolder = File(folder, FD_JNI),
            aidlFolder = File(folder, FD_AIDL),
            renderscriptFolder = File(folder, FD_RENDERSCRIPT),
            proguardRules = File(folder, FN_PROGUARD_TXT),
            lintJar = FileUtils.join(folder, FD_JARS, FN_LINT_JAR),
            externalAnnotations = File(folder, FN_ANNOTATIONS_ZIP),
            publicResources = File(folder, FN_PUBLIC_TXT),
            symbolFile = File(folder, FN_RESOURCE_TEXT),

            // not needed for this dependency type
            artifact = null,
            buildId = null,
            projectPath = null,
            variant = null
        )
    }

    override fun handleAndroidModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        aarFile: File,
        lintJar: File?,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): Library {
        return LibraryImpl(
            type = LibraryType.PROJECT,
            artifactAddress = addressSupplier(),
            buildId = buildId,
            projectPath = projectPath,
            variant = variantName,

            // not needed for this dependency type
            artifact = null,
            manifest = null,
            compileJarFiles = null,
            runtimeJarFiles = null,
            resFolder = null,
            resStaticLibrary = null,
            assetsFolder = null,
            jniFolder = null,
            aidlFolder = null,
            renderscriptFolder = null,
            proguardRules = null,
            externalAnnotations = null,
            publicResources = null,
            lintJar = null,
            symbolFile = null
        )
    }

    override fun handleJavaLibrary(
        jarFile: File,
        isProvided: Boolean,
        coordinatesSupplier: () -> MavenCoordinates,
        addressSupplier: () -> String
    ): Library {
        return LibraryImpl(
            type = LibraryType.JAVA_LIBRARY,
            artifactAddress = addressSupplier(),
            artifact = jarFile,

            // not needed for this dependency type
            buildId = null,
            projectPath = null,
            variant = null,
            manifest = null,
            compileJarFiles = null,
            runtimeJarFiles = null,
            resFolder = null,
            resStaticLibrary = null,
            assetsFolder = null,
            jniFolder = null,
            aidlFolder = null,
            renderscriptFolder = null,
            proguardRules = null,
            lintJar = null,
            externalAnnotations = null,
            publicResources = null,
            symbolFile = null
        )
    }

    override fun handleJavaModule(
        projectPath: String,
        buildId: String,
        variantName: String?,
        addressSupplier: () -> String
    ): Library {
        return LibraryImpl(
            type = LibraryType.PROJECT,
            artifactAddress = addressSupplier(),
            buildId = buildId,
            projectPath = projectPath,
            variant = variantName,

            // not needed for this dependency type
            artifact = null,
            manifest = null,
            compileJarFiles = null,
            runtimeJarFiles = null,
            resFolder = null,
            resStaticLibrary = null,
            assetsFolder = null,
            jniFolder = null,
            aidlFolder = null,
            renderscriptFolder = null,
            proguardRules = null,
            externalAnnotations = null,
            publicResources = null,
            lintJar = null,
            symbolFile = null
        )
    }
}
