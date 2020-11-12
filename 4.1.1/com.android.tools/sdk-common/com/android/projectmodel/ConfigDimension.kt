/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.projectmodel

/**
 * Describes one dimension of the [ConfigTable].
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class ConfigDimension(
        /**
         * Name of the dimension. This is the name that the build system knows the dimension as.
         */
        val dimensionName: String,
        /**
         * Possible values for this dimension. For example, if this dimension describes the set of
         * build types, the possible values might be "debug" and "release". Build dimension values
         * must be unique within a given [ConfigTable].
         */
        val values: List<String>
) {
    /**
     * Returns true iff this [ConfigDimension] contains the given value.
     */
    fun containsValue(value: String) = values.contains(value)

    class Builder(
            private val dimensionName: String
    ) {
        private val values: ArrayList<String> = ArrayList()
        private val valuesSet: HashSet<String> = HashSet()

        fun add(value: String) {
            if (!valuesSet.contains(value)) {
                values.add(value)
                valuesSet.add(value)
            }
        }

        fun build(): ConfigDimension = ConfigDimension(dimensionName, values)
    }
}
