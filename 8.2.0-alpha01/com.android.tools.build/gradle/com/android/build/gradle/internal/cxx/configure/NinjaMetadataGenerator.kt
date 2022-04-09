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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.writeNativeBuildMiniConfigValueToJsonFile
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.buildFileIndexFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.createNinjaCommand
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.ninjaBuildFile
import com.android.build.gradle.internal.cxx.model.ninjaBuildLocationFile
import com.android.build.gradle.internal.cxx.model.symbolFolderIndexFile
import com.android.build.gradle.internal.cxx.ninja.adaptNinjaToCxxBuild
import com.android.build.gradle.internal.cxx.process.ExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.ExecuteProcessType
import com.android.build.gradle.internal.cxx.process.createExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.executeProcess
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_NINJA_NOT_GENERATED
import com.android.utils.cxx.CxxDiagnosticCode.NINJA_CONFIGURE_INVALID_ARGUMENTS
import com.android.utils.cxx.CxxDiagnosticCode.NINJA_GENERIC_ERROR
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.NINJA
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File

/**
 * This is the "custom" metadata generator. It consumes build.ninja produced by a user script or
 * program.
 */
internal class NinjaMetadataGenerator(
    abi: CxxAbiModel,
    @get:Internal override val variantBuilder: GradleBuildVariant.Builder?
) : ExternalNativeJsonGenerator(abi, variantBuilder) {
    val configureScript = abi.variant.module.configureScript!!
    val variant = abi.variant

    init {
        variantBuilder?.nativeBuildSystemType = NINJA
    }
    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {

        if (!abi.configurationArguments.any { it.contains(abi.abi.tag) } ) {
            errorln(NINJA_CONFIGURE_INVALID_ARGUMENTS,
                "android.${variant}.externalNativeBuild.ninja.arguments must be " +
                        "specified and at least one argument must reference ${NDK_ABI.ref} " +
                        "[${abi.abi.tag}] " +
                        "args:[${abi.configurationArguments.joinToString(", ")}]")
            return
        }

        // Clear prior build outputs
        abi.ninjaBuildFile.delete()
        abi.ninjaBuildLocationFile.delete()
        abi.additionalProjectFilesIndexFile.delete()
        abi.symbolFolderIndexFile.delete()
        abi.buildFileIndexFile.delete()
        abi.compileCommandsJsonFile.delete()
        abi.compileCommandsJsonBinFile.delete()

        PassThroughRecordingLoggingEnvironment().use { logger ->
            // Execute tool to generate build.ninja or build.ninja.txt
            val result = abi.executeProcess(
                processType = ExecuteProcessType.CONFIGURE_PROCESS,
                command = getProcessBuilder(abi),
                ops = ops,
                processStderr = ::reportErrors,
                processStdout = ::reportErrors
            )

            // Check to make sure the tool generated build.ninja or build.ninja.txt
            if (!abi.ninjaBuildFile.isFile && !abi.ninjaBuildLocationFile.isFile) {
                if (logger.errors.isEmpty()) {
                    // No errors were recognized by reportErrors(...) so dump STDOUT and STDERR to
                    // lifecycle in the hope that there is information there the user can use to
                    // diagnose the problem.
                    result.stdout.forEachLine { lifecycleln(it) }
                    result.stderr.forEachLine { lifecycleln(it) }
                }
                errorln(
                    BUILD_NINJA_NOT_GENERATED,
                    "Expected Ninja configure script '${abi.variant.module.configureScript!!.name} " +
                            "${abi.configurationArguments.joinToString(" ")}' " +
                            "to generate '${abi.ninjaBuildFile}' or '${abi.ninjaBuildLocationFile}"
                )
                return
            }
        }

        // Build expected metadata
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = abi.ninjaBuildFile,
            abi = abi.abi.tag,
            cxxBuildFolder = abi.ninjaBuildFile.parentFile,
            createNinjaCommand = abi::createNinjaCommand,
            compileCommandsJsonBin = abi.compileCommandsJsonBinFile
        )
        writeNativeBuildMiniConfigValueToJsonFile(abi.jsonFile, config)

        // Metadata generators are expected to produce additional_project_files.txt even if it's
        // empty. This file contains a newline separated list of filenames that are known by the
        // build system and considered to be part of the project. For CMake projects, these are
        // files that CMake knows about but that don't end up in the build.ninja file. Typically,
        // these are like bitmaps or esoteric sources like Fortran.
        if (!abi.additionalProjectFilesIndexFile.isFile) {
            abi.additionalProjectFilesIndexFile.parentFile.mkdirs()
            abi.additionalProjectFilesIndexFile.writeText("")
        }
    }

    override fun getProcessBuilder(abi: CxxAbiModel): ExecuteProcessCommand {
        return createExecuteProcessCommand(configureScript)
            .copy(useScript = true)
            .addArgs(abi.configurationArguments)
    }

    override fun checkPrefabConfig() { }

    private fun reportErrors(file : File) {
        file.forEachLine { line ->
            if (isError(line)) errorln(NINJA_GENERIC_ERROR, line)
        }
    }
}

/**
 * Check whether an output line from an arbitrary user-written tool contains an error message.
 * Currently, it recognizes errors produced by MSBuild.
 */
@VisibleForTesting
fun isError(line : String) = errorMatchers.any { it.matches(line) }

private val errorMatchers = listOf(
    Regex(".*: error :.*"),
    Regex(".*: error [a-zA-Z0-9]*:.*"),
)


