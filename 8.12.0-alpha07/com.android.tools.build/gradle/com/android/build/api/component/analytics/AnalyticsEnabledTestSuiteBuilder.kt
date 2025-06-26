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

import com.android.build.api.dsl.JUnitEngineSpec
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.gradle.internal.testsuites.TestSuiteBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType

open class AnalyticsEnabledTestSuiteBuilder(
    private val delegate: TestSuiteBuilder,
    val stats: com.google.wireless.android.sdk.stats.GradleBuildVariant.Builder,
): TestSuiteBuilder {

    override var enable: Boolean
        get() = throw PropertyAccessNotAllowedException("enable", "HostTestBuilder")
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.UNIT_TEST_ENABLED_VALUE
            delegate.enable = value
        }

    override val junitEngineSpec: JUnitEngineSpec
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.JUNIT_ENGINE_SPEC_BUILDER_VALUE
            return delegate.junitEngineSpec
        }

    override fun getName(): String = delegate.name
}
