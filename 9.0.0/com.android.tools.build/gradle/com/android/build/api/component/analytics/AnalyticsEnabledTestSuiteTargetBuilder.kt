/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.api.variant.TestSuiteTargetBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType

open class AnalyticsEnabledTestSuiteTargetBuilder(
    private val delegate: TestSuiteTargetBuilder,
    val stats: com.google.wireless.android.sdk.stats.GradleBuildVariant.Builder,
): TestSuiteTargetBuilder {

    override var enable: Boolean
        get() = throw PropertyAccessNotAllowedException("enable", "HostTestBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.TEST_SUITE_TARGET_BUILDER_ENABLE_VALUE
            delegate.enable = value
        }

    override val targetDevices: MutableList<String>
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.TEST_SUITE_TARGET_BUILDER_TARGET_DEVICES_VALUE
            return delegate.targetDevices
        }

    override fun getName(): String = delegate.name
}
