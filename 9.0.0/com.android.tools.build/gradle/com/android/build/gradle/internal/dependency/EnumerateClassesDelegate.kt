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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.builder.dexing.ClassFileInput
import com.android.utils.FileUtils
import java.io.File
import java.util.zip.ZipFile
import kotlin.streams.toList

class EnumerateClassesDelegate {
    fun run(classJar: File,
        outputFile: File
    ) {
        FileUtils.deleteIfExists(outputFile)

        val outputString = extractClasses(classJar).joinToString(separator = "\n")

        outputFile.writeText(outputString)
    }

    private fun extractClasses(jarFile: File): List<String> = ZipFile(jarFile).use { zipFile ->
        return zipFile.stream()
            .filter { ClassFileInput.CLASS_MATCHER.test(it.name) }
            .map { it.name.replace('/', '.').dropLast(SdkConstants.DOT_CLASS.length) }
            .toList()
            .sorted()
    }
}