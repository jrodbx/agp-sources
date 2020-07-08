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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.cxx.services.CxxServiceRegistry
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.repository.Revision
import com.google.common.annotations.VisibleForTesting
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.StringWriter

/**
 * Write the [CxxAbiModel] to Json string.
 */
fun CxxAbiModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(toData(), writer) }
        .toString()
}

/**
 * Write the [CxxCmakeAbiModel] to Json string.
 */
fun CxxCmakeAbiModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(toData(), writer) }
        .toString()
}

/**
 * Write the [CxxVariantModel] to Json string.
 */
fun CxxVariantModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(toData(), writer) }
        .toString()
}

/**
 * Write the [CxxModuleModel] to Json string.
 */
fun CxxModuleModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(toData(), writer) }
        .toString()
}

/**
 * Create a [CxxModuleModel] from Json string.
 */
fun createCxxModuleModelFromJson(json: String): CxxModuleModel {
    return GSON.fromJson(json, CxxModuleModelData::class.java)
}

/**
 * Create a [CxxAbiModel] from Json string.
 */
fun createCxxAbiModelFromJson(json: String): CxxAbiModel {
    return GSON.fromJson(json, CxxAbiModelData::class.java)
}

/**
 * Write model to JSON file.
 */
fun CxxAbiModel.writeJsonToFile() {
    modelOutputFile.parentFile.mkdirs()
    modelOutputFile.writeText(toJsonString())
}

private val GSON = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .registerTypeAdapter(Revision::class.java, RevisionTypeAdapter())
    .setPrettyPrinting()
    .create()

/**
 * [TypeAdapter] that converts between [Revision] and Json string.
 */
private class RevisionTypeAdapter : TypeAdapter<Revision>() {

    override fun write(writer: JsonWriter, revision: Revision) {
        writer.value(revision.toString())
    }

    override fun read(reader: JsonReader): Revision {
        return Revision.parseRevision(reader.nextString())
    }
}

/**
 * Private data-backed implementation of [CxxProjectModel] that Gson can
 * use to read and write.
 */
@VisibleForTesting
data class CxxProjectModelData(
    override val compilerSettingsCacheFolder: File = File("."),
    override val cxxFolder: File = File("."),
    override val ideBuildTargetAbi: String? = null,
    override val isBuildOnlyTargetAbiEnabled: Boolean = false,
    override val isCmakeBuildCohabitationEnabled: Boolean = false,
    override val isNativeCompilerSettingsCacheEnabled: Boolean = false,
    override val rootBuildGradleFolder: File = File("."),
    override val sdkFolder: File = File("."),
    override val chromeTraceJsonFolder: File? = null,
    override val isPrefabEnabled: Boolean = false,
    override val prefabClassPath: File? = null
) : CxxProjectModel

private fun CxxProjectModel.toData() = CxxProjectModelData(
    compilerSettingsCacheFolder = compilerSettingsCacheFolder,
    cxxFolder = cxxFolder,
    ideBuildTargetAbi = ideBuildTargetAbi,
    isBuildOnlyTargetAbiEnabled = isBuildOnlyTargetAbiEnabled,
    isCmakeBuildCohabitationEnabled = isCmakeBuildCohabitationEnabled,
    isNativeCompilerSettingsCacheEnabled = isNativeCompilerSettingsCacheEnabled,
    rootBuildGradleFolder = rootBuildGradleFolder,
    sdkFolder = sdkFolder,
    isPrefabEnabled = isPrefabEnabled,
    prefabClassPath = prefabClassPath
)

/**
 * Private data-backed implementation of [CxxModuleModel] that Gson can
 * use to read and write.
 */
// TODO Can the Cxx*Data classes be automated or otherwise removed while still
// TODO retaining JSON read/write? They're a pain to maintain.
@VisibleForTesting
data class CxxModuleModelData(
    override val buildStagingFolder: File? = null,
    override val buildSystem: NativeBuildSystem = NativeBuildSystem.CMAKE,
    override val cmake: CxxCmakeModuleModelData? = null,
    override val cmakeToolchainFile: File = File("."),
    override val cxxFolder: File = File("."),
    override val gradleModulePathName: String = "",
    override val intermediatesFolder: File = File("."),
    override val makeFile: File = File("."),
    override val moduleBuildFile: File = File("."),
    override val moduleRootFolder: File = File("."),
    override val ndkDefaultAbiList: List<Abi> = listOf(),
    override val ndkFolder: File = File("."),
    override val ndkMetaAbiList: List<AbiInfo> = listOf(),
    override val ndkMetaPlatforms: NdkMetaPlatforms? = NdkMetaPlatforms(),
    override val ndkSupportedAbiList: List<Abi> = listOf(),
    override val ndkDefaultStl: Stl = Stl.NONE,
    override val ndkVersion: Revision = Revision.parseRevision("0.0.0"),
    override val project: CxxProjectModelData = CxxProjectModelData(),
    override val splitsAbiFilterSet: Set<String> = setOf(),
    override val stlSharedObjectMap: Map<Stl, Map<Abi, File>> = emptyMap()
) : CxxModuleModel {
    override val services: CxxServiceRegistry
        get() = throw RuntimeException("Cannot use services from deserialized CxxModuleModel")
}

