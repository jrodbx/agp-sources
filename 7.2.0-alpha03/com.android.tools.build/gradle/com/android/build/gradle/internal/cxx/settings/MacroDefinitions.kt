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

import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeProperty
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_PLATFORM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_ARCH_ABI
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_ANDROID_NDK
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_MAKE_PROGRAM
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_RUNTIME_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.defaultCmakeVersion
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.altCpuArchitecture
import com.android.build.gradle.internal.cxx.model.bitness
import com.android.build.gradle.internal.cxx.model.cFlags
import com.android.build.gradle.internal.cxx.model.cmakeGenerator
import com.android.build.gradle.internal.cxx.model.cmakeSettingsFile
import com.android.build.gradle.internal.cxx.model.configurationHash
import com.android.build.gradle.internal.cxx.model.cppFlags
import com.android.build.gradle.internal.cxx.model.cpuArchitecture
import com.android.build.gradle.internal.cxx.model.is64Bits
import com.android.build.gradle.internal.cxx.model.isDefault
import com.android.build.gradle.internal.cxx.model.isDeprecated
import com.android.build.gradle.internal.cxx.model.makeFileFolder
import com.android.build.gradle.internal.cxx.model.moduleName
import com.android.build.gradle.internal.cxx.model.ndkMajorVersion
import com.android.build.gradle.internal.cxx.model.ndkMaxPlatform
import com.android.build.gradle.internal.cxx.model.ndkMinPlatform
import com.android.build.gradle.internal.cxx.model.ndkMinorVersion
import com.android.build.gradle.internal.cxx.model.platform
import com.android.build.gradle.internal.cxx.model.platformCode
import com.android.build.gradle.internal.cxx.model.tag
import com.android.build.gradle.internal.cxx.settings.Environment.GRADLE
import com.android.build.gradle.internal.cxx.settings.Environment.MICROSOFT_BUILT_IN
import com.android.build.gradle.internal.cxx.settings.Environment.NDK
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_EXPOSED_BY_HOST
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty1

/**
 * Define built-in macros for CMakeSettings.json. Built-in means they're defined by the host
 * (Android Studio) and their names can be known in advance.
 *
 * Examples of macros that aren't here:
 * - User defined macros in their own CMakeSettings.json environments.
 * - Bash or cmd.exe environment variables like $HOME.
 */
