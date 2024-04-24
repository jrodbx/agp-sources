/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

open class AnalyticsEnabledAndroidResources @Inject constructor(
    open val delegate: AndroidResources,
    val stats: GradleBuildVariant.Builder,
): AndroidResources {

    override val ignoreAssetsPatterns: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.IGNORE_ASSETS_PATTERN_VALUE
            return delegate.ignoreAssetsPatterns
        }
    override val aaptAdditionalParameters: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.AAPT_ADDITIONAL_PARAMETERS_VALUE
            return delegate.aaptAdditionalParameters
        }

    override val noCompress: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.NO_COMPRESS_VALUE
            return delegate.noCompress
        }
}
