/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.extension.impl

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import org.gradle.api.Action

open class KotlinMultiplatformAndroidComponentsExtensionImpl(
    override val sdkComponents: SdkComponents,
    override val managedDeviceRegistry: ManagedDeviceRegistry,
    private val variantApiOperations: MultiplatformVariantApiOperationsRegistrar,
): KotlinMultiplatformAndroidComponentsExtension {
    override val pluginVersion: AndroidPluginVersion
        get() = CurrentAndroidGradlePluginVersion.CURRENT_AGP_VERSION

    override fun finalizeDsl(callback: (KotlinMultiplatformAndroidExtension) -> Unit) {
        variantApiOperations.add {
            callback.invoke(it)
        }
    }

    override fun finalizeDsl(callback: Action<KotlinMultiplatformAndroidExtension>) {
        variantApiOperations.add(callback)
    }

    override fun onVariant(callback: (KotlinMultiplatformAndroidVariant) -> Unit) {
        variantApiOperations.variantOperations.addOperation {
            callback.invoke(it)
        }
    }

    override fun onVariant(callback: Action<KotlinMultiplatformAndroidVariant>) {
        variantApiOperations.variantOperations.addOperation(callback)
    }
}