enum class Macro(
    // A user readable description of this variable suitable for inclusion in errors and warnings
    val description: String,
    // The environment where the value of this macro would naturally come from. For example, NDK
    // means that the current Android NDK is entity that would naturally produce this information.
    val environment: Environment,
    // The name that is used to reference this macro from within CMakeSettings.json. For example,
    // in ${ndk.minPlatform} the tag is "minPlatform".
    val tag: String,
    // An example value for this kind of macro. Suitable for including in error messages and
    // warnings about this macro.
    val example: String,
    // A property field that provides this macro's value.
    val bind: KProperty1<*, *>?,
    // Set if this macro value maps to specific CMake properties.
    val cmakeProperties: List<CmakeProperty> = listOf(),
    // If ndk-build has a different value than CMake, then this holds the ndk-build example
    val ndkBuildExample: String? = null) {

    NDK_CONFIGURATION_HASH(
        description = "First eight characters of \${ndk.fullConfigurationHash}.",
        environment = GRADLE,
        tag = "configurationHash",
        example = "1m6w461r",
        bind = CxxAbiModel::configurationHash),
    NDK_FULL_CONFIGURATION_HASH(
        description = "Hash of this CMakeSettings configuration.",
        environment = GRADLE,
        tag = "fullConfigurationHash",
        example = "1m6w461rf3l272y5d5d5c2m651a4i4j1c3n69zm476ys1g403j69363k4519",
        bind = CxxAbiModel::fullConfigurationHash),
    NDK_MIN_PLATFORM(
        description = "The minimum Android platform supported by the current Android NDK.",
        environment = NDK,
        tag = "minPlatform",
        example = "16",
        bind = CxxModuleModel::ndkMinPlatform),
    NDK_MAX_PLATFORM(
        description = "The maximum Android platform supported by the current Android NDK.",
        environment = NDK,
        tag = "maxPlatform",
        example = "29",
        bind = CxxModuleModel::ndkMaxPlatform),
    NDK_PLATFORM_SYSTEM_VERSION(
        description = "The currently targeted Android system version, suitable for passing to " +
                "CMake in CMAKE_SYSTEM_VERSION.",
        environment = Environment.NDK_PLATFORM,
        tag = "platformSystemVersion",
        example = "19",
        cmakeProperties = listOf(CMAKE_SYSTEM_VERSION),
        bind = CxxAbiModel::abiPlatformVersion),
    NDK_PLATFORM(
        description = "The currently targeted Android platform string, that can be passed to " +
                "CMake in ANDROID_PLATFORM.",
        environment = Environment.NDK_PLATFORM,
        tag = "platform",
        example = "android-19",
        cmakeProperties = listOf(ANDROID_PLATFORM),
        bind = CxxAbiModel::platform),
    NDK_PLATFORM_CODE(
        description = "The currently targeted Android platform code name.",
        environment = Environment.NDK_PLATFORM,
        tag = "platformCode",
        example = "K",
        bind = CxxAbiModel::platformCode),
    NDK_ABI_BITNESS(
        description = "The bitness of the targeted ABI.",
        environment = Environment.NDK_ABI,
        tag = "abiBitness",
        example = "64",
        bind = CxxAbiModel::bitness),
    NDK_ABI_IS_64_BITS(
        description = "Whether the targeted ABI is 64-bits.",
        environment = Environment.NDK_ABI,
        tag = "abiIs64Bits",
        example = "1",
        bind = CxxAbiModel::is64Bits),
    NDK_ABI_IS_DEFAULT(
        description = "Whether the targeted ABI is a default ABI in the current Android NDK.",
        environment = Environment.NDK_ABI,
        tag = "abiIsDefault",
        example = "1",
        bind = CxxAbiModel::isDefault),
    NDK_ABI_IS_DEPRECATED(
        description = "True if the targeted ABI is deprecated in the current Android NDK.",
        environment = Environment.NDK_ABI,
        tag = "abiIsDeprecated",
        example = "0",
        bind = CxxAbiModel::isDeprecated),
    NDK_ABI(
        description = "Currently targeted ABI.",
        environment = GRADLE,
        tag = "abi",
        example = "x86_64",
        cmakeProperties = listOf(ANDROID_ABI, CMAKE_ANDROID_ARCH_ABI),
        bind = CxxAbiModel::tag),
    NDK_ABI_CPU_ARCHITECTURE(
        description = "The CPU architecture.",
        environment = GRADLE,
        tag = "abiCpuArchitecture",
        example = "x86_64",
        bind = CxxAbiModel::cpuArchitecture),
    NDK_ABI_ALT_CPU_ARCHITECTURE(
        description = "Alternative CPU architecture name that is compatible with vcpkg.",
        environment = GRADLE,
        tag = "abiAltCpuArchitecture",
        example = "x64",
        bind = CxxAbiModel::altCpuArchitecture),
    NDK_PROJECT_SDK_DIR(
        description = "Folder of the current Android SDK.",
        environment = GRADLE,
        tag = "projectSdkDir",
        example = "\$HOME/Library/Android/sdk",
        bind = CxxProjectModel::sdkFolder),
    NDK_MODULE_NDK_DIR(
        description = "Folder of the current Android NDK.",
        environment = GRADLE,
        tag = "moduleNdkDir",
        example = "${NDK_PROJECT_SDK_DIR.ref}/ndk/$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION",
        cmakeProperties = listOf(ANDROID_NDK, CMAKE_ANDROID_NDK),
        bind = CxxModuleModel::ndkFolder),
    NDK_CMAKE_TOOLCHAIN(
        description = "Path to the current Android NDK's CMake toolchain.",
        environment = NDK,
        tag = "cmakeToolchain",
        example = "${NDK_MODULE_NDK_DIR.ref}/build/cmake/android.toolchain.cmake",
        cmakeProperties = listOf(CMAKE_TOOLCHAIN_FILE),
        bind = CxxModuleModel::cmakeToolchainFile),
    NDK_MODULE_CMAKE_EXECUTABLE(
        description = "Path to CMake executable.",
        environment = GRADLE,
        tag = "moduleCmakeExecutable",
        example = "${NDK_PROJECT_SDK_DIR.ref}/cmake/$defaultCmakeVersion/bin/cmake",
        ndkBuildExample = "",
        bind = CxxCmakeModuleModel::cmakeExe),
    NDK_MODULE_NINJA_EXECUTABLE(
        description = "Path to Ninja executable if one was found by Gradle. Otherwise, it expands" +
                " to empty string and it's up to CMake to find the ninja executable.",
        environment = GRADLE,
        tag = "moduleNinjaExecutable",
        example = "${NDK_PROJECT_SDK_DIR.ref}/cmake/$defaultCmakeVersion/bin/ninja",
        ndkBuildExample = "",
        cmakeProperties = listOf(CMAKE_MAKE_PROGRAM),
        bind = CxxCmakeModuleModel::ninjaExe),
    NDK_MODULE_CMAKE_GENERATOR(
        description = "Name of the generator used by CMake.",
        environment = GRADLE,
        tag = "moduleCmakeGenerator",
        example = "Ninja",
        ndkBuildExample = "",
        bind = CxxModuleModel::cmakeGenerator),
    NDK_MODULE_NDK_VERSION(
        description = "Version of NDK.",
        environment = GRADLE,
        tag = "moduleNdkVersion",
        example = ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION,
        bind = CxxModuleModel::ndkVersion),
    NDK_MODULE_NDK_VERSION_MAJOR(
        description = "Version number major part.",
        environment = GRADLE,
        tag = "moduleNdkVersionMajor",
        example = ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION.split(".")[0],
        bind = CxxModuleModel::ndkMajorVersion),
    NDK_MODULE_NDK_VERSION_MINOR(
        description = "Version number minor part.",
        environment = GRADLE,
        tag = "moduleNdkVersionMinor",
        example = ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION.split(".")[1],
        bind = CxxModuleModel::ndkMinorVersion),
    NDK_MODULE_DIR(
        description = "Folder of the module level build.gradle.",
        environment = GRADLE,
        tag = "moduleDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1",
        bind = CxxModuleModel::moduleRootFolder),
    NDK_MODULE_BUILD_INTERMEDIATES_BASE_DIR(
        description = "The module level build base intermediates folder.",
        environment = GRADLE,
        tag = "moduleBuildIntermediatesBaseDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1/build/intermediates",
        bind = CxxModuleModel::intermediatesBaseFolder),
    NDK_MODULE_BUILD_INTERMEDIATES_DIR(
        description = "The module level build intermediates cxx subfolder.",
        environment = GRADLE,
        tag = "moduleBuildIntermediatesDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1/build/intermediates/cxx",
        bind = CxxModuleModel::intermediatesFolder),
    NDK_VARIANT_NAME(
        description = "Name of the gradle variant.",
        environment = GRADLE,
        tag = "variantName",
        example = "debug",
        bind = CxxVariantModel::variantName),
    NDK_MODULE_NAME(
        description = "Name of the gradle module.",
        environment = GRADLE,
        tag = "moduleName",
        example = "app1",
        bind = CxxModuleModel::moduleName),
    NDK_MODULE_BUILD_ROOT(
        description = "The default module-level CMake or ndk-build build root that gradle uses.",
        environment = GRADLE,
        tag = "moduleBuildRoot",
        example = "${NDK_MODULE_DIR.ref}/.cxx",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/.cxx",
        bind = CxxModuleModel::cxxFolder),
    NDK_BUILD_ROOT(
        description = "The default CMake or ndk-build build root that gradle uses.",
        environment = GRADLE,
        tag = "buildRoot",
        example = "${NDK_MODULE_DIR.ref}/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64",
        bind = CxxAbiModel::cxxBuildFolder),
    NDK_VARIANT_C_FLAGS(
        description = "The value of cFlags from android.config.externalNativeBuild.cFlags in build.gradle.",
        environment = GRADLE,
        tag = "variantCFlags",
        example = "-DC_FLAG_DEFINED",
        cmakeProperties = listOf(CMAKE_C_FLAGS),
        bind = CxxVariantModel::cFlags),
    NDK_VARIANT_CPP_FLAGS(
        description = "The value of cppFlags from android.config.externalNativeBuild.cppFlags in build.gradle.",
        environment = GRADLE,
        tag = "variantCppFlags",
        example = "-DCPP_FLAG_DEFINED",
        cmakeProperties = listOf(CMAKE_CXX_FLAGS),
        bind = CxxVariantModel::cppFlags),
    NDK_SO_OUTPUT_DIR(
        description = "The ABI-level folder where .so files are written.",
        environment = GRADLE,
        tag = "soOutputDir",
        example = "${NDK_MODULE_DIR.ref}/build/intermediates/cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/obj/x86_64",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/intermediates/cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/obj/local/x86_64",
        cmakeProperties = listOf(CMAKE_LIBRARY_OUTPUT_DIRECTORY, CMAKE_RUNTIME_OUTPUT_DIRECTORY),
        bind = CxxAbiModel::soFolder),
    NDK_SO_REPUBLISH_DIR(
        description = "A folder with a predictable name where final build outputs (mainly .so) are" +
                "hard linked or copied after the build completes. The purpose is so scripts " +
                "and other external tools have a known path, with no embedded hashcode, to locate " +
                "these files.",
        environment = GRADLE,
        tag = "soRepublishDir",
        example = "${NDK_MODULE_DIR.ref}/build/intermediates/cmake/debug/obj/x86_64",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/intermediates/ndkBuild/debug/obj/local/x86_64",
        bind = CxxAbiModel::soRepublishFolder),
    NDK_INTERMEDIATES_PARENT_DIR(
        description = "The intermediates folder but without the ABI at the end.",
        environment = GRADLE,
        tag = "intermediatesParentDir",
        example = "${NDK_MODULE_DIR.ref}/build/intermediates/cxx/Debug/${NDK_CONFIGURATION_HASH.ref}",
        bind = CxxAbiModel::intermediatesParentFolder),
    NDK_VARIANT_OPTIMIZATION_TAG(
        description = "The CMAKE_BUILD_TYPE derived from the suffix of gradle variant name. " +
                "May be Debug, Release, RelWithDebInfo, or MinSizeRel.",
        environment = GRADLE,
        tag = "variantOptimizationTag",
        example = "Debug",
        cmakeProperties = listOf(CmakeProperty.CMAKE_BUILD_TYPE),
        bind = CxxVariantModel::optimizationTag),
    NDK_VARIANT_STL_TYPE(
        description = "The type of the runtime library type (if present).",
        environment = GRADLE,
        tag = "variantStlType",
        example = "c++_shared",
        bind = CxxVariantModel::stlType),
    NDK_STL_LIBRARY_FILE(
        description = "If present, the STL .so file that needs to be distributed with the libraries built.",
        environment = GRADLE,
        tag = "stlLibraryFile",
        example = "${NDK_MODULE_NDK_DIR.ref}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so",
        bind = CxxAbiModel::stlLibraryFile),
    NDK_PREFAB_PATH(
        description = "The CMAKE_FIND_ROOT_PATH to be used by Prefab for the current configuration.",
        environment = GRADLE,
        tag = "prefabPath",
        example = "\$PROJECTS/MyProject/Source/Android/app1/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/prefab/x86_64",
        ndkBuildExample = "\$PROJECTS/MyProject/Source/Android/app1/build/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/prefab/x86_64",
        bind = CxxAbiModel::prefabFolder),
    ENV_THIS_FILE(
        description = "Path to this CMakeSettings.json file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "thisFile",
        example = "\$PROJECTS/MyProject/CMakeSettings.json",
        bind = CxxModuleModel::cmakeSettingsFile),
    ENV_THIS_FILE_DIR(
        description = "Folder of this CMakeSettings.json file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "thisFileDir",
        example = "\$PROJECTS/MyProject",
        bind = CxxModuleModel::makeFileFolder),
    ENV_WORKSPACE_ROOT(
        description = "Folder of the project level build.gradle file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "workspaceRoot",
        example = "\$PROJECTS/MyProject/Source/Android",
        bind = CxxProjectModel::rootBuildGradleFolder),
    ENV_PROJECT_DIR(
        description = "Folder of the module level build.gradle file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "projectDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1",
        bind = CxxModuleModel::moduleRootFolder),
    NDK_ANDROID_GRADLE_IS_HOSTING(
        description = "True if Android Gradle Plugin is hosting this CMakeSettings.json.",
        environment = NDK_EXPOSED_BY_HOST,
        tag = "androidGradleIsHosting",
        example = "1",
        bind = Macro::one),
    NDK_VARIANT_VERBOSE_MAKEFILE(
        description = "Whether to invoke build tool with verbosity (for example, ninja -v).",
        environment = GRADLE,
        tag = "variantVerboseMakefile",
        example = "",
        cmakeProperties = listOf(CmakeProperty.CMAKE_VERBOSE_MAKEFILE),
        bind = CxxVariantModel::verboseMakefile);

    /**
     * The literal value of this macro in CMakeSettings.json. For example, "${ndk.minPlatform}".
     * Macros in the namespace "env" are reduced to just the name. For example, "${thisFile}".
     */
    val ref =
        if (environment.namespace == "env") "\${$tag}"
        else "\${${environment.namespace}.$tag}"

    /**
     * The namespace-qualified name of this macro.
     */
    val qualifiedName get() = "${environment.namespace}.$tag"

    /**
     * Try to look up a value for this [Macro] from [instance]
     */
    fun <T:Any> takeFrom(instance:T) : String? {
        return MACRO_DEFINITIONS_BINDINGS_GETTERS[instance::class to this]?.let { property ->
            (property as KProperty1<T, *>).get(instance)?.toString() ?: ""
        }
    }

    /**
     * In a property like MyClass::myProperty, return MyClass for this macro.
     */
    val bindingType : KClassifier? get() = MACRO_DEFINITIONS_BINDINGS_CLASS[this]

    /**
     * A built-in property that just returns "1".
     */
    private val one get() = "1"

    companion object {
        /**
         * Look up the [Macro] enum for [name]. If [name] doesn't have a namespace then 'env' is
         * used. If [name] cannot be found then null is returned.
         */
        @JvmStatic
        fun lookup(name: String): Macro? {
            val qualifiedName =
                when {
                    name.contains(".") -> name
                    else -> "env.$name"
                }
            return values().singleOrNull { it.qualifiedName == qualifiedName }
        }

        /**
         * Return macro(s), if any, that correspond to the given [cmakeProperty].
         */
        fun withCMakeProperty(cmakeProperty : String) =
            CMAKE_PROPERTY_STRING_TO_MACRO_MAP[cmakeProperty]?:listOf()

        /**
         * Return macro(s), if any, that are bound to the given property.
         */
        fun withBinding(property: KProperty1<*, *>) = BINDING_PROPERTY_TO_MACRO[property]?:listOf()

        private val MACRO_DEFINITIONS_BINDINGS_GETTERS : Map<Pair<KClassifier?, Macro>, KProperty1<*, *>> = values()
                .filter { macro -> macro.bind != null }
                .map { macro ->
                    val bind = macro.bind!!
                    (bind.parameters[0].type.classifier to macro) to bind
                }.toMap()

        private val MACRO_DEFINITIONS_BINDINGS_CLASS : Map<Macro, KClassifier?> = values()
                .filter { macro -> macro.bind != null }
                .map { macro ->
                    val bind = macro.bind!!
                    macro to bind.parameters[0].type.classifier
                }.toMap()

        private val BINDING_PROPERTY_TO_MACRO = values()
                .filter { macro -> macro.bind != null }
                .groupBy { macro -> macro.bind!! }

        private val CMAKE_PROPERTY_STRING_TO_MACRO_MAP = values()
                .flatMap { macro -> macro.cmakeProperties.map { cmake -> cmake to macro} }
                .groupBy({ (cmake, _) -> cmake.name }, { (_, macro) -> macro })
    }
}

