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

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.android.build.api.variant.KotlinMultiplatformAndroidVariantBuilder
import com.android.build.api.variant.VariantSelector
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import javax.inject.Inject

open class KotlinMultiplatformAndroidComponentsExtensionImpl@Inject constructor(
    dslServices: DslServices,
    sdkComponents: SdkComponents,
    managedDeviceRegistry: ManagedDeviceRegistry,
    private val variantApiOperations: VariantApiOperationsRegistrar<KotlinMultiplatformAndroidLibraryExtension, KotlinMultiplatformAndroidVariantBuilder, KotlinMultiplatformAndroidVariant>,
    kmpExtension: KotlinMultiplatformAndroidLibraryExtension
) : KotlinMultiplatformAndroidComponentsExtension,
    AndroidComponentsExtensionImpl<KotlinMultiplatformAndroidLibraryExtension, KotlinMultiplatformAndroidVariantBuilder, KotlinMultiplatformAndroidVariant>(
        dslServices,
        sdkComponents,
        managedDeviceRegistry,
        variantApiOperations,
        kmpExtension
    ) {

    override fun onVariant(callback: (KotlinMultiplatformAndroidVariant) -> Unit) {
        variantApiOperations.variantOperations
            .addPublicOperation({ callback.invoke(it) }, "onVariant")
    }

    override fun onVariant(callback: Action<KotlinMultiplatformAndroidVariant>) {
        variantApiOperations.variantOperations.addPublicOperation(callback, "onVariant")
    }

    override fun beforeVariants(
        selector: VariantSelector,
        callback: (KotlinMultiplatformAndroidVariantBuilder) -> Unit
    ) {
        throw RuntimeException("not supported yet")
    }

    override fun beforeVariants(
        selector: VariantSelector,
        callback: Action<KotlinMultiplatformAndroidVariantBuilder>
    ) {
        throw RuntimeException("not supported yet")
    }

    override fun addSourceSetConfigurations(suffix: String) {
        throw RuntimeException("Kotlin multiplatform Variant API does not support addSourceSetConfigurations() yet")
    }

    override fun addKspConfigurations(useGlobalConfiguration: Boolean) {
        throw RuntimeException("Kotlin multiplatform Variant API does not support addKspConfigurations() yet")
    }
}
