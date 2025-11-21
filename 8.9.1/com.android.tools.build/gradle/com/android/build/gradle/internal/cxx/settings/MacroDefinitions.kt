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

import com.android.SdkConstants.NDK_DEFAULT_VERSION
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
import com.android.build.gradle.internal.cxx.model.ModelField
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_ABI_PLATFORM_VERSION
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_ALT_CPU_ARCHITECTURE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_BITNESS
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_CPU_ARCHITECTURE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_CXX_BUILD_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_FULL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_INTERMEDIATES_PARENT_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_IS_64_BITS
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_IS_DEFAULT
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_IS_DEPRECATED
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_NINJA_BUILD_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_NINJA_BUILD_LOCATION_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_PLATFORM
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_PLATFORM_CODE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_PREFAB_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_SO_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_SO_REPUBLISH_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_STL_LIBRARY_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_ABI_MODEL_TAG
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_CMAKE_MODULE_MODEL_CMAKE_EXE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CMAKE_GENERATOR
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CMAKE_SETTINGS_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_CXX_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_HAS_BUILD_TIME_INFORMATION
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_INTERMEDIATES_BASE_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_INTERMEDIATES_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_MAKE_FILE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_MAKE_FILE_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_MODULE_NAME
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_MODULE_ROOT_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_MAJOR_VERSION
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_MAX_PLATFORM
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_MINOR_VERSION
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_MIN_PLATFORM
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NDK_VERSION
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_MODULE_MODEL_NINJA_EXE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_PROJECT_MODEL_ROOT_BUILD_GRADLE_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_PROJECT_MODEL_SDK_FOLDER
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_CPP_FLAGS
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_C_FLAGS
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_OPTIMIZATION_TAG
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_STL_TYPE
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_VARIANT_NAME
import com.android.build.gradle.internal.cxx.model.ModelField.CXX_VARIANT_MODEL_VERBOSE_MAKEFILE
import com.android.build.gradle.internal.cxx.settings.Environment.GRADLE
import com.android.build.gradle.internal.cxx.settings.Environment.MICROSOFT_BUILT_IN
import com.android.build.gradle.internal.cxx.settings.Environment.NDK
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_EXPOSED_BY_HOST
import com.android.build.gradle.internal.cxx.settings.AvailabilityPhase.BUILD
import com.android.build.gradle.internal.cxx.settings.AvailabilityPhase.CONFIGURATION

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
    val bind: ModelField? = null,
    // Set if this macro value maps to specific CMake properties.
    val cmakeProperties: List<CmakeProperty> = listOf(),
    // If ndk-build has a different value than CMake, then this holds the ndk-build example
    val ndkBuildExample: String? = null,
    // The Gradle phase that this property becomes available in.
    val available: AvailabilityPhase = CONFIGURATION) {

    NDK_CONFIGURATION_HASH(
        description = "First eight characters of \${ndk.fullConfigurationHash}.",
        environment = GRADLE,
        tag = "configurationHash",
        example = "1m6w461r",
        bind = CXX_ABI_MODEL_CONFIGURATION_HASH),
    NDK_FULL_CONFIGURATION_HASH(
        description = "Hash of this CMakeSettings configuration.",
        environment = GRADLE,
        tag = "fullConfigurationHash",
        example = "1m6w461rf3l272y5d5d5c2m651a4i4j1c3n69zm476ys1g403j69363k4519",
        bind = CXX_ABI_MODEL_FULL_CONFIGURATION_HASH),
    NDK_MIN_PLATFORM(
        description = "The minimum Android platform supported by the current Android NDK.",
        environment = NDK,
        tag = "minPlatform",
        example = "16",
        bind = CXX_MODULE_MODEL_NDK_MIN_PLATFORM),
    NDK_MAX_PLATFORM(
        description = "The maximum Android platform supported by the current Android NDK.",
        environment = NDK,
        tag = "maxPlatform",
        example = "29",
        bind = CXX_MODULE_MODEL_NDK_MAX_PLATFORM),
    NDK_MODULE_MAKE_FILE(
        description = "Path to the make file for the current build system type.",
        environment = GRADLE,
        tag = "moduleMakeFile",
        example = "\$PROJECTS/MyProject/CMakeLists.txt",
        ndkBuildExample = "\$PROJECTS/MyProject/Android.mk",
        bind = CXX_MODULE_MODEL_MAKE_FILE),
    NDK_PLATFORM_SYSTEM_VERSION(
        description = "The currently targeted Android system version, suitable for passing to " +
                "CMake in CMAKE_SYSTEM_VERSION.",
        environment = Environment.NDK_PLATFORM,
        tag = "platformSystemVersion",
        example = "19",
        cmakeProperties = listOf(CMAKE_SYSTEM_VERSION),
        bind = CXX_ABI_MODEL_ABI_PLATFORM_VERSION),
    NDK_PLATFORM(
        description = "The currently targeted Android platform string, that can be passed to " +
                "CMake in ANDROID_PLATFORM.",
        environment = Environment.NDK_PLATFORM,
        tag = "platform",
        example = "android-19",
        cmakeProperties = listOf(ANDROID_PLATFORM),
        bind = CXX_ABI_MODEL_PLATFORM),
    NDK_PLATFORM_CODE(
        description = "The currently targeted Android platform code name.",
        environment = Environment.NDK_PLATFORM,
        tag = "platformCode",
        example = "K",
        bind = CXX_ABI_MODEL_PLATFORM_CODE),
    NDK_ABI_BITNESS(
        description = "The bitness of the targeted ABI.",
        environment = Environment.NDK_ABI,
        tag = "abiBitness",
        example = "64",
        bind = CXX_ABI_MODEL_BITNESS),
    NDK_ABI_IS_64_BITS(
        description = "Whether the targeted ABI is 64-bits.",
        environment = Environment.NDK_ABI,
        tag = "abiIs64Bits",
        example = "1",
        bind = CXX_ABI_MODEL_IS_64_BITS),
    NDK_ABI_IS_DEFAULT(
        description = "Whether the targeted ABI is a default ABI in the current Android NDK.",
        environment = Environment.NDK_ABI,
        tag = "abiIsDefault",
        example = "1",
        bind = CXX_ABI_MODEL_IS_DEFAULT),
    NDK_ABI_IS_DEPRECATED(
        description = "True if the targeted ABI is deprecated in the current Android NDK.",
        environment = Environment.NDK_ABI,
        tag = "abiIsDeprecated",
        example = "0",
        bind = CXX_ABI_MODEL_IS_DEPRECATED),
    NDK_ABI(
        description = "Currently targeted ABI.",
        environment = GRADLE,
        tag = "abi",
        example = "x86_64",
        cmakeProperties = listOf(ANDROID_ABI, CMAKE_ANDROID_ARCH_ABI),
        bind = CXX_ABI_MODEL_TAG),
    NDK_ABI_CPU_ARCHITECTURE(
        description = "The CPU architecture.",
        environment = GRADLE,
        tag = "abiCpuArchitecture",
        example = "x86_64",
        bind = CXX_ABI_MODEL_CPU_ARCHITECTURE),
    NDK_ABI_ALT_CPU_ARCHITECTURE(
        description = "Alternative CPU architecture name that is compatible with vcpkg.",
        environment = GRADLE,
        tag = "abiAltCpuArchitecture",
        example = "x64",
        bind = CXX_ABI_MODEL_ALT_CPU_ARCHITECTURE),
    NDK_PROJECT_SDK_DIR(
        description = "Folder of the current Android SDK.",
        environment = GRADLE,
        tag = "projectSdkDir",
        example = "\$HOME/Library/Android/sdk",
        bind = CXX_PROJECT_MODEL_SDK_FOLDER),
    NDK_MODULE_NDK_DIR(
        description = "Folder of the current Android NDK.",
        environment = GRADLE,
        tag = "moduleNdkDir",
        example = "${NDK_PROJECT_SDK_DIR.ref}/ndk/$NDK_DEFAULT_VERSION",
        cmakeProperties = listOf(ANDROID_NDK, CMAKE_ANDROID_NDK),
        bind = CXX_MODULE_MODEL_NDK_FOLDER),
    NDK_CMAKE_TOOLCHAIN(
        description = "Path to the current Android NDK's CMake toolchain.",
        environment = NDK,
        tag = "cmakeToolchain",
        example = "${NDK_MODULE_NDK_DIR.ref}/build/cmake/android.toolchain.cmake",
        cmakeProperties = listOf(CMAKE_TOOLCHAIN_FILE),
        bind = CXX_MODULE_MODEL_CMAKE_TOOLCHAIN_FILE),
    NDK_MODULE_CMAKE_EXECUTABLE(
        description = "Path to CMake executable.",
        environment = GRADLE,
        tag = "moduleCmakeExecutable",
        example = "${NDK_PROJECT_SDK_DIR.ref}/cmake/$defaultCmakeVersion/bin/cmake",
        ndkBuildExample = "",
        available = BUILD,
        bind = CXX_CMAKE_MODULE_MODEL_CMAKE_EXE),
    NDK_MODULE_NINJA_EXECUTABLE(
        description = "Path to Ninja executable if one was found by Gradle. Otherwise, it expands" +
                " to empty string and it's up to CMake to find the ninja executable.",
        environment = GRADLE,
        tag = "moduleNinjaExecutable",
        example = "${NDK_PROJECT_SDK_DIR.ref}/cmake/$defaultCmakeVersion/bin/ninja",
        ndkBuildExample = "",
        cmakeProperties = listOf(CMAKE_MAKE_PROGRAM),
        available = BUILD,
        bind = CXX_MODULE_MODEL_NINJA_EXE),
    NDK_MODULE_CMAKE_GENERATOR(
        description = "Name of the generator used by CMake.",
        environment = GRADLE,
        tag = "moduleCmakeGenerator",
        example = "Ninja",
        ndkBuildExample = "",
        bind = CXX_MODULE_MODEL_CMAKE_GENERATOR),
    NDK_MODULE_HAS_BUILD_TIME_INFORMATION(
        description = "Whether build-time information is available. If false, then only " +
                "configuration-time information is available.",
        environment = GRADLE,
        tag = "moduleHasBuildTimeInformation",
        example = "true",
        bind = CXX_MODULE_MODEL_HAS_BUILD_TIME_INFORMATION),
    NDK_MODULE_NDK_VERSION(
        description = "Version of NDK.",
        environment = GRADLE,
        tag = "moduleNdkVersion",
        example = NDK_DEFAULT_VERSION,
        bind = CXX_MODULE_MODEL_NDK_VERSION),
    NDK_MODULE_NDK_VERSION_MAJOR(
        description = "Version number major part.",
        environment = GRADLE,
        tag = "moduleNdkVersionMajor",
        example = NDK_DEFAULT_VERSION.split(".")[0],
        bind = CXX_MODULE_MODEL_NDK_MAJOR_VERSION),
    NDK_MODULE_NDK_VERSION_MINOR(
        description = "Version number minor part.",
        environment = GRADLE,
        tag = "moduleNdkVersionMinor",
        example = NDK_DEFAULT_VERSION.split(".")[1],
        bind = CXX_MODULE_MODEL_NDK_MINOR_VERSION),
    NDK_MODULE_DIR(
        description = "Folder of the module level build.gradle.",
        environment = GRADLE,
        tag = "moduleDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1",
        bind = CXX_MODULE_MODEL_MODULE_ROOT_FOLDER),
    NDK_MODULE_BUILD_INTERMEDIATES_BASE_DIR(
        description = "The module level build base intermediates folder.",
        environment = GRADLE,
        tag = "moduleBuildIntermediatesBaseDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1/build/intermediates",
        bind = CXX_MODULE_MODEL_INTERMEDIATES_BASE_FOLDER),
    NDK_MODULE_BUILD_INTERMEDIATES_DIR(
        description = "The module level build intermediates cxx subfolder.",
        environment = GRADLE,
        tag = "moduleBuildIntermediatesDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1/build/intermediates/cxx",
        bind = CXX_MODULE_MODEL_INTERMEDIATES_FOLDER),
    NDK_VARIANT_NAME(
        description = "Name of the gradle variant.",
        environment = GRADLE,
        tag = "variantName",
        example = "debug",
        bind = CXX_VARIANT_MODEL_VARIANT_NAME),
    NDK_MODULE_NAME(
        description = "Name of the gradle module.",
        environment = GRADLE,
        tag = "moduleName",
        example = "app1",
        bind = CXX_MODULE_MODEL_MODULE_NAME),
    NDK_MODULE_BUILD_ROOT(
        description = "The default CMake, ndk-build, or Ninja build root folder without ABI.",
        environment = GRADLE,
        tag = "moduleBuildRoot",
        example = "${NDK_MODULE_DIR.ref}/.cxx",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/.cxx",
        bind = CXX_MODULE_MODEL_CXX_FOLDER),
    NDK_BUILD_ROOT(
        description = "The default CMake, ndk-build, or Ninja build root folder that gradle uses.",
        environment = GRADLE,
        tag = "buildRoot",
        example = "${NDK_MODULE_DIR.ref}/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64",
        bind = CXX_ABI_MODEL_CXX_BUILD_FOLDER),
    NDK_NINJA_BUILD_FILE(
        description = "The path to the expected build.ninja file.",
        environment = GRADLE,
        tag = "ninjaBuildFile",
        example = "${NDK_MODULE_DIR.ref}/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64/build.ninja",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64/build.ninja",
        bind = CXX_ABI_MODEL_NINJA_BUILD_FILE),
    NDK_NINJA_BUILD_LOCATION_FILE(
        description = "Path to a file that contains the location of build.ninja. Written by custom" +
                " external build systems to specify the location where build.ninja was written.",
        environment = GRADLE,
        tag = "ninjaBuildLocationFile",
        example = "${NDK_MODULE_DIR.ref}/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64/build.ninja.txt",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/x86_64/build.ninja.txt",
        bind = CXX_ABI_MODEL_NINJA_BUILD_LOCATION_FILE),
    NDK_VARIANT_C_FLAGS(
        description = "The value of cFlags from android.config.externalNativeBuild.cFlags in build.gradle.",
        environment = GRADLE,
        tag = "variantCFlags",
        example = "-DC_FLAG_DEFINED",
        cmakeProperties = listOf(CMAKE_C_FLAGS),
        bind = CXX_VARIANT_MODEL_C_FLAGS),
    NDK_VARIANT_CPP_FLAGS(
        description = "The value of cppFlags from android.config.externalNativeBuild.cppFlags in build.gradle.",
        environment = GRADLE,
        tag = "variantCppFlags",
        example = "-DCPP_FLAG_DEFINED",
        cmakeProperties = listOf(CMAKE_CXX_FLAGS),
        bind = CXX_VARIANT_MODEL_CPP_FLAGS),
    NDK_SO_OUTPUT_DIR(
        description = "The ABI-level folder where .so files are written.",
        environment = GRADLE,
        tag = "soOutputDir",
        example = "${NDK_MODULE_DIR.ref}/build/intermediates/cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/obj/x86_64",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/intermediates/cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/obj/local/x86_64",
        cmakeProperties = listOf(CMAKE_LIBRARY_OUTPUT_DIRECTORY, CMAKE_RUNTIME_OUTPUT_DIRECTORY),
        bind = CXX_ABI_MODEL_SO_FOLDER),
    NDK_SO_REPUBLISH_DIR(
        description = "A folder with a predictable name where final build outputs (mainly .so) are" +
                "hard linked or copied after the build completes. The purpose is so scripts " +
                "and other external tools have a known path, with no embedded hashcode, to locate " +
                "these files.",
        environment = GRADLE,
        tag = "soRepublishDir",
        example = "${NDK_MODULE_DIR.ref}/build/intermediates/cmake/debug/obj/x86_64",
        ndkBuildExample = "${NDK_MODULE_DIR.ref}/build/intermediates/ndkBuild/debug/obj/local/x86_64",
        bind = CXX_ABI_MODEL_SO_REPUBLISH_FOLDER),
    NDK_INTERMEDIATES_PARENT_DIR(
        description = "The intermediates folder but without the ABI at the end.",
        environment = GRADLE,
        tag = "intermediatesParentDir",
        example = "${NDK_MODULE_DIR.ref}/build/intermediates/cxx/Debug/${NDK_CONFIGURATION_HASH.ref}",
        bind = CXX_ABI_MODEL_INTERMEDIATES_PARENT_FOLDER),
    NDK_VARIANT_OPTIMIZATION_TAG(
        description = "The CMAKE_BUILD_TYPE derived from the suffix of gradle variant name. " +
                "May be Debug, Release, RelWithDebInfo, or MinSizeRel.",
        environment = GRADLE,
        tag = "variantOptimizationTag",
        example = "Debug",
        cmakeProperties = listOf(CmakeProperty.CMAKE_BUILD_TYPE),
        bind = CXX_VARIANT_MODEL_OPTIMIZATION_TAG),
    NDK_VARIANT_STL_TYPE(
        description = "The type of the runtime library type (if present).",
        environment = GRADLE,
        tag = "variantStlType",
        example = "c++_shared",
        bind = CXX_VARIANT_MODEL_STL_TYPE),
    NDK_STL_LIBRARY_FILE(
        description = "If present, the STL .so file that needs to be distributed with the libraries built.",
        environment = GRADLE,
        tag = "stlLibraryFile",
        example = "${NDK_MODULE_NDK_DIR.ref}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so",
        bind = CXX_ABI_MODEL_STL_LIBRARY_FILE),
    NDK_PREFAB_PATH(
        description = "The CMAKE_FIND_ROOT_PATH to be used by Prefab for the current configuration.",
        environment = GRADLE,
        tag = "prefabPath",
        example = "\$PROJECTS/MyProject/Source/Android/app1/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/prefab/x86_64",
        ndkBuildExample = "\$PROJECTS/MyProject/Source/Android/app1/build/.cxx/Debug/${NDK_CONFIGURATION_HASH.ref}/prefab/x86_64",
        bind = CXX_ABI_MODEL_PREFAB_FOLDER),
    ENV_THIS_FILE(
        description = "Path to this CMakeSettings.json file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "thisFile",
        example = "\$PROJECTS/MyProject/CMakeSettings.json",
        bind = CXX_MODULE_MODEL_CMAKE_SETTINGS_FILE),
    ENV_THIS_FILE_DIR(
        description = "Folder of this CMakeSettings.json file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "thisFileDir",
        example = "\$PROJECTS/MyProject",
        bind = CXX_MODULE_MODEL_MAKE_FILE_FOLDER),
    ENV_WORKSPACE_ROOT(
        description = "Folder of the project level build.gradle file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "workspaceRoot",
        example = "\$PROJECTS/MyProject/Source/Android",
        bind = CXX_PROJECT_MODEL_ROOT_BUILD_GRADLE_FOLDER),
    ENV_PROJECT_DIR(
        description = "Folder of the module level build.gradle file.",
        environment = MICROSOFT_BUILT_IN,
        tag = "projectDir",
        example = "\$PROJECTS/MyProject/Source/Android/app1",
        bind = CXX_MODULE_MODEL_MODULE_ROOT_FOLDER),
    NDK_ANDROID_GRADLE_IS_HOSTING(
        description = "True if Android Gradle Plugin is hosting this CMakeSettings.json.",
        environment = NDK_EXPOSED_BY_HOST,
        tag = "androidGradleIsHosting",
        example = "1"),
    NDK_VARIANT_VERBOSE_MAKEFILE(
        description = "Whether to invoke build tool with verbosity (for example, ninja -v).",
        environment = GRADLE,
        tag = "variantVerboseMakefile",
        example = "",
        cmakeProperties = listOf(CmakeProperty.CMAKE_VERBOSE_MAKEFILE),
        bind = CXX_VARIANT_MODEL_VERBOSE_MAKEFILE);

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
     * Placeholder to use for build-time values during configuration time.
     */
    val configurationPlaceholder : String get() {
        if (available != BUILD) {
            error("Should only need configurationPlaceholder for build-time available Macro [$name]")
        }
        return "{configuration-time-placeholder:$name}"
    }

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
        //fun withBinding(property: KProperty1<*, *>) = BINDING_PROPERTY_TO_MACRO[property]?:listOf()
        fun withBinding(field: ModelField) = BINDING_PROPERTY_TO_MACRO[field]?:listOf()

        private val BINDING_PROPERTY_TO_MACRO = values()
                .filter { macro -> macro.bind != null }
                .groupBy { macro -> macro.bind!! }

        private val CMAKE_PROPERTY_STRING_TO_MACRO_MAP = values()
                .flatMap { macro -> macro.cmakeProperties.map { cmake -> cmake to macro} }
                .groupBy({ (cmake, _) -> cmake.name }, { (_, macro) -> macro })
    }
}


/**
 * The Gradle phase that a macro value becomes available.
 */
enum class AvailabilityPhase {
    /**
     * Macro value is available during Gradle configuration.
     */
    CONFIGURATION,
    /**
     * Macro value is available during Gradle build.
     */
    BUILD
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
