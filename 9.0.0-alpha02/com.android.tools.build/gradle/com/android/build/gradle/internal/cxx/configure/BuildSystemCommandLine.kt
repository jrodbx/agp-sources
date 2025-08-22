/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.cmake.isCmakeConstantTruthy
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeGeneratorName
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeListsPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineMultiProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.NdkBuildAppendProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.NdkBuildJobs
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.UnknownArgument
import com.android.utils.cxx.os.OsBehavior
import com.android.utils.cxx.os.createOsBehavior
import com.google.common.annotations.VisibleForTesting

/**
 * Classes and functions in this file are for dealing with CMake and ndk-build command-line
 * parameters. Any unrecognized flags are classified as UnknownArgument.
 */

/**
 * Interface that represents a single CMake or ndk-build command-line argument.
 * Argument types that are specific to only one build system are prefixed with Cmake (or in the
 * future NdkBuild).
 */
sealed class CommandLineArgument {
    abstract val sourceArgument : String

    /**
     * This is an argument that was not recognized.
     */
    data class UnknownArgument(override val sourceArgument : String) : CommandLineArgument()

    /**
     * For example, -H<path-to-cmake lists>
     *
     * This is the path to the folder that contains a CMakeLists.txt file.
     */
    data class CmakeListsPath(
        override val sourceArgument: String,
        val path : String) : CommandLineArgument() {
        companion object {
            /**
             * Create a CmakeListsPath from a path.
             * Don't use if you also have an an original sourceArgument.
             */
            @JvmStatic
            fun from(path : String) : CmakeListsPath {
                return CmakeListsPath("-H$path", path)
            }
        }
    }

    /**
     * For example, -B<path-to-binary-output-dir>
     *
     * This is the build output folder. This is where the Ninja project is generated.
     * For us, it usually has a value like .cxx/cmake/debug/x86.
     */
    data class CmakeBinaryOutputPath(
        override val sourceArgument: String,
        val path : String) : CommandLineArgument() {
        companion object {
            /**
             * Create a BinaryOutputPath from a path.
             * Don't use if you also have an an original sourceArgument.
             */
            @JvmStatic
            fun from(path : String) : CmakeListsPath {
                return CmakeListsPath("-B$path", path)
            }
        }
    }

    /**
     * For example, -GAndroid Gradle - Ninja
     *
     * The generator to use for this project.
     **/
    data class CmakeGeneratorName(
        override val sourceArgument: String,
        val generator : String) : CommandLineArgument() {
        companion object {
            /**
             * Create a GeneratorName.
             * Don't use if you also have an an original sourceArgument.
             */
            @JvmStatic
            fun from(generator : String) : CmakeGeneratorName {
                return CmakeGeneratorName("-G$generator", generator)
            }
        }
    }

    /**
     * For example, --jobs=4 or -j 8
     *
     * The number of jobs for this build.
     **/
    data class NdkBuildJobs(
        override val sourceArgument: String,
        val jobs : String) : CommandLineArgument()

    /**
     * For example, -DANDROID_PLATFORM=android-19
     *
     * Defines a build property passed in from the command-line.
     */
    data class DefineProperty(
        override val sourceArgument : String,
        val propertyName : String,
        val propertyValue : String) : CommandLineArgument() {
        companion object {
            /**
             * Create a DefineProperty from a name and value.
             * Don't use if you also have an an original sourceArgument.
             */
            @JvmStatic
            fun from(property : CmakeProperty, value : String) : DefineProperty {
                return DefineProperty("-D${property.name}=$value", property.name, value)
            }
        }
    }

    /**
     * For example, -property:WarningLevel=2;OutDir=bin\Debug
     *
     * Defines one or more build properties passed in from the command-line.
     */
    data class DefineMultiProperty(
        override val sourceArgument : String,
        val properties : Map<String, String>) : CommandLineArgument() {
    }

    /**
     * For example, APP_CFLAGS+=-DMY_FLAG
     *
     * An ndk-build build command to append a flag to the given list
     */
    data class NdkBuildAppendProperty(
            override val sourceArgument : String,
            val listProperty : String,
            val flagValue : String) : CommandLineArgument()
}

/**
 * Check whether a CMake flag looks combinable with the argument that immediately follows it.
 * See: https://cmake.org/cmake/help/latest/manual/cmake.1.html
 *
 * There are some flags that are definitely known to be combinable (see [cmakeKnownCombinable]).
 * There are some flags that are definitely known to not be combinable (see [cmakeKnownNotCombinable]).
 * For the remainder, a heuristic is used to decide whether it's combinable or not.
 *
 * Last updated CMake 3.18.1
 */
