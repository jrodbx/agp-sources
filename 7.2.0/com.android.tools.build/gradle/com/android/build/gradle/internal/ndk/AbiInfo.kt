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

package com.android.build.gradle.internal.ndk

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode
import com.google.gson.annotations.SerializedName

/** Information about an ABI.  */
data class AbiInfo(
    val abi: Abi = Abi.X86,
    @SerializedName("bitness")
    val bitness: Int = 64,
    @SerializedName("deprecated")
    val isDeprecated: Boolean = false,
    @SerializedName("default")
    val isDefault: Boolean = true) {
    init {
        if (bitness != 32 && bitness != 64) {
            errorln(CxxDiagnosticCode.ABI_IS_INVALID, "ABI ${abi.tag} had an invalid value: $bitness")
        }
    }
}

