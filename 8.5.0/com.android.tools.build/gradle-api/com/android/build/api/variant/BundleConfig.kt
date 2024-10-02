/*
 * Copyright (C) 2021 The Android Open Source Project
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
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/**
 * Information related to the actions creating a bundle (.aab) file for the variant.
 */
@Incubating
interface BundleConfig {

    /**
     * Settings associated with the code transparency feature in bundles.
     * Initialized from the corresponding DSL elements.
     */
    val codeTransparency: CodeTransparency

    /**
     * Add a metadata file to the bundle (.aab) file. The file will be added under the
     * BUNDLE-METADATA folder.
     *
     * @param metadataDirectory the directory below BUNDLE-METADATA where the file should be stored.
     * @param file the [Provider] of [RegularFile] that can be wired from a [org.gradle.api.Task]
     * output or an existing file in the project directory.
     */
    fun addMetadataFile(
        metadataDirectory: String,
        file: Provider<RegularFile>,
    )
}
