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

import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.ApplicationAndroidResources
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.BundleConfig
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.DeviceTest
import com.android.build.api.variant.Dexing
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.SigningConfig
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.TestedApkPackaging
import com.android.build.api.variant.VariantOutput
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class AnalyticsEnabledApplicationVariant @Inject constructor(
    override val delegate: ApplicationVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariant(
    delegate, stats, objectFactory
), ApplicationVariant {
    override val applicationId: Property<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.APPLICATION_ID_VALUE
            return delegate.applicationId
        }

    override val outputs: List<VariantOutput>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.GET_OUTPUTS_VALUE
            return delegate.outputs
        }

    override val dependenciesInfo: DependenciesInfo
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEPENDENCIES_INFO_VALUE
            return delegate.dependenciesInfo
        }

    val userVisibleSigningConfig: AnalyticsEnabledSigningConfig by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        objectFactory.newInstance(
            AnalyticsEnabledSigningConfig::class.java,
            delegate.signingConfig,
            stats
        )
    }

    override val signingConfig: SigningConfig
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SIGNING_CONFIG_VALUE
            return userVisibleSigningConfig
        }

    private val userVisibleAndroidTest: AnalyticsEnabledAndroidTest? by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        delegate.androidTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledAndroidTest::class.java,
                it,
                stats
            )
        }
    }

    override val androidTest: AndroidTest?
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

    private val generatesApk: GeneratesApk by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        AnalyticsEnabledGeneratesApk(
                delegate,
                stats,
                objectFactory
        )
    }

    override val androidResources: ApplicationAndroidResources
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.AAPT_OPTIONS_VALUE
            return delegate.androidResources
        }

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

    private val userVisibleBundleConfig: BundleConfig by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
        objectFactory.newInstance(
            AnalyticsEnabledBundleConfig::class.java,
            delegate.bundleConfig,
            stats
        )
    }

    override val bundleConfig: BundleConfig
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.GET_BUNDLE_CONFIG_VALUE
            return userVisibleBundleConfig
        }

    override val isMinifyEnabled: Boolean
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.CODE_MINIFICATION_VALUE
            return delegate.isMinifyEnabled
        }

    override val shrinkResources: Boolean
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SHRINK_RESOURCES_VALUE
            return delegate.shrinkResources
        }

    override val targetSdk: AndroidVersion
        get()  {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.TARGET_SDK_VERSION_VALUE
            return delegate.targetSdk
        }

    override val dexing: Dexing
        get() = generatesApk.dexing

    override val deviceTests: List<DeviceTest>
        get()  {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEVICE_TESTS_VALUE
            // return a new list everytime as items may eventually be added through future APIs.
            // we may consider returning a live list instead.
            return  delegate.deviceTests.map {
                if (it is AndroidTest) {
                    AnalyticsEnabledAndroidTest(it, stats, objectFactory)
                } else {
                    AnalyticsEnabledDeviceTest(it, stats, objectFactory)
                }
            }
        }

    private val userVisibleDeviceTest: AnalyticsEnabledDeviceTest? by lazy(LazyThreadSafetyMode.SYNCHRONIZED){
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
            return userVisibleDeviceTest
        }
}
