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
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.prefabClassPath
import com.android.build.gradle.internal.cxx.model.prefabPackageConfigurationDirectoriesList
import com.android.build.gradle.internal.cxx.model.prefabPackageDirectoryList
import com.android.build.gradle.internal.cxx.prefab.PREFAB_PACKAGE_CONFIGURATION_SEGMENT
import com.android.build.gradle.internal.cxx.prefab.PREFAB_PACKAGE_SEGMENT
import com.android.build.gradle.internal.cxx.process.createProcessOutputJunction
import com.android.ide.common.process.ProcessInfoBuilder
import org.gradle.process.ExecOperations
import java.io.File

fun generatePrefabPackages(
    ops: ExecOperations,
    abi: CxxAbiModel) {

    val buildSystem = when (abi.variant.module.buildSystem) {
        NativeBuildSystem.NDK_BUILD -> "ndk-build"
        NativeBuildSystem.CMAKE -> "cmake"
        else -> error("${abi.variant.module.buildSystem}")
    }

    val osVersion = abi.abiPlatformVersion

    val prefabClassPath: File = abi.variant.prefabClassPath
        ?: throw RuntimeException(
                "CxxAbiModule.prefabClassPath cannot be null when Prefab is used"
        )

    val configureOutput = abi.prefabFolder.resolve("prefab-configure")
    val finalOutput = abi.prefabFolder.resolve("prefab")

    // TODO: Get main class from manifest.
    val builder = ProcessInfoBuilder().setClasspath(prefabClassPath.toString())
        .setMain("com.google.prefab.cli.AppKt")
        .addArgs("--build-system", buildSystem)
        .addArgs("--platform", "android")
        .addArgs("--abi", abi.abi.tag)
        .addArgs("--os-version", osVersion.toString())
        .addArgs("--stl", abi.variant.stlType)
        .addArgs("--ndk-version", abi.variant.module.ndkVersion.major.toString())
        .addArgs("--output", configureOutput.path)
        .addArgs(abi.variant.prefabConfigurationPackages.map { it.path })

    createProcessOutputJunction(
        abi.soFolder.resolve("prefab_command_${buildSystem}_${abi.abi.tag}.txt"),
        abi.soFolder.resolve("prefab_stdout_${buildSystem}_${abi.abi.tag}.txt"),
        abi.soFolder.resolve("prefab_stderr_${buildSystem}_${abi.abi.tag}.txt"),
        builder, "prefab"
    ).javaProcess().logStderr().execute(ops::javaexec)
    translateFromConfigurationToFinal(configureOutput, finalOutput)
}

/**
 * Copy files from [configureOutput] to [finalOutput]. Along the way, translate lines that
 * reference the configuration folder to reference the final folder when needed
 */
private fun translateFromConfigurationToFinal(configureOutput : File, finalOutput : File) {
    for (configureFile in configureOutput.walkTopDown()) {
        val relativeFile = configureFile.relativeTo(configureOutput)
        val finalFile = finalOutput.resolve(relativeFile)
        if (configureFile.isDirectory) {
            finalFile.mkdirs()
            continue
        }
        when(relativeFile.extension) {
            "cmake" -> {
                val sb = StringBuilder()
                configureFile.forEachLine { line ->
                    sb.appendLine(
                        if (line.contains("IMPORTED_LOCATION")) {
                            toFinalPrefabPackage(line)
                        } else line
                    )
                }
                finalFile.writeText(sb.toString())
            }
            else -> configureFile.copyTo(finalFile)
        }
    }
}

/**
 * Return the list of folders that should be used to configure prefab.
 *
 * - For module-to-module references then those are from [prefabPackageConfigurationDirectoriesList].
 *
 * - For AARs those are from [prefabPackageDirectoryList] which is fully populated with .so files.
 *   AARs are defined as everything in [prefabPackageDirectoryList] which are not covered already by
 *   [prefabPackageConfigurationDirectoriesList].
 *
 */
val CxxVariantModel.prefabConfigurationPackages : List<File> get() {
    val coveredByConfigurationPackage = prefabPackageConfigurationDirectoriesList.map {
            toFinalPrefabPackage(it.path)
        }.toSet()

    val aarPackages = prefabPackageDirectoryList.filter {
        !coveredByConfigurationPackage.contains(it.path)
    }
    return prefabPackageConfigurationDirectoriesList + aarPackages
}

/**
 * If [from] contains [PREFAB_PACKAGE_CONFIGURATION_SEGMENT] then replace it with
 * [PREFAB_PACKAGE_SEGMENT].
 */
private fun toFinalPrefabPackage(from : String) : String {
    return from.replaceFirst(PREFAB_PACKAGE_CONFIGURATION_SEGMENT, PREFAB_PACKAGE_SEGMENT)
}
