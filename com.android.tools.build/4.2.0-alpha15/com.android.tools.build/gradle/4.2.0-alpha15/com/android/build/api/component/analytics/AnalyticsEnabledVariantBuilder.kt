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

import com.android.build.api.component.AndroidTestBuilder
import com.android.build.api.component.AndroidTest
import com.android.build.api.component.UnitTestBuilder
import com.android.build.api.component.UnitTest
import com.android.build.api.component.impl.AndroidTestBuilderImpl
import com.android.build.api.component.impl.UnitTestBuilderImpl
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Action

abstract class AnalyticsEnabledVariantBuilder(
        override val delegate: VariantBuilder,
        stats: GradleBuildVariant.Builder
) : AnalyticsEnabledComponentBuilder(delegate, stats),
    VariantBuilder {

    override var minSdkVersion: AndroidVersion
        get() = delegate.minSdkVersion
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE
            delegate.minSdkVersion = value
        }

    override var maxSdkVersion: Int?
        get() = delegate.maxSdkVersion
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MAX_SDK_VERSION_VALUE_VALUE
            delegate.maxSdkVersion = value
        }

    override var renderscriptTargetApi: Int
        get() = delegate.renderscriptTargetApi
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder()
                .type = VariantMethodType.RENDERSCRIPT_TARGET_API_VALUE
            delegate.renderscriptTargetApi = value
        }

    val thisVariantApiOperationsRegistrar= VariantApiOperationsRegistrar<VariantBuilder, Variant>()

    override fun unitTest(action: UnitTestBuilder.() -> Unit) {
        thisVariantApiOperationsRegistrar.unitTestBuilderOperations.addOperation(action)
    }

    fun unitTest(action: Action<UnitTestBuilder>) {
        thisVariantApiOperationsRegistrar.unitTestBuilderOperations.addOperation(action)
    }

    override fun unitTestProperties(action: UnitTest.() -> Unit) {
        thisVariantApiOperationsRegistrar.unitTestOperations.addOperation(action)
    }

    fun unitTestProperties(action: Action<UnitTest>) {
        thisVariantApiOperationsRegistrar.unitTestOperations.addOperation(action)
    }

    override fun androidTest(action: AndroidTestBuilder.() -> Unit) {
        thisVariantApiOperationsRegistrar.androidTestBuilderOperations.addOperation(action)
    }

    fun androidTest(action: Action<AndroidTestBuilder>) {
        thisVariantApiOperationsRegistrar.androidTestBuilderOperations.addOperation(action)
    }

    override fun androidTestProperties(action: AndroidTest.() -> Unit) {
        thisVariantApiOperationsRegistrar.androidTestOperations.addOperation(action)
    }

    fun androidTestProperties(action: Action<AndroidTest>) {
        thisVariantApiOperationsRegistrar.androidTestOperations.addOperation(action)
    }

    // FIXME should be internal
    fun executeUnitTestActions(target: UnitTestBuilderImpl) {
        thisVariantApiOperationsRegistrar.unitTestBuilderOperations.executeOperations(
                AnalyticsEnabledUnitTestBuilder(target, stats))
    }

    // FIXME should be internal
    fun executeUnitTestPropertiesActions(target: UnitTest) {
        thisVariantApiOperationsRegistrar.unitTestOperations.executeOperations(target)
    }

    // FIXME should be internal
    fun executeAndroidTestActions(target: AndroidTestBuilderImpl) {
        thisVariantApiOperationsRegistrar.androidTestBuilderOperations.executeOperations(
                AnalyticsEnabledAndroidTestBuilder(target, stats))
    }

    // FIXME should be internal
    fun executeAndroidTestPropertiesActions(target: AndroidTest) {
        thisVariantApiOperationsRegistrar.androidTestOperations.executeOperations(target)
    }
}
