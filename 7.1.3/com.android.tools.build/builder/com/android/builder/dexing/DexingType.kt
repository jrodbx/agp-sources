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

package com.android.builder.dexing

/**
 * The type of dex we produce. It can be:
 *
 *
 *  * mono dex: no multidex enabled, only one final DEX file produced
 *  * legacy multidex: multidex enabled, and min sdk version is less than 21
 *  * native multidex: multidex enabled, and min sdk version is greater or equal to 21
 *
 */
enum class DexingType(
    /** If this mode allows multiple DEX files.  */
    val multiDex: Boolean,
    /** If we should pre-dex in this dexing mode.  */
    val preDex: Boolean,
    /** If a main dex list is required for this dexing mode.  */
    val needsMainDexList: Boolean
) {
    MONO_DEX(
        multiDex = false,
        preDex = true,
        needsMainDexList = false
    ),
    LEGACY_MULTIDEX(
        multiDex = true,
        preDex = false,
        needsMainDexList = true
    ),
    NATIVE_MULTIDEX(
        multiDex = true,
        preDex = true,
        needsMainDexList = false
    );

    fun isPreDex() = preDex
    fun isMultiDex() = multiDex
}

fun DexingType.isLegacyMultiDexMode() = this === DexingType.LEGACY_MULTIDEX
