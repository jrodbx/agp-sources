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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.utils.cxx.CxxDiagnosticCode.NDK_CORRUPTED
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader

/**
 * <pre>
 *
 * Read and parse the NDK file meta/abis.json. This file contains a list of ABIs supported by this
 * NDK along with relevant metadata.
 *
 * {
 *    "armeabi-v7a": {
 *       "bitness": 32,
 *       "default": true,
 *       "deprecated": false
 *     },
 *     etc.
 *  }
 *
 *  </pre>
 */
class NdkAbiFile(abiFile: File) {
    private val mapTypeToken = object : TypeToken<Map<String, AbiInfo>>() {}.type
    val abiInfoList: List<AbiInfo>

    init {
        abiInfoList = if (abiFile.isFile) FileReader(abiFile).use { reader ->
            try {
                Gson().fromJson<Map<String, AbiInfo>>(reader, mapTypeToken)
                    .entries.mapNotNull { entry ->
                    val abi = Abi.getByName(entry.key)
                    if (abi == null) {
                        warnln(
                            "Ignoring invalid ABI '${entry.key}' found in ABI " +
                                    "metadata file '$abiFile'."
                        )
                        null

                    } else {
                        AbiInfo(
                            abi = abi,
                            bitness = entry.value.bitness,
                            isDeprecated = entry.value.isDeprecated,
                            isDefault = entry.value.isDefault
                        )
                    }
                }
            } catch (e: Throwable) {
                errorln(NDK_CORRUPTED, "Could not parse '$abiFile'.")
                fallbackAbis()
            }

        } else {
            fallbackAbis()
        }
    }

    /**
     * Produce a default set of ABIs if there was a problem.
     */
    private fun fallbackAbis() = Abi.values().map { AbiInfo(
        abi = it,
        bitness = if(it.supports64Bits()) 64 else 32,
        isDeprecated = false,
        isDefault = true
    ) }
}

/**
 * Given an NDK root file path, return the name of the ABI metadata JSON file.
 */
fun ndkMetaAbisFile(ndkRoot: File): File {
    return File(ndkRoot, "meta/abis.json")
}
