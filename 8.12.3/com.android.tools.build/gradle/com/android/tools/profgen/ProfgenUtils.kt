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

package com.android.tools.profgen

import java.io.File
import kotlin.io.path.Path
import kotlin.system.exitProcess

fun expandWildcards(
    hrpPath: String,
    outPath: String,
    programPaths: Collection<String>,
    stdErrorDiagnostics: Diagnostics) {

    val hrpFile = Path(hrpPath).toFile()
    require(hrpFile.exists()) { "File not found: $hrpPath" }

    val outFile = Path(outPath).toFile()
    require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

    require(programPaths.isNotEmpty()) { "Must pass at least one program source" }

    val hrp = readHumanReadableProfileOrExit(hrpFile, stdErrorDiagnostics)
    val archiveClassFileResourceProviders = mutableListOf<ArchiveClassFileResourceProvider>()
    val classFileResources = mutableListOf<ClassFileResource>()
    for (programPath in programPaths) {
        if (programPath.endsWith(CLASS_EXTENSION)) {
            val separatorIndex = programPath.lastIndexOf(':')
            require(separatorIndex >= 0) {
                "Missing ':' separator for class file: $programPath"
            }
            val classBinaryName =
                programPath.substring(separatorIndex + 1).dropLast(CLASS_EXTENSION.length)
            val classDescriptor = getClassDescriptorFromBinaryName(classBinaryName)
            val programFile =
                Path(programPath.substring(0, separatorIndex), "$classBinaryName.class")
                    .toFile()
            require(programFile.exists()) { "File not found: $programFile" }
            classFileResources += ClassFileResource(classDescriptor, programFile.toPath())
        } else if (programPath.endsWith(JAR_EXTENSION)) {
            val programFile = Path(programPath).toFile()
            require(programFile.exists()) { "File not found: $programPath" }
            val archiveClassFileResourceProvider =
                ArchiveClassFileResourceProvider(programFile.toPath())
            archiveClassFileResourceProviders += archiveClassFileResourceProvider
            classFileResources += archiveClassFileResourceProvider.getClassFileResources()
        } else {
            throw IllegalArgumentException("Unexpected program file: $programPath")
        }
    }
    val result = hrp.expandWildcards(classFileResources)
    outFile.printWriter().use {
        result.printExact(it)
    }
    for (archiveClassFileResourceProvider in archiveClassFileResourceProviders) {
        try {
            archiveClassFileResourceProvider.close()
        } catch (_: Throwable) {
        }
    }
}

fun readHumanReadableProfileOrExit(
    hrpFile: File, stdErrorDiagnostics: Diagnostics): HumanReadableProfile {

    val hrp = HumanReadableProfile(hrpFile, stdErrorDiagnostics)
    if (hrp == null) {
        System.err.println("Failed to parse $hrpFile.")
        exitProcess(-1)
    }
    return hrp
}
