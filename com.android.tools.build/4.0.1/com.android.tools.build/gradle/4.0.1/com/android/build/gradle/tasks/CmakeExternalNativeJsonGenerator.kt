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
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxBuildModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.cmakeSettingsFile
import com.android.build.gradle.internal.ndk.Stl
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessInfoBuilder
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule
import org.gradle.api.Action
import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.IOException
import java.util.function.Function

/**
 * CMake JSON generation logic. This is separated from the corresponding CMake task so that JSON can
 * be generated during configuration.
 */
internal abstract class CmakeExternalNativeJsonGenerator(
    build: CxxBuildModel,
    variant: CxxVariantModel,
    abis: List<CxxAbiModel>,
    stats: GradleBuildVariant.Builder
) : ExternalNativeJsonGenerator(build, variant, abis, stats) {
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
        this.stats.nativeBuildSystemType = GradleNativeAndroidModule.NativeBuildSystemType.CMAKE
        this.cmake = variant.module.cmake!!

        // Check some basic requirements. This code executes at sync time but any call to
        // recordConfigurationError will later cause the generation of json to fail.
        val cmakelists = makefile
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
    abstract fun executeProcessAndGetOutput(abi: CxxAbiModel, execOperations: Function<Action<in ExecSpec>, ExecResult>): String

    public override fun executeProcess(abi: CxxAbiModel, execOperation: Function<Action<in ExecSpec>, ExecResult>): String {
        val output = executeProcessAndGetOutput(abi, execOperation)
        return makeCmakeMessagePathsAbsolute(output, makefile.parentFile)
    }

    override fun processBuildOutput(buildOutput: String, abi: CxxAbiModel) {}

    override fun getProcessBuilder(abi: CxxAbiModel): ProcessInfoBuilder {
        val builder = ProcessInfoBuilder()

        builder.setExecutable(cmake.cmakeExe)
        val arguments = mutableListOf<CommandLineArgument>()
        arguments.addAll(abi.getFinalCmakeCommandLineArguments())
        builder.addArgs(arguments.convertCmakeCommandLineArgumentsToStringList())
        return builder
    }

    override fun getNativeBuildSystem(): NativeBuildSystem {
        return NativeBuildSystem.CMAKE
    }

    override fun getStlSharedObjectFiles(): Map<Abi, File> {
        // Search for ANDROID_STL build argument. Process in order / later flags take precedent.
        var stl: Stl? = null
        for (argument in buildArguments.map { it.replace(" ", "") }) {
            if (argument.startsWith("-DANDROID_STL=")) {
                val stlName = argument.split("=".toRegex(), 2).toTypedArray()[1]
                stl = Stl.fromArgumentName(stlName)
                if (stl == null) {
                    errorln("Unrecognized STL in arguments: %s", stlName)
                }
            }
        }

        // TODO: Query the default from the NDK.
        // We currently assume the default to not require packaging for the default STL. This is
        // currently safe because the default for ndk-build has always been system (which doesn't
        // require packaging because it's a system library) and gnustl_static or c++_static for
        // CMake (which also doesn't require packaging).
        //
        // https://github.com/android-ndk/ndk/issues/744 wants to change the default for both to
        // c++_shared, but that can't happen until we stop assuming the default does not need to be
        // packaged.
        return if (stl == null) {
            mapOf()
        } else {
            variant.module.stlSharedObjectMap.getValue(stl)
                .filter { e -> getAbis().contains(e.key) }
        }

    }
}
