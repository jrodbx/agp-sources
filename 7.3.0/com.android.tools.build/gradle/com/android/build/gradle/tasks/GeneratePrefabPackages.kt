/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.prefabClassPath
import com.android.build.gradle.internal.cxx.model.prefabPackageConfigurationDirectoriesList
import com.android.build.gradle.internal.cxx.model.prefabPackageDirectoryList
import com.android.build.gradle.internal.cxx.prefab.PREFAB_PACKAGE_CONFIGURATION_SEGMENT
import com.android.build.gradle.internal.cxx.prefab.PREFAB_PACKAGE_SEGMENT
import com.android.build.gradle.internal.cxx.process.ExecuteProcessType.PREFAB_PROCESS
import com.android.build.gradle.internal.cxx.process.createJavaExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.executeProcess
import com.android.build.gradle.tasks.ErrorMatchType.InformationOnly
import com.android.build.gradle.tasks.ErrorMatchType.OtherError
import com.android.build.gradle.tasks.ErrorMatchType.RelevantLibraryDiscovery
import com.android.build.gradle.tasks.ErrorMatchType.RelevantLibraryError
import com.android.build.gradle.tasks.ErrorMatchType.Unrecognized
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_FATAL
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_JSON_FORMAT_PROBLEM
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_PREBUILTS_MISSING
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_MISMATCHED_SCHEMA
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_UNSUPPORTED_PLATFORM
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_DUPLICATE_MODULE_NAME
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_MISMATCHED_STL_TYPE
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_NO_LIBRARY_FOUND
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_SINGLE_STL_VIOLATION_LIBRARY_IS_SHARED_WITH_STATIC_STL
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_SINGLE_STL_VIOLATION_LIBRARY_REQUIRES_SHARED_STL
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_MISMATCHED_MIN_SDK_VERSION
import org.gradle.process.ExecOperations
import java.io.File

fun generatePrefabPackages(
    ops: ExecOperations,
    abi: CxxAbiModel) {

    val buildSystem = when (abi.variant.module.buildSystem) {
        NativeBuildSystem.NDK_BUILD -> "ndk-build"
        NativeBuildSystem.CMAKE -> "cmake"
        else -> error("${abi.variant.module.buildSystem}")
    }

    val osVersion = abi.abiPlatformVersion

    val prefabClassPath: File = abi.variant.prefabClassPath
        ?: error("CxxAbiModule.prefabClassPath cannot be null when Prefab is used")

    val configureOutput = abi.prefabFolder.resolve("prefab-configure")
    val finalOutput = abi.prefabFolder.resolve("prefab")

    // TODO: Get main class from manifest.
    val executeCommand = createJavaExecuteProcessCommand(
        classPath = prefabClassPath.path,
        main = "com.google.prefab.cli.AppKt")
        .addArgs("--build-system", buildSystem)
        .addArgs("--platform", "android")
        .addArgs("--abi", abi.abi.tag)
        .addArgs("--os-version", osVersion.toString())
        .addArgs("--stl", abi.variant.stlType)
        .addArgs("--ndk-version", abi.variant.module.ndkVersion.major.toString())
        .addArgs("--output", configureOutput.path)
        .addArgs(abi.variant.prefabConfigurationPackages.map { it.path })

    abi.executeProcess(
        processType = PREFAB_PROCESS,
        command = executeCommand,
        ops = ops,
        processStderr = ::reportErrors)
    translateFromConfigurationToFinal(configureOutput, finalOutput)
}

/**
 * Copy files from [configureOutput] to [finalOutput]. Along the way, translate lines that
 * reference the configuration folder to reference the final folder when needed
 */
private fun translateFromConfigurationToFinal(configureOutput : File, finalOutput : File) {
    for (configureFile in configureOutput.walkTopDown()) {
        val relativeFile = configureFile.relativeTo(configureOutput)
        val finalFile = finalOutput.resolve(relativeFile)
        if (configureFile.isDirectory) {
            finalFile.mkdirs()
            continue
        }
        when(relativeFile.extension) {
            "cmake" -> {
                val sb = StringBuilder()
                configureFile.forEachLine { line ->
                    sb.appendLine(
                        if (line.contains("IMPORTED_LOCATION")) {
                            toFinalPrefabPackage(line)
                        } else line
                    )
                }
                finalFile.writeText(sb.toString())
            }
            else -> configureFile.copyTo(finalFile)
        }
    }
}

/**
 * Return the list of folders that should be used to configure prefab.
 *
 * - For module-to-module references then those are from [prefabPackageConfigurationDirectoriesList].
 *
 * - For AARs those are from [prefabPackageDirectoryList] which is fully populated with .so files.
 *   AARs are defined as everything in [prefabPackageDirectoryList] which are not covered already by
 *   [prefabPackageConfigurationDirectoriesList].
 *
 */
val CxxVariantModel.prefabConfigurationPackages : List<File> get() {
    val coveredByConfigurationPackage = prefabPackageConfigurationDirectoriesList.map {
            toFinalPrefabPackage(it.path)
        }.toSet()

    val aarPackages = prefabPackageDirectoryList.filter {
        !coveredByConfigurationPackage.contains(it.path)
    }
    return prefabPackageConfigurationDirectoriesList + aarPackages
}

/**
 * If [from] contains [PREFAB_PACKAGE_CONFIGURATION_SEGMENT] then replace it with
 * [PREFAB_PACKAGE_SEGMENT].
 */
private fun toFinalPrefabPackage(from : String) : String {
    return from.replaceFirst(PREFAB_PACKAGE_CONFIGURATION_SEGMENT, PREFAB_PACKAGE_SEGMENT)
}

