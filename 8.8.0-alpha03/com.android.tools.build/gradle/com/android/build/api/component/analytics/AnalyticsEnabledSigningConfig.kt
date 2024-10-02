/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.variant.SigningConfig
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import javax.inject.Inject

open class AnalyticsEnabledSigningConfig@Inject constructor(
    val delegate: SigningConfig,
    val stats: GradleBuildVariant.Builder
): SigningConfig {

    override fun setConfig(signingConfig: com.android.build.api.dsl.SigningConfig) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SIGNING_CONFIG_SET_CONFIG_VALUE
        delegate.setConfig(signingConfig)
    }

    override val enableV1Signing: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V1_VALUE
            return delegate.enableV1Signing
        }

    override val enableV2Signing: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V2_VALUE
            return delegate.enableV2Signing
        }

    override val enableV3Signing: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V3_VALUE
            return delegate.enableV3Signing
        }

    override val enableV4Signing: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V4_VALUE
            return delegate.enableV4Signing
        }
}
