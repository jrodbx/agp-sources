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
    val name: String,
    val bitness: Int,
    val isDefault: Boolean,
    val isDeprecated: Boolean,
    val architecture: String,
    val triple: String,
    val llvmTriple: String) {
    init {
        if (bitness != 32 && bitness != 64) {
            errorln(CxxDiagnosticCode.ABI_IS_INVALID, "ABI $name had an invalid value: $bitness")
        }
    }
}

/**
 * ABI information as read directly from meta/abis.json.
 * Fields that are allowed to be missing in any version of the NDK are nullable.
 * There may also be extra fields compared to [AbiInfo]. These are fields from meta/abis.json
 * that we haven't had a need for in AGP yet.
 */
data class NullableAbiInfo(
    @SerializedName("bitness")
    val bitness: Int?,
    @SerializedName("default")
    val isDefault: Boolean?,
    @SerializedName("deprecated")
    val isDeprecated: Boolean?,
    @SerializedName("proc")
    val processor: String?,
    @SerializedName("arch")
    val architecture: String?,
    @SerializedName("triple")
    val triple: String?,
    @SerializedName("llvm_triple")
    val llvmTriple: String?)

