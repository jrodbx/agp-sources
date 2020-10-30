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

import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import java.io.File

/**
 * Rewrite [CxxAbiModel] without invoking any member properties.
 * Replaces select field values with callbacks to produce a new value that is still lazily
 * evaluated.
 */
fun CxxAbiModel.replaceWith(
    cmake: () -> CxxCmakeAbiModel?,
    variant: () -> CxxVariantModel,
    cxxBuildFolder: () -> File,
    buildSettings: () -> BuildSettingsConfiguration
) : CxxAbiModel {
    val original = this
    return object : CxxAbiModel by original {
        override val variant get() = variant()
        override val cmake get() = cmake()
        override val cxxBuildFolder get() = cxxBuildFolder()
        override val buildSettings get() = buildSettings()
    }
}

/**
 * Rewrite [CxxCmakeAbiModel] without invoking any member properties.
 * Replaces select field values with callbacks to produce a new value that is still lazily
 * evaluated.
 */
fun CxxCmakeAbiModel.replaceWith(
    cmakeArtifactsBaseFolder : () -> File,
    effectiveConfiguration : () -> CMakeSettingsConfiguration
) : CxxCmakeAbiModel {
    val original = this
    return object : CxxCmakeAbiModel by original {
        override val cmakeArtifactsBaseFolder get() = cmakeArtifactsBaseFolder()
        override val effectiveConfiguration get() = effectiveConfiguration()
    }
}

/**
 * Rewrite [CxxModuleModel] without invoking any member properties.
 * Replaces select field values with callbacks to produce a new value that is still lazily
 * evaluated.
 */
fun CxxModuleModel.replaceWith(
    cmake : () -> CxxCmakeModuleModel?,
    cmakeToolchainFile : () -> File
) : CxxModuleModel {
    val original = this
    return object : CxxModuleModel by original {
        override val cmake get() = cmake()
        override val cmakeToolchainFile get() = cmakeToolchainFile()
    }
}

/**
 * Rewrite [CxxCmakeModuleModel] without invoking any member properties.
 * Replaces select field values with callbacks to produce a new value that is still lazily
 * evaluated.
 */
fun CxxCmakeModuleModel.replaceWith(
    cmakeExe : () -> File
) : CxxCmakeModuleModel {
    val original = this
    return object : CxxCmakeModuleModel by original {
        override val cmakeExe get() = cmakeExe()
    }
}

/**
 * Rewrite [CxxVariantModel] without invoking any member properties.
 * Replaces select field values with callbacks to produce a new value that is still lazily
 * evaluated.
 */
fun CxxVariantModel.replaceWith(
    module : () -> CxxModuleModel
) : CxxVariantModel {
    val original = this
    return object : CxxVariantModel by original {
        override val module get() = module()
    }
}