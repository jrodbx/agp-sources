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

package com.android.build.gradle.internal.signing

import java.io.Serializable

/** Class containing information about which signature versions are enabled or disabled.  */
data class SigningConfigVersions(
    val enableV1Signing: Boolean,
    val enableV2Signing: Boolean,
    val enableV3Signing: Boolean,
    val enableV4Signing: Boolean,
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L

        // The lowest API with v2 signing support
        const val MIN_V2_SDK = 24
        // The lowest API with v3 signing support
        const val MIN_V3_SDK = 28
        // The lowest API with v4 signing support
        const val MIN_V4_SDK = 30
    }
}
