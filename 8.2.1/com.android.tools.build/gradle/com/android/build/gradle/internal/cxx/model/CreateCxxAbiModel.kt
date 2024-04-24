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

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.hashing.toBase36
import com.android.build.gradle.internal.cxx.hashing.update
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.settings.SettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.createBuildSettingsFromFile
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxDiagnosticCode.ABI_IS_UNSUPPORTED
import java.io.File
import java.security.MessageDigest

/**
 * Construct a [CxxAbiModel].
 */
fun createCxxAbiModel(
    sdkComponents: SdkComponentsBuildService,
    configurationParameters: CxxConfigurationParameters,
    variant: CxxVariantModel,
    abiName: String
) : CxxAbiModel {
    // True configuration hash values need to be computed after macros are resolved.
    // This is a placeholder hash that is used until the real hash is known.
    // It's also good for example values.
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(configurationParameters.variantName)
    val configurationHash = digest.toBase36()
    if (!variant.module.ndkMetaAbiList.map { it.name }.contains(abiName)) {
        errorln(ABI_IS_UNSUPPORTED, "ABI $abiName was not recognized. Valid ABIs are: " +
                "${variant.module.ndkMetaAbiList.sortedBy { it.name }.joinToString { it.name }}.")
        error("Unsupported ABI $abiName")
    }
    val info = variant.module.ndkMetaAbiList.single { it.name == abiName }
    with(variant) {
        val variantSoFolder = join(
            module.intermediatesBaseFolder,
            module.buildSystemTag,
            variantName,
            module.intermediatesParentDirSuffix
        )
        val variantCxxBuildFolder = join(
            module.cxxFolder,
            module.buildSystemTag,
            variantName
        )
        val variantIntermediatesFolder = join(
            module.intermediatesFolder,
            module.buildSystemTag,
            variantName
        )
        return CxxAbiModel(
                variant = this,
                info = info,
                cxxBuildFolder = join(variantCxxBuildFolder, info.name),
                soFolder = join(variantSoFolder, info.name),
                soRepublishFolder = join(variantSoFolder, info.name),
                abiPlatformVersion =
                    sdkComponents
                            .versionedNdkHandler(
                                ndkVersion = configurationParameters.ndkVersion,
                                ndkPath = configurationParameters.ndkPath
                            )
                            .ndkPlatform
                            .getOrThrow()
                            .ndkInfo
                            .findSuitablePlatformVersion(info.name,
                                configurationParameters.minSdkVersion),
                cmake = when(module.buildSystem) {
                    NativeBuildSystem.CMAKE ->  CxxCmakeAbiModel(
                        buildCommandArgs = null,
                        effectiveConfiguration = SettingsConfiguration())
                    else -> null
                },
                buildSettings = createBuildSettingsFromFile(module.buildSettingsFile),
                fullConfigurationHash = configurationHash,
                fullConfigurationHashKey = "",
                configurationArguments = listOf(),
                isActiveAbi = validAbiList.contains(info.name),
                prefabFolder = join(variantCxxBuildFolder, "prefab", info.name),
                stlLibraryFile =
                    Stl.fromArgumentName(variant.stlType)
                        ?.let { module.stlSharedObjectMap[it]?.get(info.name)?.toString() }
                        ?.let { File(it) },
                intermediatesParentFolder = variantIntermediatesFolder
        )
    }
}
