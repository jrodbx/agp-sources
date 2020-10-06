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

import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.toSharedAndroidVersion
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig
import com.android.build.gradle.internal.cxx.configure.createNativeBuildSystemVariantConfig
import com.android.build.gradle.internal.cxx.configure.isCmakeForkVersion
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxModuleModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.settings.rewriteCxxAbiModelWithCMakeSettings
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.PROFILE_DIRECTORY
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.BooleanOption.BUILD_ONLY_TARGET_ABI
import com.android.build.gradle.options.BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION
import com.android.build.gradle.options.BooleanOption.ENABLE_NATIVE_COMPILER_SETTINGS_CACHE
import com.android.build.gradle.options.BooleanOption.PREFER_CMAKE_FILE_API
import com.android.build.gradle.options.BooleanOption.ENABLE_PROFILE_JSON
import com.android.build.gradle.options.BooleanOption.ENABLE_V2_NATIVE_MODEL
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.StringOption.IDE_BUILD_TARGET_ABI
import com.android.build.gradle.options.StringOption.PROFILE_OUTPUT_DIR
import com.android.build.gradle.tasks.*
import com.android.build.gradle.tasks.CmakeAndroidNinjaExternalNativeJsonGenerator
import com.android.build.gradle.tasks.CmakeQueryMetadataGenerator
import com.android.build.gradle.tasks.CmakeServerExternalNativeJsonGenerator
import com.android.build.gradle.tasks.NdkBuildExternalNativeJsonGenerator
import com.android.builder.profile.ChromeTracingProfileConverter
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import org.gradle.api.file.FileCollection
import java.io.File
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
 * Generally, it should be data that comes from the DSL or input files. It shouldn't encode
 * information from reading local files (like local.settings or CMakeSettings.json)
 */
data class CxxConfigurationModel(
    val allDefaultAbis: Boolean,
    val buildSystem: NativeBuildSystem,
    val makeFile: File,
    val buildStagingFolder: File?,
    val moduleRootFolder: File,
    val buildDir: File,
    val rootDir: File,
    val buildFile: File,
    val isDebuggable: Boolean,
    val minSdkVersion: AndroidVersion,
    val cmakeVersion: String?,
    val splitsAbiFilterSet: Set<String>,
    val intermediatesFolder: File,
    val gradleModulePathName: String,
    val isNativeCompilerSettingsCacheEnabled: Boolean,
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
    val isV2NativeModelEnabled: Boolean,
    val isPreferCmakeFileApiEnabled: Boolean
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
        moduleRootFolder : File,
        buildStagingDirectory: File?,
        buildFolder: File): File {
    val defaultCxxFolder =
            FileUtils.join(
                    moduleRootFolder,
                    CXX_DEFAULT_CONFIGURATION_SUBFOLDER
            )
    return when {
        buildStagingDirectory == null -> defaultCxxFolder
        FileUtils.isFileInDirectory(buildStagingDirectory, buildFolder) -> {
            warnln("""
            The build staging directory you specified ('${buildStagingDirectory.absolutePath}')
            is a subdirectory of your project's temporary build directory (
            '${buildFolder.absolutePath}'). Files in this directory do not persist through clean
            builds. It is recommended to either use the default build staging directory
            ('$defaultCxxFolder'), or specify a path outside the temporary build directory.
            """.trimIndent())
            buildStagingDirectory
        }
        else -> buildStagingDirectory
    }
}

/**
 * Module-level folder for android_gradle_build.json files
 *   ex, $moduleRootFolder/.cxx
 */
val CxxConfigurationModel.cxxFolder : File get() {
    return findCxxFolder(
            moduleRootFolder,
            buildStagingFolder,
            buildDir)
}

/**
 * Base folder for android_gradle_build.json files
 *   ex, $moduleRootFolder/.cxx/cmake/debug
 */
val CxxConfigurationModel.variantJsonFolder : File get() {
    return FileUtils.join(cxxFolder, buildSystem.tag, variantName)
}

/**
 * Base intermediates folder for all build output files
 *   ex, $moduleRootFolder/build/intermediates/cmake/debug
 */
private val CxxConfigurationModel.variantIntermediatesFolder : File get() {
    return FileUtils.join(
            intermediatesFolder,
            buildSystem.tag,
            variantName)
}

/**
 * Base folder for .o files
 *   ex, $moduleRootFolder/build/intermediates/cmake/debug/obj
 */
