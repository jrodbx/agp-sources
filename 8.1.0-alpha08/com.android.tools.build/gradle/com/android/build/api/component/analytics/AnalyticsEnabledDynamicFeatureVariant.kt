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
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.TestFixtures
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

    private val userVisibleAndroidTest: AnalyticsEnabledAndroidTest? by lazy {
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

    private val userVisibleTestFixtures: TestFixtures? by lazy {
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

    private val generatesApk: GeneratesApk by lazy {
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

    override val packaging: ApkPackaging
        get() = generatesApk.packaging
}
