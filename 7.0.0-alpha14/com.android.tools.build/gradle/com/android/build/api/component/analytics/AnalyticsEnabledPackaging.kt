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

import com.android.build.api.variant.JniLibsPackaging
import com.android.build.api.variant.Packaging
import com.android.build.api.variant.ResourcesPackaging
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class AnalyticsEnabledPackaging @Inject constructor(
        open val delegate: Packaging,
        val stats: GradleBuildVariant.Builder
) : Packaging {

    override val jniLibs: JniLibsPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_VALUE
            return delegate.jniLibs
        }

    override val resources: ResourcesPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_VALUE
            return delegate.resources
        }
}
