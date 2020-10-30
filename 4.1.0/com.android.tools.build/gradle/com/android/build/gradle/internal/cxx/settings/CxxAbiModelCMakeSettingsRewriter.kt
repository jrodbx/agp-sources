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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.getCmakeProperty
import com.android.build.gradle.internal.cxx.configure.getGenerator
import com.android.build.gradle.internal.cxx.configure.isCmakeForkVersion
import com.android.build.gradle.internal.cxx.configure.onlyKeepProperties
import com.android.build.gradle.internal.cxx.configure.parseCmakeArguments
import com.android.build.gradle.internal.cxx.configure.parseCmakeCommandLine
import com.android.build.gradle.internal.cxx.configure.removeBlankProperties
import com.android.build.gradle.internal.cxx.configure.removeSubsumedArguments
import com.android.build.gradle.internal.cxx.configure.toCmakeArgument
import com.android.build.gradle.internal.cxx.hashing.toBase36
import com.android.build.gradle.internal.cxx.hashing.update
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.replaceWith
import com.android.build.gradle.internal.cxx.settings.Macro.*
import com.android.build.gradle.internal.cxx.settings.PropertyValue.*
import com.android.build.gradle.tasks.NativeBuildSystem
import java.io.File
import java.security.MessageDigest

/**
 * If there is a CMakeSettings.json then replace relevant model values with settings from it.
 */
fun CxxAbiModel.rewriteCxxAbiModelWithCMakeSettings() : CxxAbiModel {

    val original = this
    if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
        val rewriteConfig by lazy {
            getCxxAbiRewriteModel()
        }

        val configuration by lazy {
            rewriteConfig.configuration
        }

        val cmakeModule = original.variant.module.cmake!!.replaceWith(
            cmakeExe = { configuration.cmakeExecutable.toFile()!! }
        )
        val module = original.variant.module.replaceWith(
            cmake = { cmakeModule },
            cmakeToolchainFile = { configuration.cmakeToolchain.toFile()!! }
        )
        val variant = original.variant.replaceWith(
            module = { module }
        )
        val cmakeAbi = original.cmake?.replaceWith(
            cmakeArtifactsBaseFolder =  { configuration.buildRoot.toFile()!! },
            effectiveConfiguration = {
                configuration
            }
        )
        return original.replaceWith(
            cmake = { cmakeAbi },
            variant = { variant },
            cxxBuildFolder = { configuration.buildRoot.toFile()!! },
            buildSettings = { rewriteConfig.buildSettings }
        )
    } else {
//        TODO(jomof) separate CMake-ness from macro expansion and add it to NDK build
//        return original.replaceWith(
//            cmake = { cmake },
//            variant = { variant },
//            cxxBuildFolder = { cxxBuildFolder },
//            buildSettings = { rewriteModel.buildSettings }
//        )
        return this
    }
}

/**
 * Turn a string into a File with null propagation.
 */
private fun String?.toFile() = if (this != null) File(this) else null

/**
 * Build the CMake command line arguments from [CxxAbiModel] and resolve macros in
 * CMakeSettings.json and BuildSettings.json
 */
