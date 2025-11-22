/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.tasks.AarMetadataReader
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_AGP_VERSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_EXTENSION
import com.android.build.gradle.internal.tasks.AarMetadataTask.Companion.DEFAULT_MIN_COMPILE_SDK_VERSION
import com.android.build.gradle.internal.tasks.writeAarMetadataFile
import com.android.ide.common.repository.AgpVersion
import java.io.File
import kotlin.math.max

internal fun writeMergedMetadata(
    metadataFiles: Collection<File>,
    outputFile: File,
    overrideMinAgp: String?,
    overrideMinCompileSdk: Int?,
    overrideMinCompileSdkExt: Int?
) {
    val parsedAarsMetadata = metadataFiles.map { AarMetadataReader.load(it) }

    val mergedMetadata = object {
        var minCompileSdk: Int = DEFAULT_MIN_COMPILE_SDK_VERSION
        var minCompileSdkExtension: Int = DEFAULT_MIN_COMPILE_SDK_EXTENSION
        var minAgpVersion: String = DEFAULT_MIN_AGP_VERSION
    }
    for (metadataFile in parsedAarsMetadata) {
        val minCompileSdk = metadataFile.minCompileSdk?.toInt() ?: DEFAULT_MIN_COMPILE_SDK_VERSION
        val minSdkExtension =
            metadataFile.minCompileSdkExtension?.toInt() ?: DEFAULT_MIN_COMPILE_SDK_EXTENSION
        val minAgpVersion = metadataFile.minAgpVersion ?: DEFAULT_MIN_AGP_VERSION

        when {
            minCompileSdk > mergedMetadata.minCompileSdk -> {
                mergedMetadata.minCompileSdk = minCompileSdk
                mergedMetadata.minCompileSdkExtension = minSdkExtension
            }

            minCompileSdk == mergedMetadata.minCompileSdk -> {
                mergedMetadata.minCompileSdkExtension =
                    max(mergedMetadata.minCompileSdkExtension, minSdkExtension)
            }
        }
        mergedMetadata.minAgpVersion =
            if (AgpVersion.parse(minAgpVersion) >
                AgpVersion.parse(mergedMetadata.minAgpVersion)
            ) {
                minAgpVersion
            } else {
                mergedMetadata.minAgpVersion
            }
    }

    overrideMinAgp?.let {
        mergedMetadata.minAgpVersion = it
    }
    overrideMinCompileSdk?.let {
        mergedMetadata.minCompileSdk = it
        mergedMetadata.minCompileSdkExtension = overrideMinCompileSdkExt
            ?: DEFAULT_MIN_COMPILE_SDK_EXTENSION
    }

    writeAarMetadataFile(
            outputFile,
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            minCompileSdk = mergedMetadata.minCompileSdk,
            minAgpVersion = mergedMetadata.minAgpVersion,
            minCompileSdkExtension = mergedMetadata.minCompileSdkExtension
    )
}

internal fun copyFilesToDirRecursivelyWithOverriding(
        toCopy: Collection<File>,
        outputDirectory: File,
        relativeTo: (File) -> String = { it.name }) {
    copyFilesRecursively(toCopy, outputDirectory, true, relativeTo)
}

private fun copyFilesRecursively(
        toCopy: Collection<File>,
        outputDirectory: File,
        overrideDuplicates: Boolean,
        relativeTo: (File) -> String = { it.name }) {
    val dependencyOrderedFiles = toCopy
            // Reversed, to preserve dependency ordering, for overriding lower ordered libs.
            .reversed()
            .flatMap { it.walkBottomUp() }
            .filter { it.isFile }
    for (file in dependencyOrderedFiles) {
        val maybeRelativePath = relativeTo(file)
        val candidateFile = File(outputDirectory, maybeRelativePath)
        file.copyRecursively(candidateFile, overwrite = overrideDuplicates)
    }
}
