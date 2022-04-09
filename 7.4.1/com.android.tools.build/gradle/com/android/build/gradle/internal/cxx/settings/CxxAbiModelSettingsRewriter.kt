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
package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.cmake.isCmakeConstantTruthy
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.UnknownArgument
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_ABI
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_BUILD_SCRIPT
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_CFLAGS
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_CPPFLAGS
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_PLATFORM
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_ALL_ABIS
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_APPLICATION_MK
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_DEBUG
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_GRADLE_INJECTED_IMPORT_PATH
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_LIBS_OUT
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_OUT
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_PROJECT_PATH
import com.android.build.gradle.internal.cxx.configure.parseCmakeCommandLine
import com.android.build.gradle.internal.cxx.configure.removeBlankProperties
import com.android.build.gradle.internal.cxx.configure.removeSubsumedArguments
import com.android.build.gradle.internal.cxx.configure.toCmakeArgument
import com.android.build.gradle.internal.cxx.configure.toCmakeArguments
import com.android.build.gradle.internal.cxx.configure.toNdkBuildArgument
import com.android.build.gradle.internal.cxx.configure.toNdkBuildArguments
import com.android.build.gradle.internal.cxx.hashing.sha256Of
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.ModelField
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_CONFIGURATION_ARGUMENTS
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_CXX_BUILD_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_INTERMEDIATES_PARENT_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_PREFAB_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_SO_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_STL_LIBRARY_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_CMAKE_ABI_MODEL_BUILD_COMMAND_ARGS
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_CMAKE_MODULE_MODEL_CMAKE_EXE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CONFIGURE_SCRIPT
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CXX_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_INTERMEDIATES_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_MAKE_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_MODULE_ROOT_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NINJA_EXE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_PROJECT_MODEL_ROOT_BUILD_GRADLE_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_PROJECT_MODEL_SDK_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_OPTIMIZATION_TAG
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_STL_TYPE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_VERBOSE_MAKEFILE
import com.android.build.gradle.internal.cxx.model.ModelField.ENVIRONMENT_VARIABLE_NAME
import com.android.build.gradle.internal.cxx.model.ModelField.ENVIRONMENT_VARIABLE_VALUE
import com.android.build.gradle.internal.cxx.model.buildIsPrefabCapable
import com.android.build.gradle.internal.cxx.settings.Macro.ENV_THIS_FILE_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CMAKE_TOOLCHAIN
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_FULL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_INTERMEDIATES_PARENT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_CMAKE_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PREFAB_PATH
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.utils.FileUtils.join
import com.android.utils.cxx.CxxDiagnosticCode.NDK_FEATURE_NOT_SUPPORTED_FOR_VERSION
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import java.io.File
import com.android.Version

/**
 * Rewrite the CxxAbiModel in three phases:
 *
 * 1) Calculate the CMake or ndk-build command line in [CxxAbiModel::configurationArguments]
 *    without expanding any macros that refer to hash of this configuration. Also, the name of
 *    the ABI doesn't contribute to the hash so that the final folders end up with ABI as peers.
 * 2) Calculate a hash of CMake (or ndk-build) of [CxxAbiModel::configurationArguments] and store
 *    it in [CxxAbiModel::fullConfigurationHash]
 * 3) Substitute hash, variant, and ABI names into fields.
 *
 * The purpose of this separation is to allow file outputs to depend on configuration hash but
 * also for the rest of the paths to contribute to the hash itself.
 */
fun CxxAbiModel.calculateConfigurationArguments(
    providers: ProviderFactory,
    layout: ProjectLayout
) : CxxAbiModel {
    return calculateConfigurationArgumentsExceptHash(providers, layout)
            .supportNdkR14AndEarlier()
            .calculateConfigurationHash()
            .expandConfigurationHashMacros()
}

