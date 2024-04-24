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

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.Dexing
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.TestedApkPackaging
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class AnalyticsEnabledDynamicFeatureVariant @Inject constructor(
    override val delegate: DynamicFeatureVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariant(delegate, stats, objectFactory), DynamicFeatureVariant {

    private val userVisibleAndroidTest: AnalyticsEnabledAndroidTest? by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
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

    private val userVisibleTestFixtures: TestFixtures? by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
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

    override val dexing: Dexing
        get() = generatesApk.dexing

    private val generatesApk: GeneratesApk by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        AnalyticsEnabledGeneratesApk(
                delegate,
                stats,
                objectFactory
        )
    }

    override val applicationId: Provider<String>
        get() = delegate.applicationId

    override val androidResources: AndroidResources
        get() = generatesApk.androidResources

    override val renderscript: Renderscript?
        get() = generatesApk.renderscript

    private val userVisiblePackaging: TestedApkPackaging by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        objectFactory.newInstance(
            AnalyticsEnabledTestedApkPackaging::class.java,
            delegate.packaging,
            stats
        )
    }

    override val packaging: TestedApkPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackaging
        }

    override val targetSdk: AndroidVersion
        get() = generatesApk.targetSdk

    override val deviceTests: List<DeviceTest>
        get()  {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEVICE_TESTS_VALUE
            // return a new list everytime as items may eventually be added through future APIs.
            // we may consider returning a live list instead.
            return  delegate.deviceTests.map {
                @Suppress("DEPRECATION")
                if (it is com.android.build.api.variant.AndroidTest) {
                    AnalyticsEnabledAndroidTest(it, stats, objectFactory)
                } else {
                    AnalyticsEnabledDeviceTest(it, stats, objectFactory)
                }
            }
        }

    private val userVisibleDefaultDeviceTest: AnalyticsEnabledDeviceTest? by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        delegate.defaultDeviceTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledDeviceTest::class.java,
                it,
                stats
            )
        }
    }
    override val defaultDeviceTest: DeviceTest?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEFAULT_DEVICE_TEST_VALUE
            return userVisibleDefaultDeviceTest
        }
}
