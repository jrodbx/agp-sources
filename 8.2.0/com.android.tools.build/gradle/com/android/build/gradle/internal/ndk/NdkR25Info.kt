/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants.PLATFORM_DARWIN
import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.currentPlatform
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.PlatformConfigurator
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.ndk.Stl.LIBCXX_SHARED
import com.android.build.gradle.internal.ndk.Stl.LIBCXX_STATIC
import com.android.build.gradle.internal.ndk.Stl.NONE
import com.android.build.gradle.internal.ndk.Stl.SYSTEM
import com.android.build.gradle.internal.ndk.Stl.UNKNOWN
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.build.gradle.tasks.NativeBuildSystem.NINJA
import com.android.sdklib.AndroidVersion
import java.io.File

/**
 * NdkInfo for r25.
 */
class NdkR25Info(private val rootDirectory: File) : NdkInfo {

    private val platformConfigurator = PlatformConfigurator(rootDirectory)
    private val abiInfoList = NdkAbiFile(ndkMetaAbisFile(rootDirectory)).abiInfoList

    override val default32BitsAbis  get() =
        abiInfoList
            .filter { abiInfo -> abiInfo.isDefault && !abiInfo.isDeprecated && abiInfo.bitness == 32 }
            .map { it.name }
            .toList()

    override val defaultAbis get() =
        abiInfoList
            .filter { abiInfo -> abiInfo.isDefault && !abiInfo.isDeprecated }
            .map { it.name }
            .toList()

    override val supported32BitsAbis get() =
        abiInfoList
            .filter { abiInfo -> abiInfo.bitness == 32 }
            .map  { it.name }
            .toList()

    override val supportedAbis get() =
        abiInfoList
            .map { it.name }
            .toList()

    override val supportedStls = listOf(
        LIBCXX_SHARED,
        LIBCXX_STATIC,
        NONE,
        SYSTEM
    )

    override fun findSuitablePlatformVersion(
        abi: String,
        androidVersion: AndroidVersion?
    ) : Int {
        return platformConfigurator.findSuitablePlatformVersion(abi, abiInfoList, androidVersion)
    }

    override fun getDefaultStl(buildSystem: NativeBuildSystem): Stl = when (buildSystem) {
        CMAKE -> LIBCXX_STATIC
        NDK_BUILD -> SYSTEM
        NINJA -> UNKNOWN // Ninja generating script decides its own STL.
    }

    private val hostTag: String by lazy {
        when(currentPlatform()) {
            PLATFORM_WINDOWS -> "windows-x86_64"
            PLATFORM_DARWIN-> "darwin-x86_64"
            else -> "linux-x86_64"
        }
    }

    override fun getStripExecutable(abi: String) = rootDirectory.resolve(
        "toolchains/llvm/prebuilt/$hostTag/bin/llvm-strip"
    )

    override fun getObjcopyExecutable(abi: String) = rootDirectory.resolve(
        "toolchains/llvm/prebuilt/$hostTag/bin/llvm-objcopy"
    )

    override fun getStlSharedObjectFile(stl: Stl, abi: String): File {
        val info = abiInfoList.single { it.name == abi }
        // https://android.googlesource.com/platform/ndk/+/master/docs/BuildSystemMaintainers.md#stl
        return rootDirectory.resolve(
            "toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/${info.triple}/${stl.libraryName}"
        )
    }

    override fun validate() = null
}
