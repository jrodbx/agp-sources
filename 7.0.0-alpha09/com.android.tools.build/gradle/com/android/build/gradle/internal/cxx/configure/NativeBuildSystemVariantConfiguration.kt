/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.tasks.NativeBuildSystem

/**
 * This class represents a single native build variant config that is abstract against the
 * underlying native build system. That is, it hides whether the build system is CMake or ndk-build.
 */
data class NativeBuildSystemVariantConfig(
    val externalNativeBuildAbiFilters: Set<String>,
    val ndkAbiFilters: Set<String>,
    val arguments: List<String>,
    val cFlags: List<String>,
    val cppFlags: List<String>,
    val targets: Set<String>)

fun createNativeBuildSystemVariantConfig(
    buildSystem: NativeBuildSystem,
    variant: Variant,
    variantDslInfo: VariantDslInfo) : NativeBuildSystemVariantConfig {

    /**
     * The set of abiFilters from the externalNativeBuild part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         externalNativeBuild {
     *             cmake {
     *                 abiFilters "x86", "x86_64"
     *             }
     *         }
     *     }
     * </pre>
     */
    val externalNativeBuildAbiFilters: Set<String> = when (buildSystem) {
        NativeBuildSystem.CMAKE ->
            variant.externalCmake?.abiFilters?.get() ?: setOf()
        NativeBuildSystem.NDK_BUILD ->
            variant.externalNdkBuild?.abiFilters?.get() ?: setOf()
    }

    /**
     * Get the set of abiFilters from the ndk part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         ndk {
     *             abiFilters "x86", "x86_64"
     *         }
     *     }
     * </pre>
     */
    val ndkAbiFilters: Set<String> = variantDslInfo.ndkConfig.abiFilters


    /**
     * The set of build system arguments from the externalNativeBuild part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         externalNativeBuild {
     *             cmake {
     *                 arguments "-DCMAKE_BUILD_FLAG=xyz"
     *             }
     *         }
     *     }
     * </pre>
     */
    val arguments: List<String> = when (buildSystem) {
        NativeBuildSystem.CMAKE ->
            variant.externalCmake?.arguments?.get() ?: listOf()
        NativeBuildSystem.NDK_BUILD ->
            variant.externalNdkBuild?.arguments?.get() ?: listOf()
    }

    /**
     * The set of build system c flags from the externalNativeBuild part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         externalNativeBuild {
     *             cmake {
     *                 cFlags "-DMY_FLAG"
     *             }
     *         }
     *     }
     * </pre>
     */
    val cFlags: List<String> = when (buildSystem) {
        NativeBuildSystem.CMAKE ->
            variant.externalCmake?.cFlags?.get() ?: listOf()
        NativeBuildSystem.NDK_BUILD ->
            variant.externalNdkBuild?.cFlags?.get() ?: listOf()
    }

    /**
     * The set of build system c++ flags from the externalNativeBuild part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         externalNativeBuild {
     *             cmake {
     *                 cppFlags "-DMY_FLAG"
     *             }
     *         }
     *     }
     * </pre>
     */
    val cppFlags: List<String> = when (buildSystem) {
        NativeBuildSystem.CMAKE ->
            variant.externalCmake?.cppFlags?.get() ?: listOf()
        NativeBuildSystem.NDK_BUILD ->
            variant.externalNdkBuild?.cppFlags?.get() ?: listOf()
    }

    /**
     * The set of build system c++ targets from the externalNativeBuild part of the DSL. For example,
     *
     * <pre>
     *     defaultConfig {
     *         externalNativeBuild {
     *             cmake {
     *                 targets "my-target"
     *             }
     *         }
     *     }
     * </pre>
     */
    val targets: Set<String> = when (buildSystem) {
        NativeBuildSystem.CMAKE ->
            variant.externalCmake?.targets?.get() ?: setOf()
        NativeBuildSystem.NDK_BUILD ->
            variant.externalNdkBuild?.targets?.get() ?: setOf()
    }

    return NativeBuildSystemVariantConfig(
        externalNativeBuildAbiFilters,
        ndkAbiFilters,
        arguments,
        cFlags,
        cppFlags,
        targets)

}
