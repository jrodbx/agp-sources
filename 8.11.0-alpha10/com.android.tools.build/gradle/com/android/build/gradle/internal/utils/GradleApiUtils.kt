/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import org.gradle.api.file.Directory
import java.io.File

/**
 * Returns all regular files in this [Directory] in stable order (even across platforms).
 *
 * Similar to [Directory.getAsFileTree], this method
 *   - returns regular files only
 *   - includes regular files in subdirectories
 *
 * Unlike [Directory.getAsFileTree], this method
 *   - ensures stable order across platforms (by sorting files based on [File.invariantSeparatorsPath]).
 *     This is the main reason we introduced this utility method.
 *     See https://github.com/gradle/gradle/issues/21379 for more context.
 *   - is not lazy (files are resolved immediately) -- we can make it lazy later if necessary
 */
fun Directory.getOrderedFileTree(): List<File> {
    return asFileTree.files.sortedBy { it.invariantSeparatorsPath }
}
