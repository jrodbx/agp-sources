/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.impl.ComponentBuilderImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantApiServices
import com.google.wireless.android.sdk.stats.GradleBuildVariant

abstract class VariantBuilderImpl(
    variantDslInfo: VariantDslInfo,
    componentIdentity: ComponentIdentity,
    variantApiServices: VariantApiServices
) :
    ComponentBuilderImpl(variantDslInfo, componentIdentity, variantApiServices),
    VariantBuilder {

    private var _minSdkVersion= AndroidVersionImpl(
        variantDslInfo.minSdkVersion.apiLevel,
        variantDslInfo.minSdkVersion.codename)

    override var minSdkVersion: AndroidVersion
        get() = _minSdkVersion
        set(value) {
            _minSdkVersion = AndroidVersionImpl(value.apiLevel, value.codename)
        }

    private var _targetSdkVersion= AndroidVersionImpl(
            variantDslInfo.targetSdkVersion.apiLevel,
            variantDslInfo.targetSdkVersion.codename
    )
    override var targetSdkVersion: AndroidVersion
        get() = _targetSdkVersion
        set(value) {
            _targetSdkVersion = AndroidVersionImpl(value.apiLevel, value.codename)
        }
    override var maxSdkVersion: Int? = variantDslInfo.maxSdkVersion

    abstract fun <T: VariantBuilder> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            stats: GradleBuildVariant.Builder?): T

    override var renderscriptTargetApi: Int = -1
        get() {
            // if the field has been set, always use  that value, otherwise, calculate it each
            // time in case minSdkVersion changes.
            if (field != -1) return field
            val targetApi = variantDslInfo.renderscriptTarget
            // default to -1 if not in build.gradle file.
            val minSdk = minSdkVersion.getFeatureLevel()
            return if (targetApi > minSdk) targetApi else minSdk
        }
}
