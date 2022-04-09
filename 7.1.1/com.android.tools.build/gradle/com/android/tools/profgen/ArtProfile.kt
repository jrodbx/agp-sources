/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.profgen

import java.io.*
import java.util.*

internal val MAGIC = byteArrayOf('p', 'r', 'o', '\u0000')


class ArtProfile internal constructor(
    internal val profileData: Map<DexFile, DexFileData>,
) {
    fun print(os: PrintStream, obf: ObfuscationMap) {
        for ((dexFile, data) in profileData) {
            for (typeIndex in data.classes) {
                val type = dexFile.typePool[typeIndex]
                obf.deobfuscate(type).forEach { os.println(it) }
            }
            for ((methodIndex, methodData) in data.methods) {
                val method = dexFile.methodPool[methodIndex]
                val deobfuscated = obf.deobfuscate(method)
                methodData.print(os)
                deobfuscated.print(os)
                os.println()
            }
        }
    }

    /**
     * Serializes the profile in the given output stream.
     *
     * @param os the output stream
     * @param version the serialization format/version to use
     */
    fun save(os: OutputStream, version: ArtProfileSerializer) {
        with(os) {
            write(MAGIC)
            write(version.bytes)
            version.write(os, profileData)
        }
    }
}

fun ArtProfile(hrp: HumanReadableProfile, obf: ObfuscationMap, apk: Apk): ArtProfile {
    return ArtProfile(hrp, obf, apk.dexes)
}

fun ArtProfile(hrp: HumanReadableProfile, obf: ObfuscationMap, dexes: List<DexFile>): ArtProfile {
    val profileData = HashMap<DexFile, DexFileData>()
    for (iDex in dexes.indices) {
        val dex = dexes[iDex]
        val methods = dex.methodPool
        val types = dex.typePool
        val classDefs = dex.classDefPool

        val profileClasses = mutableSetOf<Int>()
        val profileMethods = mutableMapOf<Int, MethodData>()

        for (iMethod in methods.indices) {
            val method = methods[iMethod]
            val deobfuscated = obf.deobfuscate(method)
            val flags = hrp.match(deobfuscated)
            if (flags != 0) {
                profileMethods[iMethod] = MethodData(flags)
            }
        }

        for (typeIndex in classDefs) {
            val type = types[typeIndex]
            if (obf.deobfuscate(type).any { hrp.match(it) != 0 }) {
                profileClasses.add(typeIndex)
            }
        }

        if (profileClasses.isNotEmpty() || profileMethods.isNotEmpty()) {
            profileData[dex] = DexFileData(profileClasses, profileMethods)
        }
    }
    return ArtProfile(profileData)
}

fun ArtProfile(src: InputStream): ArtProfile? {
    val version = src.readProfileVersion() ?: return null
    val profileData = version.read(src)
    return ArtProfile(profileData)
}

fun ArtProfile(file: File): ArtProfile? = file.inputStream().use { ArtProfile(it) }

fun ArtProfile.save(file: File, version: ArtProfileSerializer) {
    file.outputStream().use {
        save(it, version)
    }
}

internal fun InputStream.readProfileVersion(): ArtProfileSerializer? {
    val fileMagic = read(MAGIC.size)
    if (!MAGIC.contentEquals(fileMagic)) error("Invalid magic")
    val version = read(ArtProfileSerializer.size)
    return ArtProfileSerializer.values().firstOrNull { it.bytes.contentEquals(version) }
}
