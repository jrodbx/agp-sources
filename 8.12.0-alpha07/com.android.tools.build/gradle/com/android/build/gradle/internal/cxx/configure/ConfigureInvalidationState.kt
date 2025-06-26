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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.ConfigureType.NO_CONFIGURE
import com.android.build.gradle.internal.cxx.configure.ConfigureType.HARD_CONFIGURE
import com.android.build.gradle.internal.cxx.configure.ConfigureType.SOFT_CONFIGURE
import com.android.build.gradle.internal.cxx.configure.ChangedFile.Type.DELETED
import com.android.build.gradle.internal.cxx.configure.ChangedFile.Type.CREATED
import com.android.build.gradle.internal.cxx.configure.ChangedFile.Type.LAST_MODIFIED_CHANGED
import com.android.build.gradle.internal.cxx.configure.ChangedFile.Type.LENGTH_CHANGED
import com.android.build.gradle.internal.cxx.io.FileFingerPrint
import com.android.build.gradle.internal.cxx.io.decodeFileFingerPrint
import com.android.build.gradle.internal.cxx.io.encode
import com.android.build.gradle.internal.cxx.io.fingerPrint
import com.android.build.gradle.internal.cxx.logging.CxxStructuredLogEncoder
import com.android.build.gradle.internal.cxx.logging.bugln
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.readStructuredLog
import com.android.utils.cxx.CxxBugDiagnosticCode
import com.android.utils.cxx.CxxBugDiagnosticCode.CONFIGURE_INVALIDATION_STATE_RACE
import com.android.utils.cxx.CxxDiagnosticCode.FINGER_PRINT_FILE_CORRUPTED
import java.io.File

/**----------------------------------------------------------------------------------
 * Logic for deciding whether to reconfigure C++ or not.
 *
 * The function 'createConfigurationInvalidationState' decides whether a C/C++
 * configure is needed. It does this by checking various file sizes and timestamps.
 *
 * There are 4 kinds of files:
 *
 * 1) input files: These files are inputs to the configuration phase. When they are
 *    known, changes to them will trigger a rerun of configuration. However, not all
 *    inputs are known until after the first configure is run (This is important,
 *    and it complicates the logic).
 *
 * 2) optional output files: If these files exist, changes to them since the last
 *    configuration run will trigger a new configuration run.
 *
 * 3) required output files: same as (2), but also a new configuration run will be
 *    triggered if these files don't exist.
 *
 * 4) hard configure files: changes to these files will cause the configuration
 *    folder to be deleted and a new configuration will be triggered.
 *
 * Additionally, a support file called 'lastConfigureFingerPrintFile' is used to
 * remember the existence, size, and timestamp of files as they were after the last
 * successful configuration run.
 *
 * The logic of 'createConfigurationInvalidationState' is complicated by the fact
 * that some input files are not discovered until after the first configure
 * has executed.
 *------------------------------------------------------------------------------------*/
