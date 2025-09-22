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
package com.android.utils

object JavaVersionUtil {
    /** Converts a class file version number JDK string like 1.6.0_65 to the corresponding class file version number, e.g. 50  */
    fun classVersionToJdk(version: Int): String {
        return if (version >= 53) {
            (version - 53 + 9).toString() // 53 => 9, 55 => 11, 61 => 17, ...
        } else "1.${version - 44}" // 47 => 1.3, 50 => 1.6, ...
    }
}
