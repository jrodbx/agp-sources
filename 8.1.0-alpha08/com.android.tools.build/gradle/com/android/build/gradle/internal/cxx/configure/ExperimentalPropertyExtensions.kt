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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import java.io.File

/**
 * Enable Ninja build system through experimental flags in Android Gradle Plugin.
 * Mimics the final DSL.
 *
 * Example usage in build.gradle:
 *         externalNativeBuild {
 *           experimentalProperties["ninja.abiFilters"] = ["x86", "arm64-v8a" ]
 *           experimentalProperties["ninja.path"] = "../Teapot.sln"
 *           experimentalProperties["ninja.configure"] = "msbuild"
 *           experimentalProperties["ninja.arguments"] = [
 *             "\${ndk.moduleMakeFile}",
 *            "-p:Configuration=\${ndk.variantName}",
 *            "-p:Platform=Android-\${ndk.abi}",
 *            "-p:NinjaBuildLocation=\${ndk.buildRoot}",
 *            "-p:NinjaProject=GameApplication",
 *            "-t:GenerateBuildNinja"
 *           ]
 *         }
 */

/**
 * Per-module Ninja information.
 * Planned as class, of same name, in com.android.build.api.dsl.
 */
data class Ninja(
    /**
     * The path to a build file, like ../Teapot.sln
     */
    val path : File?,
    /**
     * The path to a configuration script to call with [arguments]
     */
    val configure : File,
    /**
     * A user-specified alternate build staging location.
     */
    val buildStagingDirectory : File?
)

/**
 * Per-variant Ninja information.
 * Planned as class, of same name, in com.android.build.gradle.internal.dsl
 */
data class CoreExternalNativeNinjaOptions(
    val abiFilters : Set<String>,
    val arguments : List<String>,
    val cFlags : List<String>,
    val cppFlags : List<String>,
    val targets : Set<String>,
)

/**
 * Planned as field in  com.android.build.api.dsl.ExternalNativeBuild
 */
val NativeBuildCreationConfig.ninja : Ninja get() = externalNativeExperimentalProperties.ninja

val Map<String, Any>.ninja : Ninja get() = Ninja(
    path = propertyAsFile("ninja.path"),
    configure = propertyAsFile("ninja.configure") ?: File(""),
    buildStagingDirectory = propertyAsFile("ninja.buildStagingDirectory")
)

/**
 * Planned as field in com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
 */
val NativeBuildCreationConfig.externalNativeNinjaOptions get() = CoreExternalNativeNinjaOptions(
    abiFilters = externalNativeExperimentalProperties.propertyAsSet("ninja.abiFilters"),
    arguments = externalNativeExperimentalProperties.propertyAsList("ninja.arguments"),
    cFlags = externalNativeExperimentalProperties.propertyAsList("ninja.cFlags"),
    cppFlags = externalNativeExperimentalProperties.propertyAsList("ninja.cppFlags"),
    targets = externalNativeExperimentalProperties.propertyAsSet("ninja.targets"),
)

/**
 * Convert [name] from [externalNativeNinjaOptions] to [Set<String>].
 */
private fun Map<String, Any>.propertyAsFile(name : String) : File? {
    val value = get(name) ?: return null
    return when(value) {
        is String -> File(value)
        is File -> value
        else -> error("${value.javaClass}")
    }
}

/**
 * Convert [name] from [externalNativeNinjaOptions] to [Set<String>].
 */
private fun Map<String, Any>.propertyAsSet(name : String) : Set<String> {
    val value = get(name) ?: return setOf()
    return when (value) {
        is List<*> -> value.map { "$it" }.toSet()
        is Set<*> -> value.map { "$it" }.toSet()
        else -> error("${value.javaClass}")
    }
}

/**
 * Convert [name] from [externalNativeNinjaOptions] to [List<String>].
 */
private fun Map<String, Any>.propertyAsList(name : String) : List<String> {
    val value = get(name) ?: return listOf()
    return propertyValueAsList(value)
}

/**
 * Convert [Any] from Gradle DSL to a List<String>.
 */
private fun propertyValueAsList(value : Any) : List<String> {
    return when (value) {
        is List<*> -> value.map { "$it" }
        is Set<*> -> value.map { "$it" }
        else -> error("Could not convert from ${value.javaClass} to List<String>")
    }
}

/**
 * This is the experimental flag analog of [com.android.build.api.dsl.Prefab].
 */
data class PrefabExperimentalPackagingOptions(
    /**
     * export_libraries may specify either literal arguments to be used as-is, intra-package
     * references, or inter-package references. This field is optional.
     */
    val exportLibraries : List<String>?
)

/**
 * Retrieve user's experimental settings for an individual Prefab publishing module.
 */
fun NativeBuildCreationConfig.getPrefabExperimentalPackagingOptions(module : String)
    : PrefabExperimentalPackagingOptions {
    var exportLibraries : List<String>? = null
    for((key, value) in externalNativeExperimentalProperties) {
        if (key != "prefab.${module}.exportLibraries") continue
        exportLibraries = propertyValueAsList(value)
    }
    return PrefabExperimentalPackagingOptions(
        exportLibraries = exportLibraries
    )
}