private fun CxxAbiModel.getCxxAbiRewriteModel() : RewriteConfiguration {
    val allSettings = gatherCMakeSettingsFromAllLocations()
        .expandInheritEnvironmentMacros(this)
    val resolver = CMakeSettingsNameResolver(allSettings.environments)

    // Accumulate configuration values with later values replacing earlier values
    // when not null.
    fun CMakeSettingsConfiguration.accumulate(configuration : CMakeSettingsConfiguration?) : CMakeSettingsConfiguration {
        if (configuration == null) return this
        return CMakeSettingsConfiguration(
            name = configuration.name ?: name,
            description = configuration.description ?: description,
            generator = configuration.generator ?: generator,
            configurationType = configurationType,
            inheritEnvironments = configuration.inheritEnvironments,
            buildRoot = configuration.buildRoot ?: buildRoot,
            installRoot = configuration.installRoot ?: installRoot,
            cmakeCommandArgs = configuration.cmakeCommandArgs ?: cmakeCommandArgs,
            cmakeToolchain = configuration.cmakeToolchain ?: cmakeToolchain,
            cmakeExecutable = configuration.cmakeExecutable ?: cmakeExecutable,
            buildCommandArgs = configuration.buildCommandArgs ?: buildCommandArgs,
            ctestCommandArgs = configuration.ctestCommandArgs ?: ctestCommandArgs,
            variables = variables + configuration.variables
        )
    }

    fun CMakeSettingsConfiguration.accumulate(arguments : List<CommandLineArgument>) : CMakeSettingsConfiguration {
        return copy(
            configurationType =
                when (arguments.getCmakeProperty(CMAKE_BUILD_TYPE)) {
                    null -> configurationType
                    else -> null
                },
            cmakeToolchain =
                when (arguments.getCmakeProperty(CMAKE_TOOLCHAIN_FILE)) {
                    null -> cmakeToolchain
                    else -> null
                },
            generator = arguments.getGenerator() ?: generator,
            variables = variables +
                    arguments.onlyKeepProperties().map {
                        CMakeSettingsVariable(it.propertyName, it.propertyValue)
                    }
        )
    }

    fun getCMakeSettingsConfiguration(configurationName : String) : CMakeSettingsConfiguration? {
        val configuration = allSettings.configurations
            .firstOrNull { it.name == configurationName } ?: return null
        return reifyRequestedConfiguration(resolver, configuration)
    }

    // First, set up the traditional environment. If the user has also requested a specific
    // CMakeSettings.json environment then values from that will overwrite these.
    val combinedConfiguration = getCMakeSettingsConfiguration(TRADITIONAL_CONFIGURATION_NAME)!!
        .accumulate(getCMakeSettingsConfiguration(variant.cmakeSettingsConfiguration))
    val configuration = combinedConfiguration
        .accumulate(combinedConfiguration.getCmakeCommandLineArguments())
        .accumulate(parseCmakeArguments(variant.buildSystemArgumentList))

    // Translate to [CommandLineArgument]. Be sure that user variables from build.gradle get
    // passed after settings variables
    val configurationArguments = configuration.getCmakeCommandLineArguments()

    val hashInvariantCommandLineArguments =
        configurationArguments.removeSubsumedArguments().removeBlankProperties()

    // Compute a hash of the command-line arguments
    val digest = MessageDigest.getInstance("SHA-256")
    hashInvariantCommandLineArguments.forEach { argument ->
        digest.update(argument.sourceArgument)
    }
    val configurationHash = digest.toBase36()

    // All arguments
    val all = getCmakeCommandLineArguments() + configurationArguments

    // Fill in the ABI and configuration hash properties
    fun String.reify() = reifyString(this) { tokenMacro ->
        when(tokenMacro) {
            NDK_ABI.qualifiedName ->
                StringPropertyValue(abi.tag)
            NDK_CONFIGURATION_HASH.qualifiedName ->
                StringPropertyValue(configurationHash.substring(0, 8))
            NDK_FULL_CONFIGURATION_HASH.qualifiedName ->
                StringPropertyValue(configurationHash)
            else -> resolver.resolve(tokenMacro, configuration.inheritEnvironments)
        }
    }!!

    val arguments = all.map { argument ->
        argument.sourceArgument.reify().toCmakeArgument()
    }.removeSubsumedArguments().removeBlankProperties()

    val expandedBuildSettings = BuildSettingsConfiguration(
        environmentVariables = buildSettings.environmentVariables.map {
            EnvironmentVariable(
                name = it.name.reify(),
                value = it.value?.reify()
            )
        }
    )

    return RewriteConfiguration(
        buildSettings = expandedBuildSettings,
        configuration = CMakeSettingsConfiguration(
            name = configuration.name,
            description = "Composite reified CMakeSettings configuration",
            generator = arguments.getGenerator(),
            configurationType = configuration.configurationType,
            inheritEnvironments = configuration.inheritEnvironments,
            buildRoot = configuration.buildRoot?.reify(),
            installRoot = configuration.installRoot?.reify(),
            cmakeToolchain = arguments.getCmakeProperty(CMAKE_TOOLCHAIN_FILE),
            cmakeCommandArgs = configuration.cmakeCommandArgs?.reify(),
            cmakeExecutable = configuration.cmakeExecutable?.reify(),
            buildCommandArgs = configuration.buildCommandArgs?.reify(),
            ctestCommandArgs = configuration.ctestCommandArgs?.reify(),
            variables = arguments.mapNotNull {
                when (it) {
                    is CommandLineArgument.DefineProperty -> CMakeSettingsVariable(
                        it.propertyName,
                        it.propertyValue
                    )
                    else -> null
                }
            }
        )
    )
}

