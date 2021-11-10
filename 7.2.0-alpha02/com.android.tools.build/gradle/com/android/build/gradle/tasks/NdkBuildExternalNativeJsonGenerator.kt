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

import com.android.build.gradle.external.gnumake.NativeBuildConfigValueBuilder
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_SHORT_COMMANDS
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.LOCAL_SHORT_COMMANDS
import com.android.build.gradle.internal.cxx.configure.removeNdkBuildJobs
import com.android.build.gradle.internal.cxx.configure.toNdkBuildArguments
import com.android.build.gradle.internal.cxx.configure.toStringList
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.compileCommandsJsonBinFile
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationStderrFile
import com.android.build.gradle.internal.cxx.model.metadataGenerationStdoutFile
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.cxx.CxxDiagnosticCode.INVALID_EXTERNAL_NATIVE_BUILD_CONFIG
import com.google.common.base.Charsets
import com.google.gson.GsonBuilder
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * ndk-build JSON generation logic. This is separated from the corresponding ndk-build task so that
 * JSON can be generated during configuration.
 */
internal class NdkBuildExternalNativeJsonGenerator(
    abi: CxxAbiModel,
    variantBuilder: GradleBuildVariant.Builder?
) : ExternalNativeJsonGenerator(abi, variantBuilder) {

    /**
     * Get the process builder with -n flag. This will tell ndk-build to emit the steps that it
     * would do to execute the build.
     */
    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()
        builder.setExecutable(ndkBuild)
            .addArgs(
                abi.configurationArguments
                        + listOf(
                            // Disable any response files so we can parse the command line.
                            "$APP_SHORT_COMMANDS=false",
                            "$LOCAL_SHORT_COMMANDS=false",
                            // Clean, dry run
                            "-B", "-n"
                        )
            )
        return builder
    }

    override fun executeProcess(ops: ExecOperations, abi: CxxAbiModel) {
        createProcessOutputJunction(
            abi.metadataGenerationCommandFile,
            abi.metadataGenerationStdoutFile,
            abi.metadataGenerationStderrFile,
            getProcessBuilder(abi),
            ""
        )
            .logStderr()
            .execute(ops::exec)

        parseDryRunOutput(abi)
    }

    private fun parseDryRunOutput(abi: CxxAbiModel) {
        // Write the captured ndk-build output to a file for diagnostic purposes.
        infoln("parse and convert ndk-build output to build configuration JSON")

        // Tasks, including the Exec task used to execute ndk-build, will execute in the same folder
        // as the module build.gradle. However, parsing of ndk-build output doesn't necessarily
        // happen within a task because it may be done at sync time. The parser needs to create
        // absolute paths for source files that have relative paths in the ndk-build output. For
        // this reason, we need to tell the parser the folder of the module build.gradle. This is
        // 'projectDir'.
        //
        // Example, if a project is set up as follows:
        //
        //   project/build.gradle
        //   project/app/build.gradle
        //
        // Then, right now, the current folder is 'project/' but ndk-build -n was executed in
        // 'project/app/'. For this reason, any relative paths in the ndk-build -n output will be
        // relative to 'project/app/' but a direct call now to getAbsolutePath() would produce a
        // path relative to 'project/' which is wrong.
        //
        // NOTE: CMake doesn't have the same issue because CMake JSON generation happens fully
        // within the Exec call which has 'project/app' as the current directory.

        val buildOutput = abi.metadataGenerationStdoutFile.readText()

        // TODO(jomof): This NativeBuildConfigValue is probably consuming a lot of memory for large
        // projects. Should be changed to a streaming model where NativeBuildConfigValueBuilder
        // provides a streaming JsonReader rather than a full object.
        val commandLine = listOf(ndkBuild) + abi.configurationArguments
        val builder =
          NativeBuildConfigValueBuilder(
            makeFile,
            abi.variant.module.moduleRootFolder,
            abi.compileCommandsJsonBinFile
          )
            .setCommands(
                    commandLine,
              commandLine.removeJobsFlagIfPresent()  + listOf("clean"),
              abi.variant.variantName,
              buildOutput
            )
        builder.skipProcessingCompilerFlags = true
        val buildConfig = builder.build()
        applicationMk?.let {
            infoln("found application make file %s", it.absolutePath)
            buildConfig.buildFiles!!.add(it)
        }
        val actualResult = GsonBuilder()
          .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
          .setPrettyPrinting()
          .create()
          .toJson(buildConfig)

        // Write the captured ndk-build output to JSON file
        Files.write(
                abi.jsonFile.toPath(),
                actualResult.toByteArray(Charsets.UTF_8)
        )
    }

    /** Get the path of the ndk-build script.  */
    private val ndkBuild: String
        get() {
            var tool = "ndk-build"
            if (isWindows) {
                tool += ".cmd"
            }
            val toolFile = File(abi.variant.module.ndkFolder.path, tool)
            return try {
                // Attempt to shorten ndkFolder which may have segments of "path\.."
                // File#getAbsolutePath doesn't do this.
                toolFile.canonicalPath
            } catch (e: IOException) {
                warnln(
                    """
                Attempted to get ndkFolder canonical path and failed: %s
                Falling back to absolute path.
                """.trimIndent(),
                    e
                )
                toolFile.absolutePath
            }
        }

    /** Discovers Application.mk if one exists next to Android.mk. */
    private val applicationMk: File?
        get() = File(makeFile.parent, "Application.mk").takeIf { it.exists() }

    /**
     * If the make file is a directory then get the implied file, otherwise return the path.
     */
    private val makeFile: File
        get() = if (abi.variant.module.makeFile.isDirectory) {
            File(abi.variant.module.makeFile, "Android.mk")
        } else abi.variant.module.makeFile

    private fun List<String>.removeJobsFlagIfPresent() =
            toNdkBuildArguments().removeNdkBuildJobs().toStringList()

    init {
        variantBuilder?.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD

        // Do some basic sync time checks.
        if (abi.variant.module.makeFile.isDirectory) {
            errorln(
                INVALID_EXTERNAL_NATIVE_BUILD_CONFIG,
                "Gradle project ndkBuild.path %s is a folder. "
                        + "Only files (like Android.mk) are allowed.",
                abi.variant.module.makeFile
            )
        } else if (!abi.variant.module.makeFile.exists()) {
            errorln(
                INVALID_EXTERNAL_NATIVE_BUILD_CONFIG,
                "Gradle project ndkBuild.path is %s but that file doesn't exist",
                abi.variant.module.makeFile
            )
        }
    }
}
