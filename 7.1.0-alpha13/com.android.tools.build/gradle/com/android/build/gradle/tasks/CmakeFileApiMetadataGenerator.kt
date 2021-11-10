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
import com.android.build.gradle.internal.cxx.cmake.parseCmakeFileApiReply
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.additionalProjectFilesIndexFile
import com.android.build.gradle.internal.cxx.model.clientQueryFolder
import com.android.build.gradle.internal.cxx.model.clientReplyFolder
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonFile
import com.android.build.gradle.internal.cxx.model.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.model.ifLogNativeConfigureToLifecycle
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationStderrFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationStdoutFile
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
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
        abi: CxxAbiModel,
        @get:Internal override val variantBuilder: GradleBuildVariant.Builder?
) : ExternalNativeJsonGenerator(abi, variantBuilder) {
    init {
        variantBuilder?.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
        cmakeMakefileChecks(abi.variant)
    }
    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {
        // Request File API responses from CMake by creating placeholder files
        // with specific query type names and versions
        abi.clientQueryFolder.mkdirs()
        join(abi.clientQueryFolder, "codemodel-v2").writeText("")
        join(abi.clientQueryFolder, "cache-v2").writeText("")
        join(abi.clientQueryFolder, "cmakeFiles-v1").writeText("")

        // Execute CMake
        createProcessOutputJunction(
          abi.metadataGenerationCommandFile,
          abi.metadataGenerationStdoutFile,
          abi.metadataGenerationStderrFile,
          getProcessBuilder(abi),
          "${abi.variant.variantName}|${abi.abi.tag} :")
          .logStderr()
          .logStdout()
          .logFullStdout(abi.variant.ifLogNativeConfigureToLifecycle { true } ?: false)
          .execute(ops::exec)

        // Build expected metadata
        val buildTargetsCommand = CmakeUtils.getBuildTargetsCommand(
            abi.variant.module.cmake!!.cmakeExe!!,
            abi.cxxBuildFolder,
            abi.getBuildCommandArguments()
        )
        parseCmakeFileApiReply(
            replyFolder = abi.clientReplyFolder,
            additionalFiles = abi.additionalProjectFilesIndexFile,
            androidGradleBuildJsonFile = abi.jsonFile,
            compileCommandsJsonFile = abi.compileCommandsJsonFile,
            compileCommandsJsonBinFile = abi.compileCommandsJsonBinFile,
            buildTargetsCommand = buildTargetsCommand
        )
    }

    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()
        builder.setExecutable(abi.variant.module.cmake!!.cmakeExe!!)
        builder.addArgs(abi.configurationArguments)
        return builder
    }

    override fun checkPrefabConfig() { }
}
