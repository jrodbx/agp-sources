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
@file:JvmName("OptionParsers")

package com.android.build.gradle.options

import java.util.Locale

fun parseBoolean(propertyName: String, value: Any): Boolean {
    return when (value) {
        is Boolean -> value
        is CharSequence ->
            when (value.toString().toLowerCase(Locale.US)) {
                "true" -> true
                "false" -> false
                else -> parseBooleanFailure(propertyName, value)
            }
        is Number ->
            when (value.toInt()) {
                0 -> false
                1 -> true
                else -> parseBooleanFailure(propertyName, value)
            }
        else -> parseBooleanFailure(propertyName, value)
    }
}

private fun parseBooleanFailure(propertyName: String, value: Any): Nothing {
    throw IllegalArgumentException(
        "Cannot parse project property "
                + propertyName
                + "='"
                + value
                + "' of type '"
                + value.javaClass
                + "' as boolean. Expected 'true' or 'false'."
    )
}