/*
    Handle prefab STDERR such as:
      com.google.prefab.api.NoMatchingLibraryException: No compatible library found for //lib/foo. Rejected the following libraries:
      android.arm64-v8a: User is targeting x86 but library is for arm64-v8a
      android.armeabi-v7a: User is targeting x86 but library is for armeabi-v7a
      android.x86: Library is a shared library with a statically linked STL and cannot be used with any library using the STL
      android.x86_64: User is targeting x86 but library is for x86_64

    Elevate the useful pieces of information:
    - The name of the library: //lib/foo
    - The message about STL: Library is a shared library with a statically linked STL and cannot be used with any library using the STL
    And log the rest to info.

    Anything not recognized goes to error.
*/
fun reportErrors(stderr : File) {
    if (!stderr.isFile) return
    var relevantLibrary = ""
    var errorPresent = false
    PassThroughDeduplicatingLoggingEnvironment().use { logger ->
        stderr.forEachLine { line ->
            if (logger.errors.isNotEmpty()) {
                // Everything after the first error is logged as lifecycle
                lifecycleln(line)
                return@forEachLine
            }
            if (line.isBlank()) {
                infoln(line)
                return@forEachLine
            }
            errorPresent = true
            for((regex, behavior) in errorMatchers) {
                val found = regex.find(line)
                if (found != null) {
                    when(behavior.type) {
                        RelevantLibraryDiscovery -> {
                            relevantLibrary = " [${found.destructured.component1()}]"
                            lifecycleln(line)
                        }
                        RelevantLibraryError -> {
                            val text = found.destructured.component1()
                            errorln(behavior.code!!, "$text$relevantLibrary")
                        }
                        OtherError -> {
                            relevantLibrary = ""
                            errorln(behavior.code!!, found.destructured.component1())
                        }
                        InformationOnly ->
                            infoln(line)
                        Unrecognized ->
                            error("$line$relevantLibrary")
                    }
                    break
                }
            }
        }

        // If we know an error is present, but it wasn't logged then issue a generic error.
        if (errorPresent && logger.errors.isEmpty()) {
            errorln(PREFAB_NO_LIBRARY_FOUND, "No compatible library found$relevantLibrary")
        }
    }
}

/**
 * Behavior to execute when a line is matched.
 */
private data class ErrorMatchBehavior(
    val type : ErrorMatchType,
    val code : CxxDiagnosticCode? = null
)

/**
 * Type of behavior to execute when a line is matched.
 */
private enum class ErrorMatchType {
    RelevantLibraryDiscovery,
    RelevantLibraryError,
    OtherError,
    InformationOnly,
    Unrecognized,
}

// Well-known Prefab error messages
private val errorMatchers : List<Pair<Regex, ErrorMatchBehavior>> = run {
        fun library(code : CxxDiagnosticCode) = ErrorMatchBehavior(RelevantLibraryError, code)
        fun other(code : CxxDiagnosticCode) = ErrorMatchBehavior(OtherError, code)
        val fatal = other(PREFAB_FATAL)
        val informational = ErrorMatchBehavior(InformationOnly)
        listOf(
            "^.*NoMatchingLibraryException.*No compatible library found for (.*)\\."
                    to ErrorMatchBehavior(RelevantLibraryDiscovery),
            "^.*(User requested .* but library requires .*)"
                    to library(PREFAB_MISMATCHED_STL_TYPE),
            "^.*(User has minSdkVersion .* but library was built for .*)"
                    to library(PREFAB_MISMATCHED_MIN_SDK_VERSION),
            "^.*(Library is a shared library with a statically linked STL and cannot be used with any library using the STL.*)"
                    to library(PREFAB_SINGLE_STL_VIOLATION_LIBRARY_IS_SHARED_WITH_STATIC_STL),
            "^.*(User is using a static STL but library requires a shared STL.*)"
                    to library(PREFAB_SINGLE_STL_VIOLATION_LIBRARY_REQUIRES_SHARED_STL),
            "^.*UnsupportedPlatformException: (.*)"
                    to other(PREFAB_UNSUPPORTED_PLATFORM),
            "^.*DuplicateModuleNameException: (.*)"
                    to other(PREFAB_DUPLICATE_MODULE_NAME),
            "^.*IllegalArgumentException: (Only schema_version 1 is supported\\. .* uses version .*)"
                    to other(PREFAB_MISMATCHED_SCHEMA),
            "^.*IllegalArgumentException: (schema_version must be between .* and .*. Package uses version .*)"
                    to other(PREFAB_MISMATCHED_SCHEMA),
            "^.*JsonDecodingException: (.*)"
                    to other(PREFAB_JSON_FORMAT_PROBLEM),
            "^.*RuntimeException: (Prebuilt directory does not contain .*)"
                    to other(PREFAB_PREBUILTS_MISSING),
            "^.*IllegalArgumentException: (Unknown ABI.*)"
                    to fatal,
            "^.*IllegalArgumentException: (version must be compatible with CMake, if present.*)"
                    to fatal,
            "^.*Error: (Invalid value for \".*\".*)"
                    to fatal,
            "^.*(Unknown ABI: .*)"
                    to fatal,
            "^.*(Unknown STL: .*)"
                    to fatal,
            "^.*(User is targeting .* but library is for .*)"
                    to informational,
            "^Usage: prefab.*"
                    to informational,
            ".*" to ErrorMatchBehavior(Unrecognized)
        ).map { Regex(it.first) to it.second }
    }
