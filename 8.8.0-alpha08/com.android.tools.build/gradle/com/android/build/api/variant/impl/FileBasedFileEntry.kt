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

package com.android.build.api.variant.impl

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Abstraction of a source file within the Variant object model.
 */
class FileBasedFileEntry(
        override val name: String,
        private val file: File,
        override val isUserAdded: Boolean = false,
        override val shouldBeAddedToIdeModel: Boolean = false,
): FileEntry {

    override val isGenerated: Boolean = false
    override fun asFile(
            projectDir: Provider<Directory>
    ): Provider<RegularFile> =
            projectDir.map {
                it.file(file.absolutePath)
            }
}
