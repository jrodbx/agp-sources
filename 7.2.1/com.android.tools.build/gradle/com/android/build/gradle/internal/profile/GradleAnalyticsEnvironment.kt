/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.profile

import com.android.tools.analytics.Environment
import org.gradle.api.provider.ProviderFactory

/** Gradle-specific implementation of the [Environment] for the analytics library. */
class GradleAnalyticsEnvironment(providerFactory: ProviderFactory) : Environment() {

    private val systemProperties = SystemProperty.values().associate {
        it to providerFactory.systemProperty(it.key).forUseAtConfigurationTime().orNull
    }
    private val envVariables = EnvironmentVariable.values().associate {
        it to providerFactory.environmentVariable(it.key).forUseAtConfigurationTime().orNull
    }

    override fun getVariable(name: EnvironmentVariable): String? {
        return envVariables[name]
    }

    override fun getSystemProperty(name: SystemProperty): String? {
        return systemProperties[name]
    }
}

// TODO(b/205741140): Aggregate with GradleAnalyticsEnvironment
/**
 * Gradle-specific implementation of the [com.android.utils.Environment] for computer
 * architecture utils.
 */
class GradleSystemEnvironment(providerFactory: ProviderFactory) : com.android.utils.Environment() {

    private val systemProperties = SystemProperty.values().associate {
        it to providerFactory.systemProperty(it.key).forUseAtConfigurationTime().orNull
    }
    private val envVariables = EnvironmentVariable.values().associate {
        it to providerFactory.environmentVariable(it.key).forUseAtConfigurationTime().orNull
    }

    override fun getVariable(name: EnvironmentVariable): String? {
        return envVariables[name]
    }

    override fun getSystemProperty(name: SystemProperty): String? {
        return systemProperties[name]
    }
}
