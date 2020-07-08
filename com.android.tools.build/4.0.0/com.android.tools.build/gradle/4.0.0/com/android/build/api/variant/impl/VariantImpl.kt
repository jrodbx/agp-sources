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
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.internal.core.VariantDslInfo
import org.gradle.api.Action

abstract class VariantImpl<VariantPropertiesT: VariantProperties>(
    variantDslInfo: VariantDslInfo,
    componentIdentity: ComponentIdentity
):
    ComponentImpl<VariantPropertiesT>(componentIdentity), Variant<VariantPropertiesT> {

    private val unitTestActions = DelayedActionExecutor<UnitTest<UnitTestProperties>>()
    private val androidTestActions = DelayedActionExecutor<AndroidTest<AndroidTestProperties>>()

    override var minSdkVersion = variantDslInfo.minSdkVersion.apiLevel

    override fun unitTest(action: UnitTest<UnitTestProperties>.() -> Unit) {
        unitTestActions.registerAction(Action { action(it) })
    }

    fun unitTest(action: Action<UnitTest<UnitTestProperties>>) {
        unitTestActions.registerAction(action)
    }

    override fun androidTest(action: AndroidTest<AndroidTestProperties>.() -> Unit) {
        androidTestActions.registerAction(Action { action(it) })
    }

    fun androidTest(action: Action<AndroidTest<AndroidTestProperties>>) {
        androidTestActions.registerAction(action)
    }

    // FIXME should be internal
    fun executeUnitTestActions(target: UnitTest<UnitTestProperties>) {
        unitTestActions.executeActions(target)
    }

    // FIXME should be internal
    fun executeAndroidTestActions(target: AndroidTest<AndroidTestProperties>) {
        androidTestActions.executeActions(target)
    }
}
