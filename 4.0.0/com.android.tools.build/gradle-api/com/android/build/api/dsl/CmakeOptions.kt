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
 * DSL object for per-module CMake configurations, such as the path to your `CMakeLists.txt`
 * build script and external native build output directory.
 *
 * To include CMake projects in your Gradle build, you need to use Android Studio 2.2 and higher
 * with Android plugin for Gradle 2.2.0 and higher. To learn more about Android Studio's support for
 * external native builds, read
 * [Add C and C++ Code to Your Project](https://developer.android.com/studio/projects/add-native-code.html)
 *
 * If you want to instead build your native libraries using ndk-build, see [NdkBuildOptions]
 */
@Incubating
interface CmakeOptions {
    // TODO(b/140406102)
}