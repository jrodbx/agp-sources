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

package com.android.build.gradle.internal.cxx.gradle.generator

import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.toSharedAndroidVersion
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig
import com.android.build.gradle.internal.cxx.configure.NativeLocationsBuildService
import com.android.build.gradle.internal.cxx.configure.NinjaMetadataGenerator
import com.android.build.gradle.internal.cxx.configure.createNativeBuildSystemVariantConfig
import com.android.build.gradle.internal.cxx.configure.ninja
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.model.minimumCmakeVersion
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.PROFILE_DIRECTORY
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.BooleanOption.BUILD_ONLY_TARGET_ABI
import com.android.build.gradle.options.BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION
import com.android.build.gradle.options.BooleanOption.ENABLE_PROFILE_JSON
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.StringOption.IDE_BUILD_TARGET_ABI
import com.android.build.gradle.options.StringOption.NATIVE_BUILD_OUTPUT_LEVEL
import com.android.build.gradle.options.StringOption.PROFILE_OUTPUT_DIR
import com.android.build.gradle.tasks.CmakeQueryMetadataGenerator
import com.android.build.gradle.tasks.CmakeServerExternalNativeJsonGenerator
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NINJA
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.build.gradle.tasks.NdkBuildExternalNativeJsonGenerator
import com.android.build.gradle.tasks.getPrefabFromMaven
import com.android.builder.profile.ChromeTracingProfileConverter
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_OUTPUT_LEVEL_NOT_SUPPORTED
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_IS_MISSING
import com.android.utils.cxx.CxxDiagnosticCode.INVALID_EXTERNAL_NATIVE_BUILD_CONFIG
import com.android.utils.cxx.CxxDiagnosticCode.NINJA_IS_MISSING
import org.gradle.api.file.FileCollection
import java.io.File
import java.util.Locale

/**
 * The createCxxMetadataGenerator(...) function is meant to be use at
 * task action time and specifically not during config-time. Any config-
 * time data needed for C/C++ build should come from [CxxConfigurationModel].
 *
 * Change to 'true' to check that createCxxMetadataGenerator(...) is not
 * called at configuration time. But don't check it in set to 'true'.
 * There's a unittest to enforce this.
 */
val ENABLE_CHECK_CONFIG_TIME_CONSTRUCTION by lazy { System.getenv().containsKey("TEST_WORKSPACE") }

/**
 * This is the task data model that gets serialized with the task in configuration caching.
 */
data class CxxConfigurationModel(
    val variant: CxxVariantModel,
    // ABIs that are used in the build
    val activeAbis: List<CxxAbiModel>,
    // Remaining ABIs that are not used in the build
    val unusedAbis: List<CxxAbiModel>
)

/**
 * C/C++ logging options passed via -Pandroid.native.buildOutput gradle flag
 *
 * This enum is meant to enable different logging subsystems rather than to
 * supplement or replace Gradle's existing verbosity levels.
 *
 * Default (or 'none' logging) is represented by an empty set.
 */
enum class NativeBuildOutputOptions {
    VERBOSE, // Whether to forward the full native build, configure, and clean output to stdout.
    PREFAB_STDOUT, // Whether to forward the full native prefab output to stdout.
    CONFIGURE_STDOUT, // Whether to forward the full native configure output to stdout.
    BUILD_STDOUT, // Whether to forward the full native build output to stdout.
    CLEAN_STDOUT, // Whether to forward the full native clean output to stdout.
}

/**
 * Parameters common to configuring the creation of [CxxConfigurationModel].
 */
