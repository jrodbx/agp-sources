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

import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CXX_LOCAL_PROPERTIES_CACHE_DIR
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.profile.ProfilerInitializer
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.getPrefabFromMaven
import com.android.builder.profile.ChromeTracingProfileConverter
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Create a [CxxProjectModel] to hold project-wide settings and flags.
 *
 * Note, the data in this class is project-wide but there is still one instance per module.
 * For this reason, [CxxProjectModel] is not suitable to hold services that are meant to
 * be strictly per-project.
 */
fun createCxxProjectModel(global : GlobalScope) : CxxProjectModel {
    fun option(option: BooleanOption) = global.projectOptions.get(option)
    fun option(option: StringOption) = global.projectOptions.get(option)
    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(global.project.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    return object : CxxProjectModel {
        override val rootBuildGradleFolder
            get() = global.project.rootDir
        override val sdkFolder by lazy {
            global.sdkComponents.getSdkDirectory()
        }
        override val isNativeCompilerSettingsCacheEnabled by lazy {
            option(BooleanOption.ENABLE_NATIVE_COMPILER_SETTINGS_CACHE)
        }
        override val isBuildOnlyTargetAbiEnabled by lazy {
            option(BooleanOption.BUILD_ONLY_TARGET_ABI)
        }
        override val ideBuildTargetAbi by lazy {
            option(StringOption.IDE_BUILD_TARGET_ABI)
        }
        override val isCmakeBuildCohabitationEnabled by lazy {
            option(BooleanOption.ENABLE_CMAKE_BUILD_COHABITATION)
        }
        override val compilerSettingsCacheFolder by lazy {
            localPropertyFile(CXX_LOCAL_PROPERTIES_CACHE_DIR) ?:
            join(global.project.rootDir, CXX_DEFAULT_CONFIGURATION_SUBFOLDER)
        }
        override val chromeTraceJsonFolder: File?
            get() {
                if (!option(BooleanOption.ENABLE_PROFILE_JSON)) return null
                val gradle = global.project.gradle
                val profileDir: File = option(StringOption.PROFILE_OUTPUT_DIR)
                    ?.let { gradle.rootProject.file(it) }
                    ?: gradle.rootProject.buildDir.resolve(ProfilerInitializer.PROFILE_DIRECTORY)
                return profileDir.resolve(ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY)
            }

        override val isPrefabEnabled: Boolean = global.projectOptions.get(BooleanOption.ENABLE_PREFAB)
        override val prefabClassPath: File? by lazy {
            if (isPrefabEnabled) {
                getPrefabFromMaven(global)
            } else {
                null
            }
        }
    }
}
