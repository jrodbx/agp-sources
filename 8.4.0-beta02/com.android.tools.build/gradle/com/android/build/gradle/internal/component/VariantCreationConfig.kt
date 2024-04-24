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

package com.android.build.gradle.internal.component

import com.android.build.api.variant.Component
import com.android.build.gradle.internal.dsl.ModulePropertyKey.OptionalBoolean
import com.android.build.gradle.options.BooleanOption
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider

interface VariantCreationConfig: ConsumableCreationConfig {
    val maxSdk: Int?

    val experimentalProperties: MapProperty<String, Any>

    val nestedComponents: List<ComponentCreationConfig>

    fun <T: Component> createUserVisibleVariantObject(
        stats: GradleBuildVariant.Builder?
    ): T

    /**
     * Whether to use K2 UAST when running lint for this component or its nested components.
     */
    val useK2Uast: Provider<Boolean>
        get() =
            experimentalProperties.zip(
                services.projectOptions.getProvider(BooleanOption.LINT_USE_K2_UAST)
            ) { experimentalProperties, lintUseK2UastBooleanOption ->
                OptionalBoolean.LINT_USE_K2_UAST.getValue(experimentalProperties)
                    ?: lintUseK2UastBooleanOption
            }
}
