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
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.VariantApiServices
import org.gradle.api.Action

abstract class VariantImpl<PropertiesT: VariantPropertiesImpl>(
    variantDslInfo: VariantDslInfo,
    componentIdentity: ComponentIdentity,
    variantApiServices: VariantApiServices
) :
    ComponentImpl<PropertiesT>(variantDslInfo, componentIdentity, variantApiServices),
    Variant<PropertiesT> {

    private val unitTestActions = DelayedActionExecutor<UnitTest<UnitTestProperties>>()
    private val unitTestPropertiesOperations = DelayedActionExecutor<UnitTestProperties>()

    private val androidTestActions = DelayedActionExecutor<AndroidTest<AndroidTestProperties>>()
    private val androidTestPropertiesOperations = DelayedActionExecutor<AndroidTestProperties>()

    override var minSdkVersion = variantDslInfo.minSdkVersion.apiLevel

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
        unitTestActions.executeActions(target as UnitTest<UnitTestProperties>)
    }

    // FIXME should be internal
    fun executeUnitTestPropertiesActions(target: UnitTestProperties) {
        unitTestPropertiesOperations.executeActions(target)
    }

    // FIXME should be internal
    fun executeAndroidTestActions(target: AndroidTestImpl) {
        @Suppress("UNCHECKED_CAST")
        androidTestActions.executeActions(target as AndroidTest<AndroidTestProperties>)
    }

    // FIXME should be internal
    fun executeAndroidTestPropertiesActions(target: AndroidTestProperties) {
        androidTestPropertiesOperations.executeActions(target)
    }
}
