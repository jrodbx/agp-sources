/*
 * Copyright (C) 2022 The Android Open Source Project
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

import java.io.File

fun dumpProfile(
        os: Appendable,
        profile: ArtProfile,
        apk: Apk,
        obf: ObfuscationMap,
        strict: Boolean = true,
) {
    for ((dexFile, dexFileData) in profile.profileData) {
        val file = apk.dexes.find { it.name == extractName(dexFile.name) }
                ?: if (strict) {
                    throw IllegalStateException("Cannot find Dex File ${dexFile.name}")
                } else {
                    continue
                }

        if (strict && !dexFile.compatibleWith(file)) {
            val message = """
                Profile header not compatible with the Dex header.
                -----------------------------------------------------------------------------------
                APK: ${apk.name}
                Dex: ${dexFile.name}
                -----------------------------------------------------------------------------------
                Dex Checksum: ${dexFile.dexChecksum}              | ${file.dexChecksum}
                Method ids  : ${dexFile.header.methodIds.size}    | ${file.header.methodIds.size}
                Type ids    : ${dexFile.header.typeIds.size}      | ${file.header.typeIds.size}
                -----------------------------------------------------------------------------------
            """.trimIndent()
            throw IllegalStateException(message)
        }

        // Even if the checksums don't match we can try and dump as much information as possible.

        for ((key, method) in dexFileData.methods) {
            // Method data is not guaranteed to exist given they might be stored as
            // extra descriptors.

            // Context:
            // java/com/google/android/art/profiles/ArtProfileLoaderForS.java;l=469;rcl=382185618
            val dexMethod = file.methodPool.getOrNull(key)
            if (method.flags != 0 && dexMethod != null) {
                val deobfuscated = obf.deobfuscate(dexMethod)
                method.print(os)
                deobfuscated.print(os)
                os.append('\n')
            }
        }

        for (key in dexFileData.typeIndexes) {
            val dexClass = file.typePool.getOrNull(key)
            if (dexClass != null) {
                val deobfuscated = obf.deobfuscate(dexClass)
                for (type in deobfuscated) {
                    os.append(type)
                    os.append('\n')
                }
            }
        }
    }
}

private fun DexFile.compatibleWith(other: DexFile): Boolean {
    val checkSumsMatch = dexChecksum == other.dexChecksum
    // We don't really care about offsets in profile headers.
    // They are only meaningful in dex file headers.
    val methodIdsMatch = header.methodIds.size == other.header.methodIds.size
    // Type Ids might not always be present
    val typeIdsMatch = header.typeIds.size == other.header.typeIds.size || header.typeIds.size <= 0
    return checkSumsMatch && methodIdsMatch && typeIdsMatch
}

fun dumpProfile(
        file: File,
        profile: ArtProfile,
        apk: Apk,
        obf: ObfuscationMap,
        strict: Boolean = true
) {
    val writer = file.outputStream().bufferedWriter()
    writer.use {
        dumpProfile(writer, profile, apk, obf, strict = strict)
    }
}

/**
 * Extracts the dex name from the incoming profile key.
 *
 * `base.apk!classes.dex` is a typical profile key.
 *
 * On Android O or lower, the delimiter used is a `:`.
 */
private fun extractName(profileKey: String): String {
    var index = profileKey.indexOf("!")
    if (index < 0) {
        index = profileKey.indexOf(":")
    }
    if (index < 0 && profileKey.endsWith(".apk")) {
        // `base.apk` is equivalent to `base.apk!classes.dex`
        return "classes.dex"
    }
    return profileKey.substring(index + 1)
}
