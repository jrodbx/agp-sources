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

package com.android.build.gradle.tasks


import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.internal.cxx.cmake.readCmakeFileApiReply
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.convertCmakeCommandLineArgumentsToStringList
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputLevel
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile
import com.android.build.gradle.internal.cxx.model.*
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.settings.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.settings.getFinalCmakeCommandLineArguments
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils.join
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations

/**
 * Invoke CMake to generate ninja project. Along the way, generate android_gradle_build.json from
 * the result of CMake file API query.
 */
internal class CmakeQueryMetadataGenerator(
        variant: CxxVariantModel,
        abis: List<CxxAbiModel>,
        @get:Internal override val variantBuilder: GradleBuildVariant.Builder
) : ExternalNativeJsonGenerator(variant, abis, variantBuilder) {
    init {
        variantBuilder.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
        cmakeMakefileChecks(variant)
    }
    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {
        val cmakeAbi = abi.cmake!!

        // Request File API responses from CMake by creating placeholder files
        // with specific query type names and versions
        cmakeAbi.clientQueryFolder.mkdirs()
        join(cmakeAbi.clientQueryFolder, "codemodel-v2").writeText("")
        join(cmakeAbi.clientQueryFolder, "cache-v2").writeText("")
        join(cmakeAbi.clientQueryFolder, "cmakeFiles-v1").writeText("")

        // Execute CMake
        createProcessOutputJunction(
                abi.metadataGenerationCommandFile,
                abi.metadataGenerationStdoutFile,
                abi.metadataGenerationStderrFile,
                getProcessBuilder(abi),
                "${variant.variantName}|${abi.abi.tag} :")
            .logStderr()
            .logStdout()
            .logFullStdout(variant.module.nativeBuildOutputLevel == NativeBuildOutputLevel.VERBOSE)
            .execute(ops::exec)

        val config = readCmakeFileApiReply(cmakeAbi.clientReplyFolder) {
            // TODO(152223150) populate compile_commands.json.bin and stop generating compile_commands.json
        }

        // Write the ninja build command, possibly with user settings from CMakeSettings.json.
        config.buildTargetsCommandComponents =
            CmakeUtils.getBuildTargetsCommand(
                    variant.module.cmake!!.cmakeExe!!,
                    abi.cxxBuildFolder,
                    abi.getBuildCommandArguments()
            )
        writeNativeBuildConfigValueToJsonFile(abi.jsonFile, config)
    }


    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()

        builder.setExecutable(variant.module.cmake!!.cmakeExe!!)
        val arguments = mutableListOf<CommandLineArgument>()
        arguments.addAll(abi.getFinalCmakeCommandLineArguments())
        builder.addArgs(arguments.convertCmakeCommandLineArgumentsToStringList())
        return builder
    }

    override fun checkPrefabConfig() { }
}
