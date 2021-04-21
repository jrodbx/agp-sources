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

import com.android.build.api.variant.Aapt
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.TestVariant
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class AnalyticsEnabledTestVariant @Inject constructor(
    override val delegate: TestVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
): AnalyticsEnabledVariant(delegate, stats, objectFactory), TestVariant {
    override val applicationId: Property<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.APPLICATION_ID_VALUE
            return delegate.applicationId
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

    override val testedApplicationId: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TESTED_APPLICATION_ID_VALUE
            return delegate.testedApplicationId
        }

    override val instrumentationRunner: Property<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.INSTRUMENTATION_RUNNER_VALUE
            return delegate.instrumentationRunner
        }

    override val handleProfiling: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.HANDLE_PROFILING_VALUE
            return delegate.handleProfiling
        }

    override val functionalTest: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.FUNCTIONAL_TEST_VALUE
            return delegate.functionalTest
        }

    override val testLabel: Property<String?>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TEST_LABEL_VALUE
            return delegate.testLabel
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
}