val CxxConfigurationModel.variantObjFolder : File get() {
    return if (buildSystem == NativeBuildSystem.NDK_BUILD) {
        // ndkPlatform-build create libraries in a "local" subfolder.
        FileUtils.join(variantIntermediatesFolder, "obj", "local")
    } else {
        FileUtils.join(variantIntermediatesFolder, "obj")
    }
}

/**
 * Base folder for .so files
 *   ex, $moduleRootFolder/build/intermediates/cmake/debug/lib
 */
val CxxConfigurationModel.variantSoFolder: File get() {
    return FileUtils.join(variantIntermediatesFolder, "lib")
}

/**
 * The .cxx build folder
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a
 */
fun CxxConfigurationModel.abiCxxBuildFolder(abi : Abi): File {
    return variantJsonFolder.resolve(abi.tag)
}

/**
 * The .cxx build folder
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a
 */
fun CxxConfigurationModel.abiJsonFile(abi : Abi): File {
    return abiCxxBuildFolder(abi).resolve("android_gradle_build.json")
}

/**
 * This creates the [CxxConfigurationModel]. After deserialization it is used to construct
 * [CxxMetadataGenerator] for tasks to operate on. The resulting [CxxConfigurationModel] is
 * passed to [createCxxMetadataGenerator] to construct the generator.
 * Time phases are:
 * 1) Configuration phase. [tryCreateCxxConfigurationModel] is called and the resulting
 *    [CxxConfigurationModel] is passed to the task's [CreationAction] and should be stored in
 *    the task.
 * 2) Execution phase. The [CxxConfigurationModel] is used to construct a [CxxMetadataGenerator]
 *    which does the task's work.
 * Phase (2) may be run with the freshly created [CxxConfigurationModel] or from a version that
 * was deserialized from the task graph.
 */
