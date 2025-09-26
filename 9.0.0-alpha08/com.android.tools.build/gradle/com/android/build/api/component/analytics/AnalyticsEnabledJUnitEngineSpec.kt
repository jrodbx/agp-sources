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

import com.android.build.api.dsl.AgpTestSuiteInputParameters
import com.android.build.api.variant.JUnitEngineSpec
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.provider.Provider

open class AnalyticsEnabledJUnitEngineSpec(
    private val delegate: JUnitEngineSpec,
    val stats: com.google.wireless.android.sdk.stats.GradleBuildVariant.Builder,
): JUnitEngineSpec {

    override val inputs: List<AgpTestSuiteInputParameters>
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.JUNIT_ENGINE_BUILDER_INPUTS_VALUE
            return delegate.inputs
        }

    override val includeEngines: MutableSet<String>
        get() {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.JUNIT_ENGINE_BUILDER_INCLUDE_ENGINES_VALUE
            return delegate.includeEngines
        }

    override fun addInputProperty(propertyName: String, propertyValue: String) {
        stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
            VariantMethodType.JUNIT_ENGINE_BUILDER_INPUT_PROPERTIES_VALUE
        delegate.addInputProperty(propertyName, propertyValue)
    }

    override fun addInputProperty(
        propertyName: String,
        propertyValue: Provider<String>
    ) {
        stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
            VariantMethodType.JUNIT_ENGINE_BUILDER_INPUT_PROPERTIES_VALUE
        delegate.addInputProperty(propertyName, propertyValue)
    }

    override val enginesDependencies: DependencyCollector
        get()  {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.JUNIT_ENGINE_BUILDER_ENGINE_DEPENDENCIES_VALUE
            return delegate.enginesDependencies
        }
}
