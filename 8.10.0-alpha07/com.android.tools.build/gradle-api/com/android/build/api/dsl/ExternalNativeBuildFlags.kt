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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object for per-variant CMake and ndk-build configurations, such as toolchain arguments and
 * compiler flags.
 *
 * ```
 * android {
 *     // Similar to other properties in the defaultConfig block, you can override
 *     // these properties for each product flavor you configure.
 *     defaultConfig {
 *         // This block is different from the one you use to link Gradle
 *         // to your CMake or ndk-build script.
 *         externalNativeBuild {
 *             // For ndk-build, instead use the ndkBuild block.
 *             cmake {
 *                 // Passes optional arguments to CMake.
 *                 arguments "-DANDROID_ARM_NEON=TRUE", "-DANDROID_TOOLCHAIN=clang"
 *
 *                 // Sets a flag to enable format macro constants for the C compiler.
 *                 cFlags "-D__STDC_FORMAT_MACROS"
 *
 *                 // Sets optional flags for the C++ compiler.
 *                 cppFlags "-fexceptions", "-frtti"
 *
 *                 // Specifies the library and executable targets from your CMake project
 *                 // that Gradle should build.
 *                 targets "libexample-one", "my-executible-demo"
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * To enable external native builds and set the path to your CMake or ndk-build script, use
 * [android.externalNativeBuild][ExternalNativeBuild].
 */
@Incubating
interface ExternalNativeBuildFlags {
    /**
     * Encapsulates per-variant ndk-build configurations, such as compiler flags and toolchain
     * arguments.
     *
     * To enable external native builds and set the path to your `Android.mk` script, use
     * [android.externalNativeBuild.ndkBuild.path][NdkBuild.path].
     */
    val ndkBuild: NdkBuildFlags

    /**
     * Encapsulates per-variant ndk-build configurations, such as compiler flags and toolchain
     * arguments.
     *
     * To enable external native builds and set the path to your `Android.mk` script, use
     * [android.externalNativeBuild.ndkBuild.path][NdkBuild.path].
     */
    fun ndkBuild(action: NdkBuildFlags.() -> Unit)

    /**
     * Encapsulates per-variant CMake configurations, such as compiler flags and toolchain
     * arguments.
     *
     * To enable external native builds and set the path to your `CMakeLists.txt` script, use
     * [android.externalNativeBuild.cmake.path][Cmake.path].
     */
    val cmake: CmakeFlags

    /**
     * Encapsulates per-variant CMake configurations, such as compiler flags and toolchain
     * arguments.
     *
     * To enable external native builds and set the path to your `CMakeLists.txt` script, use
     * [android.externalNativeBuild.cmake.path][Cmake.path].
     */
    fun cmake(action: CmakeFlags.() -> Unit)

    /**
     * Additional per-variant experimental properties for C and C++.
     */
    @get:Incubating
    val experimentalProperties: MutableMap<String, Any>
}
