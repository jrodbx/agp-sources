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

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_ABI
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_DEBUG
import com.android.build.gradle.internal.cxx.configure.MSBuildProperty.Platform
import com.android.build.gradle.internal.cxx.logging.bugln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.buildSystemNameForTasks
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NINJA
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.utils.appendCapitalized
import com.android.utils.cxx.CxxBugDiagnosticCode.NINJA_BUILD_SCRIPT_AUTHOR_FEEDBACK
import com.google.common.annotations.VisibleForTesting

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
     * Key: A [Configuration].
     * Value: The name of the task for this configuration.
     */
    private val configurers = mutableMapOf<Configuration, TaskName>()

    /**
     * Unique names for C/C++ Gradle configure tasks.
     *
     * Key: A [Configuration] annotation name combined with the targets
     * built with this task
     *
     * Value: The name of the task for this configuration and these tasks.
     */
    private val builders = mutableMapOf<Pair<Configuration, Targets>, TaskName>()

    // Task names seen so far
    private val namesSeen = mutableSetOf<TaskName>()

    // Build system type
    private val buildSystem = abis.first().variant.module.buildSystem

    // Build system type name for naming tasks.
    private val buildSystemName = abis.first().variant.module.buildSystemNameForTasks

    /**
     * Configuration task name to ABI.
     * Key: The name of the configuration task (like 'configureCMakeDebug[x86]')
     * Value: The ABI to configure when this task is run along with the names of variants covered.
     */
    val configureAbis = mutableMapOf<TaskName, Pair<List<VariantName>, CxxAbiModel>>()

    /**
     * Configure group task name to individual per-ABI configure tasks.
     * Key: The name of a configuration group task (like 'configureCMakeDebug')
     * Value: The names of the per-ABI configure tasks (like 'configureCMakeDebug[x86]')
     */
    val configureGroups = mutableMapOf<VariantName, MutableSet<TaskName>>()

    /**
     * Build task name to ABI.
     * Key: The name of the build task (like 'buildCMakeDebug')
     * Value: The ABI to build when this task is run along with the names of variants covered.
     */
    val buildAbis = mutableMapOf<TaskName, Pair<List<VariantName>, CxxAbiModel>>()

    /**
     * Build group task name to individual per-ABI build tasks.
     * Key: The name of a configuration group task (like 'buildCMakeDebug')
     * Value: The names of the per-ABI configure tasks (like 'buildCMakeDebug[x86]')
     */
    val buildGroups = mutableMapOf<VariantName, MutableSet<TaskName>>()

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
    val buildConfigureEdges = mutableSetOf<Pair<TaskName, TaskName>>()

    init {
        for (abi in abis) {
            if (!abi.isActiveAbi) continue
            val configureTaskName = createConfigurationTask(abi)
            createBuildTask(abi, configureTaskName)
        }
    }

    /**
     * Create a configuration task for a single [CxxAbiModel].
     */
    private fun createConfigurationTask(abi: CxxAbiModel) : TaskName {
        val (groupTaskName, configureTaskName) = configureTaskNameOf(abi.configurationArguments)
        configureAbis.setRepresentativeAbiForTask(configureTaskName, abi)
        configureGroups
            .computeIfAbsent(groupTaskName) { mutableSetOf() }
            .add(configureTaskName)
        variantToConfiguration
            .computeIfAbsent(abi.variant.variantName) { mutableSetOf() }
            .add(configureTaskName)
        return configureTaskName
    }

    /**
     * Create a build task for a single [CxxAbiModel].
     */
    private fun createBuildTask(abi: CxxAbiModel, configureTaskName : TaskName) {
        val (groupTaskName, buildTaskName) =
            buildTaskNameOf(
                abi.configurationArguments,
                abi.variant.buildTargetSet)
        buildAbis.setRepresentativeAbiForTask(buildTaskName, abi)
        buildConfigureEdges += buildTaskName to configureTaskName
        buildGroups
            .computeIfAbsent(groupTaskName) { mutableSetOf() }
            .add(buildTaskName)
        variantToBuild
            .computeIfAbsent(abi.variant.variantName) { mutableSetOf() }
            .add(buildTaskName)
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
    private fun taskNameAnnotation(configuration : Configuration) : String {
        val buildType =  buildTypeOf(buildSystem, configuration)
        return buildSystemName.appendCapitalized(buildType)
    }

    /**
     * Create a configure task name based on the content of the configuration.
     * Returns a pair of [TaskName] where the first is a group task name for
     * all ABIs (like configureCMakeDebug) and the second is a task name for
     * the specific ABI (like configureCMakeDebug[x86]).
     */
    private fun configureTaskNameOf(configuration: Configuration) : Pair<TaskName, TaskName> {
        val annotation = taskNameAnnotation(configuration)
        val groupTaskName = "configure$annotation"
        return groupTaskName to configurers.computeIfAbsent(configuration) {
            val abi = abiOf(buildSystem, configuration)
            uniquify(legalize("$groupTaskName[$abi]"))
        }
    }

    /**
     * Create a build task name based on the content of the configuration and list
     * of targets.
     * Returns a pair of [TaskName] where the first is a group task name for
     * all ABIs (like buildCMakeDebug[target1, target2]) and the second is a task name for
     * the specific ABI (like configureCMakeDebug[x86][target1, target2]).
     */
    private fun buildTaskNameOf(configuration: Configuration, targets: Targets)
            : Pair<TaskName, TaskName> {
        val annotation = taskNameAnnotation(configuration)
        val groupTaskName = "build$annotation"
        return groupTaskName to builders.computeIfAbsent(configuration to targets) {
            val abi = abiOf(buildSystem, configuration)
            val targetAnnotation = targetAnnotation(targets)
            uniquify(legalize("$groupTaskName[$abi]$targetAnnotation"))
        }
    }

    /**
     * Utility method that sets a representative ABI for the given task. If there is already an ABI
     * then this function verifies that the new ABI is accurately represented by the prior ABI.
     */
    private fun MutableMap<TaskName, Pair<List<VariantName>,CxxAbiModel>>.setRepresentativeAbiForTask(
        taskName : TaskName,
        abi : CxxAbiModel) {
        val map  = this
        val prior = map[taskName]
        if (prior == null) {
            map[taskName] = listOf(abi.variant.variantName) to abi
        } else {
            val (priorVariants, priorAbi) = prior
            if (priorAbi.abi != abi.abi) {
                error("Expected ${priorAbi} but got ${abi.abi}")
            }
            if (priorAbi.configurationArguments != abi.configurationArguments) {
                error("Expected same configuration arguments")
            }
            map[taskName] = (priorVariants + abi.variant.variantName) to abi
        }
    }
}


