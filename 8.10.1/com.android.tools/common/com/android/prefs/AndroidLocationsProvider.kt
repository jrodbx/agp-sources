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

package com.android.prefs

import java.nio.file.Path

/**
 * A provider of locations for android tools
 */
interface AndroidLocationsProvider {

    /**
     * The location of the .android folder
     *
     * This creates the folder if it's missing
     *
     * To query the AVD Folder, use [avdLocation] as it could be be overridden
     */
    @get:Throws(AndroidLocationsException::class)
    val prefsLocation: Path

    /**
     * The location of the AVD folder.
     */
    @get:Throws(AndroidLocationsException::class)
    val avdLocation: Path

    /**
     * The location of the managed devices avd folder.
     */
    @get:Throws(AndroidLocationsException::class)
    val gradleAvdLocation: Path

    /**
     * The root folder where the android folder will be located
     *
     * This is NOT the .android folder. Use [prefsLocation]
     *
     * To query the AVD Folder, use [avdLocation] as it could be overridden
     */
    val userHomeLocation: Path
}
