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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.prefabClassPath
import com.android.build.gradle.internal.cxx.model.prefabPackageDirectoryList
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.ide.common.process.ProcessInfoBuilder
import org.gradle.process.ExecOperations
import java.io.File

fun generatePrefabPackages(
    ops: ExecOperations,
    abiModel: CxxAbiModel) {
    val packagePaths =
        abiModel.variant.prefabPackageDirectoryList.map { it.path }

    val buildSystem = when (abiModel.variant.module.buildSystem) {
        NativeBuildSystem.NDK_BUILD -> "ndk-build"
        NativeBuildSystem.CMAKE -> "cmake"
        else -> error("${abiModel.variant.module.buildSystem}")
    }

    val osVersion = abiModel.abiPlatformVersion

    val prefabClassPath: File = abiModel.variant.prefabClassPath
        ?: throw RuntimeException(
                "CxxAbiModule.prefabClassPath cannot be null when Prefab is used"
        )

    // TODO: Get main class from manifest.
    val builder = ProcessInfoBuilder().setClasspath(prefabClassPath.toString())
        .setMain("com.google.prefab.cli.AppKt")
        .addArgs("--build-system", buildSystem)
        .addArgs("--platform", "android")
        .addArgs("--abi", abiModel.abi.tag)
        .addArgs("--os-version", osVersion.toString())
        .addArgs("--stl", abiModel.variant.stlType)
        .addArgs("--ndk-version", abiModel.variant.module.ndkVersion.major.toString())
        .addArgs("--output", abiModel.prefabFolder.resolve("prefab").toString())
        .addArgs(packagePaths)

    createProcessOutputJunction(
        abiModel.soFolder.resolve("prefab_command_${buildSystem}_${abiModel.abi.tag}.txt"),
        abiModel.soFolder.resolve("prefab_stdout_${buildSystem}_${abiModel.abi.tag}.txt"),
        abiModel.soFolder.resolve("prefab_stderr_${buildSystem}_${abiModel.abi.tag}.txt"),
        builder, "prefab"
    ).javaProcess().logStderr().execute(ops::javaexec)
}
