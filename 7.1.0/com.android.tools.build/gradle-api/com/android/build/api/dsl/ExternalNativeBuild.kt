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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object to configure external native builds using
 * [CMake](https://cmake.org/)
 * or
 * [ndk-build](https://developer.android.com/ndk/guides/build.html)
 *
 * ```
 * android {
 *     externalNativeBuild {
 *         // Encapsulates your CMake build configurations.
 *         // For ndk-build, instead use the ndkBuild block.
 *         cmake {
 *             // Specifies a path to your CMake build script that's
 *             // relative to the build.gradle file.
 *             path "CMakeLists.txt"
 *         }
 *     }
 * }
 * ```
 *
 * To learn more about including external native builds to your Android Studio projects, read
 * [Add C and C++ Code to Your Project](https://developer.android.com/studio/projects/add-native-code.html)
 */
interface ExternalNativeBuild {

    /**
     * Encapsulates per-variant configurations for your external ndk-build project, such as the path
     * to your `Android.mk` build script and build output directory.
     *
     * For more information about the properties you can configure in this block, see
     * [NdkBuild]
     */
    val ndkBuild: NdkBuild

    /**
     * Encapsulates per-variant configurations for your external ndk-build project, such as the path
     * to your `Android.mk` build script and build output directory.
     *
     * For more information about the properties you can configure in this block, see [NdkBuild]
     */
    fun ndkBuild(action: NdkBuild.() -> Unit)

    /**
     * Encapsulates per-variant configurations for your external ndk-build project, such as the path
     * to your `CMakeLists.txt` build script and build output directory.
     *
     * For more information about the properties you can configure in this block, see [Cmake]
     */
    val cmake: Cmake

    /**
     * Encapsulates per-variant configurations for your external ndk-build project, such as the path
     * to your `CMakeLists.txt` build script and build output directory.
     *
     * For more information about the properties you can configure in this block, see [Cmake]
     */
    fun cmake(action: Cmake.() -> Unit)

}