fun cmakeFlagLooksCombinable(flag: String) =
        // Is the flag in the allow-list of known combinable CMake flags?
        cmakeKnownCombinable.contains(flag) ||
                // Is the flag in the disallow-list of flags known to not be combinable?
                (!cmakeKnownNotCombinable.contains(flag) &&
                        // Heuristic to guess whether the flag is combinable or not.
                        flag.length == 2 && flag[0]=='-' && flag[1].isUpperCase())

private val cmakeKnownNotCombinable = listOf("-N")
private val cmakeKnownCombinable = listOf("-S", "-B", "-C", "-D", "-U", "-G", "-T", "-A")

/**
 * Parse a CMake command-line and returns the corresponding list of [CommandLineArgument].
 */
fun parseCmakeCommandLine(
    commandLine : String,
    host : OsBehavior = createOsBehavior()) : List<CommandLineArgument> {
    val combinedTokens =
        host.tokenizeCommandLineToEscaped(commandLine) zip
            host.tokenizeCommandLineToRaw(commandLine)
    var prior : Pair<String, String>? = null
    val result = mutableListOf<CommandLineArgument>()

    for(combinedToken in combinedTokens) {
        val (escaped, raw) = combinedToken
        when {
            prior != null -> {
                result +=
                    "${prior.first} $escaped".toCmakeArgument("${prior.second} $raw")
                prior = null
            }
            cmakeFlagLooksCombinable(escaped) -> prior = combinedToken
            else -> result += escaped.toCmakeArgument(raw)
        }
    }
    return result
}

/**
 * Parse a single CMake command line argument.
 */
fun String.toCmakeArgument(sourceArgument: String = this): CommandLineArgument {
    return when {
        /*
        Parse a property like -DX=Y. CMake supports typed properties as in -DX:STRING=Y but
        we don't currently need to support that. When these properties appear, the type will
        be passed through as part of the name.
         */
        startsWith("-D") && contains("=") -> {
            val propertyName = substringAfter("-D").substringBefore("=").trim()
            val propertyValue = substringAfter("=").trim()
            DefineProperty(sourceArgument, propertyName, propertyValue)
        }
        startsWith("-H") -> {
            val path = substringAfter("-H")
            CmakeListsPath(sourceArgument, path)
        }
        startsWith("-B") -> {
            val path = substringAfter("-B")
            CmakeBinaryOutputPath(sourceArgument, path)
        }
        startsWith("-G") -> {
            val path = substringAfter("-G")
            CmakeGeneratorName(sourceArgument, path)
        }
        else ->
            // Didn't recognize the flag so return unknown argument
            UnknownArgument(sourceArgument)
    }
}

/**
 * Parse a set of flags passed to CMake.
 */
fun List<String>.toCmakeArguments() = map { it.toCmakeArgument() }

/**
 * Parse a single ndk-build command line argument.
 */
fun String.toNdkBuildArgument(sourceArgument: String = this): CommandLineArgument {
    return when {
        // Parse an ndk-build command-line additive property like X+=Y
        !startsWith("-") && contains("+=") -> {
            val listProperty = substringBefore("+=").trim()
            val flagValue = substringAfter("+=").trim()
            NdkBuildAppendProperty(sourceArgument, listProperty, flagValue)
        }
        // Parse an ndk-build command-line property like X=Y
        !startsWith("-") && contains("=") -> {
            val propertyName = substringBefore("=").trim()
            val propertyValue = substringAfter("=").trim()
            DefineProperty(sourceArgument, propertyName, propertyValue)
        }
        startsWith("--jobs=") ->
            NdkBuildJobs(sourceArgument, substringAfter("=").trim())
        startsWith("--jobs ") || startsWith("-j ") ->
            NdkBuildJobs(sourceArgument, substringAfterLast(" ").trim())
        startsWith("-j") ->
            NdkBuildJobs(sourceArgument, substringAfter("-j").trim())
        else ->
            // Didn't recognize the flag so return unknown argument
            UnknownArgument(sourceArgument)
    }
}

/**
 * Parse a single MSBuild command-line argument.
 * See: https://docs.microsoft.com/en-us/visualstudio/msbuild/msbuild-command-line-reference
 */
fun String.toMSBuildArgument(sourceArgument: String = this): CommandLineArgument {
    fun propertyAfter(prefix : String) = run {
        DefineMultiProperty(sourceArgument,
            substringAfter(prefix).split(";").associate { body ->
                body.substringBefore("=").trim() to body.substringAfter("=").trim()
        })
    }
    return when {
        startsWith("-p:") && contains("=") -> propertyAfter("-p:")
        startsWith("/p:") && contains("=") -> propertyAfter("/p:")
        startsWith("-property:") && contains("=") -> propertyAfter("-property:")
        startsWith("/property:") && contains("=") -> propertyAfter("/property:")
        else -> UnknownArgument(sourceArgument)
    }
}

