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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_SETTINGS_PARSE_ERROR
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.io.StringReader
import java.io.StringWriter

/**
 * Given a json string construct a [Settings].
 */
fun createSettingsFromJsonString(
    json: String,
    lenient: Boolean = true // Should be true for production to allow //-style comments
) : Settings {
    val reader = JsonReader(StringReader(json))
    reader.isLenient = lenient
    val settings =  try {
        Gson()
            .getAdapter(object : TypeToken<Settings>() {})
            .read(reader)
    } catch (e: Throwable) {
        // Parse errors are "recoverable" by issuing an error and returning an empty result
        errorln(BUILD_SETTINGS_PARSE_ERROR, e.message ?: e.cause?.message ?: e.javaClass.name)
        Settings()
    }
    return Settings(
        // [filterNotNull] needed to remove nulls introduced by Gson when there is a trailing comma
        environments = settings.environments.filterNotNull(),
        configurations = settings.configurations.filterNotNull().map { configuration ->
            configuration.copy(
                variables = configuration.variables.filterNotNull()
            )
        }
    )
}

/**
 * Given a file with json construct [Settings].
 */
fun createSettingsFromJsonFile(
    json: File,
    providers: ProviderFactory,
    layout: ProjectLayout
) : Settings {
    PassThroughPrefixingLoggingEnvironment(file = json).use {
        val jsonContent = providers.fileContents(
            layout.file(providers.provider { json })
        ).asText.get()
        return createSettingsFromJsonString(jsonContent)
    }
}


/**
 * Write the [Settings] to Json string.
 */
fun Settings.toJsonString(): String {
    return StringWriter()
        .also { writer -> GsonBuilder()
            .setPrettyPrinting()
            .create().toJson(this, writer) }
        .toString()
}

/**
 * Write the [SettingsConfiguration] to Json string.
 */
fun SettingsConfiguration.toJsonString(): String {
    return StringWriter()
        .also { writer -> GsonBuilder()
            .setPrettyPrinting()
            .create().toJson(this, writer) }
        .toString()
}

