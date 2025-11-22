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

import com.android.build.api.variant.ExternalNativeBuild
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

open class AnalyticsEnabledExternalCmake @Inject constructor(
        val delegate: ExternalNativeBuild,
        val stats: GradleBuildVariant.Builder
) : ExternalNativeBuild {

    override val abiFilters: SetProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.CMAKE_OPTIONS_ABI_FILTERS_VALUE
            return delegate.abiFilters
        }

    override val arguments: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.CMAKE_OPTIONS_ARGUMENTS_VALUE
            return delegate.arguments
        }

    override val cFlags: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.CMAKE_OPTIONS_C_FLAGS_VALUE
            return delegate.cFlags
        }

    override val cppFlags: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.CMAKE_OPTIONS_CPP_FLAGS_VALUE
            return delegate.cppFlags
        }

    override val targets: SetProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.CMAKE_OPTIONS_TARGETS_VALUE
            return delegate.targets
        }

}
