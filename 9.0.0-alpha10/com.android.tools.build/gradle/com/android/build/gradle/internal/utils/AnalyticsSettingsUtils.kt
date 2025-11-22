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

@file:JvmName("AnalyticsSettingsUtils")

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.LoggerWrapper
import com.android.tools.analytics.AnalyticsSettings
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Class providing configuration-cache compatible way to check if analytics are enabled.
 */
abstract class AnalyticsEnabledValueSource : ValueSource<Boolean, AnalyticsEnabledValueSource.Params> {
    override fun obtain(): Boolean {
        AnalyticsSettings.initialize(
            LoggerWrapper.getLogger(AnalyticsEnabledValueSource::class.java)
        )
        return AnalyticsSettings.optedIn || parameters.profileJsonEnabled.get()
    }

    interface Params: ValueSourceParameters {
        val profileJsonEnabled: Property<Boolean>
    }
}

fun analyticsEnabledProvider(
    providerFactory: ProviderFactory,
    profileJsonEnabled: Boolean
): Provider<Boolean> =
    providerFactory.of(AnalyticsEnabledValueSource::class.java) {
        it.parameters.profileJsonEnabled.set(profileJsonEnabled)
    }
