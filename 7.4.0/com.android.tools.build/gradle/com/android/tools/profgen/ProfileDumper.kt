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
    apk: Apk, obf: ObfuscationMap,
    strict: Boolean = true,
) {
    for ((dexFile, dexFileData) in profile.profileData) {
        val file = apk.dexes.find { it.name == dexFile.name }
          ?: if (strict) {
              throw IllegalStateException("Cannot find Dex File ${dexFile.name}")
          } else {
              continue
          }
        for ((key, method) in dexFileData.methods) {
            if (method.flags != 0) {
                val dexMethod = file.methodPool[key]
                val deobfuscated = obf.deobfuscate(dexMethod)
                method.print(os)
                deobfuscated.print(os)
                os.append('\n')
            }
        }
        for (key in dexFileData.typeIndexes) {
            val dexClass = file.typePool[key]
            val deobfuscated = obf.deobfuscate(dexClass)
            for (type in deobfuscated) {
                os.append(type)
                os.append('\n')
            }
        }
    }
}

fun dumpProfile(file: File, profile: ArtProfile, apk: Apk, obf: ObfuscationMap) {
    dumpProfile(file.outputStream().bufferedWriter(), profile, apk, obf)
}
