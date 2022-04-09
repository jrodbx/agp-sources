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
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

@Incubating
interface SdkComponents {
    /**
     * The path to the Android SDK that Gradle uses for this project.
     *
     * To learn more about downloading and installing the Android SDK, read
     * [Update Your Tools with the SDK Manager](https://developer.android.com/studio/intro/update.html#sdk-manager)
     */
    val sdkDirectory: Provider<Directory>

    /**
     * The path to the [Android NDK](https://developer.android.com/ndk/index.html) that Gradle uses for this project.
     *
     * You can install the Android NDK by either
     * [using the SDK manager](https://developer.android.com/studio/intro/update.html#sdk-manager)
     * or downloading
     * [the standalone NDK package](https://developer.android.com/ndk/downloads/index.html).
     */
    val ndkDirectory: Provider<Directory>

    /**
     * The path to the
     * [Android Debug Bridge (ADB)](https://developer.android.com/studio/command-line/adb.html)
     * executable from the Android SDK.
     */
    val adb: Provider<RegularFile>

    /**
     * The bootclasspath that will be used to compile classes in this project.
     *
     * The returned [Provider] can only be used at execution time and therefore must be used as
     * a [org.gradle.api.Task] input to do so.
     */
    val bootClasspath: Provider<List<RegularFile>>
}
