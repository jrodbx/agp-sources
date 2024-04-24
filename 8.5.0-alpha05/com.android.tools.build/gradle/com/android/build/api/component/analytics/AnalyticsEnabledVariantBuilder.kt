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

import com.android.build.api.variant.VariantBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant

abstract class AnalyticsEnabledVariantBuilder(
        override val delegate: VariantBuilder,
        stats: GradleBuildVariant.Builder
) : AnalyticsEnabledComponentBuilder(delegate, stats),
    VariantBuilder {

    override var minSdk: Int?
        get() = delegate.minSdk
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE
            delegate.minSdk = value
        }

    override var minSdkPreview: String?
        get() = delegate.minSdkPreview
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MIN_SDK_PREVIEW_VALUE
            delegate.minSdkPreview = value
        }

    override var maxSdk: Int?
        get() = delegate.maxSdk
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MAX_SDK_VERSION_VALUE_VALUE
            delegate.maxSdk = value
        }

    override var targetSdk: Int?
        get() = delegate.targetSdk
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                    VariantMethodType.TARGET_SDK_VERSION_VALUE_VALUE
            delegate.targetSdk = value
        }

    override var targetSdkPreview: String?
        get() = delegate.targetSdkPreview
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.TARGET_SDK_PREVIEW_VALUE
            delegate.targetSdkPreview = value
        }

    override var renderscriptTargetApi: Int
        get() = delegate.renderscriptTargetApi
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder()
                .type = VariantMethodType.RENDERSCRIPT_TARGET_API_VALUE
            delegate.renderscriptTargetApi = value
        }

    override var unitTestEnabled: Boolean
        get() = delegate.enableUnitTest
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.UNIT_TEST_ENABLED_VALUE
            delegate.enableUnitTest = value
        }

    override var enableUnitTest: Boolean
        get() = delegate.enableUnitTest
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.UNIT_TEST_ENABLED_VALUE
            delegate.enableUnitTest = value
        }

    override fun <T: Any> registerExtension(type: Class<out T>, instance: T) {
        stats.variantApiAccessBuilder.addVariantAccessBuilder()
            .type = VariantMethodType.REGISTER_EXTENSION_VALUE
        delegate.registerExtension(type, instance)
    }
}