/**
 * Calculates the configuration arguments for CMake and ndk-build. This incorporates the flags from
 * the user's build.gradle file as well as any values from CMakeSettings.json.
 *
 * Since some of those arguments come from the user, we then need to update the model based on
 * the command-line. For example, if user defines:
 *
 *      -DCMAKE_BUILD_TYPE=MinSizeRel
 *
 * We need to update [CxxVariantModel::optimizationTag] with that value.
 */
private fun CxxAbiModel.calculateConfigurationArgumentsExceptHash(
    providers: ProviderFactory,
    layout: ProjectLayout
) : CxxAbiModel {
    val rewriteConfig = getAbiRewriteConfiguration(providers, layout)
    val argsAdded = copy(
            cmake = cmake?.copy(buildCommandArgs = rewriteConfig.buildCommandArgs),
            configurationArguments = rewriteConfig.configurationArgs
    )
    val argsRewritten = argsAdded.rewrite { property, value ->
            val replaced = property.let {
                Macro.withBinding(it).firstOrNull()?.ref
            } ?: value
            rewriteConfig.reifier(replaced)
        }
    // Remove arguments that supersede earlier arguments and remove properties that
    // have a blank value.
    return argsRewritten.copy(
        configurationArguments = argsRewritten.configurationArguments
            // Instantiate ${...} macro values in the argument
            .map { argument -> rewriteConfig.reifier(argument) }
            // Parse the argument
            .map { argument -> when(variant.module.buildSystem) {
                    CMAKE -> argument.toCmakeArgument()
                    NDK_BUILD -> argument.toNdkBuildArgument()
                    else -> UnknownArgument(argument)
                }
            }
            // Get rid of arguments that are irrelevant because they were superseded
            .removeSubsumedArguments()
            // Get rid of blank properties
            .removeBlankProperties()
            // Convert back to string
            .map { it.sourceArgument }
    )
}

/**
 * Returns a model with pre-ndk-r15-wrapper android toolchain cmake file for NDK r14 and below
 * that has a fix to work with CMake versions 3.7+. Note: This is a hacky solution, ideally,
 * the user should install NDK r15+ so it works with CMake 3.7+.
 */
private fun CxxAbiModel.supportNdkR14AndEarlier() : CxxAbiModel {
    if (variant.module.ndkVersion.major >= 15) { return this }
    else {
        val originalToolchain = variant.module.cmakeToolchainFile.absolutePath
        val toolchainFile = join(cxxBuildFolder, "pre-ndk-r15-wrapper-android.toolchain.cmake")
        cxxBuildFolder.mkdirs()
        toolchainFile.writeText("""
             # This toolchain file was generated by Gradle to support NDK versions r14 and below.
             include("${originalToolchain.replace("\\", "/")}")
             set($CMAKE_SYSTEM_VERSION 1)
             """.trimIndent())
        return copy(
                variant = variant.copy(
                        module = variant.module.copy(
                                cmakeToolchainFile = toolchainFile
                        )
                )
        )
    }
}

/**
 * Calculate hash and plug it into the model. Return the new model along with the text that
 * was hashed.
 *
 * The user can pass arbitrary parameters to CMake (or ndk-build) so we can't guard against every
 * case that could make the hash local-machine-specific. This is best effort, works in the default
 * case, and we provide the hash_key.txt file (written later) to be able to diagnose problems.
 * The consequence of a non-portable paths is that hypothetical caching tech (ccache, reclient,
 * gomacc, etc) may not be able to share results. However, build results will still be accurate.
 */
