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

import com.android.build.gradle.internal.testsuites.JUnitEngineSpec
import com.android.build.gradle.internal.testsuites.TestSuite
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.testing.Test

open class AnalyticsEnabledTestSuite(
    val delegate: TestSuite,
    val stats: com.google.wireless.android.sdk.stats.GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
): TestSuite {

    override fun configureTestTask(action: (Test) -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.CONFIGURE_TEST_TASK_VALUE
        delegate.configureTestTask(action)
    }

    override val junitEngineSpec: JUnitEngineSpec
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.JUNIT_ENGINE_SPEC_VALUE
            return delegate.junitEngineSpec
        }

    override fun getName(): String = delegate.name
}
