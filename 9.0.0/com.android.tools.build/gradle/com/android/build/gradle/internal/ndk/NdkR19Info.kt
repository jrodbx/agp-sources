/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.google.common.base.Preconditions.checkArgument
import com.google.common.base.Preconditions.checkState
import java.io.File

/**
 * NdkInfo for r19.
 */
open class NdkR19Info(val root: File) : DefaultNdkInfo(root) {

    // TODO: Add metadata to the NDK to populate this list?
    // https://github.com/android-ndk/ndk/issues/966
    override val supportedStls = listOf(
        Stl.LIBCXX_SHARED,
        Stl.LIBCXX_STATIC,
        Stl.NONE,
        Stl.SYSTEM
    )

    override fun getToolchainAbi(abi: String): String {
        return if (abi == Abi.MIPS.tag) {
            Abi.MIPS64.tag
        } else abi
    }

    override fun getDefaultStl(buildSystem: NativeBuildSystem): Stl = when (buildSystem) {
        NativeBuildSystem.CMAKE -> Stl.LIBCXX_STATIC
        NativeBuildSystem.NDK_BUILD -> Stl.SYSTEM
        // Ninja generating script decides its own STL.
        NativeBuildSystem.NINJA -> Stl.UNKNOWN
        else -> error("$buildSystem")
    }

    override fun getStlSharedObjectFile(stl: Stl, abi: String): File {
        checkArgument(
            stl == Stl.LIBCXX_SHARED,
            "Only c++_shared is valid for packaging as of NDK r19"
        )
        checkArgument(abi in supportedAbis, "Unsupported ABI for NDK r19+: $abi")

        // https://android.googlesource.com/platform/ndk/+/master/docs/BuildSystemMaintainers.md#architectures
        val sysrootTriple = when (abi) {
            Abi.ARM64_V8A.tag -> "aarch64-linux-android"
            Abi.ARMEABI_V7A.tag -> "arm-linux-androideabi"
            Abi.X86.tag -> "i686-linux-android"
            Abi.X86_64.tag -> "x86_64-linux-android"
            else -> throw RuntimeException("Unsupported ABI for NDK r19+: $abi")
        }

        // https://android.googlesource.com/platform/ndk/+/master/docs/BuildSystemMaintainers.md#stl
        val file = rootDirectory.resolve(
            "toolchains/llvm/prebuilt/$hostTag/sysroot/usr/lib/$sysrootTriple/${stl.libraryName}"
        )
        checkState(file.isFile, "Expected NDK STL shared object file at $file")
        return file
    }

    override fun getStripExecutable(abi: String) : File {
        val triple = abiInfoList.single { info -> info.name == abi }.triple
        return rootDirectory.resolve(
            "toolchains/llvm/prebuilt/$hostTag/bin/$triple-strip"
        )
    }

    override fun getObjcopyExecutable(abi: String) : File {
        val triple = abiInfoList.single { info -> info.name == abi }.triple
        return rootDirectory.resolve(
            "toolchains/llvm/prebuilt/$hostTag/bin/$triple-objcopy"
        )
    }

    override fun validate(): String? {
        // Intentionally not calling super's validate. NDK r19 does not require many of the paths
        // required by prior NDKs.
        val toolchainsDir = rootDirectory.resolve("toolchains")
        if (!toolchainsDir.isDirectory) {
            return "$toolchainsDir is not a directory."
        }

        return null
    }
}
