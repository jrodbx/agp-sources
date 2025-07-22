/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage

/**
 * Scope object that contains all the configurations.
 */
class PluginConfigurations {

    private val configurations = mutableListOf<Configuration>()

    fun add(configuration: Configuration): Boolean {
        return configurations.add(configuration)
    }

    fun addAll(configurations: Collection<Configuration>) {
        configurations.forEach(this::add)
    }

    fun getByConfigType(configType: AndroidArtifacts.ConsumedConfigType): Configuration {
        return configurations.find { configuration ->
            configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name == configType.toUsage()
        } ?: throw IllegalArgumentException(
            "No configuration found with config ${configType.name} (usage: ${configType.toUsage()})"
        )
    }

    private fun AndroidArtifacts.ConsumedConfigType.toUsage(): String {
        return when(this) {
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH -> Usage.JAVA_API
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH -> Usage.JAVA_RUNTIME
            else -> error("${getName()} cannot be converted to Usage.")
        }
    }
}
