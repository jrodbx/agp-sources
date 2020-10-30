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

import com.android.build.api.component.AndroidTest
import com.android.build.api.component.AndroidTestProperties
import com.android.build.api.component.UnitTest
import com.android.build.api.component.UnitTestProperties
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.api.variant.impl.DelayedActionExecutor
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Action

abstract class AnalyticsEnabledVariant<PropertiesT: VariantProperties>(
    override val delegate: Variant<PropertiesT>,
    stats: GradleBuildVariant.Builder
) : AnalyticsEnabledComponent<PropertiesT>(delegate, stats),
    Variant<PropertiesT> {

    private val unitTestActions = DelayedActionExecutor<UnitTest<UnitTestProperties>>()
    private val unitTestPropertiesOperations = DelayedActionExecutor<UnitTestProperties>()

    private val androidTestActions = DelayedActionExecutor<AndroidTest<AndroidTestProperties>>()
    private val androidTestPropertiesOperations = DelayedActionExecutor<AndroidTestProperties>()

    override var minSdkVersion: AndroidVersion
        get() = delegate.minSdkVersion
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE
            delegate.minSdkVersion = value}

    override fun unitTest(action: UnitTest<UnitTestProperties>.() -> Unit) {
        unitTestActions.registerAction(Action { action(it) })
    }

    fun unitTest(action: Action<UnitTest<UnitTestProperties>>) {
        unitTestActions.registerAction(action)
    }

    override fun unitTestProperties(action: UnitTestProperties.() -> Unit) {
        unitTestPropertiesOperations.registerAction(Action { action(it) })
    }

    fun unitTestProperties(action: Action<UnitTestProperties>) {
        unitTestPropertiesOperations.registerAction(action)
    }

    override fun androidTest(action: AndroidTest<AndroidTestProperties>.() -> Unit) {
        androidTestActions.registerAction(Action { action(it) })
    }

    fun androidTest(action: Action<AndroidTest<AndroidTestProperties>>) {
        androidTestActions.registerAction(action)
    }

    override fun androidTestProperties(action: AndroidTestProperties.() -> Unit) {
        androidTestPropertiesOperations.registerAction(Action { action(it) })
    }

    fun androidTestProperties(action: Action<AndroidTestProperties>) {
        androidTestPropertiesOperations.registerAction(action)
    }

    // FIXME should be internal
    fun executeUnitTestActions(target: UnitTestImpl) {
        @Suppress("UNCHECKED_CAST")
        unitTestActions.executeActions(
            AnalyticsEnabledUnitTest(target, stats) as UnitTest<UnitTestProperties>)
    }

    // FIXME should be internal
    fun executeUnitTestPropertiesActions(target: UnitTestProperties) {
        unitTestPropertiesOperations.executeActions(target)
    }

    // FIXME should be internal
    fun executeAndroidTestActions(target: AndroidTestImpl) {
        @Suppress("UNCHECKED_CAST")
        androidTestActions.executeActions(
            AnalyticsEnabledAndroidTest(target, stats) as AndroidTest<AndroidTestProperties>)
    }

    // FIXME should be internal
    fun executeAndroidTestPropertiesActions(target: AndroidTestProperties) {
        androidTestPropertiesOperations.executeActions(target)
    }
}