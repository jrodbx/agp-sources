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
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CXX_LOCAL_PROPERTIES_CACHE_DIR
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters

import com.android.utils.FileUtils.join
import java.io.File

/**
 * Create a [CxxProjectModel] to hold project-wide settings and flags.
 *
 * Note, the data in this class is project-wide but there is still one instance per module.
 * For this reason, [CxxProjectModel] is not suitable to hold services that are meant to
 * be strictly per-project.
 */
fun createCxxProjectModel(
    sdkComponents: SdkComponentsBuildService,
    configurationParameters: CxxConfigurationParameters
) : CxxProjectModel {
    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(configurationParameters.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    return CxxProjectModel(
        rootBuildGradleFolder = configurationParameters.rootDir,
        cxxFolder = join(configurationParameters.rootDir, ".cxx"),
        sdkFolder = sdkComponents.sdkDirectoryProvider.get().asFile,
        isNativeCompilerSettingsCacheEnabled = configurationParameters.isNativeCompilerSettingsCacheEnabled,
        isBuildOnlyTargetAbiEnabled = configurationParameters.isBuildOnlyTargetAbiEnabled,
        ideBuildTargetAbi = configurationParameters.ideBuildTargetAbi,
        isCmakeBuildCohabitationEnabled = configurationParameters.isCmakeBuildCohabitationEnabled,
        compilerSettingsCacheFolder = localPropertyFile(CXX_LOCAL_PROPERTIES_CACHE_DIR) ?:
            join(configurationParameters.rootDir, CXX_DEFAULT_CONFIGURATION_SUBFOLDER),
        chromeTraceJsonFolder = configurationParameters.chromeTraceJsonFolder,
        isPrefabEnabled = configurationParameters.isPrefabEnabled,
        isV2NativeModelEnabled = configurationParameters.isV2NativeModelEnabled
    )
}
