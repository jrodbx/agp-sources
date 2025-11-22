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

import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant

open class AnalyticsEnabledHostTestBuilder(
    val delegate: HostTestBuilder,
    val stats: GradleBuildVariant.Builder,
): HostTestBuilder {
    override var enable: Boolean
        get() = throw PropertyAccessNotAllowedException("enable", "HostTestBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.UNIT_TEST_ENABLED_VALUE
            delegate.enable = value
        }

    override var type: String
        get() = delegate.type
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.HOST_TEST_TYPE_VALUE
            delegate.type = value
        }

    override var enableCodeCoverage: Boolean
        get() = throw PropertyAccessNotAllowedException("enableCodeCoverage", "HostTestBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.HOST_TEST_ENABLE_CODE_COVERAGE_VALUE
            delegate.enableCodeCoverage = value
        }
}
