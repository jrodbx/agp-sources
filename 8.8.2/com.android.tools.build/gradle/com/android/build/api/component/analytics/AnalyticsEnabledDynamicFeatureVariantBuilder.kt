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

import com.android.build.api.variant.AndroidTestBuilder
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.DynamicFeatureVariantBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.api.variant.HostTestBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

/**
 * Shim object for [DynamicFeatureVariantBuilder] that records all mutating accesses to the analytics.
 */
open class AnalyticsEnabledDynamicFeatureVariantBuilder @Inject constructor(
    final override val delegate: DynamicFeatureVariantBuilder,
    stats: GradleBuildVariant.Builder
) : AnalyticsEnabledVariantBuilder(delegate, stats),
    DynamicFeatureVariantBuilder {

    override var androidTestEnabled: Boolean
        get() = delegate.androidTest.enable
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ANDROID_TEST_ENABLED_VALUE
            delegate.androidTest.enable = value
        }

    override var enableAndroidTest: Boolean
        get() = delegate.androidTest.enable
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ANDROID_TEST_ENABLED_VALUE
            delegate.androidTest.enable = value
        }

    override var enableTestFixtures: Boolean
        get() = delegate.enableTestFixtures
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.TEST_FIXTURES_ENABLED_VALUE
            delegate.enableTestFixtures = value
        }

    override var enableMultiDex: Boolean?
        get() = throw PropertyAccessNotAllowedException("enableMultiDex", "DynamicFeatureVariantBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.ENABLE_MULTI_DEX_VALUE
            delegate.enableMultiDex = value
        }

    private val _androidTest =
        AnalyticsEnabledAndroidTestBuilder(
                delegate.androidTest,
                stats
        )

    override val androidTest: AndroidTestBuilder
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.ANDROID_TEST_BUILDER_VALUE
            return _androidTest
        }

    override val deviceTests: Map<String, DeviceTestBuilder>
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.DEVICE_TESTS_BUILDER_VALUE
            // return a copy of the map every time as new items may have
            // been added to it since last call.
            return delegate.deviceTests.mapValues {
                val value = it.value
                @Suppress("DEPRECATION")
                if (value is AndroidTestBuilder) {
                    AnalyticsEnabledAndroidTestBuilder(
                        value,
                        stats
                    )
                } else {
                    AnalyticsEnabledDeviceTestBuilder(
                        value,
                        stats
                    )
                }
            }
        }

    override val hostTests: Map<String, HostTestBuilder>
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.HOST_TESTS_BUILDER_VALUE
            // return a copy of the map every time as new items may have
            // been added to it since last call.
            return delegate.hostTests.mapValues {
                AnalyticsEnabledHostTestBuilder(
                    it.value,
                    stats
                )
            }
        }
}
