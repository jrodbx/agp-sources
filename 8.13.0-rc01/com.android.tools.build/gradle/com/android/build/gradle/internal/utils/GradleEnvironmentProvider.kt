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

package com.android.build.gradle.internal.utils

import com.android.utils.EnvironmentProvider
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/** Gradle-specific APIs for accessing system properties and environment variables. */
interface GradleEnvironmentProvider {
    fun getSystemProperty(key: String): Provider<String>
    fun getEnvVariable(key: String): Provider<String>
}

/**
 * Provides environment variables and system properties by using [ProviderFactory] APIs.
 */
class GradleEnvironmentProviderImpl(private val providerFactory: ProviderFactory) :
    GradleEnvironmentProvider {

    override fun getSystemProperty(key: String): Provider<String> {
        return providerFactory.systemProperty(key)
    }

    override fun getEnvVariable(key: String): Provider<String> {
        return providerFactory.environmentVariable(key)
    }
}

/** Implementation of [EnvironmentProvider] interface w/o dependencies on Gradle APIs. */
class EnvironmentProviderImpl(private val gradleEnvironmentProvider: GradleEnvironmentProvider) :
    EnvironmentProvider {
    override fun getSystemProperty(key: String): String? {
        return gradleEnvironmentProvider.getSystemProperty(key).orNull
    }

    override fun getEnvVariable(key: String): String? {
        return gradleEnvironmentProvider.getEnvVariable(key).orNull
    }
}