data class CxxConfigurationParameters(
    val cxxFolder: File,
    val cxxCacheFolder: File,
    val buildSystem: NativeBuildSystem,
    val makeFile: File,
    val configureScript: File?,
    val buildStagingFolder: File?,
    val moduleRootFolder: File,
    val buildDir: File,
    val rootDir: File,
    val buildFile: File,
    val isDebuggable: Boolean,
    val minSdkVersion: AndroidVersion,
    val compileSdkVersion: String,
    val ndkVersion: String?,
    val ndkPath: String?,
    val cmakeVersion: String?,
    val splitsAbiFilterSet: Set<String>,
    val intermediatesFolder: File,
    val gradleModulePathName: String,
    val isBuildOnlyTargetAbiEnabled: Boolean,
    val ideBuildTargetAbi: String?,
    val isCmakeBuildCohabitationEnabled: Boolean,
    val chromeTraceJsonFolder: File?,
    val isPrefabEnabled: Boolean,
    val prefabClassPath: FileCollection?,
    val prefabPackageDirectoryList: FileCollection?,
    val prefabPackageConfigurationList: FileCollection?,
    val implicitBuildTargetSet: Set<String>,
    val variantName: String,
    val nativeVariantConfig: NativeBuildSystemVariantConfig,
    val outputOptions: Set<NativeBuildOutputOptions>,
)

/**
 * Finds the location of the build-system output folder. For example, .cxx/cmake/debug/x86/
 *
 * If user specific externalNativeBuild.cmake.buildStagingFolder = 'xyz' then that folder
 * will be used instead of the default of moduleRoot/.cxx.
 *
 * If the resulting build output folder would be inside of moduleRoot/build then issue an error
 * because moduleRoot/build will be deleted when the user does clean and that will lead to
 * undefined behavior.
 */
 private fun findCxxFolder(
        buildSystem : NativeBuildSystem,
        moduleRootFolder : File,
        buildStagingDirectory: File?,
        buildFolder: File): File {
    val defaultCxxFolder =
        when(buildSystem) {
            NDK_BUILD -> join(buildFolder, CXX_DEFAULT_CONFIGURATION_SUBFOLDER)
            else -> join(moduleRootFolder, CXX_DEFAULT_CONFIGURATION_SUBFOLDER)
        }
    return when {
        buildStagingDirectory == null -> defaultCxxFolder
        FileUtils.isFileInDirectory(buildStagingDirectory, buildFolder) -> {
            if (buildSystem != NDK_BUILD) {
                warnln("""
                    The build staging directory you specified ('${buildStagingDirectory.absolutePath}')
                    is a subdirectory of your project's temporary build directory (
                    '${buildFolder.absolutePath}'). Files in this directory do not persist through clean
                    builds. It is recommended to either use the default build staging directory
                    ('$defaultCxxFolder'), or specify a path outside the temporary build directory.
            """.trimIndent())
            }
            buildStagingDirectory
        }
        else -> buildStagingDirectory
    }
}

/**
 * Try to create C/C++ model configuration parameters [CxxConfigurationParameters].
 * Return null when there is no C/C++ in the user's project or if there is some other kind of error.
 * In the latter case, an error will have been reported.
 */
