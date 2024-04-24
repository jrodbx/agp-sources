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

import com.android.build.gradle.internal.cxx.hashing.shortSha256Of
import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.android.build.gradle.internal.cxx.json.writeJsonFileIfDifferent
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.name
import com.android.build.gradle.internal.cxx.model.prefabClassPath
import com.android.build.gradle.internal.cxx.model.refsFolder
import com.android.build.gradle.internal.cxx.model.prefabPackageDirectoryList
import com.android.build.gradle.internal.cxx.prefab.PREFAB_PUBLICATION_FILE
import com.android.build.gradle.internal.cxx.prefab.PayloadMapping
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.Configuration
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.HeaderOnly
import com.android.build.gradle.internal.cxx.prefab.buildPrefabPackage
import com.android.build.gradle.internal.cxx.prefab.copyAsSingleAbi
import com.android.build.gradle.internal.cxx.prefab.readPublicationFileOrNull
import com.android.build.gradle.internal.cxx.process.ExecuteProcessType.PREFAB_PROCESS
import com.android.build.gradle.internal.cxx.process.createJavaExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.executeProcess
import com.android.build.gradle.tasks.ErrorMatchType.InformationOnly
import com.android.build.gradle.tasks.ErrorMatchType.OtherError
import com.android.build.gradle.tasks.ErrorMatchType.RelevantLibraryDiscovery
import com.android.build.gradle.tasks.ErrorMatchType.RelevantLibraryError
import com.android.build.gradle.tasks.ErrorMatchType.Unrecognized
import com.android.build.gradle.tasks.PrefabCliInput.AarPackage
import com.android.build.gradle.tasks.PrefabCliInput.ModulePackage
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
import kotlin.io.path.createTempDirectory

/**
 * Invokes the Prefab CLI to generate CMake or ndk-build build system glue for connecting to
 * Prefab packages. The Prefab packages may be an unzipped AAR or they may be module references.
 *
 * In the case of module references a temporary Prefab package is generated in a temporary
 * folder. However, the resulting CMake (or ndk-build) glue will *not* have references to the
 * temporary folder. Instead, the glue will have references to the header files and libraries
 * from the originating module.
 */
fun createPrefabBuildSystemGlue(
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


   abi.createFolderLayout().use { layout ->

        val prefabPackages = getPrefabCliInputs(
            abi.name,
            layout.cliStagedInput,
            abi.variant.prefabPackageDirectoryList
        )

        // TODO: Get main class from manifest.
        val executeCommand = createJavaExecuteProcessCommand(
            classPath = prefabClassPath.path,
            main = "com.google.prefab.cli.AppKt")
            .addArgs("--build-system", buildSystem)
            .addArgs("--platform", "android")
            .addArgs("--abi", abi.name)
            .addArgs("--os-version", osVersion.toString())
            .addArgs("--stl", abi.variant.stlType)
            .addArgs("--ndk-version", abi.variant.module.ndkVersion.major.toString())
            .addArgs("--output", layout.cliStagedOutput.path)
            .addArgs(prefabPackages.map { it.packageFolder.path })

        abi.executeProcess(
            processType = PREFAB_PROCESS,
            command = executeCommand,
            ops = ops,
            processStderr = ::reportErrors
        )
        // TODO it should be possible to avoid this translation phase by implementing
        //  com.google.prefab.api.BuildSystemProvider and passing its jar to the CLI
        //  via classpath.
        translateFromStagedToFinal(
            layout.cliStagedOutput,
            layout.cliFinalOutput,
            prefabPackages.filterIsInstance<ModulePackage>().flatMap { it.payloadMappings }
        )
    }
}

const val TEMP_FOLDER_NAME_BASE_NAME = "agp-prefab-staging"

/**
 * Create the folder layout for this Prefab package generation.
 */
private fun CxxAbiModel.createFolderLayout() : FolderLayout {
    val temporaryRootFolder = createTempDirectory(TEMP_FOLDER_NAME_BASE_NAME).toFile()
    return FolderLayout(
        cliFinalOutput = prefabFolder.resolve("prefab"),
        cliStagedInput = variant.module.refsFolder,
        cliStagedOutput = temporaryRootFolder.resolve("staged-cli-output"),
        temporaryRootFolder = temporaryRootFolder
    )
}

