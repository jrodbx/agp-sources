/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.artifact

import com.android.build.api.artifact.Artifact.ContainsMany
import com.android.build.api.artifact.Artifact.Replaceable
import com.android.build.api.artifact.Artifact.Transformable
import com.android.build.api.artifact.ArtifactKind.DIRECTORY
import com.android.build.api.artifact.ArtifactKind.FILE
import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

/**
 * Public [Artifact] for Android Gradle Plugin.
 *
 * These are [Artifact.SingleArtifact], see [MultipleArtifactType] for multiple ones.
 *
 * All methods in [Artifacts] should be supported with any subclass of this
 * class.
 */
@Incubating
sealed class ArtifactType<T : FileSystemLocation>(
    kind: ArtifactKind<T>,
    private val fileSystemLocationName: FileNames? = null
)
    : Artifact.SingleArtifact<T>(kind) {

    override fun getFileSystemLocationName(): String {
        return fileSystemLocationName?.fileName ?: ""
    }

    /**
     * APK directory where final APK files will be located.
     */
    @Incubating
    object APK: ArtifactType<Directory>(DIRECTORY), Transformable, Replaceable, ContainsMany

    /**
     * Merged manifest file that will be used in the APK, Bundle and InstantApp packages.
     */
    @Incubating
    object MERGED_MANIFEST: ArtifactType<RegularFile>(FILE, FileNames.ANDROID_MANIFEST_XML),
        Replaceable, Transformable

    @Incubating
    object OBFUSCATION_MAPPING_FILE: ArtifactType<RegularFile>(FILE, FileNames.OBFUSCATION_MAPPING_FILE)

    @Incubating
    object BUNDLE: ArtifactType<RegularFile>(FILE), Transformable
}