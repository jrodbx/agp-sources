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

package com.android.builder.dexing

/**
 * The type of dex we produce. It can be:
 *
 *  - [MONO_DEX]: multidex disabled (only one final dex file is produced)
 *  - [LEGACY_MULTIDEX]: multidex enabled and minSdkVersion < 21
 *  - [NATIVE_MULTIDEX]: multidex enabled and minSdkVersion >= 21
 *
 * Note that we can also run native multidex for a dynamic feature module regardless of
 * minSdkVersion because the base module will take care of packaging the dex files from
 * dynamic feature modules correctly if minSdkVersion < 21.
 */
enum class DexingType {
    MONO_DEX,
    LEGACY_MULTIDEX,
    NATIVE_MULTIDEX;

    val isMultiDex: Boolean
        get() = (this == LEGACY_MULTIDEX || this == NATIVE_MULTIDEX)

    val isLegacyMultiDex: Boolean
        get() = (this == LEGACY_MULTIDEX)

}
