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

import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * Exhaustive list of artifact file representations supported by the Android Gradle plugin.
 *
 * As of now, only [RegularFile] represented by [FILE] and [Directory] represented by [DIRECTORY]
 * are supported.
 */
sealed class ArtifactKind<T: FileSystemLocation>(): Serializable {
    object FILE : ArtifactKind<RegularFile>() {
        override fun dataType(): KClass<RegularFile> {
            return RegularFile::class
        }
    }

    object DIRECTORY : ArtifactKind<Directory>() {
        override fun dataType(): KClass<Directory> {
            return Directory::class
        }
    }

    /**
     * @return The data type used by Gradle to represent the file abstraction for this
     * artifact kind.
     */
    abstract fun dataType(): KClass<T>
}
