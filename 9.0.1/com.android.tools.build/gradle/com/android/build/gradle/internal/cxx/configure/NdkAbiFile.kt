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

import com.android.SdkConstants
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.internal.ndk.NullableAbiInfo
import com.android.utils.cxx.CxxDiagnosticCode.NDK_CORRUPTED
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.Reader

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

    val abiInfoList: List<AbiInfo>


    init {
        abiInfoList = if (abiFile.isFile) FileReader(abiFile).use { reader ->
            try {
                parseAbiJson(reader, abiFile.toString())
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
    private fun fallbackAbis() = Abi.values()
        .filter { it.isFallbackAbi }
        .map { AbiInfo(
            name = it.tag,
            bitness = it.bitness,
            architecture = it.architecture,
            isDeprecated = false,
            isDefault = true,
            triple = it.triple,
            llvmTriple = it.llvmTriple
        ) }
}

/**
 * Given an NDK root file path, return the name of the ABI metadata JSON file.
 */
fun ndkMetaAbisFile(ndkRoot: File): File {
    return File(ndkRoot, "meta/abis.json")
}

private val mapTypeToken = object : TypeToken<Map<String, NullableAbiInfo>>() {}.type
fun parseAbiJson(reader : Reader, abiFile : String) : List<AbiInfo> {
    return Gson().fromJson<Map<String, NullableAbiInfo>>(reader, mapTypeToken)
        .entries.mapNotNull { entry ->
            val raw = entry.value
            val abi = Abi.getByName(entry.key)
            if (abi == null) {
                if (raw.architecture == null) {
                    warnln("Ignoring ABI '${entry.key}' found in ABI metadata file '$abiFile' because it had no 'arch' field.")
                    null
                } else if (raw.triple == null) {
                    warnln("Ignoring ABI '${entry.key}' found in ABI metadata file '$abiFile' because it had no 'triple' field.")
                    null
                } else if (raw.bitness == null) {
                    warnln("Ignoring ABI '${entry.key}' found in ABI metadata file '$abiFile' because it had no 'bitness' field.")
                    null
                } else if (raw.llvmTriple == null) {
                    warnln("Ignoring ABI '${entry.key}' found in ABI metadata file '$abiFile' because it had no 'llvm_triple' field.")
                    null
                } else{
                    infoln("ABI '${entry.key}' found in ABI metadata file '$abiFile' was not known in advance.")
                    AbiInfo(
                        name = entry.key,
                        bitness = raw.bitness,
                        isDefault = raw.isDefault ?: true,
                        isDeprecated = raw.isDeprecated ?: false,
                        architecture = raw.architecture,
                        triple = raw.triple,
                        llvmTriple = raw.llvmTriple
                    )
                }

            } else {

                    AbiInfo(
                        name = entry.key,
                        bitness = raw.bitness ?: abi.bitness,
                        isDefault = raw.isDefault ?: true,
                        isDeprecated = raw.isDeprecated ?: false,
                        architecture = raw.architecture ?: abi.architecture,
                        triple = raw.triple ?: abi.triple,
                        llvmTriple = raw.llvmTriple ?: abi.llvmTriple
                    )
                }
            }
        }

private val Abi.bitness : Int get() = when(this) {
    Abi.X86 -> 32
    Abi.MIPS -> 32
    Abi.ARMEABI -> 32
    Abi.ARMEABI_V7A -> 32
    else -> 64
}

private val Abi.isFallbackAbi get() = when(this) {
    Abi.ARMEABI,
    Abi.ARMEABI_V7A,
    Abi.ARM64_V8A,
    Abi.MIPS,
    Abi.MIPS64,
    Abi.X86,
    Abi.X86_64 -> true
    else -> false
}

val Abi.architecture : String get() = when(this) {
    Abi.ARMEABI -> "arm"
    Abi.ARMEABI_V7A -> "arm"
    Abi.ARM64_V8A -> "arm64"
    Abi.MIPS -> "mips"
    Abi.MIPS64 -> "mips64"
    Abi.X86 -> "x86"
    Abi.X86_64 -> "x86_64"
    else -> error("Should only use for fallback")
}

val Abi.triple : String get() = when(this) {
    Abi.ARMEABI -> "arm-linux-androideabi"
    Abi.ARMEABI_V7A -> "arm-linux-androideabi"
    Abi.ARM64_V8A -> "aarch64-linux-android"
    Abi.MIPS -> "mipsel-linux-android"
    Abi.MIPS64 -> "mips64el-linux-android"
    Abi.X86 -> "i686-linux-android"
    Abi.X86_64 -> "x86_64-linux-android"
    else -> error("Should only use for fallback")
}

private val Abi.llvmTriple : String get() = when(this) {
    Abi.ARMEABI_V7A -> "armv7-none-linux-androideabi"
    Abi.ARM64_V8A -> "aarch64-none-linux-android"
    Abi.X86 -> "i686-none-linux-android"
    Abi.X86_64 -> "x86_64-none-linux-android"
    else -> "unknown-llvm-triple"
}

