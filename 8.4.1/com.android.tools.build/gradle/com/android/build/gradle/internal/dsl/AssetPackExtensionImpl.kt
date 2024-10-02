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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.AssetPackExtension
import com.android.build.api.dsl.DynamicDelivery
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class AssetPackExtensionImpl @Inject constructor(objectFactory: ObjectFactory) : AssetPackExtension{
    override val dynamicDelivery: DynamicDelivery = objectFactory.newInstance(
        DynamicDeliveryImpl::class.java)

    override fun dynamicDelivery(action: DynamicDelivery.() -> Unit) {
        action.invoke(dynamicDelivery)
    }

    fun dynamicDelivery(action: Action<DynamicDelivery>) {
        action.execute(dynamicDelivery)
    }
}