/**
 * Directory structure for staging and invoking Prefab CLI to generate CMake and ndk-build
 * glue code.
 */
private data class FolderLayout(
    // The final location for the glue code generated by Prefab.
    val cliFinalOutput : File,
    // The staged location for glue code generated by Prefab.
    val cliStagedOutput : File,
    // The staged location for any dynamically generated Prefab packages.
    val cliStagedInput : File,
    // The root temporary folder that, when deleted, will completely clean up temporaries
    private val temporaryRootFolder : File) : AutoCloseable {
    override fun close() {
        temporaryRootFolder.deleteRecursively()
    }
}

/**
 * Abstract source for a Prefab package
 */
sealed class PrefabCliInput {
    abstract val packageFolder : File

    /**
     * A Prefab package coming from an unzipped AAR directory.
     */
    data class AarPackage(
        override val packageFolder: File) : PrefabCliInput()

    /**
     * A Prefab package coming from another module in this project.
     */
    data class ModulePackage(
        override val packageFolder: File,
        val payloadMappings: List<PayloadMapping>
    ) : PrefabCliInput()
}

private fun getPrefabCliInputs(
    abiName: String,
    cliStagedInput: File,
    realPackages: List<File>,
) : List<PrefabCliInput> {
    val modulePublications = realPackages.map { realPackage ->
        // When purely building from the command-line then only 'Configuration' will be available.
        // When purely syncing from Android Studio then only 'HeaderOnly' will be available.
        // When both are available, use 'Configuration'. It is the same as 'HeaderOnly' but it
        // also has libraru information (paths to .so files).
        Configuration.readPublicationFileOrNull(realPackage)
            ?: HeaderOnly.readPublicationFileOrNull(realPackage)
    }

    return realPackages.indices.map { i ->
        val realPackage = realPackages[i]
        val publication = modulePublications[i]
        if (publication != null) {
            val patched = publication.copyAsSingleAbi(abiName)
            // Try to create a short base path for the package. Prefab packages can have relatively
            // long subfolder structures and this file is already nested inside build/intermediates.
            // For example,
            //     app/build/intermediates/cxx/refs/lib/2u45445o/modules/foo/libs/android.arm64-v8a/libfoo.so
            // Fortunately, these packages don't contain payload so we don't need to worry about
            // the user's own include folder structures which can be arbitrarily deep.
            val sha = shortSha256Of(patched.packageInfo)
            val gradleSegment = patched.gradlePath.replace(":", "/").trim('/')
            val temporaryPackageFolder = cliStagedInput.resolve(gradleSegment).resolve(sha)
            val temporaryPackagePublicationFile = temporaryPackageFolder.resolve(PREFAB_PUBLICATION_FILE)
            writeJsonFileIfDifferent(temporaryPackagePublicationFile, patched)
            val payloadMappings = buildPrefabPackage(
                payloadIndirection = true,
                publication = patched.copy(installationFolder = temporaryPackageFolder)
            )
            ModulePackage(
                temporaryPackageFolder,
                payloadMappings
            )
        } else AarPackage(realPackage)
    }
}

/**
 * Copy files from [cliStagedOutput] to [cliFinalOutput]. Along the way, translate lines that
 * reference the configuration folder to reference the final folder when needed
 */
private fun translateFromStagedToFinal(
    cliStagedOutput : File,
    cliFinalOutput : File,
    payloadMappings : List<PayloadMapping>
) {
    for (configureFile in cliStagedOutput.walkTopDown()) {
        val relativeFile = configureFile.relativeTo(cliStagedOutput)
        val finalFile = cliFinalOutput.resolve(relativeFile)
        if (configureFile.isDirectory) {
            finalFile.mkdirs()
            continue
        }

        var body = configureFile.readText()
        for ((to, from) in payloadMappings) {
            body = body.replace(from, to)
        }
        finalFile.writeTextIfDifferent(body)
    }
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
    PassThroughRecordingLoggingEnvironment().use { logger ->
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
            "^.*Error: (invalid value for --stl: invalid choice:.*)"
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
            "Picked up _JAVA_OPTIONS: .*"
                    to informational,
            "Picked up JAVA_TOOL_OPTIONS: .*"
                    to informational,
            ".*" to ErrorMatchBehavior(Unrecognized)
        ).map { Regex(it.first) to it.second }
    }
