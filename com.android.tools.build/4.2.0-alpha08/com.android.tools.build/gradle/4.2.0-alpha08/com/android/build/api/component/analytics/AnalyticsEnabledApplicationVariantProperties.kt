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

import com.android.build.api.variant.AaptOptions
import com.android.build.api.variant.ApkPackagingOptions
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.SigningConfig
import com.android.build.api.variant.VariantOutput
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class AnalyticsEnabledApplicationVariantProperties @Inject constructor(
    override val delegate: ApplicationVariantProperties,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariantProperties(
    delegate, stats, objectFactory
), ApplicationVariantProperties {
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

    override val aaptOptions: AaptOptions
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.AAPT_OPTIONS_VALUE
            return delegate.aaptOptions
        }

    override fun aaptOptions(action: AaptOptions.() -> Unit) {
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

    override val packagingOptions: ApkPackagingOptions
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return delegate.packagingOptions
        }

    override fun packagingOptions(action: ApkPackagingOptions.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE
        delegate.packagingOptions(action)
    }
}
