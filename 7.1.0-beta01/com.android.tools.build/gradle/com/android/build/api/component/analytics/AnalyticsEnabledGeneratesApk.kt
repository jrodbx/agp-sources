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

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.GeneratesApk
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider

open class AnalyticsEnabledGeneratesApk(
        val delegate: GeneratesApk,
        val stats: GradleBuildVariant.Builder,
        val objectFactory: ObjectFactory,
): GeneratesApk {

    override val applicationId: Provider<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.READ_ONLY_APPLICATION_ID_VALUE
            return delegate.applicationId
        }

    private val userVisibleRenderscript: Renderscript? by lazy {
        delegate.renderscript?.let {
            objectFactory.newInstance(
                    AnalyticsEnabledRenderscript::class.java,
                    it,
                    stats
            )
        }
    }

    override val renderscript: Renderscript?
        get() {
            return if (userVisibleRenderscript != null) {
                stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                        VariantPropertiesMethodType.RENDERSCRIPT_VALUE
                userVisibleRenderscript
            } else null
        }

    override val androidResources: AndroidResources
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.AAPT_OPTIONS_VALUE
            return delegate.androidResources
        }

    private val userVisibleApkPackaging: ApkPackaging by lazy {
        objectFactory.newInstance(
                AnalyticsEnabledApkPackaging::class.java,
                delegate.packaging,
                stats
        )
    }

    override val packaging: ApkPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisibleApkPackaging
        }
}
