/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.cmake.makeCmakeMessagePathsAbsolute
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.convertCmakeCommandLineArgumentsToStringList
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.statsBuilder
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.build.gradle.internal.cxx.settings.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.settings.getFinalCmakeCommandLineArguments
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
import org.gradle.process.ExecOperations

/**
 * This strategy uses the older custom CMake (version 3.6) that directly generates the JSON file as
 * part of project configuration.
 */
internal class CmakeAndroidNinjaExternalNativeJsonGenerator(
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>
) : ExternalNativeJsonGenerator(variant, abis) {
    init {
        variant.statsBuilder.nativeBuildSystemType = CMAKE
        cmakeMakefileChecks(variant)
    }

    private val cmake get() = variant.module.cmake!!

    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel): String {
        val output = executeProcessAndGetOutput(ops, abi)
        return makeCmakeMessagePathsAbsolute(output, variant.module.makeFile.parentFile.parentFile)
    }

    override fun processBuildOutput(buildOutput: String, abiConfig: CxxAbiModel) {}

    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()

        builder.setExecutable(cmake.cmakeExe)
        val arguments = mutableListOf<CommandLineArgument>()
        arguments.addAll(abi.getFinalCmakeCommandLineArguments())
        builder.addArgs(arguments.convertCmakeCommandLineArgumentsToStringList())
        return builder
    }

    override fun checkPrefabConfig() {
        errorln("Prefab cannot be used with CMake 3.6. Use CMake 3.7 or newer.")
    }

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    private fun executeProcessAndGetOutput(ops: ExecOperations, abi: CxxAbiModel): String {
        // buildCommandArgs is set in CMake server json generation
        if(abi.getBuildCommandArguments().isNotEmpty()){
            warnln("buildCommandArgs from CMakeSettings.json is not supported for CMake version 3.6 and below.")
        }

        val logPrefix = "${variant.variantName}|${abi.abi.tag} :"
        return createProcessOutputJunction(
            abi.cxxBuildFolder,
            "android_gradle_generate_cmake_ninja_json_${abi.abi.tag}",
            getProcessBuilder(abi),
            logPrefix)
            .logStderrToInfo()
            .logStdoutToInfo()
            .executeAndReturnStdoutString(ops::exec)
    }
}
