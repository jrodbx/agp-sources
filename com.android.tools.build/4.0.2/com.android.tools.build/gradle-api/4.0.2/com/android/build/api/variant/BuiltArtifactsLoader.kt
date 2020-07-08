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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection

/**
 * Facility to load [BuiltArtifacts] instances from metadata files in the file system.
 */
@Incubating
interface BuiltArtifactsLoader {

    /**
     * Loads a metadata file from the provided [folder] and returns a new [BuiltArtifacts]
     * containing the [Collection] of [BuiltArtifact] that are present in the [folder]
     *
     * @param folder the directory abstraction that should contain built artifacts and associated
     * metadata file saved with [BuiltArtifacts.save] methods.
     */
    fun load(folder: Directory): BuiltArtifacts?

    /**
     * Loads a metadata file from the provided [FileCollection] and returns a new [BuiltArtifacts]
     * containing the [Collection] of [BuiltArtifact] that are present in the [FileCollection]
     *
     * @param fileCollection the file collection that should contain built artifacts and associated
     * metadata file saved with [BuiltArtifacts.save] methods.
     */
    fun load(fileCollection: FileCollection): BuiltArtifacts?
}