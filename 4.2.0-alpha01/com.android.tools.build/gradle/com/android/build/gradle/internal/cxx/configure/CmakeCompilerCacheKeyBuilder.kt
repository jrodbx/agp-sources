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

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.BinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeListsPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.GeneratorName
import com.android.build.gradle.internal.cxx.logging.warnln
import java.io.File

/**
 * Convert a CMake command-line into a compiler hash key. Removes black-listed
 * flags and keeps unrecognized flags in case they are relevant to compiler
 * settings.
 */
fun makeCmakeCompilerCacheKey(commandLine : List<CommandLineArgument>) : CmakeCompilerCacheKey? {
    val (args, ndkInstallationFolder) = commandLine
        .asSequence()
        .removeBlackListedProperties()
        .removeBlacklistedFlags()
        .findAndroidNdk()
    if (ndkInstallationFolder == null) {
        warnln("$ANDROID_NDK property was not defined")
        return null
    }
    val sourceProperties = readSourceProperties(ndkInstallationFolder) ?: return null
    return CmakeCompilerCacheKey(
        ndkInstallationFolder,
        sourceProperties,
        replaceAndroidNdkInProperties(args, ndkInstallationFolder))
}

/**
 * Remove properties that shouldn't affect the outcome of the compiler settings.
 */
private fun Sequence<CommandLineArgument>.removeBlackListedProperties() : Sequence<CommandLineArgument> {
    return asSequence()
        .filter { argument ->
            when (argument) {
                is DefineProperty -> {
                    when {
                        CMAKE_COMPILER_CHECK_CACHE_KEY_BLACKLIST_STRINGS
                            .contains(argument.propertyName) -> false
                        else -> true
                    }
                }
                else -> true
            }
        }
}

/**
 * Remove flags that shouldn't affect the outcome of the compiler settings
 */
private fun Sequence<CommandLineArgument>.removeBlacklistedFlags() : Sequence<CommandLineArgument> {
    return asSequence()
        .filter { argument ->
            when (argument) {
                is GeneratorName -> false
                is BinaryOutputPath -> false
                is CmakeListsPath -> false
                else -> true
            }
        }
}

/**
 * Find the ANDROID_NDK property and record it in the key.
 * Remove the property from args.
 */
private fun Sequence<CommandLineArgument>.findAndroidNdk()
        : Pair<List<String>, File?> {
    var androidNdkFolder: File? = null
    val args = filter { argument ->
                when (argument) {
                    is DefineProperty -> {
                        if (argument.propertyName == ANDROID_NDK.name) {
                            androidNdkFolder = File(argument.propertyValue)
                            false
                        } else {
                            true
                        }
                    }
                    else -> true
                }
            }
            .map { it.sourceArgument }.toList()
    return Pair(args, androidNdkFolder)
}

/**
 * Try to find the NDK's source.properties file, read it, and record the settings as part of
 * the key. Returns null if not found
 */
private fun readSourceProperties(ndkInstallationFolder : File) :  SdkSourceProperties? {
    val sourceProperties = File(ndkInstallationFolder, "source.properties")
    if (!sourceProperties.isFile) {
        warnln("ANDROID_NDK location ($ndkInstallationFolder) had no source.properties")
        return null
    }
    return SdkSourceProperties.fromInstallFolder(ndkInstallationFolder)
}

/**
 * Replace literal cases of the NDK path with ${ANDROID_NDK} so that the physical location of the
 * NDK is only determined by the ndkInstallationFolder portion of the key.
 */
private fun replaceAndroidNdkInProperties(args : List<String>, ndkInstallationFolder: File)
        : List<String> {
    return args.map { arg ->
        arg.replace(ndkInstallationFolder.path, "\${$ANDROID_NDK}")
    }
}
