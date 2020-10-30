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

/**
 * Public [ArtifactType] for Android Gradle Plugin.
 *
 * All methods in [Operations] should be supported with any subclass of this
 * class.
 */
@Incubating
sealed class PublicArtifactType<T : FileSystemLocation>(kind: ArtifactKind<T>)
    : ArtifactType<T>(kind) {
    override val isPublic: Boolean = true

    /**
     * APK directory where final APK files will be located.
     */
    @Incubating
    object APK: PublicArtifactType<Directory>(DIRECTORY), Replaceable
}