/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant

open class AnalyticsEnabledDeviceTestBuilder(
    val delegate: DeviceTestBuilder,
    val stats: GradleBuildVariant.Builder,
): DeviceTestBuilder {

    override var enable: Boolean
        get() = delegate.enable
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.ANDROID_TEST_ENABLED_VALUE
            delegate.enable = value
        }

    override var enableMultiDex: Boolean?
        get() = throw PropertyAccessNotAllowedException("enableMultiDex", "DeviceTestBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.ENABLE_MULTI_DEX_VALUE
            delegate.enableMultiDex = value
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


    override var enableCodeCoverage: Boolean
        get() =  throw PropertyAccessNotAllowedException("enableCodeCoverage", "AndroidTestBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.DEVICE_TEST_ENABLE_CODE_COVERAGE_VALUE
            delegate.enableCodeCoverage = value
        }
}