fun CMakeSettingsConfiguration.getCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    if (configurationType != null) {
        result += "-D$CMAKE_BUILD_TYPE=$configurationType".toCmakeArgument()
    }
    if (cmakeToolchain != null) {
        result +="-D$CMAKE_TOOLCHAIN_FILE=$cmakeToolchain".toCmakeArgument()
    }
    result += variables.map { (name, value) -> "-D$name=$value".toCmakeArgument() }
    if (buildRoot != null) {
        result += "-B$buildRoot".toCmakeArgument()
    }
    if (generator != null) {
        result += "-G$generator".toCmakeArgument()
    }

    if (cmakeCommandArgs != null) {
        parseCmakeCommandLine(cmakeCommandArgs)
    }
    return result.removeSubsumedArguments().removeBlankProperties()
}

fun CxxAbiModel.getCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    result += "-H${variant.module.makeFile.parentFile}".toCmakeArgument()
    result += "-B${resolveMacroValue(NDK_BUILD_ROOT)}".toCmakeArgument()

    result += if (variant.module.cmake!!.minimumCmakeVersion.isCmakeForkVersion()) {
        "-GAndroid Gradle - Ninja".toCmakeArgument()
    } else {
        "-GNinja".toCmakeArgument()
    }
    result += "-D$CMAKE_BUILD_TYPE=${resolveMacroValue(NDK_DEFAULT_BUILD_TYPE)}".toCmakeArgument()
    result += "-D$CMAKE_TOOLCHAIN_FILE=${resolveMacroValue(NDK_CMAKE_TOOLCHAIN)}".toCmakeArgument()
    result += "-D$CMAKE_CXX_FLAGS=${resolveMacroValue(NDK_CPP_FLAGS)}".toCmakeArgument()
    result += "-D$CMAKE_C_FLAGS=${resolveMacroValue(NDK_C_FLAGS)}".toCmakeArgument()

    // This can be passed a few different ways:
    // https://cmake.org/cmake/help/latest/command/find_package.html#search-procedure
    //
    // <PACKAGE_NAME>_ROOT would probably be best, but it's not supported until 3.12, and we support
    // CMake 3.6.
    result += "-D$CMAKE_FIND_ROOT_PATH=${resolveMacroValue(NDK_PREFAB_PATH)}".toCmakeArgument()

    return result.removeSubsumedArguments().removeBlankProperties()
}

fun CxxAbiModel.getFinalCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    result += getCmakeCommandLineArguments()
    result += cmake!!.effectiveConfiguration.getCmakeCommandLineArguments()
    return result.removeSubsumedArguments().removeBlankProperties()
}

/*
* Returns the Ninja build commands from CMakeSettings.json.
* Returns an empty string if it does not exist.
*/
fun CxxAbiModel.getBuildCommandArguments() : String {
    return cmake?.effectiveConfiguration?.buildCommandArgs ?: ""
}

/**
 * This model contains the inner models of [CxxAbiModel] that are rewritten during
 * clean/build for CMake builds.
 */
private class RewriteConfiguration(
    val buildSettings: BuildSettingsConfiguration,
    val configuration: CMakeSettingsConfiguration
)
