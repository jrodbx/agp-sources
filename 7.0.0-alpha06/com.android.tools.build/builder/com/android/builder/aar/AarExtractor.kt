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

package com.android.builder.aar

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.SdkConstants.FN_LINT_JAR
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.io.Files
import java.io.File
import java.util.zip.ZipInputStream

private const val LIBS_PREFIX = SdkConstants.LIBS_FOLDER + '/'
private const val LIBS_PREFIX_LENGTH = LIBS_PREFIX.length
private const val JARS_PREFIX_LENGTH = FD_JARS.length + 1

class AarExtractor {
    /**
     * [StringBuilder] used to construct all paths. It gets truncated back to [JARS_PREFIX_LENGTH]
     * on every calculation.
     */
    private val stringBuilder = StringBuilder(60).apply {
        append(FD_JARS)
        append(File.separatorChar)
    }

    private fun choosePathInOutput(entryName: String): String {
        stringBuilder.setLength(JARS_PREFIX_LENGTH)

        return when {
            entryName == FN_CLASSES_JAR || entryName == FN_LINT_JAR -> {
                stringBuilder.append(entryName)
                stringBuilder.toString()
            }
            entryName.startsWith(LIBS_PREFIX) -> {
                // In case we have libs/classes.jar we are going to rename them, due an issue in
                // Gradle.
                // TODO: stop doing this once this is fixed in gradle. b/65298222
                when (val pathWithinLibs = entryName.substring(LIBS_PREFIX_LENGTH)) {
                    FN_CLASSES_JAR -> stringBuilder.append(LIBS_PREFIX).append("classes-2$DOT_JAR")
                    FN_LINT_JAR -> stringBuilder.append(LIBS_PREFIX).append("lint-2$DOT_JAR")
                    else -> stringBuilder.append(LIBS_PREFIX).append(pathWithinLibs)
                }
                stringBuilder.toString()
            }
            else -> entryName
        }
    }

    fun extract(aar: File, outputDir: File) {
        ZipInputStream(aar.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.isDirectory || !isValidZipEntryName(entry) || entry.name.isEmpty()) {
                    continue
                }
                val path = FileUtils.toSystemDependentPath(choosePathInOutput(entry.name))
                val outputFile = File(outputDir, path)
                Files.createParentDirs(outputFile)
                Files.asByteSink(outputFile).writeFrom(zipInputStream)
            }
        }
    }
}
