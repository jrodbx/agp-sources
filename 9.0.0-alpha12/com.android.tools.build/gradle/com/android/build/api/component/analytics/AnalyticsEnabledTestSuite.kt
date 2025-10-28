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

import com.android.build.api.dsl.TestTaskContext
import com.android.build.api.variant.JUnitEngineSpec
import com.android.build.api.variant.TestSuite
import com.android.build.api.variant.TestSuiteTarget
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.testing.Test

open class AnalyticsEnabledTestSuite(
    val delegate: TestSuite,
    val stats: com.google.wireless.android.sdk.stats.GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
): TestSuite {

    override fun configureTestTasks(action: Test.(context: TestTaskContext) -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.CONFIGURE_TEST_TASK_VALUE
        delegate.configureTestTasks(action)
    }

    override val junitEngineSpec: JUnitEngineSpec
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.JUNIT_ENGINE_SPEC_VALUE
            return AnalyticsEnabledJUnitEngineSpec(
                delegate.junitEngineSpec,
                stats)
        }

    override fun getName(): String = delegate.name

    override val targets: Map<String, TestSuiteTarget>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TEST_SUITE_TARGETS_VALUE
            return delegate.targets.mapValues { target ->
                AnalyticsEnabledTestSuiteTarget(target.value, stats)
            }
        }
    override val codeCoverage: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.TEST_SUITE_CODE_COVERAGE_VALUE
            return delegate.codeCoverage
        }
}
