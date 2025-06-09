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

class AndroidLocationsException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {

        /**
         * Creates an instance with a list of [LocationValue] pairs where the locations of the
         * preferences was searched.
         */
        internal fun createForHomeLocation(
            vars: List<LocationValue>
        ): AndroidLocationsException {
            val list =
                vars.joinToString(separator = "\n") { "- ${it.propertyName}(${it.queryType} -> ${it.value}" }

            val start = """
                Unable to find the location of the home directory.
                The following locations have been checked, but they do not exist:
                """.trimIndent()

            return AndroidLocationsException("$start\n$list")
        }
    }
}

internal interface LocationValue: Comparable<LocationValue> {
    val propertyName: String
    val queryType: String
    val value: String

    override fun compareTo(other: LocationValue): Int {
        return when (val i = propertyName.compareTo(other.propertyName)) {
            0 -> return queryType.compareTo(other.queryType)
            else -> i
        }
    }
}