fun tryCreateConfigurationParameters(
    projectOptions: ProjectOptions,
    variant: VariantCreationConfig
): CxxConfigurationParameters? {
    val globalConfig = variant.global
    val projectInfo = variant.services.projectInfo
    val nativeBuildCreationConfig = variant.nativeBuildCreationConfig!!
    val (buildSystem, makeFile, configureScript, buildStagingFolder) =
        getProjectPath(nativeBuildCreationConfig, globalConfig.externalNativeBuild) ?: return null

    val cxxFolder = findCxxFolder(
        buildSystem,
        projectInfo.projectDirectory.asFile,
        buildStagingFolder,
        projectInfo.getBuildDir()
    )
    val cxxCacheFolder = join(projectInfo.getIntermediatesDir(), "cxx")
    fun option(option: BooleanOption) = variant.services.projectOptions.get(option)
    fun option(option: StringOption) = variant.services.projectOptions.get(option)

    /**
     * Construct an [NdkHandler] and attempt to auto-download an NDK. If auto-download fails then
     * allow valid [errorln]s to pass or throw exception that will trigger download hyperlinks
     * in Android Studio
     */
    val ndkHandler = globalConfig.versionedNdkHandler
    val ndkInstall = CachingEnvironment(cxxCacheFolder).use {
        ndkHandler.getNdkPlatform(downloadOkay = true)
    }
    if (!ndkInstall.isConfigured) {
        infoln("Not creating C/C++ model because NDK could not be configured.")
        return null
    }

    /**
     * Chrome trace settings.
     */
    val enableProfileJson = option(ENABLE_PROFILE_JSON)
    val chromeTraceJsonFolder = if (enableProfileJson) {
        val profileDir = option(PROFILE_OUTPUT_DIR)
            ?.let { variant.services.file(it) }
            ?: variant.services.projectInfo.rootBuildDir.resolve(PROFILE_DIRECTORY)
        profileDir.resolve(ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY)
    } else {
        null
    }
    val prefabTargets = globalConfig.prefabOrEmpty.map { it.name }.toSet()

    /**
     * Prefab settings.
     */
    val prefabPackageDirectoryList = if (variant.buildFeatures.prefab) {
        variant.variantDependencies.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
        ).artifactFiles
    } else {
        null
    }

    val prefabPackageConfigurationList = if (variant.buildFeatures.prefab) {
        variant.variantDependencies.getArtifactCollection(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.ALL,
            AndroidArtifacts.ArtifactType.PREFAB_PACKAGE_CONFIGURATION
        ).artifactFiles
    } else {
        null
    }

    val prefabClassPath = if (variant.buildFeatures.prefab) {
        getPrefabFromMaven(projectOptions, variant.services)
    } else {
        null
    }
    val outputOptions = (option(NATIVE_BUILD_OUTPUT_LEVEL)?:"")
        .uppercase(Locale.US)
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { level ->
            val result = NativeBuildOutputOptions.values().firstOrNull { "$it" == level }
            if (result == null) {
                errorln(
                    BUILD_OUTPUT_LEVEL_NOT_SUPPORTED,
                    "$NATIVE_BUILD_OUTPUT_LEVEL contains unrecognized $level")
            }
            result
        }
        .toSet()

    return CxxConfigurationParameters(
        cxxFolder = cxxFolder,
        cxxCacheFolder = cxxCacheFolder,
        buildSystem = buildSystem,
        makeFile = makeFile,
        configureScript = configureScript,
        buildStagingFolder = buildStagingFolder,
        moduleRootFolder = projectInfo.projectDirectory.asFile,
        buildDir = projectInfo.getBuildDir(),
        rootDir = projectInfo.rootDir,
        buildFile = projectInfo.buildFile,
        isDebuggable = variant.debuggable,
        minSdkVersion = variant.minSdk.toSharedAndroidVersion(),
        compileSdkVersion = globalConfig.compileSdkHashString,
        ndkVersion = globalConfig.ndkVersion,
        ndkPath = globalConfig.ndkPath,
        cmakeVersion = globalConfig.externalNativeBuild.cmake.version,
        splitsAbiFilterSet = globalConfig.splits.abiFilters.toSet(),
        intermediatesFolder = projectInfo.getIntermediatesDir(),
        gradleModulePathName = projectInfo.path,
        isBuildOnlyTargetAbiEnabled = option(BUILD_ONLY_TARGET_ABI),
        ideBuildTargetAbi = option(IDE_BUILD_TARGET_ABI),
        isCmakeBuildCohabitationEnabled = option(ENABLE_CMAKE_BUILD_COHABITATION),
        chromeTraceJsonFolder = chromeTraceJsonFolder,
        isPrefabEnabled = variant.buildFeatures.prefab,
        prefabClassPath = prefabClassPath,
        prefabPackageDirectoryList = prefabPackageDirectoryList,
        prefabPackageConfigurationList = prefabPackageConfigurationList,
        implicitBuildTargetSet = prefabTargets,
        variantName = variant.name,
        nativeVariantConfig = createNativeBuildSystemVariantConfig(
            variant as VariantImpl<*>,
            nativeBuildCreationConfig
        ),
        outputOptions = outputOptions
    )
}

/**
 * Return true if this Gradle module contains a C/C++ build.
 */
