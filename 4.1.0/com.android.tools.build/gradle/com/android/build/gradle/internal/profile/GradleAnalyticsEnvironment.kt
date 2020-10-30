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
import java.util.concurrent.ConcurrentHashMap

/** Gradle-specific implementation of the [Environment] for the analytics library. */
class GradleAnalyticsEnvironment(private var providerFactory: ProviderFactory?) : Environment() {

    private val systemProperties = ConcurrentHashMap<String, NullableString>()
    private val envVariables = ConcurrentHashMap<String, NullableString>()

    override fun getVariable(name: String): String? {
        return envVariables.computeIfAbsent(name) {
            NullableString(
                providerFactory?.environmentVariable(name)?.forUseAtConfigurationTime()?.orNull
            )
        }.value
    }

    override fun getSystemProperty(name: String): String? {
        return systemProperties.computeIfAbsent(name) {
            NullableString(
                providerFactory?.systemProperty(name)?.forUseAtConfigurationTime()?.orNull
            )
        }.value
    }

    /**
     * Release the provider factory to avoid leaks in http://b/160330055. Configuration cached runs
     * will use cached system properties and env variables, and http://b/157470515 will fix this
     * properly.
     */
    fun releaseProviderFactory() {
        providerFactory = null
    }

    // ConcurrentHashMap does not allow null values, so wrap in in a data class
    data class NullableString(val value: String?)
}