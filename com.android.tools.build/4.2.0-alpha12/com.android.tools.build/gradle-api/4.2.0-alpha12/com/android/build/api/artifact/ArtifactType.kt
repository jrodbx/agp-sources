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

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile

/**
 * Public [Artifact] for Android Gradle plugin.
 *
 * These are [Artifact.SingleArtifact], see [MultipleArtifactType] for multiple ones.
 *
 * All methods in the [Artifacts] class should be supported with any subclass of this
 * class.
 */
@Incubating
sealed class ArtifactType<T : FileSystemLocation>(
    kind: ArtifactKind<T>,
    category: Category = Category.INTERMEDIATES,
    private val fileSystemLocationName: FileNames? = null
)
    : Artifact.SingleArtifact<T>(kind, category) {

    override fun getFileSystemLocationName(): String {
        return fileSystemLocationName?.fileName ?: ""
    }

    /**
     * Directory where APK files will be located. Some builds can be optimized for testing when
     * invoked from Android Studio. In such cases, the APKs are not suitable for deployment to
     * Play Store.
     */
    @Incubating
    object APK:
        ArtifactType<Directory>(DIRECTORY),
        Transformable,
        Replaceable,
        ContainsMany

    /**
     * Merged manifest file that will be used in the APK, Bundle and InstantApp packages.
     * This will only be available on modules applying one of the following plugins :
     *      com.android.application
     *      com.android.dynamic-feature
     *      com.android.library
     *      com.android.test
     *
     * For each module, unit test and android test variants will not have a manifest file
     * available.
     */
    @Incubating
    object MERGED_MANIFEST:
        ArtifactType<RegularFile>(FILE, Category.INTERMEDIATES, FileNames.ANDROID_MANIFEST_XML),
        Replaceable,
        Transformable

    @Incubating
    object OBFUSCATION_MAPPING_FILE:
        ArtifactType<RegularFile>(FILE, Category.OUTPUTS, FileNames.OBFUSCATION_MAPPING_FILE) {
            override fun getFolderName(): String = "mapping"
        }

    /**
     * The final Bundle ready for consumption at Play Store.
     * This is only valid for the base module.
     */
    @Incubating
    object BUNDLE:
        ArtifactType<RegularFile>(FILE, Category.OUTPUTS),
        Transformable

    @Incubating
    object AAR:
        ArtifactType<RegularFile>(FILE),
        Transformable

    /**
     * A file containing the list of public resources exported by a library project.
     *
     * It will have one resource per line and be of the format
     * `<resource-type> <resource-name>`
     *
     * for example
     * ```
     * string public_string
     * ```
     *
     * This file will always be created, even if there are no resources.
     *
     * See [Choose resources to make public](https://developer.android.com/studio/projects/android-library.html#PrivateResources).
     */
    @Incubating
    object PUBLIC_ANDROID_RESOURCES_LIST: ArtifactType<RegularFile>(FILE)
}