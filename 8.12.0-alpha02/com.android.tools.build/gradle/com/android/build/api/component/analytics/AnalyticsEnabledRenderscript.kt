/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.variant.Renderscript
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Property
import javax.inject.Inject

open class AnalyticsEnabledRenderscript @Inject constructor(
    val delegate: Renderscript,
    val stats: GradleBuildVariant.Builder)
: Renderscript {

    override val supportModeEnabled: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RENDERSCRIPT_SUPPORT_MODE_VALUE
            return delegate.supportModeEnabled
        }

    override val supportModeBlasEnabled: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RENDERSCRIPT_SUPPORT_MODE_BLAS_VALUE
            return delegate.supportModeBlasEnabled
        }

    override val ndkModeEnabled: Property<Boolean>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RENDERSCRIPT_NDK_MODE_VALUE
            return delegate.ndkModeEnabled
        }

    override val optimLevel: Property<Int>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RENDERSCRIPT_OPTIMIZATION_LEVEL_VALUE
            return delegate.optimLevel
        }
}
