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

import com.android.build.api.component.AndroidTest
import com.android.build.api.component.UnitTest
import com.android.build.api.variant.Aapt
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.Dexing
import com.android.build.api.variant.SigningConfig
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

    override val aapt: Aapt
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.AAPT_OPTIONS_VALUE
            return delegate.aapt
        }

    override fun aaptOptions(action: Aapt.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.AAPT_OPTIONS_ACTION_VALUE
        delegate.aaptOptions(action)
    }

    override val signingConfig: SigningConfig
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SIGNING_CONFIG_VALUE
            return delegate.signingConfig
        }

    override fun signingConfig(action: SigningConfig.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SIGNING_CONFIG_ACTION_VALUE
        delegate.signingConfig(action)
    }

    private val userVisiblePackagingOptions: ApkPackaging by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledApkPackaging::class.java,
            delegate.packaging,
            stats
        )
    }

    override val packaging: ApkPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackagingOptions
        }

    override fun packaging(action: ApkPackaging.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE
        action.invoke(userVisiblePackagingOptions)
    }

    private val userVisibleDexing: Dexing by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledDexing::class.java,
            delegate.dexing,
            stats
        )
    }

    private val userVisibleAndroidTest: AndroidTest? by lazy {
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

    override val dexing: Dexing
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEXING_VALUE
            return userVisibleDexing
        }

    override fun dexing(action: Dexing.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.DEXING_ACTION_VALUE
        action.invoke(userVisibleDexing)
    }
}