private fun CxxAbiModel.calculateConfigurationHash() : CxxAbiModel {
    var header =
        """
        # Values used to calculate the hash in this folder name.
        # Should not depend on the absolute path of the project itself.
        #   - AGP: ${Version.ANDROID_GRADLE_PLUGIN_VERSION}.
        #   - ${'$'}NDK is the path to NDK ${variant.module.ndkVersion}.
        #   - ${'$'}PROJECT is the path to the parent folder of the root Gradle build file.
        #   - ${'$'}ABI is the ABI to be built with. The specific value doesn't contribute to the value of the hash.
        #   - ${'$'}HASH is the hash value computed from this text.

        """.trimIndent()

    var arguments = configurationArguments.joinToString("\n")
        .replace(variant.module.ndkFolder.path, "\$NDK")
        .replace(variant.module.project.rootBuildGradleFolder.path, "\$PROJECT")
        .replace(NDK_ABI.ref, "\$ABI")
        .replace(NDK_CONFIGURATION_HASH.ref, "\$HASH")

    if (variant.module.cmake != null) {
        header +=             """
            #   - ${'$'}CMAKE is the path to CMake ${variant.module.cmake.minimumCmakeVersion}.

            """.trimIndent()
        arguments = arguments.replace(variant.module.cmake.cmakeExe!!.path, "\$CMAKE")

    }

    if (variant.module.ninjaExe != null) {
        header +=             """
            #   - ${'$'}NINJA is the path to Ninja.

            """.trimIndent()
        arguments = arguments.replace(variant.module.ninjaExe.path, "\$NINJA")
    }

    val hashKey = header + arguments.replace("\\", "/")

    return copy(
        fullConfigurationHash = sha256Of(hashKey, includeGradleVersionInHash = false),
        fullConfigurationHashKey = hashKey
    )
}

/**
 * Finally, expand all of the post-hash macros.
 */
private fun CxxAbiModel.expandConfigurationHashMacros() : CxxAbiModel {
    val result = rewrite { _, value ->
        reifyString(value) { tokenMacro ->
            when(tokenMacro) {
                NDK_ABI.qualifiedName,
                NDK_CONFIGURATION_HASH.qualifiedName,
                NDK_FULL_CONFIGURATION_HASH.qualifiedName -> {
                    val macro = Macro.lookup(tokenMacro) ?: error("Unrecognized macro: $tokenMacro")
                    resolveMacroValue(macro)
                }
                else ->
                    error(tokenMacro)
            }
        }
    }
    return result
}

/**
 * Create a [RewriteConfiguration] which has information for transforming this ABI by
 * expanding macros in different fields.
 */
@VisibleForTesting
fun CxxAbiModel.getAbiRewriteConfiguration(
    providers: ProviderFactory,
    layout: ProjectLayout
) : RewriteConfiguration {
    val allSettings = gatherSettingsFromAllLocations(providers, layout)

   val configuration =
            allSettings.getConfiguration(TRADITIONAL_CONFIGURATION_NAME)!!
                    .withConfigurationsFrom(allSettings.getConfiguration(variant.cmakeSettingsConfiguration))

    val builtInCommandLineArguments = when(variant.module.buildSystem) {
        CMAKE -> configuration.getCmakeCommandLineArguments()
        NDK_BUILD -> getNdkBuildCommandLineArguments()
        else -> listOf()
    }

    val buildGradleCommandLineArguments =  when(variant.module.buildSystem) {
        CMAKE -> variant.buildSystemArgumentList.toCmakeArguments()
        NDK_BUILD -> variant.buildSystemArgumentList.toNdkBuildArguments()
        else -> variant.buildSystemArgumentList.map { UnknownArgument(it) }
    }

    val arguments =
            (builtInCommandLineArguments + buildGradleCommandLineArguments).removeSubsumedArguments()

    val environments = listOf(
            configuration.getSettingsFromConfiguration(),
            getSettingsFromCommandLine(arguments),
            allSettings)
            .flatMap { it.environments }

    val reifier = getPreHashReifier(
            SettingsEnvironmentNameResolver(environments), configuration.inheritEnvironments)

    return RewriteConfiguration(
            reifier = reifier,
            buildCommandArgs = configuration.buildCommandArgs,
            configurationArgs = arguments.map { it.sourceArgument }
    )
}

/**
 * This model contains the inner models of [CxxAbiModel] that are rewritten during
 * clean/build for CMake builds.
 */
