/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ZipEntryUtils")

package com.android.builder.utils

import java.io.File
import java.util.zip.ZipEntry

/**
 * Validates the name of a zip entry. Zip files support .. in the file name as such an attacker
 * could use this to place a file in a directory in the users root. This function returns true
 * if the entry contains ../
 */
fun isValidZipEntryName(entry: ZipEntry): Boolean {
    return !entry.name.contains("../")
}

/**
 * Helper function to validate the path inside a zipfile does not leave the output directory.
 */
fun isValidZipEntryPath(filePath: File, outputDir: File): Boolean {
    return filePath.canonicalPath.startsWith(outputDir.canonicalPath + File.separator)
}

/** Creates a new zip entry with time set to zero. */
fun zipEntry(name: String): ZipEntry = ZipEntry(name).apply { time = -1L }