fun externalNativeBuildIsActive(creationConfig : ConsumableCreationConfig) : Boolean {
    return creationConfig.nativeBuildCreationConfig?.let { nativeBuildCreationConfig ->
        getProjectPath(
            nativeBuildCreationConfig,
            creationConfig.global.externalNativeBuild
        )
    } != null
}

/**
 * Resolve the CMake or ndk-build path and buildStagingDirectory of native build project.
 * - If there is exactly 1 path in the DSL, then use it.
 * - If there are more than 1, then that is an error. The user has specified both cmake and
 *   ndkBuild in the same project.
 */
private fun getProjectPath(
    component: NativeBuildCreationConfig,
    config: ExternalNativeBuild
): NativeProjectPath? {
    val externalProjectPaths = listOfNotNull(
        component.ninja.path?.let { NativeProjectPath(NINJA, it, component.ninja.configure, component.ninja.buildStagingDirectory) },
        config.cmake.path?.let { NativeProjectPath(CMAKE, it, null, config.cmake.buildStagingDirectory) },
        config.ndkBuild.path?.let { NativeProjectPath(NDK_BUILD, it, null, config.ndkBuild.buildStagingDirectory) })

    return when {
        externalProjectPaths.size > 1 -> {
            errorln(
                INVALID_EXTERNAL_NATIVE_BUILD_CONFIG,
                "More than one externalNativeBuild path specified"
            )
            null
        }
        externalProjectPaths.isEmpty() -> {
            // No external projects present.
            null
        }
        else -> externalProjectPaths[0]
    }
}

private data class NativeProjectPath(
    val buildSystem : NativeBuildSystem,
    val makefile : File,
    val configureScript : File?,
    val buildStagingDirectory: File?
)

/**
 * This function is used at task execution time to construct a [CxxMetadataGenerator] to do the
 * task's work. It should only have parameters that can be obtained after task graph
 * deserialization.
 */
fun createCxxMetadataGenerator(
    abi: CxxAbiModel,
    analyticsService: AnalyticsService
): CxxMetadataGenerator {
    if(ENABLE_CHECK_CONFIG_TIME_CONSTRUCTION) {
        check(!isGradleConfiguration()) {
            "Should not call createCxxMetadataGenerator(...) at configuration time"
        }
    }

    val variant = abi.variant

    val variantBuilder = analyticsService.getVariantBuilder(
        variant.module.gradleModulePathName, variant.variantName)

    return when (variant.module.buildSystem) {
        NINJA -> NinjaMetadataGenerator(abi, variantBuilder)
        NDK_BUILD -> NdkBuildExternalNativeJsonGenerator(
            abi,
            variantBuilder
        )
        CMAKE -> {
            val cmake = abi.variant.module.cmake
            if (cmake == null) {
                errorln(CMAKE_IS_MISSING, "No valid CMake executable was found.")
                CxxNopMetadataGenerator(variantBuilder)
            } else {
                val cmakeRevision = cmake.minimumCmakeVersion
                variantBuilder?.nativeCmakeVersion = cmakeRevision.toString()
                if (cmakeRevision.major < 3
                    || cmakeRevision.major == 3 && cmakeRevision.minor <= 6
                ) {
                    // Aside from fork-CMake, this is the range of CMake versions that was
                    // unsupported before the introduction of Ninja-parsing based metadata generation.
                    CMakeNinjaParserMetadataGenerator(abi, variantBuilder)
                } else {
                    val isPreCmakeFileApiVersion = cmakeRevision.major == 3 && cmakeRevision.minor < 15
                    if (isPreCmakeFileApiVersion) {
                        CmakeServerExternalNativeJsonGenerator(abi, variantBuilder)
                    } else {
                        CmakeQueryMetadataGenerator(abi, variantBuilder)
                    }
                }
            }
        }
        else -> error("${variant.module.buildSystem}")
    }
}

private fun isGradleConfiguration() : Boolean {
    return Thread.currentThread().stackTrace
            .any { it.toString().contains("BasePlugin.createAndroidTasks" ) }
}
