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
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.abiCxxBuildFolder
import com.android.build.gradle.internal.cxx.gradle.generator.variantJsonFolder
import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.createBuildSettingsFromFile
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Construct a [CxxAbiModel], careful to be lazy with module level fields.
 */
fun createCxxAbiModel(
    sdkComponents: SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel,
    variant: CxxVariantModel,
    abi: Abi
) : CxxAbiModel {
    return object : CxxAbiModel {
        override val variant = variant
        override val abi = abi
        override val info by lazy {
            variant.module.ndkMetaAbiList.single { it.abi == abi }
        }
        override val originalCxxBuildFolder by lazy {
            configurationModel.abiCxxBuildFolder(abi)
        }
        override val cxxBuildFolder by lazy {
            configurationModel.abiCxxBuildFolder(abi)
        }
        override val abiPlatformVersion by lazy {
            val minSdkVersion = configurationModel.minSdkVersion
            sdkComponents
                .ndkHandler
                .ndkPlatform
                .getOrThrow()
                .ndkInfo
                .findSuitablePlatformVersion(abi.tag, minSdkVersion)
        }
        override val cmake by lazy {
            if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                object : CxxCmakeAbiModel {
                    override val cmakeServerLogFile by lazy {
                        join(cmakeArtifactsBaseFolder, "cmake_server_log.txt")
                    }
                    override val effectiveConfiguration by lazy { CMakeSettingsConfiguration() }
                    override val cmakeWrappingBaseFolder by lazy {
                       join(variant.gradleBuildOutputFolder, abi.tag)
                    }
                    override val cmakeArtifactsBaseFolder by lazy {
                        join(configurationModel.variantJsonFolder, abi.tag)
                    }
                }
            } else {
                null
            }
        }

        override val buildSettings by lazy {
            createBuildSettingsFromFile(variant.module.buildSettingsFile)
        }
        override val prefabFolder: File = variant.prefabDirectory.resolve(abi.tag)
    }
}