/**
 * The environment that a macro exists within.
 *
 * In CMakeSettings.json environments may inherit other environments. Inheritance means values from
 * parent environments exist in the child environment and can be replaced in the child environment.
 */
enum class Environment(
    // The name of this environment so that it can be referenced in CMakeSettings.json
    // inheritEnvironments lists.
    val environment : String,
    // The namespace of macros exposed by this environment.
    val namespace : String,
    // Environments that this environment inherits from.
    vararg inheritEnvironments : Environment) {
    /**
     * Environment for holding the built-in macros exposed by Microsoft. For example, ${thisFile}.
     * Even though the names of the macros are defined by Microsoft, the values in this particular
     * environment come from us. Therefore, the name of the environment contains "from-android-ndk".
     */
    MICROSOFT_BUILT_IN("microsoft-built-ins-from-android-ndk", "env"),
    /**
     * Environment that would be produced by the current Android NDK.
     */
    NDK("android-ndk", "ndk"),
    /**
     * Environment produced by the NDK for an explicit platform ID.
     */
    NDK_PLATFORM("android-ndk-platform-\${ndk.platformSystemVersion}", "ndk"),
    /**
     * Environment produced by the NDK for particular ABI.
     */
    NDK_ABI("android-ndk-abi-\${ndk.abi}", "ndk"),
    /**
     * Environment for macros exposed by Android Gradle Plugin. For example, ${ndk.ABI} is the
     * name of the ABI that is currently being built.
     */
    GRADLE("android-gradle", "ndk"),
    /**
     * This is a special purpose environment that inherits from the others in the defined order.
     *
     * In this ordering, earlier environment values can be replaced by later environment values.
     *
     * NDK is first (for global NDK values), then the more specific NDK environments NDK_PLATFORM
     * and NDK_ABI. Next GRADLE so that Android Gradle Plugin can replace NDK values if needed.
     * Lastly, there is MICROSOFT_BUILT_IN which shouldn't be replaceable.
     *
     * It exposes a single macro ${ndk.androidGradleIsHosting} that can be used to detect whether
     * CMakeSettings.json is being evaluated by Android Gradle Plugin.
     */
    NDK_EXPOSED_BY_HOST("ndk", "ndk", NDK, NDK_PLATFORM, NDK_ABI,
        GRADLE, MICROSOFT_BUILT_IN);

    /**
     * Environments that this environment inherits from.
     */
    val inheritEnvironments = inheritEnvironments.toList()
}