@VisibleForTesting
class RewriteConfiguration(
        val buildCommandArgs: String?,
        val reifier: (String) -> (String),
        val configurationArgs: List<String>
)

/**
 * Get a reify function based on the given [SettingsEnvironmentNameResolver] that will expand all
 * macros except for macros that should be expanded *after* the unique configuration has been
 * determined for the given ABI. The macros that are *not* reified are:
 * - [NDK_FULL_CONFIGURATION_HASH] -- so that the hash code can be used in the path to output files
 *   and folders.
 * - [NDK_CONFIGURATION_HASH] -- Just a short version of [NDK_FULL_CONFIGURATION_HASH].
 * - [NDK_ABI] -- This is so that that when folder paths are constructed containing
 *   [NDK_FULL_CONFIGURATION_HASH] each of the individual ABI folders are grouped together as
 *   peers.
 */
private fun getPreHashReifier(
        resolver: SettingsEnvironmentNameResolver,
        inheritEnvironments: List<String>) : (String)->(String) {
    return { value ->
        fun resolve(tokenMacro : String) =
                resolver.resolve(tokenMacro, inheritEnvironments)
                        ?: error("Unrecognized macro $tokenMacro")
        reifyString(value) { tokenMacro ->
            when(tokenMacro) {
                // Exclude properties that shouldn't be evaluated before the configuration hash.
                NDK_ABI.qualifiedName,
                NDK_CONFIGURATION_HASH.qualifiedName,
                NDK_FULL_CONFIGURATION_HASH.qualifiedName -> "\${$tokenMacro}"
                else -> resolve(tokenMacro)
            }
        }
    }
}

/**
 * Builds an environment from CMake command-line arguments.
 */
private fun SettingsConfiguration.getSettingsFromConfiguration(): Settings {
    return Settings(
            environments = NameTable(
                    NDK_MODULE_CMAKE_EXECUTABLE to cmakeExecutable,
                    NDK_CMAKE_TOOLCHAIN to cmakeToolchain
            ).environments(),
            configurations = listOf()
    )
}

/**
 * This is "our" version of the command-line arguments for CMake configuration.
 * The user may override or enhance with arguments from build.gradle or CMakeSettings.json.
 */
private fun SettingsConfiguration.getCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    result += "-H${ENV_THIS_FILE_DIR.ref}".toCmakeArgument()
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
        result += parseCmakeCommandLine(cmakeCommandArgs)
    }
    return result.removeSubsumedArguments().removeBlankProperties()
}

private fun CxxAbiModel.getNdkBuildCommandLineArguments() =
        getNdkBuildCommandLine()
                .toNdkBuildArguments()
                .removeSubsumedArguments()
                .removeBlankProperties()

/**
 * This is "our" version of the command-line arguments for ndk-build.
 * The user may override or enhance with arguments from build.gradle.
 */
