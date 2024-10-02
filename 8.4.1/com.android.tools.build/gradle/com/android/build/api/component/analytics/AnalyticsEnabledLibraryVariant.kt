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

import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.TestedComponentPackaging
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledLibraryVariant @Inject constructor(
    override val delegate: LibraryVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariant(
    delegate, stats, objectFactory
), LibraryVariant {

    private val userVisibleAndroidTest: AnalyticsEnabledAndroidTest? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        delegate.androidTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledAndroidTest::class.java,
                it,
                stats
            )
        }
    }

    @Suppress("DEPRECATION")
    override val androidTest: com.android.build.api.variant.AndroidTest?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANDROID_TEST_VALUE
            return userVisibleAndroidTest
        }

    private val userVisibleTestFixtures: TestFixtures? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        delegate.testFixtures?.let {
            objectFactory.newInstance(
                AnalyticsEnabledTestFixtures::class.java,
                it,
                stats
            )
        }
    }

    override val testFixtures: TestFixtures?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TEST_FIXTURES_VALUE
            return userVisibleTestFixtures
        }

    private val userVisibleRenderscript: Renderscript by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        objectFactory.newInstance(
            AnalyticsEnabledRenderscript::class.java,
            delegate.renderscript,
            stats
        )
    }

    override val renderscript: Renderscript?
        get() {
            return if (delegate.renderscript != null) {
                stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.RENDERSCRIPT_VALUE
                userVisibleRenderscript
            } else null
        }

    private val userVisibleAarMetadata: AarMetadata by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        objectFactory.newInstance(
            AnalyticsEnabledAarMetadata::class.java,
            delegate.aarMetadata,
            stats
        )
    }
    override val aarMetadata: AarMetadata
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_VALUE
            return userVisibleAarMetadata
        }

    override val isMinifyEnabled: Boolean
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.CODE_MINIFICATION_VALUE
            return delegate.isMinifyEnabled
        }

    override val deviceTests: List<DeviceTest>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEVICE_TESTS_VALUE
            // return a new list everytime as items may eventually be added through future APIs.
            // we may consider returning a live list instead.
            return delegate.deviceTests.map {
                @Suppress("DEPRECATION")
                if (it is com.android.build.api.variant.AndroidTest) {
                    AnalyticsEnabledAndroidTest(it, stats, objectFactory)
                } else {
                    AnalyticsEnabledDeviceTest(it, stats, objectFactory)
                }
            }
        }

    private val userVisiblePackaging: TestedComponentPackaging by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        objectFactory.newInstance(
            AnalyticsEnabledTestedComponentPackaging::class.java,
            delegate.packaging,
            stats
        )
    }

    override val packaging: TestedComponentPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackaging
        }
}
