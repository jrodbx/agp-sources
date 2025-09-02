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

package com.android.ide.common.symbols

import com.android.resources.ResourceType

/**
 * Provides IDs for resource assignment.
 *
 * [IdProvider.sequential] gives 'realistic' unique IDs, sequential within type.
 * [IdProvider.constant] gives the constant ID of 0 (invalid resource)
 */
interface IdProvider {

    /** Provides an ID for the next resource parsed. */
    fun next(resourceType: ResourceType): Int

    companion object {

        /**
         * Obtains a new ID provider that provides sequential IDs.
         *
         *
         * The generated IDs follow the usual aapt format of `PPTTNNNN`.
         */
        fun sequential(): IdProvider {
            return object : IdProvider {
                private val next = ShortArray(ResourceType.values().size)

                override fun next(resourceType: ResourceType): Int {
                    val typeIndex = resourceType.ordinal
                    return 0x7f shl 24 or (typeIndex + 1 shl 16) or (++next[typeIndex]).toInt()
                }
            }
        }

        /** Obtains a new constant ID provider that provides constant IDs of "0".  */
        fun constant(): IdProvider {
            return object : IdProvider{
                override fun next(resourceType: ResourceType): Int {
                    return 0
                }
            }
        }
    }
}