private fun CxxAbiModel.getNdkBuildCommandLine(): List<String> {
    val makeFile =
            if (variant.module.makeFile.isDirectory) {
                File(variant.module.makeFile, "Android.mk")
            } else variant.module.makeFile
    val applicationMk = File(makeFile.parent, "Application.mk").takeIf { it.exists() }

    val result: MutableList<String> = Lists.newArrayList()
    result.add("$NDK_PROJECT_PATH=null")
    result.add("$APP_BUILD_SCRIPT=$makeFile")
    // NDK_APPLICATION_MK specifies the Application.mk file.
    applicationMk?.let {
        result.add("$NDK_APPLICATION_MK=" + it.absolutePath)
    }
    if (buildIsPrefabCapable()) {
        if (variant.module.ndkVersion.major < 21) {
            // These cannot be automatically imported prior to NDK r21 which started handling
            // NDK_GRADLE_INJECTED_IMPORT_PATH, but the user can add that search path explicitly
            // for older releases.
            // TODO(danalbert): Include a link to the docs page when it is published.
            // This can be worked around on older NDKs, but it's too verbose to include in the
            // warning message.
            warnln(
                    NDK_FEATURE_NOT_SUPPORTED_FOR_VERSION,
                    "Prefab packages cannot be automatically imported until NDK r21."
            )
        }
        result.add("$NDK_GRADLE_INJECTED_IMPORT_PATH=${NDK_PREFAB_PATH.ref}")
    }

    // APP_ABI and NDK_ALL_ABIS work together. APP_ABI is the specific ABI for this build.
    // NDK_ALL_ABIS is the universe of all ABIs for this build. NDK_ALL_ABIS is set to just the
    // current ABI. If we don't do this, then ndk-build will erase build artifacts for all abis
    // aside from the current.
    result.add("$APP_ABI=${NDK_ABI.ref}")
    result.add("$NDK_ALL_ABIS=${NDK_ABI.ref}")
    if (variant.isDebuggableEnabled) {
        result.add("$NDK_DEBUG=1")
    } else {
        result.add("$NDK_DEBUG=0")
    }
    result.add("$APP_PLATFORM=android-$abiPlatformVersion")
    result.add("$NDK_OUT=${NDK_INTERMEDIATES_PARENT_DIR.ref}/obj")
    result.add("$NDK_LIBS_OUT=${NDK_INTERMEDIATES_PARENT_DIR.ref}/lib")

    // Related to issuetracker.google.com/69110338. Semantics of APP_CFLAGS and APP_CPPFLAGS
    // is that the flag(s) are unquoted. User may place quotes if it is appropriate for the
    // target compiler. User in this case is build.gradle author of
    // externalNativeBuild.ndkBuild.cppFlags or the author of Android.mk.
    for (flag in variant.cFlagsList) {
        result.add("$APP_CFLAGS+=$flag")
    }
    for (flag in variant.cppFlagsList) {
        result.add("$APP_CPPFLAGS+=$flag")
    }
    result.addAll(variant.buildSystemArgumentList)
    return result
}

/**
 * General [CxxAbiModel] rewriter utility. It accepts a rewriter function [rewrite] that is called
 * for each property of the model. The implementation of [rewrite] receives the property definition
 * and the current value of the property; it is expected to return a [String] (or null) that can
 * be converted back into the original type of the property.
 *
 * This function also rewrites the referenced [CxxVariantModel] which continues on to the rest of
 * the model.
 */
fun CxxAbiModel.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
        variant = variant.rewrite(rewrite),
        cmake = cmake?.rewrite(rewrite),
        cxxBuildFolder = rewrite(CXX_ABI_MODEL_CXX_BUILD_FOLDER, cxxBuildFolder.path).toFile(),
        prefabFolder = rewrite(CXX_ABI_MODEL_PREFAB_FOLDER, prefabFolder.path).toFile(),
        soFolder = rewrite(CXX_ABI_MODEL_SO_FOLDER, soFolder.path).toFile(),
        intermediatesParentFolder = rewrite(CXX_ABI_MODEL_INTERMEDIATES_PARENT_FOLDER, intermediatesParentFolder.path).toFile(),
        stlLibraryFile = rewrite.fileOrNull(CXX_ABI_MODEL_STL_LIBRARY_FILE, stlLibraryFile),
        buildSettings = buildSettings.rewrite(rewrite),
        configurationArguments = configurationArguments.map { rewrite(CXX_ABI_MODEL_CONFIGURATION_ARGUMENTS, it ) }
)

// Rewriter for CxxProjectModel
private fun CxxProjectModel.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
        rootBuildGradleFolder = rewrite(CXX_PROJECT_MODEL_ROOT_BUILD_GRADLE_FOLDER, rootBuildGradleFolder.path).toFile(),
        sdkFolder = rewrite(CXX_PROJECT_MODEL_SDK_FOLDER, sdkFolder.path).toFile()
)

