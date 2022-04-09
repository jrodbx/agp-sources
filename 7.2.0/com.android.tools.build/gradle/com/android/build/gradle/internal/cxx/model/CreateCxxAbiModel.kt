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
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.hashing.toBase36
import com.android.build.gradle.internal.cxx.hashing.update
import com.android.build.gradle.internal.cxx.settings.SettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.createBuildSettingsFromFile
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import java.io.File
import java.security.MessageDigest

/**
 * Construct a [CxxAbiModel].
 */
fun createCxxAbiModel(
    sdkComponents: SdkComponentsBuildService,
    configurationParameters: CxxConfigurationParameters,
    variant: CxxVariantModel,
    abi: Abi
) : CxxAbiModel {
    // True configuration hash values need to be computed after macros are resolved.
    // This is a placeholder hash that is used until the real hash is known.
    // It's also good for example values.
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(configurationParameters.variantName)
    val configurationHash = digest.toBase36()
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
                abi = abi,
                info = module.ndkMetaAbiList.single { it.abi == abi },
                cxxBuildFolder = join(variantCxxBuildFolder, abi.tag),
                soFolder = join(variantSoFolder, abi.tag),
                soRepublishFolder = join(variantSoFolder, abi.tag),
                abiPlatformVersion =
                    sdkComponents
                            .versionedNdkHandler(
                                compileSdkVersion = configurationParameters.compileSdkVersion,
                                ndkVersion = configurationParameters.ndkVersion,
                                ndkPath = configurationParameters.ndkPath
                            )
                            .ndkPlatform
                            .getOrThrow()
                            .ndkInfo
                            .findSuitablePlatformVersion(abi.tag,
                                configurationParameters.minSdkVersion),
                cmake = when(module.buildSystem) {
                    NativeBuildSystem.CMAKE ->  CxxCmakeAbiModel(
                        buildCommandArgs = null,
                        effectiveConfiguration = SettingsConfiguration())
                    else -> null
                },
                buildSettings = createBuildSettingsFromFile(module.buildSettingsFile),
                fullConfigurationHash = configurationHash,
                configurationArguments = listOf(),
                isActiveAbi = validAbiList.contains(abi),
                prefabFolder = join(variantCxxBuildFolder, "prefab", abi.tag),
                stlLibraryFile =
                    Stl.fromArgumentName(variant.stlType)
                        ?.let { module.stlSharedObjectMap.getValue(it)[abi]?.toString() }
                        ?.let { File(it) },
                intermediatesParentFolder = variantIntermediatesFolder
        )
    }
}

