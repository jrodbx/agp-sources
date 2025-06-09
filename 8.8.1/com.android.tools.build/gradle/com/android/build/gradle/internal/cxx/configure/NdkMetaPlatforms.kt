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

import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode.NDK_CORRUPTED
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.Reader

data class NdkMetaPlatforms(
    val min : Int = potentialPlatforms.first,
    val max : Int = potentialPlatforms.last,
    val aliases : Map<String, Int> = mapOf()) {

    init {
        if (!potentialPlatforms.contains(min)) {
            errorln(NDK_CORRUPTED, "potentialPlatforms range needs to include $min")
        }
        if (!potentialPlatforms.contains(max)) {
            errorln(NDK_CORRUPTED, "potentialPlatforms range needs to include $max")
        }
    }

    companion object {

        /**
         * Theoretical range of platforms across all NDKs. Used to place limits on possible
         * platforms when the meta/platforms.json hasn't yet been read.
         */
        val potentialPlatforms : IntRange = (0 until 64)

        /**
         * Given an NDK root file path, return the name of the platforms metadata JSON file.
         */
        fun jsonFile(ndkRoot: File): File {
            return File(ndkRoot, "meta/platforms.json")
        }

        /**
         * Given a reader to reference some JSON convert to NdkMetaPlatforms.
         */
        fun fromReader(reader : Reader) : NdkMetaPlatforms {
            val mapTypeToken = object : TypeToken<NdkMetaPlatforms>() {}.type
            return Gson().fromJson(reader, mapTypeToken)
        }
    }
}

