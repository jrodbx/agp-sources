/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.api

import com.android.utils.appendCapitalized
import org.gradle.api.tasks.SourceSet

data class AndroidSourceSetName(
    val name: String
) {

    companion object {
        const val CONFIG_NAME_COMPILE_ONLY = "compileOnly"
        const val CONFIG_NAME_COMPILE_ONLY_API = "compileOnlyApi"

        private const val CONFIG_NAME_COMPILE = "compile"
        private const val CONFIG_NAME_PUBLISH = "publish"
        private const val CONFIG_NAME_APK = "apk"
        private const val CONFIG_NAME_PROVIDED = "provided"
        private const val CONFIG_NAME_WEAR_APP = "wearApp"
        private const val CONFIG_NAME_ANNOTATION_PROCESSOR = "annotationProcessor"
        private const val CONFIG_NAME_API = "api"
        private const val CONFIG_NAME_IMPLEMENTATION = "implementation"
        private const val CONFIG_NAME_RUNTIME_ONLY = "runtimeOnly"
    }

    private fun getName(config: String): String {
        return if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
            config
        } else {
            name.appendCapitalized(config)
        }
    }

    val apiConfigurationName: String
        get() = getName(CONFIG_NAME_API)

    val compileOnlyConfigurationName: String
        get() = getName(CONFIG_NAME_COMPILE_ONLY)

    val compileOnlyApiConfigurationName: String
        get() = getName(CONFIG_NAME_COMPILE_ONLY_API)

    val implementationConfigurationName: String
        get() = getName(CONFIG_NAME_IMPLEMENTATION)

    val runtimeOnlyConfigurationName: String
        get() = getName(CONFIG_NAME_RUNTIME_ONLY)

    val compileConfigurationName: String
        get() = getName(CONFIG_NAME_COMPILE)

    val providedConfigurationName: String
        get() = getName(CONFIG_NAME_PROVIDED)

    val wearAppConfigurationName: String
        get() = getName(CONFIG_NAME_WEAR_APP)

    val annotationProcessorConfigurationName: String
        get() = getName(CONFIG_NAME_ANNOTATION_PROCESSOR)

    val publishedPackageConfigurationName: String
        get() = getName(CONFIG_NAME_PUBLISH)

    val packageConfigurationName: String
        get() = getName(CONFIG_NAME_APK)

}
