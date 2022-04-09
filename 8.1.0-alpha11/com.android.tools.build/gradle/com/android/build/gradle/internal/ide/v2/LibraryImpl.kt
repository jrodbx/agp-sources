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

import com.android.builder.model.v2.ide.AndroidLibraryData
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryInfo
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectInfo
import java.io.File
import java.io.Serializable

/**
 * Implementation of [Library] for serialization via the Tooling API.
 */
@Suppress("DataClassPrivateConstructor")
data class LibraryImpl private constructor(
    override val key: String,
    override val type: LibraryType,
    override val projectInfo: ProjectInfo? = null,
    override val libraryInfo: LibraryInfo? = null,
    override val artifact: File? = null,
    override val lintJar: File?,
    override val srcJar: File?,
    override val docJar: File?,
    override val samplesJar: File?,
    override val androidLibraryData: AndroidLibraryData? = null
) : Library, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L

        fun createProjectLibrary(
            key: String,
            projectInfo: ProjectInfo,
            artifactFile: File?,
            lintJar: File?,
        ) = LibraryImpl(
            key = key,
            type = LibraryType.PROJECT,
            projectInfo = projectInfo,
            artifact = artifactFile,
            lintJar = lintJar,
            srcJar = null,
            docJar = null,
            samplesJar = null,
        )

        fun createJavaLibrary(
            key: String,
            libraryInfo: LibraryInfo,
            artifact: File,
            srcJar: File?,
            docJar: File?,
            samplesJar: File?,
        ) = LibraryImpl(
            key = key,
            type = LibraryType.JAVA_LIBRARY,
            libraryInfo = libraryInfo,
            artifact = artifact,
            lintJar = null,
            srcJar = srcJar,
            docJar = docJar,
            samplesJar = samplesJar,
        )

        fun createAndroidLibrary(
            key: String,
            libraryInfo: LibraryInfo,
            artifact: File,
            manifest: File,
            compileJarFiles: List<File>,
            runtimeJarFiles: List<File>,
            resFolder: File,
            resStaticLibrary: File,
            assetsFolder: File,
            jniFolder: File,
            aidlFolder: File,
            renderscriptFolder: File,
            proguardRules: File,
            lintJar: File?,
            srcJar: File?,
            docJar: File?,
            samplesJar: File?,
            externalAnnotations: File,
            publicResources: File,
            symbolFile: File
        ) = LibraryImpl(
            key = key,
            type = LibraryType.ANDROID_LIBRARY,
            libraryInfo = libraryInfo,
            artifact = artifact,
            lintJar = lintJar,
            srcJar = srcJar,
            docJar = docJar,
            samplesJar = samplesJar,
            androidLibraryData = AndroidLibraryDataImpl(
                manifest = manifest,
                compileJarFiles = compileJarFiles,
                runtimeJarFiles = runtimeJarFiles,
                resFolder = resFolder,
                resStaticLibrary = resStaticLibrary,
                assetsFolder = assetsFolder,
                jniFolder = jniFolder,
                aidlFolder = aidlFolder,
                renderscriptFolder = renderscriptFolder,
                proguardRules = proguardRules,
                externalAnnotations = externalAnnotations,
                publicResources = publicResources,
                symbolFile = symbolFile
            )
        )

        fun createRelocatedLibrary(
            key: String,
            libraryInfo: LibraryInfo,
        ) = LibraryImpl(
            key = key,
            type = LibraryType.RELOCATED,
            libraryInfo = libraryInfo,
            artifact = null,
            lintJar = null,
            srcJar = null,
            docJar = null,
            samplesJar = null,
        )

        fun createNoArtifactFileLibrary(
            key: String,
            libraryInfo: LibraryInfo,
        ) = LibraryImpl(
            key = key,
            type = LibraryType.NO_ARTIFACT_FILE,
            libraryInfo = libraryInfo,
            artifact = null,
            lintJar = null,
            srcJar = null,
            docJar = null,
            samplesJar = null,
        )
    }
}

private data class AndroidLibraryDataImpl(
    override val manifest: File,
    override val compileJarFiles: List<File>,
    override val runtimeJarFiles: List<File>,
    override val resFolder: File,
    override val resStaticLibrary: File,
    override val assetsFolder: File,
    override val jniFolder: File,
    override val aidlFolder: File,
    override val renderscriptFolder: File,
    override val proguardRules: File,
    override val externalAnnotations: File,
    override val publicResources: File,
    override val symbolFile: File
) : AndroidLibraryData, Serializable {
    companion object {
       @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