/**
 * Parse a set of flags passed to ndk-build.
 */
fun List<String>.toNdkBuildArguments() = map { it.toNdkBuildArgument() }

/**
 * Parse a set of flags passed to MSBuild.
 */
fun List<String>.toMSBuildArguments() = map { it.toMSBuildArgument() }

/**
 * Returns true when a set of args contains a property whose value would be considered true by
 * CMake. If a property is set more than one time then CMake behavior is to use the last. If
 * the property isn't in the list then returns null.
 */
fun List<CommandLineArgument>.getCmakeBooleanProperty(property : CmakeProperty) : Boolean? {
    val value = getCmakeProperty(property) ?: return null
    return isCmakeConstantTruthy(value)
}

/**
 * Returns the value of the property. Null if not present. If the value is present more than once
 * then the last value is taken.
 */
fun List<CommandLineArgument>.getCmakeProperty(property : CmakeProperty) =
    getProperty(property.name)

/**
 * Returns the value of the property. Null if not present. If the value is present more than once
 * then the last value is taken.
 */
fun List<CommandLineArgument>.getNdkBuildProperty(property : NdkBuildProperty) =
        getProperty(property.name)

/**
 * Returns the value of the property. Empty string if not present. If the value is present more t
 * han oncethen the last value is taken.
 */
fun List<CommandLineArgument>.getMSBuildProperty(property : MSBuildProperty) =
    getProperty(property.name)

/**
 * Returns the value of the property. Null if not present. If the value is present more than once
 * then the last value is taken.
 */
@VisibleForTesting
fun List<CommandLineArgument>.getProperty(property : String) =
    mapNotNull {
            when(it) {
                is DefineProperty -> if (it.propertyName == property) it.propertyValue else null
                is DefineMultiProperty -> it.properties[property]
                else -> null
            }
        }.lastOrNull()

/**
 * Returns the generator. Null if none present
 */
fun List<CommandLineArgument>.getCmakeGenerator() = filterType<CmakeGeneratorName>()?.generator

/**
 * Returns the folder of CMakeLists.txt.
 */
fun List<CommandLineArgument>.getCmakeListsFolder() = filterType<CmakeListsPath>()?.path

/**
 * Returns the buildRoot folder.
 */
fun List<CommandLineArgument>.getCmakeBinaryOutputPath() = filterType<CmakeBinaryOutputPath>()?.path

/**
 * Remove all instances of the given property from the list of args
 */
fun List<CommandLineArgument>.removeCmakeProperty(property : CmakeProperty) =
    filter {
        !(it is DefineProperty &&  it.propertyName == property.name)
    }

/**
 * Remove --jobs flag from list.
 */
fun List<CommandLineArgument>.removeNdkBuildJobs() = filter { it !is NdkBuildJobs }

/**
 * Utility method for filtering [CommandLineArgument] list by type and optional predicate.
 */
private inline fun <reified T : CommandLineArgument> List<CommandLineArgument>.filterType(
    predicate: (T) -> Boolean = { true } ) = filterIsInstance<T>().lastOrNull { predicate(it) }

/**
 * Convert to the equivalent command-line arguments String list.
 */
fun List<CommandLineArgument>.toStringList() = map { it.sourceArgument }

/**
 * Keep the [CommandLineArgument]s that should be passed to CMake Server. Remove the rest.
 */
fun List<CommandLineArgument>.onlyKeepCmakeServerArguments() =
    filter { argument ->
        when(argument) {
            is CmakeBinaryOutputPath,
            is CmakeListsPath,
            is CmakeGeneratorName -> false
            else -> true
        }
    }

/**
 * Keep the [CommandLineArgument]s that should be passed to CMake Server. Remove the rest.
 */
fun List<CommandLineArgument>.onlyKeepProperties() =
    filterIsInstance(DefineProperty::class.java)

/**
 * Keep the [CommandLineArgument]s that are not explicitly recognized but that need to be
 * forwarded to the CMake command-line invocation.
 */
fun List<CommandLineArgument>.onlyKeepUnknownArguments() =
        filterIsInstance(UnknownArgument::class.java)

/**
 * Remove duplicate property names and other arguments, leaving only the last.
 */
fun List<CommandLineArgument>.removeSubsumedArguments() =
    reversed()
    .distinctBy {
        when (it) {
            is DefineProperty -> it.propertyName
            is NdkBuildAppendProperty,
            is UnknownArgument -> it.sourceArgument
            else -> it.javaClass
        }
    }
    .reversed()

/**
 * Remove properties that don't set a value.
 */
fun List<CommandLineArgument>.removeBlankProperties() =
    filter { argument ->
        when(argument) {
            is DefineProperty -> argument.propertyValue.isNotBlank()
            else -> true
        }
    }