fun createConfigurationInvalidationState(
    forceConfigure: Boolean,
    lastConfigureFingerPrintFile: File,
    configureInputFiles: List<File>,
    requiredOutputFiles : List<File>,
    optionalOutputFiles : List<File>,
    hardConfigureFiles : List<File>
) : ConfigureInvalidationState {
    val result = ConfigureInvalidationState.newBuilder()
        .setForceConfigure(forceConfigure)
        .setFingerPrintFile(lastConfigureFingerPrintFile.path)
        .addAllInputFiles(configureInputFiles.map { it.path }.sorted())
        .addAllRequiredOutputFiles(requiredOutputFiles.map { it.path }.sorted())
        .addAllOptionalOutputFiles(optionalOutputFiles.map { it.path }.sorted())
        .addAllHardConfigureFiles(hardConfigureFiles.map { it.path }.sorted())
    val lastConfigureFingerPrint = tryReadFingerPrintFile(lastConfigureFingerPrintFile)
        ?: return result
            .setConfigureType(HARD_CONFIGURE)
            .build()

    val compareLastFingerPrintFilesToCurrent =
        lastConfigureFingerPrint.map { it to it.compareToCurrent() }
    val (unchangedFingerPrintFiles, changedFingerPrintFiles) =
        compareLastFingerPrintFilesToCurrent.partition { it.second == null }
    val changesToFingerPrintFiles = changedFingerPrintFiles.map { it.second!! }
    val changes = changesToFingerPrintFiles.associate { it.fileName to it.type }
    val unchanged = unchangedFingerPrintFiles.map { it.first.fileName }.toSet()
    val fingerprintTimestamp = lastConfigureFingerPrintFile.lastModified()

    val allFiles = (configureInputFiles + requiredOutputFiles + optionalOutputFiles + hardConfigureFiles).toSet()
    val allFilesFromLastConfigure = lastConfigureFingerPrint.map { File(it.fileName) }.toSet()
    val addedSinceLastConfigure = allFiles - allFilesFromLastConfigure
    val removedSinceLastConfigure = allFilesFromLastConfigure - allFiles
    fun computeChanged(files :List<File>, filesRequired: Boolean = true) =
        computeChangedFiles(unchanged, changes, fingerprintTimestamp, files, filesRequired)

    val changedInputFiles = computeChanged(configureInputFiles, filesRequired = false)
    val changedRequiredOutputFiles = computeChanged(requiredOutputFiles)
    val changedOptionalOutputFiles = computeChanged(optionalOutputFiles, filesRequired = false)
    val changedHardConfigureFiles = computeChanged(hardConfigureFiles)
    val softConfigureReasons = (changedInputFiles + changedRequiredOutputFiles +
            changedOptionalOutputFiles).distinct()
    val configureType = when {
        forceConfigure
                || changedHardConfigureFiles.isNotEmpty() -> HARD_CONFIGURE
        softConfigureReasons.isNotEmpty() -> SOFT_CONFIGURE
        else -> NO_CONFIGURE
    }

    // b/255965912 -- check whether any fingerprinted files were modified during this check.
    val compareLastFingerPrintFilesToCurrentAgain =
        lastConfigureFingerPrint.map { it.compareToCurrent() }
    for(i in compareLastFingerPrintFilesToCurrentAgain.indices) {
        val filename = compareLastFingerPrintFilesToCurrent[i].first
        val firstCompareToCurrent = compareLastFingerPrintFilesToCurrent[i].second
        val secondCompareToCurrent = compareLastFingerPrintFilesToCurrentAgain[i]
        if (firstCompareToCurrent != secondCompareToCurrent) {
            bugln(
                CONFIGURE_INVALIDATION_STATE_RACE,
                "File '${filename}' was modified during checks for C/C++ configuration invalidation. " +
                        "Before [${firstCompareToCurrent?.type}], after [${secondCompareToCurrent?.type}].")
            return result
                .setConfigureType(HARD_CONFIGURE)
                .build()
        }
    }

    return result
        .setFingerPrintFileExisted(true)
        .addAllAddedSinceFingerPrintsFiles(addedSinceLastConfigure.map { it.path }.sorted())
        .addAllRemovedSinceFingerPrintsFiles(removedSinceLastConfigure.map { it.path }.sorted())
        .addAllChangesToFingerPrintFiles(changesToFingerPrintFiles.sortedBy { it.fileName })
        .addAllUnchangedFingerPrintFiles(unchanged.sorted())
        .setConfigureType(configureType)
        .addAllSoftConfigureReasons(softConfigureReasons.sortedBy { it.fileName} )
        .addAllHardConfigureReasons(changedHardConfigureFiles.sortedBy { it.fileName} )
        .build()
}

/**
 * Read the fingerprint file, return null if it doesn't exist or if there are other problems
 * reading it.
 */
private fun tryReadFingerPrintFile(lastConfigureFingerPrintFile: File): List<FileFingerPrint>? {
    val lastConfigureFingerPrint =
        if (lastConfigureFingerPrintFile.isFile)
            try {
                readStructuredLog(
                    lastConfigureFingerPrintFile,
                    ::decodeFileFingerPrint
                )
            } catch (e: Exception) {
                errorln(
                    FINGER_PRINT_FILE_CORRUPTED,
                    "Could not read '$lastConfigureFingerPrintFile': ${e.message}"
                )
                null
            }
        else null
    return lastConfigureFingerPrint
}

