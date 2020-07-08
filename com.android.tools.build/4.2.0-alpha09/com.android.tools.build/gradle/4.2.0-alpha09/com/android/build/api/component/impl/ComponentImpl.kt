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

package com.android.build.api.component.impl

import com.android.build.api.component.Component
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.ComponentProperties
import com.android.build.api.variant.impl.DelayedActionExecutor
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.VariantApiServices
import org.gradle.api.Action

abstract class ComponentImpl<PropertiesT : ComponentProperties>(
    protected val variantDslInfo: VariantDslInfo,
    variantConfiguration: ComponentIdentity,
    protected val variantApiServices: VariantApiServices
) :
    Component<PropertiesT>, ComponentIdentity by variantConfiguration {

    protected val propertiesActions = DelayedActionExecutor<PropertiesT>()

    override var enabled: Boolean = true

    override fun onProperties(action: PropertiesT.() -> Unit) {
        propertiesActions.registerAction(Action { action(it) })
    }

    fun onProperties(action: Action<in PropertiesT>) {
        propertiesActions.registerAction(action)
    }

    // FIXME should be internal
    abstract fun executePropertiesActions(target: PropertiesT)
}