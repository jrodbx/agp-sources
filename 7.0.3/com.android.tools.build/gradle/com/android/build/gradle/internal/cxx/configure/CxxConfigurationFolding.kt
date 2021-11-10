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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_ABI
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_DEBUG
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD

/**
 * Characters that are illegal in Gradle task names.
 */
private val illegalGradleTaskChars = arrayOf('/', '\\', ':', '<', '>', '"', '?', '*', '|')

/**
 * A list of strings that defines a C/C++ configuration.
 *
 * For CMake, this is the list of arguments passed to CMake.exe when the Ninja project is
 * generated.
 *
 * For ndk-build, this is the list of arguments passed along with -nB to do a dry run of
 * the build
 */
typealias Configuration = List<String>

// A set of C/C++ build targets.
typealias Targets = Set<String>

// The name of a Gradle variant (like 'debug').
typealias VariantName = String

// The name of a Gradle task.
typealias TaskName = String

/**
 * Creates unique and human-readable Gradle task names for a group of ABIs where identical
 * The names should be unique per C/C++ configuration and, in the case of build task, build targets.
 */
class CxxConfigurationFolding(abis : List<CxxAbiModel>) {

    /**
     * Unique names for C/C++ Gradle build tasks.
     *
     * Key: A list of [Configuration]. One for each ABI folded together.
     * Value: The name of the task for this configuration.
     */
    private val configurers = mutableMapOf<List<Configuration>, TaskName>()

    /**
     * Unique names for C/C++ Gradle configure tasks.
     *
     * Key: A list of [Configuration], one for each ABI folded together, combined with the targets
     * built with this task
     *
     * Value: The name of the task for this configuration and these tasks.
     */
    private val builders = mutableMapOf<Pair<List<Configuration>, Targets>, TaskName>()

    // Just the active ABIs (those that are built)
    private val activeAbis = abis.filter { it.isActiveAbi }.map { it.abi.tag }.distinct().sorted()

    // Task names seen so far
    private val namesSeen = mutableSetOf<TaskName>()

    // Build system type
    private val buildSystem = abis.first().variant.module.buildSystem

    // Build system type name for naming tasks.
    private val buildSystemName = when(buildSystem) {
        CMAKE -> "CMake"
        NDK_BUILD -> "NdkBuild"
    }

    /**
     * Configuration task name to ABIs.
     * Key: The name of the configuration task (like 'configureCMakeDebug')
     * Value: The ABIs to configure when this task is run.
     */
    val configureAbis = mutableMapOf<TaskName, List<CxxAbiModel>>()

    /**
     * Build task name to ABIs.
     * Key: The name of the build task (like 'buildCMakeDebug')
     * Value: The ABIs to build when this task is run.
     */
    val buildAbis = mutableMapOf<TaskName, List<CxxAbiModel>>()

    /**
     * The configuration tasks for each variant.
     * Key: Name of the variant.
     * Value: The names of configuration tasks for this variant.
     */
    val variantToConfiguration = mutableMapOf<VariantName, MutableSet<TaskName>>()

    /**
     * The build tasks for each variant.
     * Key: Name of the variant.
     * Value: The names of build tasks for this variant.
     */
    val variantToBuild = mutableMapOf<VariantName, MutableSet<TaskName>>()

    /**
     * Dependencies from build tasks to configure tasks.
     * First: Name of a build task.
     * Second: Name of a configure task.
     */
    val buildConfigureEdges = mutableListOf<Pair<TaskName, TaskName>>()