// Rewriter for CxxCmakeModuleModel
private fun CxxCmakeModuleModel.rewrite(rewrite : (property: ModelField, String: String) -> String) = copy(
        cmakeExe = rewrite.fileOrNull(CXX_CMAKE_MODULE_MODEL_CMAKE_EXE, cmakeExe),
)

// Rewriter for CxxModuleModel
private fun CxxModuleModel.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
        project = project.rewrite(rewrite),
        cmake = cmake?.rewrite(rewrite),
        cmakeToolchainFile = rewrite(CXX_MODULE_MODEL_CMAKE_TOOLCHAIN_FILE, cmakeToolchainFile.path).toFile(),
        cxxFolder = rewrite(CXX_MODULE_MODEL_CXX_FOLDER, cxxFolder.path).toFile(),
        intermediatesFolder = rewrite(CXX_MODULE_MODEL_INTERMEDIATES_FOLDER, intermediatesFolder.path).toFile(),
        moduleRootFolder = rewrite(CXX_MODULE_MODEL_MODULE_ROOT_FOLDER, moduleRootFolder.path).toFile(),
        ndkFolder = rewrite(CXX_MODULE_MODEL_NDK_FOLDER, ndkFolder.path).toFile(),
        ninjaExe = rewrite.fileOrNull(CXX_MODULE_MODEL_NINJA_EXE, ninjaExe),
        makeFile = rewrite.fileOrNull(CXX_MODULE_MODEL_MAKE_FILE, makeFile)!!,
        configureScript = rewrite.fileOrNull(CXX_MODULE_MODEL_CONFIGURE_SCRIPT, configureScript)
)

// Rewriter for CxxVariantModel
private fun CxxVariantModel.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
        module = module.rewrite(rewrite),
        optimizationTag = rewrite(CXX_VARIANT_MODEL_OPTIMIZATION_TAG, optimizationTag),
        stlType = rewrite(CXX_VARIANT_MODEL_STL_TYPE, stlType),
        verboseMakefile = rewrite.booleanOrNull(CXX_VARIANT_MODEL_VERBOSE_MAKEFILE, verboseMakefile),
)

// Rewriter for CxxCmakeAbiModel
private fun CxxCmakeAbiModel.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
      buildCommandArgs = rewrite.stringOrNull(CXX_CMAKE_ABI_MODEL_BUILD_COMMAND_ARGS, buildCommandArgs)
)

// Rewriter for EnvironmentVariable
private fun EnvironmentVariable.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
        name = rewrite(ENVIRONMENT_VARIABLE_NAME, name),
        value = rewrite.stringOrNull(ENVIRONMENT_VARIABLE_VALUE, value)
)

// Rewriter for BuildSettingsConfiguration
private fun BuildSettingsConfiguration.rewrite(rewrite : (property: ModelField, value: String) -> String) = copy(
        environmentVariables = environmentVariables.map { it.rewrite(rewrite) }
)

// Rewriter utility function for converting from String to File
private fun String.toFile() : File = File(this)

/**
 * Rewrite a String?. Use isBlank() to transmit null.
 */
private fun ((ModelField, String) -> String).stringOrNull(property: ModelField, string : String?) : String? {
    val result = invoke(property, string ?: "")
    if (result.isBlank()) return null
    return result
}

/**
 * Rewrite a File?. Use isBlank() to transmit null.
 */
private fun ((ModelField, String) -> String).fileOrNull(property: ModelField, file : File?) : File? {
    val result = invoke(property, file?.path ?: "")
    if (result.isBlank()) return null
    return result.toFile()
}

/**
 * Rewrite a Boolean?. Use isBlank() to transmit null.
 */
private fun ((ModelField, String) -> String).booleanOrNull(
    property: ModelField,
    flag: Boolean?
): Boolean? {
    val value = when (flag) {
        null -> ""
        true -> "1"
        false -> "0"
    }
    val result = invoke(property, value)
    if (result.isBlank()) return null
    return isCmakeConstantTruthy(result)
}


