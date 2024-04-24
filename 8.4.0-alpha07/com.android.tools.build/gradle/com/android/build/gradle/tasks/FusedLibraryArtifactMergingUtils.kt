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

import com.android.build.api.dsl.AarMetadata
import com.android.build.gradle.internal.tasks.AarMetadataReader
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.writeAarMetadataFile
import org.gradle.internal.impldep.org.eclipse.jgit.util.FileUtils
import org.gradle.util.GradleVersion
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.max

internal fun writeMergedMetadata(metadataFiles: Collection<File>, outputFile: File) {
    val parsedAarsMetadata = metadataFiles.map { AarMetadataReader(it) }
    val mergedMetadata = object : AarMetadata {
        override var minCompileSdk: Int? = 0 // No minimum restriction.
        override var minCompileSdkExtension: Int? =
                AarMetadataTask.DEFAULT_MIN_COMPILE_SDK_EXTENSION
        override var minAgpVersion: String? =
                AarMetadataTask.DEFAULT_MIN_AGP_VERSION
    }
    for (metadataFile in parsedAarsMetadata) {
        mergedMetadata.minCompileSdk = max(
                mergedMetadata.minCompileSdk!!,
                metadataFile.minCompileSdk?.toInt()!!
        )
        mergedMetadata.minAgpVersion =
                if (GradleVersion.version(metadataFile.minAgpVersion) >
                        GradleVersion.version(mergedMetadata.minAgpVersion)
                ) {
                    metadataFile.minAgpVersion
                } else {
                    mergedMetadata.minAgpVersion
                }

        mergedMetadata.minCompileSdkExtension = max(
                mergedMetadata.minCompileSdkExtension!!,
                metadataFile.minCompileSdkExtension!!.toInt())
    }
    writeAarMetadataFile(
            outputFile,
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            minCompileSdk = mergedMetadata.minCompileSdk ?: 1,
            minAgpVersion = mergedMetadata.minAgpVersion ?: AarMetadataTask.DEFAULT_MIN_AGP_VERSION,
            minCompileSdkExtension = mergedMetadata.minCompileSdkExtension
                    ?: AarMetadataTask.DEFAULT_MIN_COMPILE_SDK_EXTENSION
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
