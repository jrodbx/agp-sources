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

package com.android.build.gradle.options

import com.android.ide.common.repository.AgpVersion

/**
 * Represents an Android Gradle plugin major and minor version (e.g., AGP 9.0),
 * ignoring micro version.
 */
enum class Version(val major: Int, val minor: Int) {

    /**
     * A version before version 4.0, used when the exact version is not known, except that it's
     * guaranteed to be before 4.0.
     *
     * Internally, we use "0.0" to represent this version (not the nicest solution, but probably
     * works for now).
     */
    VERSION_BEFORE_4_0(0, 0),

    VERSION_3_5(3, 5),
    VERSION_3_6(3, 6),
    VERSION_4_0(4, 0),
    VERSION_4_1(4, 1),
    VERSION_4_2(4, 2),
    VERSION_7_0(7, 0),
    VERSION_7_2(7, 2),
    VERSION_7_3(7, 3),
    VERSION_8_0(8, 0),
    VERSION_8_1(8, 1),
    VERSION_8_2(8, 2),
    VERSION_8_3(8, 3),
    VERSION_8_11(8, 11),
    VERSION_9_0(9, 0),
    VERSION_10_0(10, 0),

    ; // end of enums

    val agpVersion: AgpVersion = AgpVersion(major, minor, 0)

    fun getDeprecationTargetMessage(): String {
        check(this != VERSION_BEFORE_4_0)
        return "It will be removed in version $major.$minor of the Android Gradle plugin."
    }

    fun getRemovedVersionMessage(): String {
        return if (this == VERSION_BEFORE_4_0) {
            "It has been removed from the current version of the Android Gradle plugin."
        } else {
            "It was removed in version $major.$minor of the Android Gradle plugin."
        }
    }
}
