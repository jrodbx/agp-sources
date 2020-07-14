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

import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.AbiConfigurationKey
import com.android.build.gradle.internal.cxx.configure.AbiConfigurator
import com.android.build.gradle.internal.cxx.configure.createNativeBuildSystemVariantConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Construct a [CxxVariantModel], careful to be lazy with module-level fields.
 */
fun createCxxVariantModel(
    module: CxxModuleModel,
    variantScope: VariantScope) : CxxVariantModel {
    val baseVariantData = variantScope.variantData
    return object : CxxVariantModel {
        private val buildSystem by lazy {
            createNativeBuildSystemVariantConfig(
                module.buildSystem,
                baseVariantData.variantDslInfo
            )
        }
        private val intermediatesFolder by lazy {
            join(module.intermediatesFolder, module.buildSystem.tag, variantName)
        }
        override val buildTargetSet get() = buildSystem.targets
        override val module = module
        override val buildSystemArgumentList get() = buildSystem.arguments
        override val cFlagsList get() = buildSystem.cFlags
        override val cppFlagsList get() = buildSystem.cppFlags
        override val variantName get() = baseVariantData.name
        override val cmakeSettingsConfiguration
            // TODO remove this after configuration has been added to DSL
            // If CMakeSettings.json has a configuration with this exact name then
            // it will be used. The point is to delay adding 'configuration' to the
            // DSL.
            get() = "android-gradle-plugin-predetermined-name"
        override val objFolder get() =
            if (module.buildSystem == NativeBuildSystem.NDK_BUILD) {
                // ndkPlatform-build create libraries in a "local" subfolder.
                join(intermediatesFolder, "obj", "local")
            } else {
                join(intermediatesFolder, "obj")
            }
        override val isDebuggableEnabled
            get() = baseVariantData.variantDslInfo.isDebuggable
        override val validAbiList by lazy {
            CachingEnvironment(module.cxxFolder).use {
                AbiConfigurator(
                    AbiConfigurationKey(
                        module.ndkSupportedAbiList,
                        module.ndkDefaultAbiList,
                        buildSystem.externalNativeBuildAbiFilters,
                        buildSystem.ndkAbiFilters,
                        module.splitsAbiFilterSet,
                        module.project.isBuildOnlyTargetAbiEnabled,
                        module.project.ideBuildTargetAbi
                    )
                ).validAbis.toList()
            }
        }

        override val prefabPackageDirectoryList: List<File> by lazy {
            variantScope.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.PREFAB_PACKAGE
            ).artifactFiles.toList()
        }

        override val prefabDirectory: File = jsonFolder.resolve("prefab")
    }
}

/**
 * Base folder for android_gradle_build.json files
 *   ex, $moduleRootFolder/.cxx/cmake/debug
 */
val CxxVariantModel.jsonFolder
        get() = join(module.cxxFolder, module.buildSystem.tag, variantName)

/**
 * The gradle build output folder
 *   ex, '$moduleRootFolder/.cxx/cxx/debug'
 */
val CxxVariantModel.gradleBuildOutputFolder
        get() = join(module.cxxFolder, "cxx", variantName)

