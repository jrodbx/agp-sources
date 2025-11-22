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

package com.android.build.gradle.internal.cxx.gradle.generator

import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.writeNativeBuildMiniConfigValueToJsonFile
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.ninjaBuildFile
import com.android.build.gradle.internal.cxx.model.createNinjaCommand
import com.android.build.gradle.internal.cxx.model.name
import com.android.build.gradle.internal.cxx.ninja.adaptNinjaToCxxBuild
import com.android.build.gradle.internal.cxx.process.ExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.ExecuteProcessType.CONFIGURE_PROCESS
import com.android.build.gradle.internal.cxx.process.createExecuteProcessCommand
import com.android.build.gradle.internal.cxx.process.executeProcess
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator
import com.android.build.gradle.tasks.cmakeMakefileChecks
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File

/**
 * Build with CMake and use [adaptNinjaToCxxBuild] to construct build system metadata.
 * This should work with any version of CMake that can produce a Ninja project (which is all of
 * them).
 */
internal class CMakeNinjaParserMetadataGenerator(
    abi: CxxAbiModel,
    @get:Internal override val variantBuilder: GradleBuildVariant.Builder?
) : ExternalNativeJsonGenerator(abi, variantBuilder) {
    init {
        variantBuilder?.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
        cmakeMakefileChecks(abi.variant)
    }
    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {
        // Execute CMake
        abi.executeProcess(
            processType = CONFIGURE_PROCESS,
            command = getProcessBuilder(abi),
            ops = ops
        )

        // Build expected metadata
        val config = adaptNinjaToCxxBuild(
            ninjaBuildFile = abi.ninjaBuildFile,
            abi = abi.name,
            cxxBuildFolder = abi.cxxBuildFolder,
            createNinjaCommand = abi::createNinjaCommand,
            compileCommandsJsonBin = abi.compileCommandsJsonBinFile,
            buildFileFilter = filterBuildFile(
                abi.variant.module.project.rootBuildGradleFolder,
                abi.cxxBuildFolder)
        )
        writeNativeBuildMiniConfigValueToJsonFile(abi.jsonFile, config)
        abi.additionalProjectFilesIndexFile.parentFile.mkdirs()
        abi.additionalProjectFilesIndexFile.writeText("")
        if (abi.compileCommandsJsonFile.isFile) {
            abi.compileCommandsJsonFile.delete()
        }
    }

    private fun filterBuildFile(
        rootSourceFolder : File,
        rootBuildFolder : File
    ) : (File) -> Boolean {
        // Keep only the files named CMakeLists.txt or files in this project that aren't in
        // a build output folder
        return { input ->
            input.name.equals("CMakeLists.txt", ignoreCase = true)
                    || (input.path.startsWith(rootSourceFolder.path, ignoreCase = true)
                    && !input.path.startsWith(rootBuildFolder.path, ignoreCase = true))
        }
    }

    override fun getProcessBuilder(abi: CxxAbiModel): ExecuteProcessCommand {
        return createExecuteProcessCommand(abi.variant.module.cmake!!.cmakeExe!!)
            .addArgs(abi.configurationArguments)
    }

    override fun checkPrefabConfig() { }
}