    init {

        abis
            .groupBy { it.fullConfigurationHash }
            .forEach { (_, configurationAbis) ->
                val representatives = configurationAbis
                        .filter { it.isActiveAbi }
                        .distinctBy { it.configurationArguments }
                        .sortedBy { it.abi.ordinal }
                if (representatives.isNotEmpty()) {
                    val configureTaskName = configureTaskNameOf(
                            representatives.map { it.configurationArguments })
                    configureAbis[configureTaskName] = representatives

                    configurationAbis.forEach {
                        variantToConfiguration
                                .computeIfAbsent(it.variant.variantName) { mutableSetOf() }
                                .add(configureTaskName)
                    }

                    configurationAbis
                            .groupBy { it.variant.buildTargetSet }
                            .forEach { (targets, configurationAbis) ->
                                val representatives = configurationAbis
                                        .filter { it.isActiveAbi }
                                        .distinctBy { it.configurationArguments }
                                        .sortedBy { it.abi.ordinal }
                                val buildTaskName =
                                        buildTaskNameOf(
                                                representatives.map { it.configurationArguments },
                                                targets)
                                buildAbis[buildTaskName] = representatives
                                buildConfigureEdges += buildTaskName to configureTaskName
                                configurationAbis.forEach {
                                    variantToBuild
                                            .computeIfAbsent(it.variant.variantName) { mutableSetOf() }
                                            .add(buildTaskName)
                                }
                            }
                }
            }
    }

    /**
     * Replace characters that are illegal in Gradle task names with '_'.
     */
    private fun legalize(name: String) : String {
        var result = name
        for(ch in illegalGradleTaskChars) {
            if (result.contains(ch)) {
                result = result.replace(ch, '_')
            }
        }
        return result
    }

    /**
     * Make [base] unique by appending '-2' to the name for the second, etc.
     */
    private fun uniquify(base: String) : String {
        var name = base
        var index = 1
        while(namesSeen.contains(name)) {
            index++
            name = "$base-$index"
        }
        namesSeen += name
        return name
    }

    /**
     * Examine a C/C++ configuration and extract the ABI that it targets.
     */
    private fun abiOf(commands : Configuration) : String {
        return when(buildSystem) {
            CMAKE -> {
                val arguments = commands.toCmakeArguments()
                arguments.getCmakeProperty(CMAKE_ANDROID_ARCH_ABI) ?: ""
            }
            NDK_BUILD -> {
                val arguments = commands.toNdkBuildArguments()
                arguments.getNdkBuildProperty(APP_ABI) ?: ""
            }
        }
    }

    /**
     * Extract a build type like 'Debug' from a configuration.
     */
    private fun buildTypeOf(commands : Configuration) : String {
        return when(buildSystem) {
            CMAKE -> {
                val arguments = commands.toCmakeArguments()
                arguments.getCmakeProperty(CMAKE_BUILD_TYPE) ?: ""
            }
            NDK_BUILD -> {
                val arguments = commands.toNdkBuildArguments()
                arguments.getNdkBuildProperty(NDK_DEBUG)?.let {
                    if(it == "1") "Debug" else "Release"
                } ?: ""
            }
        }
    }

    /**
     * Create some text that can be appended to a task name to distinguish which targets are built.
     * When there are more than two targets use 'etc' to avoid very long task names. Uniqueness of
     * the overall task name is still guaranteed.
     */
    private fun targetAnnotation(unsorted: Targets) : String {
        val targets = unsorted.toList().sorted()
        return when {
            targets.isEmpty() -> ""
            targets.size == 1 -> "[${targets[0]}]"
            targets.size == 2 -> "[${targets[0]},${targets[1]}]"
            else -> "[${targets[0]},${targets[1]},etc]"
        }
    }

    /**
     * Create a human-readable task name suffix.
     */
    private fun configurationNameAnnotation(configurations : List<Configuration>) : String {
        val buildType = configurations.map { buildTypeOf(it) }.distinct().single()
        val abis = configurations.map { abiOf(it) }.distinct().sorted()
        return if (abis == activeAbis) "$buildSystemName$buildType"
            else "$buildSystemName$buildType[${abis.joinToString()}]"
    }

    /**
     * Create a configure task name based on the content of the configurations.
     */
    private fun configureTaskNameOf(configurations: List<Configuration>) : String {
        return configurers.computeIfAbsent(configurations) {
            val annotation = configurationNameAnnotation(configurations)
            uniquify(legalize("configure$annotation"))
        }
    }

    /**
     * Create a build task name based on the content of the configurations and build targets.
     */
    private fun buildTaskNameOf(configurations: List<Configuration>, targets: Targets) : String {
        return builders.computeIfAbsent(configurations to targets) {
            val annotation = configurationNameAnnotation(configurations)
            val targetAnnotation = targetAnnotation(targets)
            uniquify(legalize("build$annotation$targetAnnotation"))
        }
    }
}


