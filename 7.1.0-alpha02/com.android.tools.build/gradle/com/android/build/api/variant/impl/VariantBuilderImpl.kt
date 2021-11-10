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

abstract class VariantBuilderImpl (
    variantDslInfo: VariantDslInfo<*>,
    componentIdentity: ComponentIdentity,
    variantApiServices: VariantApiServices
) :
    ComponentBuilderImpl(variantDslInfo, componentIdentity, variantApiServices),
    VariantBuilder {

    private var _minSdkPreview: String? = variantDslInfo.minSdkVersion.codename
    private var _minSdk: Int? = if (variantDslInfo.minSdkVersion.codename == null)
        variantDslInfo.minSdkVersion.apiLevel else null

    override var minSdk: Int?
        get() = _minSdk
        set(value) {
            _minSdkPreview = null
            _minSdk = value
        }

    override var minSdkPreview: String?
        get() = _minSdkPreview
        set(value) {
            _minSdk = null
            _minSdkPreview = value
        }

    private var _targetSdkPreview: String? =  variantDslInfo.targetSdkVersion.codename
    private var _targetSdk: Int? =  if (variantDslInfo.targetSdkVersion.codename == null)
        variantDslInfo.targetSdkVersion.apiLevel else null

    override var targetSdk: Int?
        get() = _targetSdk
        set(value) {
            _targetSdkPreview = null
            _targetSdk = value
        }

    override var targetSdkPreview: String?
        get() = _targetSdkPreview
        set(value) {
            _targetSdk = null
            _targetSdkPreview = value
        }

    override var maxSdk: Int? = variantDslInfo.maxSdkVersion

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
            val minSdk = AndroidVersionImpl(
                _minSdk ?: 1,
                _minSdkPreview).getFeatureLevel()
            return if (targetApi > minSdk) targetApi else minSdk
        }

    override var enableUnitTest: Boolean = true

    override var unitTestEnabled: Boolean
        get() = enableUnitTest
        set(value) {
            enableUnitTest = value
        }

    private val registeredExtensionDelegate= lazy {
        mutableMapOf<Class<out Any>, Any>()
    }

    override fun <T: Any> registerExtension(type: Class<out T>, instance: T) {
        registeredExtensionDelegate.value[type] = instance
    }

    fun getRegisteredExtensions(): Map<Class<out Any>, Any>? =
        if (registeredExtensionDelegate.isInitialized())
            registeredExtensionDelegate.value
        else null

}