fun tryCreateCxxConfigurationModel(
    variant: VariantBuilderImpl,
    componentProperties: VariantImpl,
    allDefaultAbis: Boolean = true
) : CxxConfigurationModel? {
    val global = componentProperties.globalScope

    val (buildSystem, makeFile, buildStagingFolder) =
        getProjectPath(global.extension.externalNativeBuild)
            ?: return null

    val cxxFolder = findCxxFolder(
        global.project.projectDir,
        buildStagingFolder,
        global.project.buildDir
    )

    fun option(option: BooleanOption) = global.projectOptions.get(option)
    fun option(option: StringOption) = global.projectOptions.get(option)

    /**
     * Construct an [NdkHandler] and attempt to auto-download an NDK. If auto-download fails then
     * allow valid [errorln]s to pass or throw exception that will trigger download hyperlinks
     * in Android Studio
     */
    val ndkHandler = global.sdkComponents.get().ndkHandler
    val ndkInstall = CachingEnvironment(cxxFolder).use {
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
            val gradle = global.project.gradle
            val profileDir = option(PROFILE_OUTPUT_DIR)
                ?.let { gradle.rootProject.file(it) }
                ?: gradle.rootProject.buildDir.resolve(PROFILE_DIRECTORY)
            profileDir.resolve(ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY)
        } else {
            null
        }
    val prefabTargets = when (val extension = componentProperties.globalScope.extension) {
            is LibraryExtension -> extension.prefab.map { it.name }.toSet()
            else -> emptySet()
        }

    /**
     * Prefab settings.
     */
    val prefabPackageDirectoryList = if (componentProperties.buildFeatures.prefab) {
            componentProperties.variantDependencies.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
            ).artifactFiles
        } else {
            null
        }

    val prefabClassPath = if (componentProperties.buildFeatures.prefab) {
            getPrefabFromMaven(componentProperties.globalScope)
        } else {
            null
        }

    return CxxConfigurationModel(
        allDefaultAbis = allDefaultAbis,
        buildSystem = buildSystem,
        makeFile = makeFile,
        buildStagingFolder = buildStagingFolder,
        moduleRootFolder = global.project.projectDir,
        buildDir = global.project.buildDir,
        rootDir = global.project.rootDir,
        buildFile = global.project.buildFile,
        isDebuggable = componentProperties.variantDslInfo.isDebuggable,
        minSdkVersion = variant.minSdkVersion.toSharedAndroidVersion(),
        cmakeVersion = global.extension.externalNativeBuild.cmake.version,
        splitsAbiFilterSet = global.extension.splits.abiFilters,
        intermediatesFolder = global.intermediatesDir,
        gradleModulePathName = global.project.path,
        isNativeCompilerSettingsCacheEnabled = option(ENABLE_NATIVE_COMPILER_SETTINGS_CACHE),
        isBuildOnlyTargetAbiEnabled = option(BUILD_ONLY_TARGET_ABI),
        ideBuildTargetAbi = option(IDE_BUILD_TARGET_ABI),
        isCmakeBuildCohabitationEnabled = option(ENABLE_CMAKE_BUILD_COHABITATION),
        chromeTraceJsonFolder = chromeTraceJsonFolder,
        isPrefabEnabled = componentProperties.buildFeatures.prefab,
        prefabClassPath = prefabClassPath,
        prefabPackageDirectoryList = prefabPackageDirectoryList,
        implicitBuildTargetSet = prefabTargets,
        variantName = componentProperties.name,
        nativeVariantConfig = createNativeBuildSystemVariantConfig(
            buildSystem, componentProperties.variantDslInfo
        ),
        isV2NativeModelEnabled = option(ENABLE_V2_NATIVE_MODEL),
        isPreferCmakeFileApiEnabled = option(PREFER_CMAKE_FILE_API)
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
        config.cmake.path?.let { Triple(NativeBuildSystem.CMAKE, it, config.cmake.buildStagingDirectory)},
        config.ndkBuild.path?.let { Triple(NativeBuildSystem.NDK_BUILD, it, config.ndkBuild.buildStagingDirectory) })

    return when {
        externalProjectPaths.size > 1 -> {
            errorln("More than one externalNativeBuild path specified")
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
 * deserialization. Effectively, this limits us to Gradle build services like [CxxMetadataGenerator]
 * and to the information saved in the [CxxConfigurationModel] constructed during configuration
 * phase by [tryCreateCxxConfigurationModel].
 */
fun createCxxMetadataGenerator(
    sdkComponents: SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel,
    analyticsService: AnalyticsService
): CxxMetadataGenerator {
    if(ENABLE_CHECK_CONFIG_TIME_CONSTRUCTION) {
        check(!isGradleConfiguration()) {
            "Should not call createCxxMetadataGenerator(...) at configuration time"
        }
    }
    val module =
        createCxxModuleModel(
            sdkComponents,
            configurationModel
        )
    val variant =
        createCxxVariantModel(
            configurationModel,
            module
        )
    val abiTypes =
        if (configurationModel.allDefaultAbis) Abi.getDefaultValues() else variant.validAbiList
    val abis = abiTypes.map { abi ->
        createCxxAbiModel(
            sdkComponents,
            configurationModel,
            variant,
            abi
        ).rewriteCxxAbiModelWithCMakeSettings()
    }
    val variantBuilder = analyticsService.getVariantBuilder(
        variant.module.gradleModulePathName, variant.variantName)
    return when (module.buildSystem) {
        NativeBuildSystem.NDK_BUILD -> NdkBuildExternalNativeJsonGenerator(
            variant,
            abis,
            variantBuilder
        )
        NativeBuildSystem.CMAKE -> {
            val cmake =
                Objects.requireNonNull(variant.module.cmake)!!
            if (!cmake.isValidCmakeAvailable) {
                errorln("No valid CMake executable was found.")
                return CxxNopMetadataGenerator(variant, abis, variantBuilder)
            }
            val cmakeRevision = cmake.minimumCmakeVersion
            variantBuilder.nativeCmakeVersion = cmakeRevision.toString()
            if (cmakeRevision.isCmakeForkVersion()) {
                return CmakeAndroidNinjaExternalNativeJsonGenerator(variant, abis, variantBuilder)
            }
            if (cmakeRevision.major < 3
                || cmakeRevision.major == 3 && cmakeRevision.minor <= 6
            ) {
                errorln("Unsupported CMake version $cmakeRevision. Try 3.7.0 or later.")
                return CxxNopMetadataGenerator(variant, abis, variantBuilder)
            }

            val isPreCmakeFileApiVersion = cmakeRevision.major == 3 && cmakeRevision.minor < 15
            if (isPreCmakeFileApiVersion ||
                !configurationModel.isV2NativeModelEnabled ||
                !configurationModel.isPreferCmakeFileApiEnabled) {
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
