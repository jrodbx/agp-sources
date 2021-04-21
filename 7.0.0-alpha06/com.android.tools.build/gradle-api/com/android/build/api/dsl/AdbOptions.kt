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
 * Options for the adb tool.
 */
@Incubating
interface AdbOptions {
    /** The time out used for all adb operations. */
    var timeOutInMs: Int

    /** The list of FULL_APK installation options. */
    var installOptions: Collection<String>?

    /** Sets the list of FULL_APK installation options */
    fun installOptions(option: String)

    /** Sets the list of FULL_APK installation options */
    fun installOptions(vararg options: String)
}