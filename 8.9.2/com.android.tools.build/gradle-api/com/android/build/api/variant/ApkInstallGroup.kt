/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.file.RegularFile

/**
 * An interface representing a set of APKs files and that are installed as a unit on a device.
 * These Apks are installed together with one install-multiple adb command if there are more than one
 * Apks in the group.
 */
@Incubating
interface ApkInstallGroup {
    /**
     * A group of APK files installed as a unit on a device (e.g., via the install-multiple adb command).
     */
    @get:Incubating
    val apks: List<RegularFile>

    /**
     * A brief description of this install group.
     */
    @get:Incubating
    val description: String
}
