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

import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.soFolder
import com.android.build.gradle.internal.cxx.services.createProcessOutputJunction
import com.android.build.gradle.internal.ndk.Stl
import com.android.ide.common.process.ProcessInfoBuilder
import org.gradle.api.Action
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import java.io.File
import java.util.function.Function

fun generatePrefabPackages(
    nativeBuildSystem: NativeBuildSystem,
    moduleModel: CxxModuleModel,
    abiModel: CxxAbiModel,
    packages: List<File>,
    execOperation: Function<Action<in JavaExecSpec>, ExecResult>
) {
    val packagePaths = packages.map { it.path }

    val buildSystem = when (nativeBuildSystem) {
        NativeBuildSystem.NDK_BUILD -> "ndk-build"
        NativeBuildSystem.CMAKE -> "cmake"
    }

    val osVersion = abiModel.abiPlatformVersion

    val prefabClassPath: File = abiModel.variant.module.project.prefabClassPath
        ?: throw RuntimeException(
                "CxxAbiModule.prefabClassPath cannot be null when Prefab is enabled"
        )

    // We need to determine the user's STL choice as best we can. It can come from
    val stlArgumentPrefix = when (nativeBuildSystem) {
        NativeBuildSystem.NDK_BUILD -> "APP_STL="
        NativeBuildSystem.CMAKE -> "-DANDROID_STL="
    }
    var selectedStl = abiModel.variant.buildSystemArgumentList.findLast {
        it.startsWith(stlArgumentPrefix)
    }?.split("=", limit = 2)?.last()

    // Don't parse the Application.mk if we found an STL in the gradle file. The gradle arguments
    // will override the Application.mk, so there's no sense in possibly failing due to a parse
    // error if we don't have to.
    if (selectedStl == null && nativeBuildSystem == NativeBuildSystem.NDK_BUILD) {
        // The user has probably specified APP_STL in their Application.mk rather than in their
        // build.gradle. Prefab needs to know the STL, so try parsing it if it's trivial, and emit
        // an error if we can't. If we can't parse it then Prefab doesn't have the information it
        // needs, so the user will need to take some action (alter their Application.mk such that
        // APP_STL becomes trivially parsable, or define it in their build.gradle instead.
        val applicationMk = moduleModel.makeFile.resolveSibling("Application.mk")
        for (line in applicationMk.readText().lines()) {
            val match = Regex("^APP_STL\\s*:?=\\s*(.*)$").find(line.trim()) ?: continue
            val appStlMatch = match.groups[1]
            require(appStlMatch != null) // Should be impossible.
            val appStl = appStlMatch.value
            if (appStl.isEmpty()) {
                // Reset to the default case
                selectedStl = null
                continue
            }
            val stl = Stl.fromArgumentName(appStlMatch.value)
            if (stl == null) {
                errorln("Unable to parse APP_STL from $applicationMk: $appStl")
                return
            }
            selectedStl = stl.argumentName
        }
    }

    val defaultStl = abiModel.variant.module.ndkDefaultStl.argumentName

    // TODO: Get main class from manifest.
    val builder = ProcessInfoBuilder().setClasspath(prefabClassPath.toString())
        .setMain("com.google.prefab.cli.AppKt")
        .addArgs("--build-system", buildSystem)
        .addArgs("--platform", "android")
        .addArgs("--abi", abiModel.abi.tag)
        .addArgs("--os-version", osVersion.toString())
        .addArgs("--stl", selectedStl ?: defaultStl)
        .addArgs("--ndk-version", abiModel.variant.module.ndkVersion.major.toString())
        .addArgs("--output", abiModel.prefabFolder.resolve("prefab").toString())
        .addArgs(packagePaths)

    moduleModel.createProcessOutputJunction(
        abiModel.soFolder,
        "prefab_${buildSystem}_${abiModel.abi.tag}",
        builder, "prefab"
    ).javaProcess().logStderrToInfo().execute(execOperation::apply)
}
