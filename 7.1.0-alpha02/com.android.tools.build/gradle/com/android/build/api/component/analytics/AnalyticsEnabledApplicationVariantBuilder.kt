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

package com.android.build.api.component.analytics

import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

/**
 * Shim object for [ApplicationVariantBuilder] that records all mutating accesses to the analytics.
 */
open class AnalyticsEnabledApplicationVariantBuilder @Inject constructor(
        override val delegate: ApplicationVariantBuilder,
        stats: GradleBuildVariant.Builder
) : AnalyticsEnabledVariantBuilder(delegate, stats),
    ApplicationVariantBuilder {

    override val debuggable: Boolean
        get() = delegate.debuggable
    override val dependenciesInfo: DependenciesInfoBuilder
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.VARIANT_BUILDER_DEPENDENCIES_INFO_VALUE
            return delegate.dependenciesInfo
        }

    override var androidTestEnabled: Boolean
        get() = delegate.enableAndroidTest
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ANDROID_TEST_ENABLED_VALUE
            delegate.enableAndroidTest = value
        }

    override var enableAndroidTest: Boolean
        get() = delegate.enableAndroidTest
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ANDROID_TEST_ENABLED_VALUE
            delegate.enableAndroidTest = value
        }

    override var enableTestFixtures: Boolean
        get() = delegate.enableTestFixtures
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.TEST_FIXTURES_ENABLED_VALUE
            delegate.enableTestFixtures = value
        }
}
