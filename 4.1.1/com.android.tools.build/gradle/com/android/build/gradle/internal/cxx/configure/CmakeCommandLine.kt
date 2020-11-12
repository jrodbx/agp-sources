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

import com.android.build.gradle.external.gnumake.AbstractOsFileConventions
import com.android.build.gradle.external.gnumake.OsFileConventions
import com.android.build.gradle.internal.cxx.cmake.isCmakeConstantTruthy
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.BinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeListsPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.GeneratorName
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.UnknownArgument

/**
 * Classes and functions in this file are for dealing with CMake command-line parameters.
 * This is complete enough for compiler settings cache purposes. Any unrecognized flags are
 * classified as UnknownArgument.
 */

/**
 * Interface that represents a single CMake command-line argument.
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
    data class BinaryOutputPath(
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
    data class GeneratorName(
        override val sourceArgument: String,
        val generator : String) : CommandLineArgument() {
        companion object {
            /**
             * Create a GeneratorName.
             * Don't use if you also have an an original sourceArgument.
             */
            @JvmStatic
            fun from(generator : String) : GeneratorName {
                return GeneratorName("-G$generator", generator)
            }
        }
    }

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
}

/**
 * Given a list of flags that probably came from android.defaultConfig.cmake.arguments
 * augmented by Json generator classes like CmakeServerExternalNativeJsonGenerator parse
 * the flags into implementors of CommandLineArgument.
 */
fun parseCmakeArguments(args : List<String>) : List<CommandLineArgument> {
    return args.map { arg ->
        arg.toCmakeArgument()
    }
}

/**
 * Parse a CMake command-line and returns the corresponding list of [CommandLineArgument].
 */
fun parseCmakeCommandLine(
    commandLine : String,
    hostConventions : OsFileConventions =
        AbstractOsFileConventions.createForCurrentHost()) : List<CommandLineArgument> {
    val combinedTokens =
        hostConventions.tokenizeCommandLineToEscaped(commandLine) zip
            hostConventions.tokenizeCommandLineToRaw(commandLine)
    var prior : Pair<String, String>? = null
    val result = mutableListOf<CommandLineArgument>()
    val combinable = listOf("-D", "-H", "-B", "-G")
    for(combinedToken in combinedTokens) {
        val (escaped, raw) = combinedToken
        when {
            prior != null -> {
                result +=
                    "${prior.first} $escaped".toCmakeArgument("${prior.second} $raw")
                prior = null
            }
            combinable.contains(escaped) -> prior = combinedToken
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
            BinaryOutputPath(sourceArgument, path)
        }
        startsWith("-G") -> {
            val path = substringAfter("-G")
            GeneratorName(sourceArgument, path)
        }
        else ->
            // Didn't recognize the flag so return unknown argument
            UnknownArgument(sourceArgument)
    }
}

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
    getCmakeProperty(property.name)

/**
 * Returns the value of the property. Null if not present. If the value is present more than once
 * then the last value is taken.
 */
fun List<CommandLineArgument>.getCmakeProperty(property : String) =
    filterType<DefineProperty> { it.propertyName == property }?.propertyValue

/**
 * Returns the generator. Null if none present
 */
fun List<CommandLineArgument>.getGenerator() = filterType<GeneratorName>()?.generator

/**
 * Returns the folder of CMakeLists.txt.
 */
fun List<CommandLineArgument>.getCmakeListsFolder() = filterType<CmakeListsPath>()?.path

/**
 * Returns the buildRoot folder.
 */
fun List<CommandLineArgument>.getBuildRootFolder() = filterType<BinaryOutputPath>()?.path

/**
 * Remove all instances of the given property from the list of args
 */
fun List<CommandLineArgument>.removeProperty(property : CmakeProperty) =
    filter {
        !(it is DefineProperty &&  it.propertyName == property.name)
    }

/**
 * Utility method for filtering [CommandLineArgument] list by type and optional predicate.
 */
private inline fun <reified T : CommandLineArgument> List<CommandLineArgument>.filterType(
    predicate: (T) -> Boolean = { true }) = filterIsInstance<T>().lastOrNull { predicate(it) }

/**
 * Convert to the equivalent command-line arguments String list.
 */
fun List<CommandLineArgument>.convertCmakeCommandLineArgumentsToStringList() =
    map { it.sourceArgument }

/**
 * Keep the [CommandLineArgument]s that should be passed to CMake Server. Remove the rest.
 */
fun List<CommandLineArgument>.onlyKeepServerArguments() =
    filter { argument ->
        when(argument) {
            is BinaryOutputPath,
            is CmakeListsPath,
            is GeneratorName -> false
            else -> true
        }
    }

/**
 * Keep the [CommandLineArgument]s that should be passed to CMake Server. Remove the rest.
 */
fun List<CommandLineArgument>.onlyKeepProperties() =
    filterIsInstance(DefineProperty::class.java)

/**
 * Remove duplicate property names and other arguments, leaving only the last.
 */
fun List<CommandLineArgument>.removeSubsumedArguments() =
    reversed()
    .distinctBy {
        when (it) {
            is DefineProperty -> it.propertyName
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
