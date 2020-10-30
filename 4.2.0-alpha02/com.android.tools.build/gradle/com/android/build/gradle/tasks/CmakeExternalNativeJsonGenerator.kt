/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.build.gradle.internal.cxx.configure.convertCmakeCommandLineArgumentsToStringList
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.settings.getFinalCmakeCommandLineArguments
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.cmakeSettingsFile
import com.android.build.gradle.internal.cxx.model.statsBuilder
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import java.io.IOException

/**
 * CMake JSON generation logic. This is separated from the corresponding CMake task so that JSON can
 * be generated during configuration.
 */
internal abstract class CmakeExternalNativeJsonGenerator(
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>
) : ExternalNativeJsonGenerator(variant, abis) {
    @JvmField
    protected val cmake: CxxCmakeModuleModel

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    fun getCmakeSettingsJson(): File? {
        val cmakeSettings = variant.module.cmakeSettingsFile
        return if (cmakeSettings.isFile) cmakeSettings else null
    }

    init {
        variant.statsBuilder.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
        this.cmake = variant.module.cmake!!

        // Check some basic requirements. This code executes at sync time but any call to
        // recordConfigurationError will later cause the generation of json to fail.
        val cmakelists = variant.module.makeFile
        if (cmakelists.isDirectory) {
            errorln(
                "Gradle project cmake.path %s is a folder. It must be CMakeLists.txt",
                cmakelists
            )
        } else if (cmakelists.isFile) {
            val filename = cmakelists.name
            if (filename != "CMakeLists.txt") {
                errorln(
                    "Gradle project cmake.path specifies %s but it must be CMakeLists.txt",
                    filename
                )
            }
        } else {
            errorln("Gradle project cmake.path is %s but that file doesn't exist", cmakelists)
        }
    }

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    @Throws(IOException::class, ProcessException::class)
    abstract fun executeProcessAndGetOutput(ops: ExecOperations, abi: CxxAbiModel): String

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
}
