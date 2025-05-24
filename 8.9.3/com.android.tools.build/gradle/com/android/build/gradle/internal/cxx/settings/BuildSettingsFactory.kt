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

// Null checks are required here because Gson deserialization may return null
@file:Suppress("UselessCallOnCollection", "UselessCallOnNotNull")

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_SETTINGS_GENERIC
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_SETTINGS_JSON_EMPTY
import com.google.gson.Gson
import java.io.File

/**
 * Given a json string construct a [BuildSettingsConfiguration].
 */
fun createBuildSettingsFromJson(json: String): BuildSettingsConfiguration {
    return try {
        val settings = Gson().fromJson(json, BuildSettingsConfiguration::class.java)

        // Null checks are required here because Gson deserialization may return null
        if (settings != null) {
            BuildSettingsConfiguration(environmentVariables = settings.environmentVariables
                ?.filterNotNull()
                ?.filter { !it.name.isNullOrBlank() }
                ?: emptyList()
            )
        } else {
            errorln(BUILD_SETTINGS_JSON_EMPTY, "Json is empty")
            BuildSettingsConfiguration()
        }

    } catch (e: Throwable) {
        errorln(BUILD_SETTINGS_GENERIC, e.message ?: e.cause?.message ?: e.javaClass.name)
        BuildSettingsConfiguration()
    }
}

/**
 * Given a file with json construct [BuildSettingsConfiguration].
 */
fun createBuildSettingsFromFile(jsonFile: File): BuildSettingsConfiguration {
    return if (jsonFile.exists()) {
        PassThroughPrefixingLoggingEnvironment(file = jsonFile).use {
            return createBuildSettingsFromJson(jsonFile.readText())
        }
    } else {
        BuildSettingsConfiguration()
    }
}

/**
 * Converts [BuildSettingsConfiguration] into a name:value Map.
 * Omits environment variables with no name provided.
 */
fun BuildSettingsConfiguration.getEnvironmentVariableMap(): Map<String, String> {
    return environmentVariables.associateBy({ it.name }, { it.value ?: "" })
}
