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
import com.android.build.api.variant.Aapt
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.SigningConfig
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.Serializable
import javax.inject.Inject

open class AnalyticsEnabledAndroidTest @Inject constructor(
    override val delegate: AndroidTest,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledTestComponent(
    delegate, stats, objectFactory
), AndroidTest {
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

    override val namespace: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NAMESPACE_VALUE
            return delegate.namespace
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

    override val buildConfigFields: MapProperty<String, out BuildConfigField<out Serializable>>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE
            return delegate.buildConfigFields
        }

    override fun addResValue(name: String, type: String, value: String, comment: String?) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ADD_RES_VALUE_VALUE
        delegate.addResValue(name, type, value, comment)
    }

    override fun addResValue(
        name: String,
        type: String,
        value: Provider<String>,
        comment: String?
    ) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.ADD_RES_VALUE_VALUE
        delegate.addResValue(name, type, value, comment)
    }

    override val manifestPlaceholders: MapProperty<String, String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.MANIFEST_PLACEHOLDERS_VALUE
            return delegate.manifestPlaceholders
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

    private val userVisiblePackaging: ApkPackaging by lazy {
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
            return userVisiblePackaging
        }

    override fun packaging(action: ApkPackaging.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE
        action.invoke(userVisiblePackaging)
    }
}
