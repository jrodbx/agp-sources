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

import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.toSharedAndroidVersion
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig
import com.android.build.gradle.internal.cxx.configure.createNativeBuildSystemVariantConfig
import com.android.build.gradle.internal.cxx.configure.isCmakeForkVersion
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
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
import com.android.build.gradle.tasks.CmakeAndroidNinjaExternalNativeJsonGenerator
import com.android.build.gradle.tasks.CmakeQueryMetadataGenerator
import com.android.build.gradle.tasks.CmakeServerExternalNativeJsonGenerator
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.build.gradle.tasks.NdkBuildExternalNativeJsonGenerator
import com.android.build.gradle.tasks.getPrefabFromMaven
import com.android.builder.profile.ChromeTracingProfileConverter
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_IS_MISSING
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_VERSION_IS_UNSUPPORTED
import com.android.utils.cxx.CxxDiagnosticCode.INVALID_EXTERNAL_NATIVE_BUILD_CONFIG
import org.gradle.api.file.FileCollection
import java.io.File
import java.lang.IllegalStateException
import java.util.Locale
import java.util.Objects

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
    variant: VariantImpl) : CxxConfigurationParameters? {
    val global = variant.globalScope
    val projectInfo = variant.services.projectInfo
    val project = projectInfo.getProject()

    val (buildSystem, makeFile, buildStagingFolder) =
        getProjectPath(global.extension.externalNativeBuild)
            ?: return null

    val cxxFolder = findCxxFolder(
        buildSystem,
        project.projectDir,
        buildStagingFolder,
        project.buildDir
    )
    val cxxCacheFolder = join(projectInfo.getIntermediatesDir(), "cxx")
    fun option(option: BooleanOption) = variant.services.projectOptions.get(option)
    fun option(option: StringOption) = variant.services.projectOptions.get(option)

    /**
     * Construct an [NdkHandler] and attempt to auto-download an NDK. If auto-download fails then
     * allow valid [errorln]s to pass or throw exception that will trigger download hyperlinks
     * in Android Studio
     */
    val ndkHandler = global.sdkComponents.get().versionedNdkHandler(
        compileSdkVersion = global.extension.compileSdkVersion ?:
            throw IllegalStateException("compileSdkVersion not set in android configuration"),
        ndkVersion = global.extension.ndkVersion,
        ndkPath = global.extension.ndkPath
    )
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
        val gradle = project.gradle
        val profileDir = option(PROFILE_OUTPUT_DIR)
            ?.let { gradle.rootProject.file(it) }
            ?: gradle.rootProject.buildDir.resolve(PROFILE_DIRECTORY)
        profileDir.resolve(ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY)
    } else {
        null
    }
    val prefabTargets = when (val extension = variant.globalScope.extension) {
        is LibraryExtension -> extension.prefab.map { it.name }.toSet()
        else -> emptySet()
    }

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

    val prefabClassPath = if (variant.buildFeatures.prefab) {
        getPrefabFromMaven(
            projectOptions,
            variant.services.projectInfo.getProject())
    } else {
        null
    }
    val outputOptions = (option(NATIVE_BUILD_OUTPUT_LEVEL)?:"")
        .toUpperCase(Locale.US)
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { level ->
            val result = NativeBuildOutputOptions.values().firstOrNull { "$it" == level }
            if (result == null) {
                errorln("$NATIVE_BUILD_OUTPUT_LEVEL contains unrecognized $level")
            }
            result
        }
        .toSet()

    return CxxConfigurationParameters(
        cxxFolder = cxxFolder,
        cxxCacheFolder = cxxCacheFolder,
        buildSystem = buildSystem,
        makeFile = makeFile,
        buildStagingFolder = buildStagingFolder,
        moduleRootFolder = project.projectDir,
        buildDir = project.buildDir,
        rootDir = project.rootDir,
        buildFile = project.buildFile,
        isDebuggable = variant.debuggable,
        minSdkVersion = variant.minSdkVersion.toSharedAndroidVersion(),
        compileSdkVersion = global.extension.compileSdkVersion ?:
            throw IllegalStateException("compileSdkVersion not set in android configuration"),
        ndkVersion = global.extension.ndkVersion,
        ndkPath = global.extension.ndkPath,
        cmakeVersion = global.extension.externalNativeBuild.cmake.version,
        splitsAbiFilterSet = global.extension.splits.abiFilters,
        intermediatesFolder = projectInfo.getIntermediatesDir(),
        gradleModulePathName = project.path,
        isBuildOnlyTargetAbiEnabled = option(BUILD_ONLY_TARGET_ABI),
        ideBuildTargetAbi = option(IDE_BUILD_TARGET_ABI),
        isCmakeBuildCohabitationEnabled = option(ENABLE_CMAKE_BUILD_COHABITATION),
        chromeTraceJsonFolder = chromeTraceJsonFolder,
        isPrefabEnabled = variant.buildFeatures.prefab,
        prefabClassPath = prefabClassPath,
        prefabPackageDirectoryList = prefabPackageDirectoryList,
        implicitBuildTargetSet = prefabTargets,
        variantName = variant.name,
        nativeVariantConfig = createNativeBuildSystemVariantConfig(
            buildSystem, variant, variant.variantDslInfo
        ),
        outputOptions = outputOptions
    )
}


