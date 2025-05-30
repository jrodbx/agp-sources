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

package com.android.build.gradle.internal.cxx.cmake

import java.util.Locale

/**
 * This file holds functions for interpreting things as the CMake language interprets them.
 */


/**
 * https://cmake.org/cmake/help/latest/command/if.html
 *
 * True if the constant is 1, ON, YES, TRUE, Y, or a non-zero number. False if the constant
 * is 0, OFF, NO, FALSE, N, IGNORE, NOTFOUND, the empty string, or ends in the suffix
 * -NOTFOUND. Named boolean constants are case-insensitive. If the argument is not one of
 * these specific constants, it is treated as a variable or string and the following signature
 * is used.
 *
 */
fun isCmakeConstantTruthy(value : String) : Boolean {
    return when(val upper = value.uppercase(Locale.US)) {
        "1", "ON", "YES", "TRUE", "Y" -> true
        "0", "OFF", "NO", "FALSE", "N", "IGNORE", "NOTFOUND", "" -> false
        else ->
            when {
                upper.endsWith("-NOTFOUND") -> false
                else -> {
                    val asInteger = try {
                        Integer.parseInt(upper)
                    } catch(e: NumberFormatException) {
                        // Note that we don't support referencing another variable within the
                        // value of the string (like -DX=${Y}). These are treated as false.
                        0
                    }
                    return asInteger != 0
                }
            }
    }
}


/**
 * Convert boolean to "1" or "0" for CMake.
 */
fun cmakeBoolean(bool : Boolean) = if (bool) "1" else "0"
