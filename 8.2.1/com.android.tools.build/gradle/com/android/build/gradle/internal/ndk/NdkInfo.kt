/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.sdklib.AndroidVersion
import java.io.File

/**
 * Interface describing the NDK.
 */
interface NdkInfo {

    val default32BitsAbis: Collection<String>

    val defaultAbis: Collection<String>

    val supported32BitsAbis: Collection<String>

    val supportedAbis: Collection<String>

    val supportedStls: Collection<Stl>

    fun findSuitablePlatformVersion(
        abi: String,
        androidVersion: AndroidVersion?
    ): Int

    /** Return the executable for removing debug symbols from a shared object.  */
    fun getStripExecutable(abi: String): File

    /** Return the executable for extracting debug metadata from a shared object.  */
    fun getObjcopyExecutable(abi: String): File

    /** Returns the default STL for the given build system. */
    fun getDefaultStl(buildSystem: NativeBuildSystem): Stl

    /** Returns the STL shared object file matching the given STL/ABI pair. */
    fun getStlSharedObjectFile(stl: Stl, abi: String): File

    /**
     * Returns a list of shared STL libraries to be included in the APK for the given configuration.
     *
     * @param stl A nullable string matching the APP_STL argument to ndk-build (or ANDROID_STL for
     *            CMake). If null, the default STL for the given NDK is used.
     * @param abis The collection of ABIs to return libraries for.
     */
    fun getStlSharedObjectFiles(stl: Stl, abis: Collection<String>): Map<String, File> {
        // Static STLs, system STLs, and non-STLs do not need to be packaged.
        if (!stl.requiresPackaging) {
            return emptyMap()
        }

        return abis.map { it to getStlSharedObjectFile(stl, it) }.toMap()
    }

    /**
     * Validates that the described NDK is valid.
     *
     * Performs a sanity check that the pointed-to NDK contains all the expected pieces. If any
     * issues are found, an error message is returned. If no issues are found, null is returned.
     */
    fun validate(): String?
}
