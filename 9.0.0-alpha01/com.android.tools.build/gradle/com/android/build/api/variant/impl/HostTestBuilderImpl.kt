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
package com.android.build.api.variant.impl

import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl

open class HostTestBuilderImpl(
    override var enable: Boolean,
    override var type: String,
    val componentType: ComponentType,
    internal var _enableCodeCoverage: Boolean
) : HostTestBuilder {

    override var enableCodeCoverage: Boolean
        get() = throw PropertyAccessNotAllowedException("enableCodeCoverage", "HostTestBuilder")
        set(value) {
            _enableCodeCoverage = value
        }

    companion object {
        private fun forUnitTest(
            enableCodeCoverage: Boolean,
        ): HostTestBuilderImpl = HostTestBuilderImpl(
            enable = true,
            type = HostTestBuilder.UNIT_TEST_TYPE,
            componentType = ComponentTypeImpl.UNIT_TEST,
            _enableCodeCoverage = enableCodeCoverage,
        )

        private fun forScreenshotTest(
            experimentalProperties: Map<String, Any>,
            enableCodeCoverage: Boolean,
        ): HostTestBuilderImpl = HostTestBuilderImpl(
            ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST.getValue(experimentalProperties),
            HostTestBuilder.SCREENSHOT_TEST_TYPE,
            ComponentTypeImpl.SCREENSHOT_TEST,
            enableCodeCoverage,
        )

        /**
         * Create the list of host tests for this component, the list is driven by
         * the passed [dslDefinedHostTestsDefinitions] which is the list of host
         * tests implicitly or explicitly defined by the Component type and its DSL.
         */
        // TODO: Improve this once the Screenshot tests specific types are removed.
        fun create(
            dslDefinedHostTestsDefinitions: List<ComponentDslInfo.DslDefinedHostTest>,
            experimentalProperties: Map<String, Any>,
        ): Map<String, HostTestBuilder> =
            dslDefinedHostTestsDefinitions.associate { it.type to
                    when(it.type) {
                        HostTestBuilder.UNIT_TEST_TYPE ->
                            forUnitTest(
                                it.codeCoverageEnabled,
                            )

                        HostTestBuilder.SCREENSHOT_TEST_TYPE ->
                            forScreenshotTest(
                                experimentalProperties,
                                it.codeCoverageEnabled,
                            )
                        else -> throw RuntimeException("Unknown host test type : ${it.type}")
                    }
            }
    }
}