/**
 * Resolve the CMake or ndk-build path and buildStagingDirectory of native build project.
 * - If there is exactly 1 path in the DSL, then use it.
 * - If there are more than 1, then that is an error. The user has specified both cmake and
 *   ndkBuild in the same project.
 */
private fun getProjectPath(config: ExternalNativeBuild)
        : Triple<NativeBuildSystem, File, File?>? {
    val externalProjectPaths = listOfNotNull(
        config.cmake.path?.let { Triple(CMAKE, it, config.cmake.buildStagingDirectory)},
        config.ndkBuild.path?.let { Triple(NativeBuildSystem.NDK_BUILD, it, config.ndkBuild.buildStagingDirectory) })

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

/**
 * This function is used at task execution time to construct a [CxxMetadataGenerator] to do the
 * task's work. It should only have parameters that can be obtained after task graph
 * deserialization.
 */
fun createCxxMetadataGenerator(
    configurationModel: CxxConfigurationModel,
    analyticsService: AnalyticsService
): CxxMetadataGenerator {
    if(ENABLE_CHECK_CONFIG_TIME_CONSTRUCTION) {
        check(!isGradleConfiguration()) {
            "Should not call createCxxMetadataGenerator(...) at configuration time"
        }
    }

    val (variant, abis) = configurationModel

    val variantBuilder = analyticsService.getVariantBuilder(
        variant.module.gradleModulePathName, variant.variantName)

    return when (variant.module.buildSystem) {
        NativeBuildSystem.NDK_BUILD -> NdkBuildExternalNativeJsonGenerator(
            variant,
            abis,
            variantBuilder
        )
        CMAKE -> {
            val cmake =
                Objects.requireNonNull(variant.module.cmake)!!
            if (!cmake.isValidCmakeAvailable) {
                errorln(CMAKE_IS_MISSING, "No valid CMake executable was found.")
                return CxxNopMetadataGenerator(variantBuilder)
            }
            val cmakeRevision = cmake.minimumCmakeVersion
            variantBuilder?.nativeCmakeVersion = cmakeRevision.toString()
            if (cmakeRevision.isCmakeForkVersion()) {
                return CmakeAndroidNinjaExternalNativeJsonGenerator(variant, abis, variantBuilder)
            }
            if (cmakeRevision.major < 3
                || cmakeRevision.major == 3 && cmakeRevision.minor <= 6
            ) {
                errorln(
                    CMAKE_VERSION_IS_UNSUPPORTED,
                    "Unsupported CMake version $cmakeRevision. Try 3.7.0 or later."
                )
                return CxxNopMetadataGenerator(variantBuilder)
            }

            val isPreCmakeFileApiVersion = cmakeRevision.major == 3 && cmakeRevision.minor < 15
            if (isPreCmakeFileApiVersion) {
                return CmakeServerExternalNativeJsonGenerator(variant, abis, variantBuilder)
            }
            return CmakeQueryMetadataGenerator(variant, abis, variantBuilder)
        }
    }
}

private fun isGradleConfiguration() : Boolean {
    return Thread.currentThread().stackTrace
            .any { it.toString().contains("BasePlugin.createAndroidTasks" ) }
}