/**
 * Compute the list of changed [files] according to the fingerprint file.
 *
 * The fingerprint file contains all files that were known the last time configure ran (even if
 * those files didn't exist on disk). If there is a file that isn't known to the fingerprint file
 * then it is a new input (for example, CMakeLists.txt changed to pull in a new dependency.cmake).
 *
 * A new file appearing this way alone doesn't mean that we should redo C++ configure, instead we
 * compare unknown files like this to the timestamp of the fingerprint file itself (which was
 * written after the last successful C++ configure).
 *
 */
private fun computeChangedFiles(
    unchanged : Set<String>,
    changes: Map<String, ChangedFile.Type>,
    // Timestamp of the fingerprint file used in the case that a file wasn't known during the last
    // C/C++ configure.
    fallbackTimestampMillis: Long,
    files: List<File>,
    filesRequired : Boolean = true) : List<ChangedFile> {
    return files.mapNotNull { file ->
        when {
            filesRequired && !file.isFile -> DELETED
            unchanged.contains(file.path) -> null
            changes.containsKey(file.path) -> changes.getValue(file.path)
            file.lastModified() >= fallbackTimestampMillis -> LAST_MODIFIED_CHANGED
            else -> null
        }?.let { type ->
            ChangedFile.newBuilder()
                .setFileName(file.path)
                .setType(type)
                .build()
        }
    }
}

/**
 * Whether configure should be executed or not.
 */
val ConfigureInvalidationState.shouldConfigure : Boolean get() =
    configureType== HARD_CONFIGURE || configureType == SOFT_CONFIGURE

/**
 * Whether it's okay to *not* delete the configuration folder before configuration is executed.
 */
val ConfigureInvalidationState.softConfigureOkay : Boolean get() =
    configureType == SOFT_CONFIGURE

/**
 * Provide a human-readable reason that the configuration ran.
 */
val ConfigureInvalidationState.shouldConfigureReasonMessages: List<String>
    get()  {
        val messages = mutableListOf<String>()
        val softRegenerateMessage =
            if (softConfigureOkay) ""
            else ", will remove stale configuration folder"

        if (forceConfigure) {
            messages += "- force flag$softRegenerateMessage"
            return messages
        }

        if (!fingerPrintFileExisted) {
            messages += "- no fingerprint file$softRegenerateMessage"
            return messages
        }

        if (hardConfigureReasonsList.isNotEmpty()) {
            messages += "- a hard configure file changed$softRegenerateMessage"
            hardConfigureReasonsList.forEach {
                messages += "  - ${it.fileName} (${it.type})"
            }
            return messages
        }

        if (softConfigureReasonsList.isNotEmpty()) {
            messages += "- a file changed$softRegenerateMessage"
            softConfigureReasonsList.forEach {
                messages += "  - ${it.fileName} (${it.type})"
            }
            return messages
        }

        return messages
    }

/**
 * Record the current state of files related to C++ configure phase in the finger print file.
 */
fun ConfigureInvalidationState.recordConfigurationFingerPrint() {
    val fingerPrints =
        (inputFilesList + requiredOutputFilesList + optionalOutputFilesList + hardConfigureFilesList)
            .distinct()
            .sorted()
            .map { File(it).fingerPrint }
    File(fingerPrintFile).delete()
    CxxStructuredLogEncoder(File(fingerPrintFile)).use { encoder ->
        fingerPrints.forEach { fingerPrint ->
            encoder.write(fingerPrint.encode(encoder))
        }
    }
}

/**
 * Check whether the file has changed and report the kind of change that occurred.
 * Returns null if there was no change.
 */
private fun FileFingerPrint.compareToCurrent() : ChangedFile? {
    val builder = ChangedFile.newBuilder()
    builder.fileName = fileName
    val file = File(fileName)
    if (isFile && !file.isFile) return builder.setType(DELETED).build()
    if (!isFile && file.isFile) return builder.setType(CREATED).build()
    if (lastModified != file.lastModified()) return builder.setType(LAST_MODIFIED_CHANGED).build()
    if (length != file.length()) return builder.setType(LENGTH_CHANGED).build()
    return null
}