private fun CxxModuleModel.toData() = CxxModuleModelData(
    buildStagingFolder = buildStagingFolder,
    buildSystem = buildSystem,
    cmake = cmake?.toData(),
    cmakeToolchainFile = cmakeToolchainFile,
    cxxFolder = cxxFolder,
    gradleModulePathName = gradleModulePathName,
    intermediatesFolder = intermediatesFolder,
    makeFile = makeFile,
    moduleBuildFile = moduleBuildFile,
    moduleRootFolder = moduleRootFolder,
    ndkDefaultAbiList = ndkDefaultAbiList,
    ndkFolder = ndkFolder,
    ndkMetaAbiList = ndkMetaAbiList,
    ndkMetaPlatforms = ndkMetaPlatforms,
    ndkSupportedAbiList = ndkSupportedAbiList,
    ndkDefaultStl = ndkDefaultStl,
    ndkVersion = ndkVersion,
    project = project.toData(),
    splitsAbiFilterSet = splitsAbiFilterSet,
    stlSharedObjectMap = stlSharedObjectMap
)

@VisibleForTesting
data class CxxCmakeModuleModelData(
    override val cmakeExe: File,
    override val minimumCmakeVersion: Revision,
    override val ninjaExe: File?
) : CxxCmakeModuleModel

private fun CxxCmakeModuleModel.toData() =
    CxxCmakeModuleModelData(
        cmakeExe = cmakeExe,
        minimumCmakeVersion = minimumCmakeVersion,
        ninjaExe = ninjaExe
    )

/**
 * Private data-backed implementation of [CxxVariantModel] that Gson can
 * use to read and write.
 */
@VisibleForTesting
internal data class CxxVariantModelData(
    override val buildSystemArgumentList: List<String> = listOf(),
    override val buildTargetSet: Set<String> = setOf(),
    override val cFlagsList: List<String> = listOf(),
    override val cmakeSettingsConfiguration: String = "",
    override val cppFlagsList: List<String> = listOf(),
    override val isDebuggableEnabled: Boolean = false,
    override val module: CxxModuleModelData = CxxModuleModelData(),
    override val objFolder: File = File("."),
    override val variantName: String = "",
    override val validAbiList: List<Abi> = listOf(),
    override val prefabDirectory: File = File("."),
    override val prefabPackageDirectoryList: List<File> = listOf()
) : CxxVariantModel

private fun CxxVariantModel.toData() =
    CxxVariantModelData(
        buildSystemArgumentList = buildSystemArgumentList,
        buildTargetSet = buildTargetSet,
        cFlagsList = cFlagsList,
        cmakeSettingsConfiguration = cmakeSettingsConfiguration,
        cppFlagsList = cppFlagsList,
        isDebuggableEnabled = isDebuggableEnabled,
        module = module.toData(),
        objFolder = objFolder,
        validAbiList = validAbiList,
        variantName = variantName,
        prefabDirectory = prefabDirectory,
        prefabPackageDirectoryList = prefabPackageDirectoryList
    )

/**
 * Private data-backed implementation of [CxxAbiModel] that Gson can use
 * to read and write.
 */
@VisibleForTesting
internal data class CxxAbiModelData(
    override val abi: Abi = Abi.X86,
    override val abiPlatformVersion: Int = 0,
    override val buildSettings: BuildSettingsConfiguration = BuildSettingsConfiguration(),
    override val cmake: CxxCmakeAbiModelData? = null,
    override val cxxBuildFolder: File = File("."),
    override val info: AbiInfo = AbiInfo(),
    override val originalCxxBuildFolder: File = File("."),
    override val variant: CxxVariantModelData = CxxVariantModelData(),
    override val prefabFolder: File = File(".")
) : CxxAbiModel {
    override val services: CxxServiceRegistry
        get() = throw RuntimeException("Cannot use services from deserialized CxxAbiModel")
}

private fun CxxAbiModel.toData(): CxxAbiModel = CxxAbiModelData(
    abi = abi,
    abiPlatformVersion = abiPlatformVersion,
    buildSettings = buildSettings,
    cmake = cmake?.toData(),
    cxxBuildFolder = cxxBuildFolder,
    info = info,
    originalCxxBuildFolder = originalCxxBuildFolder,
    variant = variant.toData(),
    prefabFolder = prefabFolder
)

/**
 * Private data-backed implementation of [CxxCmakeAbiModel] that Gson can use
 * to read and write.
 */
@VisibleForTesting
internal data class CxxCmakeAbiModelData(
    override val cmakeArtifactsBaseFolder: File,
    override val cmakeServerLogFile: File,
    override val cmakeWrappingBaseFolder: File,
    override val effectiveConfiguration: CMakeSettingsConfiguration

) : CxxCmakeAbiModel

private fun CxxCmakeAbiModel.toData() = CxxCmakeAbiModelData(
    cmakeArtifactsBaseFolder = cmakeArtifactsBaseFolder,
    cmakeServerLogFile = cmakeServerLogFile,
    cmakeWrappingBaseFolder = cmakeWrappingBaseFolder,
    effectiveConfiguration = effectiveConfiguration
)

/**
 * Prefab configuration state to be persisted to disk.
 *
 * Prefab configuration state needs to be persisted to disk because changes in configuration
 * require model regeneration.
 */
data class PrefabConfigurationState(
    val enabled: Boolean,
    val prefabPath: File?,
    val packages: List<File>
) {
    fun toJsonString(): String {
        return StringWriter()
            .also { writer -> GSON.toJson(this, writer) }
            .toString()
    }

    companion object {
        @JvmStatic
        fun fromJson(json: String): PrefabConfigurationState {
            return GSON.fromJson(json, PrefabConfigurationState::class.java)
        }
    }
}