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

package com.android.build.gradle.internal.cxx.stripping

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.ndk.NdkHandler
import java.io.File
import java.io.Serializable

/**
 * This class is responsible for locating symbol strip tool withing the NDK.
 */
class SymbolStripExecutableFinder(val stripExecutables: Map<Abi, File>): Serializable {

    companion object {
        private const val serialVersionUID = 4L
    }

    /**
     * Return the collection of strip tools that we know about.
     */
    fun executables(): Collection<File> {
        return stripExecutables.values
    }

    /**
     * This method finds the path to the strip executable for a given ABI. Some basic checks are
     * done and if a problem is find then report will be invoked with an appropriate message.
     *
     * @return The path to the exe or null if it couldn't be located.
     */
    fun stripToolExecutableFile(
            input: File,
            abi: Abi?,
            reportAndFallback: (String) -> File?): File? {
        if (abi == null) {
            return reportAndFallback("Unable to strip library '${input.absolutePath}' due to " +
                    "unknown ABI.")
        }
        return stripExecutables[abi]
                ?: return reportAndFallback(
                        "Unable to strip library '${input.absolutePath}' due to missing strip " +
                                "tool for ABI '$abi'.")
    }
}

/**
 * Construct a @SymbolStripExecutableFinder from and NdkHandler
 */
fun createSymbolStripExecutableFinder(ndkHandler: NdkHandler): SymbolStripExecutableFinder {
    if (!ndkHandler.ndkPlatform.isConfigured) {
        return SymbolStripExecutableFinder(mapOf())
    }
    val stripExecutables = mutableMapOf<Abi, File>()
    for (abi in ndkHandler.ndkPlatform.getOrThrow().supportedAbis) {
        stripExecutables[abi] = ndkHandler.ndkPlatform.getOrThrow().ndkInfo.getStripExecutable(abi)
    }
    return SymbolStripExecutableFinder(stripExecutables)
}