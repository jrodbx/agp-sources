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

package com.android.build.gradle.internal.cxx.json

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_FILE_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_TARGET_COMMAND_COMPONENTS_COMMAND_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.COULD_NOT_CANONICALIZE_PATH
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_ARTIFACT_NAME_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_ABI_NAME_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_HAD_MULTIPLE_ABIS
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_ABI_NAME_IS_INVALID
import java.io.File
import java.io.IOException

/**
 * Lint [NativeBuildConfigValueMini]. Emphasis is on detecting problematic information
 * early because it may be easier to diagnose here.
 */
fun NativeBuildConfigValueMini.lint(json : File) {
    // Set up a logger that will output the name of the offending JSON file.
    PassThroughPrefixingLoggingEnvironment(file = json).use {
        for (buildFile in buildFiles) {
            if (!buildFile.isFile) {
                errorln(
                    BUILD_FILE_DID_NOT_EXIST,
                    "expected buildFiles file '$buildFile' to exist")
            }
        }

        val buildCommand = buildTargetsCommandComponents?:listOf()
        val hasTopLevelBuildCommand = buildCommand.isNotEmpty()
        if (hasTopLevelBuildCommand && !File(buildCommand[0]).isFile) {
            errorln(
                BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST,
                "expected buildTargetsCommandComponents command '${buildCommand[0]}' to exist")
        }

        for((name, library) in libraries) {
            val buildCommandComponents = library.buildCommandComponents ?: listOf()
            if (buildCommandComponents.isEmpty()) {
                if (!hasTopLevelBuildCommand) {
                    errorln(
                        BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST,
                        "expected buildTargetsCommandComponents or ${name}.buildCommandComponents to exist")
                }
            } else if (!File(buildCommandComponents[0]).isFile) {
                errorln(
                    BUILD_TARGET_COMMAND_COMPONENTS_COMMAND_DID_NOT_EXIST,
                    "expected ${name}.buildCommandComponents command '${buildCommandComponents[0]}' to exist")
            }
            val artifactName = library.artifactName ?: ""
            if (artifactName.isEmpty()) {
                errorln(
                    LIBRARY_ARTIFACT_NAME_DID_NOT_EXIST,
                    "expected ${name}.artifactName to exist")
            }
            for(runtimeFile in library.runtimeFiles) {
                // Special check for runtime files because they may later be canonicalized in order
                // to hard-link them to expected output locations.
                checkCanonicalize(
                    "$name.runtimeFiles",
                    runtimeFile)
            }
            val abiName = library.abi ?: ""
            if (abiName.isEmpty()) {
                errorln(LIBRARY_ABI_NAME_DID_NOT_EXIST, "expected ${name}.abi to exist")
            } else {
                val abi = Abi.getByName(abiName)
                if (abi == null) {
                    errorln(
                        LIBRARY_ABI_NAME_IS_INVALID,
                        "${name}.abi '$abiName' is invalid. Valid values are '${Abi.getDefaultValues().joinToString { it.tag }}'")
                }
            }
        }

        // Libraries for a single JSON should all have the same ABI
        val allAbis = libraries.values
            .mapNotNull { it.abi }
            .mapNotNull { Abi.getByName(it) }
            .mapNotNull { it.tag }
            .distinct()
        if (allAbis.size > 1) {
            errorln(LIBRARY_HAD_MULTIPLE_ABIS, "unexpected mismatched library ABIs: ${allAbis.joinToString()}")
        }
    }
}


/**
 * Canonicalize a file name. If there is an IOException from the file system then an error is
 * logged with the name of the file that caused the problem. A regular IOException doesn't always
 * contain the path of the file and that's usually what we need to diagnose a bug.
 */
private fun checkCanonicalize(
    location : String,
    file : File) {
    try {
        file.canonicalFile
    } catch (e : IOException) {
        val cause = e.cause?.let { " ($it)" } ?: ""
        errorln(
            COULD_NOT_CANONICALIZE_PATH,
            "Could not canonicalize '$file' in $location due to ${e.javaClass.simpleName}$cause")
    }
}