/**
 * Examine a C/C++ configuration and extract the ABI that it targets.
 */
@VisibleForTesting
fun abiOf(buildSystem: NativeBuildSystem, commands : Configuration) : String {
    return when(buildSystem) {
        NINJA -> {
            // When it's Ninja, the configure script is user-authored. We want to guess the ABI
            // anyway because it will give the user better task names.
            // The way we guess is as follows:
            // 1) We assume the arguments are like those passed to MSBuild and we look for a
            //    'Platform' property which will be like "Android-x86" if the is AGDE.
            // 2) If that fails, we assume the arguments are like those passed to CMake and we
            //    look for "CMAKE_ANDROID_ARCH_ABI" or "ANDROID_ABI".
            // 3) If that fails, we just return "" and the task name will not have an ABI in it.
            val arguments = commands.toMSBuildArguments()
            arguments.getMSBuildProperty(Platform) ?: run {
                val asCMakeArguments = commands.toCmakeArguments()
                asCMakeArguments.getCmakeProperty(CMAKE_ANDROID_ARCH_ABI) ?:
                asCMakeArguments.getCmakeProperty(ANDROID_ABI)
            } ?: run {
                bugln(NINJA_BUILD_SCRIPT_AUTHOR_FEEDBACK,
                    "Ninja-generating script must accept a parameter the defines the " +
                            "target ABI. Currently accepted examples are: -p:Platform=x86, " +
                            "/DCMAKE_ANDROID_ARCH_ABI=x86, and /DANDROID_ABI=x86")
                ""
            }
        }
        CMAKE -> {
            val arguments = commands.toCmakeArguments()
            arguments.getCmakeProperty(CMAKE_ANDROID_ARCH_ABI) ?: ""
        }
        NDK_BUILD -> {
            val arguments = commands.toNdkBuildArguments()
            arguments.getNdkBuildProperty(APP_ABI) ?: ""
        }
        else -> error("$buildSystem")
    }
}

/**
 * Extract a build type like 'Debug' from a configuration.
 */
@VisibleForTesting
fun buildTypeOf(buildSystem: NativeBuildSystem, commands : Configuration) : String {
    when(buildSystem) {
        NINJA -> {
            // When it's Ninja, the configure script is user-authored. We want to guess the
            // build type if we can, because it will give the user a more human-readable
            // task name.
            // The way we guess is as follows:
            // 1) We assume the arguments are like those passed to MSBuild and we append
            //    'Configuration' (which is like Debug from -p:Configuration=Debug) if it
            //    exists. Then we append 'NinjaProject' (which is like NinjaProject from
            //    -p:NinjaProject=Teapots) if it exists.
            // 2) If that gave no results then we assume the command-line is like CMake and
            //    we look for a CMAKE_BUILD_TYPE property.
            // 3) If that fails, then we return "" and the Gradle task name may not be as
            //    readable. It could be something like buildNinja13 instead of
            //    buildNinjaDebugTeapots[Android-x86].
            val arguments = commands.toMSBuildArguments()
            var result = ""
            arguments.getMSBuildProperty(MSBuildProperty.Configuration)?.let {
                result = result.appendCapitalized(it)
            }
            arguments.getMSBuildProperty(MSBuildProperty.NinjaProject)?.let {
                result = result.appendCapitalized(it)
            }
            if (result != "") {
                return result
            }
            val asCMakeArguments = commands.toCmakeArguments()
            return asCMakeArguments.getCmakeProperty(CMAKE_BUILD_TYPE) ?: run {
                bugln(
                    NINJA_BUILD_SCRIPT_AUTHOR_FEEDBACK,
                    "Ninja-generating script must accept a parameter the defines the " +
                            "target Configuration name. Currently accepted examples are: " +
                            "-p:Configuration=Debug, -p:NinjaProject=Teapots, and  " +
                            "/DCMAKE_BUILD_TYPE=MinSizeRel"
                )
                ""
            }
        }
        CMAKE -> {
            val arguments = commands.toCmakeArguments()
            return arguments.getCmakeProperty(CMAKE_BUILD_TYPE) ?: ""
        }
        NDK_BUILD -> {
            val arguments = commands.toNdkBuildArguments()
            return arguments.getNdkBuildProperty(NDK_DEBUG)?.let {
                if(it == "1") "Debug" else "Release"
            } ?: ""
        }
        else -> error("$buildSystem")
    }
}

