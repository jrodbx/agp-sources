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
    private val apkName: String = ""
) {
    fun print(os: PrintStream, obf: ObfuscationMap) {
        for ((dexFile, data) in profileData) {
            for (typeIndex in data.typeIndexes) {
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
            write(version.magicBytes)
            write(version.versionBytes)
            version.write(os, profileData, apkName)
        }
    }

    private fun extractKey(key: String): String {
        val result =  key.substringAfter('!').substringAfter(':')
        assert(result.indexOf(':') == -1)
        assert(result.indexOf('!') == -1)
        return result
    }

    operator fun plus(other: ArtProfile): ArtProfile {
        val values = mutableMapOf<String, DexFileData>()
        val files = mutableMapOf<String, DexFile>()
        for ((file, value) in profileData) {
            val key = extractKey(file.name)
            files[key] = file
            values[key] = value
        }
        for ((file, value) in other.profileData) {
            val key = extractKey(file.name)
            files.putIfAbsent(key, file)
            values[key] = value + values[key]
        }
        return ArtProfile(
                values.map { (key, value) ->
                    val file = files[key]
                    if (file == null) null
                    else
                        file to value
                }
                        .filterNotNull()
                        .toMap(),
                apkName
        )
    }
}

fun ArtProfile(hrp: HumanReadableProfile, obf: ObfuscationMap, apk: Apk): ArtProfile {
    return ArtProfile(hrp, obf, apk.dexes, apk.name)
}

fun ArtProfile(
        hrp: HumanReadableProfile,
        obf: ObfuscationMap,
        dexes: List<DexFile>,
        apkName: String = ""
): ArtProfile {
    val profileData = HashMap<DexFile, DexFileData>()
    for (iDex in dexes.indices) {
        val dex = dexes[iDex]
        val methods = dex.methodPool
        val types = dex.typePool
        val classDefs = dex.classDefPool

        val profileTypeIndexes = mutableSetOf<Int>()
        val profileClassIndexes = mutableSetOf<Int>()
        val profileMethods = mutableMapOf<Int, MethodData>()

        for (iMethod in methods.indices) {
            val method = methods[iMethod]
            val deobfuscated = obf.deobfuscate(method)
            val flags = hrp.match(deobfuscated)
            if (flags != 0) {
                profileMethods[iMethod] = MethodData(flags)
            }
        }

        for (classIndex in classDefs.indices) {
            val typeIndex = classDefs[classIndex]
            val type = types[typeIndex]
            if (obf.deobfuscate(type).any { hrp.match(it) != 0 }) {
                profileTypeIndexes.add(typeIndex)
                profileClassIndexes.add(classIndex)
            }
        }

        if (profileTypeIndexes.isNotEmpty() || profileMethods.isNotEmpty()) {
            profileData[dex] = DexFileData(
                    profileTypeIndexes,
                    profileClassIndexes,
                    profileMethods
            )
        }
    }
    return ArtProfile(profileData, apkName)
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
    val fileMagic = read(ArtProfileSerializer.size)
    val version = read(ArtProfileSerializer.size)
    if (ArtProfileSerializer.values().none { it.magicBytes.contentEquals(fileMagic) }) {
        error("Invalid magic")
    }
    return ArtProfileSerializer.values().firstOrNull {
        it.magicBytes.contentEquals(fileMagic) &&
        it.versionBytes.contentEquals(version)
    }
}
