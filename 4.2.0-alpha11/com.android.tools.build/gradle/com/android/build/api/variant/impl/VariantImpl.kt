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

import com.android.build.api.component.AndroidTest
import com.android.build.api.component.AndroidTestProperties
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.UnitTest
import com.android.build.api.component.UnitTestProperties
import com.android.build.api.component.analytics.AnalyticsEnabledVariant
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantApiServices
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Action

abstract class VariantImpl<PropertiesT: VariantProperties>(
    variantDslInfo: VariantDslInfo,
    componentIdentity: ComponentIdentity,
    variantApiServices: VariantApiServices
) :
    ComponentImpl<PropertiesT>(variantDslInfo, componentIdentity, variantApiServices),
    Variant<PropertiesT> {

    private var _minSdkVersion= AndroidVersionImpl(
        variantDslInfo.minSdkVersion.apiLevel,
        variantDslInfo.minSdkVersion.codename)

    override var minSdkVersion: AndroidVersion
        get() = _minSdkVersion
        set(value) {
            _minSdkVersion = AndroidVersionImpl(value.apiLevel, value.codename)
        }

    override fun unitTest(action: UnitTest<UnitTestProperties>.() -> Unit) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    fun unitTest(action: Action<UnitTest<UnitTestProperties>>) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    override fun unitTestProperties(action: UnitTestProperties.() -> Unit) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    fun unitTestProperties(action: Action<UnitTestProperties>) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    override fun androidTest(action: AndroidTest<AndroidTestProperties>.() -> Unit) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    fun androidTest(action: Action<AndroidTest<AndroidTestProperties>>) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    override fun androidTestProperties(action: AndroidTestProperties.() -> Unit) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    fun androidTestProperties(action: Action<AndroidTestProperties>) {
        throw RuntimeException("Actions can only be registered through DSL aware objects.")
    }

    abstract fun createUserVisibleVariantObject(projectServices: ProjectServices, stats: GradleBuildVariant.Builder): AnalyticsEnabledVariant<in PropertiesT>


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
