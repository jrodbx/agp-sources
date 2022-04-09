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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.PlatformConfigurator
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.repository.Revision
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.base.Preconditions.checkState
import com.google.common.collect.Maps
import org.gradle.api.InvalidUserDataException
import java.io.File
import java.util.Locale
import kotlin.streams.toList

/** Default NdkInfo. Used for r13 and earlier.  */
open class DefaultNdkInfo(protected val rootDirectory: File) : NdkInfo {

    private val platformConfigurator: PlatformConfigurator = PlatformConfigurator(rootDirectory)

    private val abiInfoList: List<AbiInfo> = NdkAbiFile(ndkMetaAbisFile(rootDirectory)).abiInfoList

    private val defaultToolchainVersions = Maps.newHashMap<Abi, String>()

    override fun findSuitablePlatformVersion(
        abi: String,
        androidVersion: AndroidVersion?
    ): Int {
        return platformConfigurator.findSuitablePlatformVersion(abi, androidVersion)
    }

    private fun getToolchainPrefix(abi: Abi): String {
        return abi.gccToolchainPrefix
    }

    protected val hostTag: String by lazy {
        val osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
        val osType = when {
            osName.contains("windows") -> "windows"
            osName.contains("mac") -> "darwin"
            else -> "linux"
        }

        val checkTags = mutableListOf("$osType-x86_64")
        if (osType == "windows") {
            // 64-bit should be preferred, but check for 32-bit if it is not found.
            checkTags.add("windows")
        }

        val prebuiltBase = rootDirectory.resolve("prebuilt")
        val checkDirs = checkTags.map { prebuiltBase.resolve(it) }
        checkTags.find { prebuiltBase.resolve(it).isDirectory } ?: throw InvalidUserDataException(
            "Could not determine NDK host architecture. None of the following directories " +
                    "exist: ${checkDirs.joinToString(",")}"
        )
    }

    /**
     * Return the directory containing the toolchain.
     *
     * @param abi target ABI of the toolchains
     * @return a directory that contains the executables.
     */
    private fun getToolchainPath(abi: Abi): File {
        val toolchainAbi = getToolchainAbi(abi)
        val version = getDefaultToolchainVersion(toolchainAbi).let {
            // TODO: Why would this ever be empty?
            // AIUI this will only ever be the GCC toolchain version (which is not the correct
            // result for r19+, but historically is correct), and that should never be empty.
            if (it.isEmpty()) "" else "-$it"
        }

        val prebuiltFolder = rootDirectory.resolve(
            "toolchains/${getToolchainPrefix(toolchainAbi)}$version/prebuilt/$hostTag"
        )

        if (!prebuiltFolder.isDirectory) {
            throw InvalidUserDataException("Toolchain directory does not exist: $prebuiltFolder")
        }

        return prebuiltFolder
    }

    protected open fun getToolchainAbi(abi: Abi): Abi {
        return abi
    }

    /** Return the executable for removing debug symbols from a shared object.  */
    override fun getStripExecutable(abi: Abi): File {
        val toolchainAbi = getToolchainAbi(abi)
        return FileUtils.join(
            getToolchainPath(toolchainAbi), "bin", toolchainAbi.gccExecutablePrefix + "-strip"
        )
    }

    /** Return the executable for extracting debug metadata from a shared object.  */
    override fun getObjcopyExecutable(abi: Abi): File {
        val toolchainAbi = getToolchainAbi(abi)
        return FileUtils.join(
            getToolchainPath(toolchainAbi), "bin", toolchainAbi.gccExecutablePrefix + "-objcopy"
        )
    }

    /**
     * Return the default version of the specified toolchain for a target abi.
     *
     *
     * The default version is the highest version found in the NDK for the specified toolchain
     * and ABI. The result is cached for performance.
     */
    private fun getDefaultToolchainVersion(abi: Abi): String {
        val toolchainAbi = getToolchainAbi(abi)
        val defaultVersion = defaultToolchainVersions[toolchainAbi]
        if (defaultVersion != null) {
            return defaultVersion
        }

        val toolchainPrefix = getToolchainPrefix(toolchainAbi)
        val toolchains = File(rootDirectory, "toolchains")
        val toolchainsForAbi =
            toolchains.listFiles { _, filename -> filename.startsWith(toolchainPrefix) }
        if (toolchainsForAbi == null || toolchainsForAbi.isEmpty()) {
            throw RuntimeException(
                "No toolchains found in the NDK toolchains folder for ABI with prefix: $toolchainPrefix"
            )
        }

        // Once we have a list of toolchains, we look the highest version
        var bestRevision: Revision? = null
        var bestVersionString = ""
        for (toolchainFolder in toolchainsForAbi) {
            val folderName = toolchainFolder.name

            var revision = Revision(0)
            var versionString = ""
            if (folderName.length > toolchainPrefix.length + 1) {
                // Find version if folderName is in the form {prefix}-{version}
                try {
                    versionString = folderName.substring(toolchainPrefix.length + 1)
                    revision = Revision.parseRevision(versionString)
                } catch (ignore: NumberFormatException) {
                }

            }
            if (bestRevision == null || revision > bestRevision) {
                bestRevision = revision
                bestVersionString = versionString
            }
        }
        defaultToolchainVersions[toolchainAbi] = bestVersionString
        return bestVersionString
    }

    override val default32BitsAbis  get() =
        abiInfoList
            .stream()
            .filter { abiInfo -> abiInfo.isDefault && !abiInfo.isDeprecated }
            .map { it.abi }
            .filter { abi -> !abi.supports64Bits() }
            .toList()

    override val defaultAbis get() =
        abiInfoList
            .stream()
            .filter { abiInfo -> abiInfo.isDefault && !abiInfo.isDeprecated }
            .map<Abi>{ it.abi }
            .toList()

    override val supported32BitsAbis get() =
        abiInfoList
            .stream()
            .map  { it.abi }
            .filter { abi -> !abi.supports64Bits() }
            .toList()

    override val supportedAbis get() =
        abiInfoList
            .stream()
            .map{ it.abi }
            .toList()

    override val supportedStls = Stl.values().toList()

    override fun getDefaultStl(buildSystem: NativeBuildSystem): Stl = when (buildSystem) {
        NativeBuildSystem.CMAKE -> Stl.GNUSTL_STATIC
        NativeBuildSystem.NDK_BUILD -> Stl.SYSTEM
        else -> error("$buildSystem")
    }

    override fun getStlSharedObjectFile(stl: Stl, abi: Abi): File {
        val stlBasePath = rootDirectory.resolve(when (stl) {
            Stl.LIBCXX_SHARED -> "sources/cxx-stl/llvm-libc++"
            Stl.GNUSTL_SHARED -> "sources/cxx-stl/gnu-libstdc++/4.9"
            Stl.STLPORT_SHARED -> "sources/cxx-stl/stlport"
            else -> throw RuntimeException("Unexpected STL for packaging: $stl")
        })

        val file = stlBasePath.resolve("libs/${abi.tag}/${stl.libraryName}")
        checkState(file.isFile, "Expected NDK STL shared object file at $file")
        return file
    }

    override fun validate(): String? {
        val platformsDir = rootDirectory.resolve("platforms")
        if (!platformsDir.isDirectory) {
            return "$platformsDir is not a directory."
        }
        val toolchainsDir = rootDirectory.resolve("toolchains")
        if (!toolchainsDir.isDirectory) {
            return "$toolchainsDir is not a directory."
        }
        return null
    }
}
