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

import com.android.SdkConstants
import com.android.build.gradle.external.gnumake.NativeBuildConfigValueBuilder
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxBuildModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.soFolder
import com.android.build.gradle.internal.cxx.services.createProcessOutputJunction
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.gson.GsonBuilder
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * ndk-build JSON generation logic. This is separated from the corresponding ndk-build task so that
 * JSON can be generated during configuration.
 */
internal class NdkBuildExternalNativeJsonGenerator(
    build: CxxBuildModel,
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>,
    stats: GradleBuildVariant.Builder
) : ExternalNativeJsonGeneratorBase(build, variant, abis, stats) {
    @Throws(IOException::class)
    override fun processBuildOutput(
        buildOutput: String,
        abiConfig: CxxAbiModel
    ) {
        // Discover Application.mk if one exists next to Android.mk
        // If there is an Application.mk file next to Android.mk then pick it up.
        val applicationMk = File(makeFile.parent, "Application.mk")

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

        // TODO(jomof): This NativeBuildConfigValue is probably consuming a lot of memory for large
        // projects. Should be changed to a streaming model where NativeBuildConfigValueBuilder
        // provides a streaming JsonReader rather than a full object.
        val buildConfig = NativeBuildConfigValueBuilder(
            makeFile, variant.module.moduleRootFolder
        )
            .setCommands(
                getBuildCommand(abiConfig, applicationMk, false /* removeJobsFlag */),
                getBuildCommand(abiConfig, applicationMk, true /* removeJobsFlag */)
                        + " clean",
                variant.variantName,
                buildOutput
            )
            .build()
        if (applicationMk.exists()) {
            infoln("found application make file %s", applicationMk.absolutePath)
            buildConfig.buildFiles!!.add(applicationMk)
        }
        val actualResult = GsonBuilder()
            .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            .setPrettyPrinting()
            .create()
            .toJson(buildConfig)

        // Write the captured ndk-build output to JSON file
        Files.write(
            abiConfig.jsonFile.toPath(),
            actualResult.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Get the process builder with -n flag. This will tell ndk-build to emit the steps that it
     * would do to execute the build.
     */
    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        // Discover Application.mk if one exists next to Android.mk
        // If there is an Application.mk file next to Android.mk then pick it up.
        val applicationMk = File(makeFile.parent, "Application.mk")
        val builder = ProcessInfoBuilder()
        builder.setExecutable(ndkBuild)
            .addArgs(
                getBaseArgs(
                    abi,
                    applicationMk,
                    false /* removeJobsFlag */
                )
            ) // Disable response files so we can parse the command line.
            .addArgs("APP_SHORT_COMMANDS=false")
            .addArgs("LOCAL_SHORT_COMMANDS=false")
            .addArgs("-B") // Build as if clean
            .addArgs("-n")
        return builder
    }

    @Throws(ProcessException::class, IOException::class)
    override fun executeProcess(
        abi: CxxAbiModel,
        execOperation: (Action<in ExecSpec?>) -> ExecResult
    ): String {
        return abi.variant.module.createProcessOutputJunction(
            abi.soFolder,
            "android_gradle_generate_ndk_build_json_" + abi.abi.tag,
            getProcessBuilder(abi),
            ""
        )
            .logStderrToInfo()
            .executeAndReturnStdoutString(execOperation)
    }

    override val nativeBuildSystem: NativeBuildSystem
        get() = NativeBuildSystem.NDK_BUILD

    override fun getStlSharedObjectFiles(): Map<Abi, File> {
        return Maps.newHashMap()
    }// Attempt to shorten ndkFolder which may have segments of "path\.."
    // File#getAbsolutePath doesn't do this.

    /** Get the path of the ndk-build script.  */
    private val ndkBuild: String
        get() {
            var tool = "ndk-build"
            if (isWindows) {
                tool += ".cmd"
            }
            val toolFile = File(ndkFolder, tool)
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

    /**
     * If the make file is a directory then get the implied file, otherwise return the path.
     */
    private val makeFile: File
        get() = if (makefile.isDirectory) {
            File(makefile, "Android.mk")
        } else makefile

    /** Get the base list of arguments for invoking ndk-build.  */
    private fun getBaseArgs(
        abi: CxxAbiModel, applicationMk: File, removeJobsFlag: Boolean
    ): List<String?> {
        val result: MutableList<String?> =
            Lists.newArrayList()
        result.add("NDK_PROJECT_PATH=null")
        result.add("APP_BUILD_SCRIPT=$makeFile")
        if (applicationMk.exists()) {
            // NDK_APPLICATION_MK specifies the Application.mk file.
            result.add("NDK_APPLICATION_MK=" + applicationMk.absolutePath)
        }
        if (abi.variant.prefabPackageDirectoryList.isNotEmpty()) {
            if (abi.variant.module.ndkVersion.major < 21) {
                // These cannot be automatically imported prior to NDK r21 which started handling
                // NDK_GRADLE_INJECTED_IMPORT_PATH, but the user can add that search path explicitly
                // for older releases.
                // TODO(danalbert): Include a link to the docs page when it is published.
                // This can be worked around on older NDKs, but it's too verbose to include in the
                // warning message.
                warnln("Prefab packages cannot be automatically imported until NDK r21.")
            }
            result.add("NDK_GRADLE_INJECTED_IMPORT_PATH=" + abi.prefabFolder.toString())
        }

        // APP_ABI and NDK_ALL_ABIS work together. APP_ABI is the specific ABI for this build.
        // NDK_ALL_ABIS is the universe of all ABIs for this build. NDK_ALL_ABIS is set to just the
        // current ABI. If we don't do this, then ndk-build will erase build artifacts for all abis
        // aside from the current.
        result.add("APP_ABI=" + abi.abi.tag)
        result.add("NDK_ALL_ABIS=" + abi.abi.tag)
        if (isDebuggable) {
            result.add("NDK_DEBUG=1")
        } else {
            result.add("NDK_DEBUG=0")
        }
        result.add("APP_PLATFORM=android-" + abi.abiPlatformVersion)

        // getObjFolder is set to the "local" subfolder in the user specified directory, therefore,
        // NDK_OUT should be set to getObjFolder().getParent() instead of getObjFolder().
        var ndkOut = File(objFolder).parent
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            // Due to b.android.com/219225, NDK_OUT on Windows requires forward slashes.
            // ndk-build.cmd is supposed to escape the back-slashes but it doesn't happen.
            // Workaround here by replacing back slash with forward.
            // ndk-build will have a fix for this bug in r14 but this gradle fix will make it
            // work back to r13, r12, r11, and r10.
            ndkOut = ndkOut.replace('\\', '/')
        }
        result.add("NDK_OUT=$ndkOut")
        result.add("NDK_LIBS_OUT=$soFolder")

        // Related to issuetracker.google.com/69110338. Semantics of APP_CFLAGS and APP_CPPFLAGS
        // is that the flag(s) are unquoted. User may place quotes if it is appropriate for the
        // target compiler. User in this case is build.gradle author of
        // externalNativeBuild.ndkBuild.cppFlags or the author of Android.mk.
        for (flag in getcFlags()) {
            result.add(String.format("APP_CFLAGS+=%s", flag))
        }
        for (flag in cppFlags) {
            result.add(String.format("APP_CPPFLAGS+=%s", flag))
        }
        var skipNextArgument = false
        for (argument in buildArguments) {
            // Jobs flag is removed for clean command because Make has issues running
            // cleans in parallel. See b.android.com/214558
            if (removeJobsFlag && argument == "-j") {
                // This is the arguments "-j" "4" case. We need to skip the current argument
                // which is "-j" as well as the next argument, "4".
                skipNextArgument = true
                continue
            }
            if (removeJobsFlag && argument == "--jobs") {
                // This is the arguments "--jobs" "4" case. We need to skip the current argument
                // which is "--jobs" as well as the next argument, "4".
                skipNextArgument = true
                continue
            }
            if (skipNextArgument) {
                // Skip the argument following "--jobs" or "-j"
                skipNextArgument = false
                continue
            }
            if (removeJobsFlag && (argument.startsWith("-j") || argument.startsWith("--jobs="))) {
                // This is the "-j4" or "--jobs=4" case.
                continue
            }
            result.add(argument)
        }
        return result
    }

    /** Get the build command  */
    private fun getBuildCommand(
        abi: CxxAbiModel, applicationMk: File, removeJobsFlag: Boolean
    ): String {
        return (ndkBuild
                + " "
                + Joiner.on(" ")
            .join(getBaseArgs(abi, applicationMk, removeJobsFlag)))
    }

    init {
        this.stats.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD

        // Do some basic sync time checks.
        if (makefile.isDirectory) {
            errorln(
                "Gradle project ndkBuild.path %s is a folder. "
                        + "Only files (like Android.mk) are allowed.",
                makefile
            )
        } else if (!makefile.exists()) {
            errorln(
                "Gradle project ndkBuild.path is %s but that file doesn't exist",
                makefile
            )
        }
    }
}