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

package com.android.build.api.extension.impl

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.api.variant.VariantSelector
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtension
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action

abstract class AndroidComponentsExtensionImpl<
        DslExtensionT: CommonExtension<*, *, *, *>,
        VariantBuilderT: VariantBuilder,
        VariantT: Variant>(
        private val dslServices: DslServices,
        override val sdkComponents: SdkComponents,
        private val variantApiOperations: VariantApiOperationsRegistrar<DslExtensionT, VariantBuilderT, VariantT>,
        private val commonExtension: DslExtensionT
): AndroidComponentsExtension<DslExtensionT, VariantBuilderT, VariantT> {

    override fun finalizeDsl(callback: (DslExtensionT) -> Unit) {
        variantApiOperations.add {
            callback.invoke(it)
        }
    }

    override fun finalizeDsl(callback: Action<DslExtensionT>) {
        variantApiOperations.add(callback)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun finalizeDSl(callback: Action<DslExtensionT>) {
        variantApiOperations.add(callback)
    }

    override val pluginVersion: AndroidPluginVersion
        get() = CURRENT_AGP_VERSION

    override fun beforeVariants(selector: VariantSelector, callback: (VariantBuilderT) -> Unit) {
        variantApiOperations.variantBuilderOperations.addOperation({
            callback.invoke(it)
        }, selector)
    }

    override fun beforeVariants(selector: VariantSelector, callback: Action<VariantBuilderT>) {
        variantApiOperations.variantBuilderOperations.addOperation(callback, selector)
    }

    override fun onVariants(selector: VariantSelector, callback: (VariantT) -> Unit) {
        variantApiOperations.variantOperations.addOperation({
            callback.invoke(it)
        }, selector)
    }

    override fun onVariants(selector: VariantSelector, callback: Action<VariantT>) {
        variantApiOperations.variantOperations.addOperation(callback, selector)
    }

    override fun selector(): VariantSelectorImpl =
            dslServices.newInstance(VariantSelectorImpl::class.java)

    class RegisteredApiExtension<VariantT: Variant>(
        val dslExtensionTypes: DslExtension,
        val configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension
    )

    override fun registerExtension(
        dslExtension: DslExtension,
        configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension
    ) {
        variantApiOperations.dslExtensions.add(
            RegisteredApiExtension(
                dslExtensionTypes = dslExtension,
                configurator = configurator
        ))

        dslExtension.buildTypeExtensionType?.let {
            commonExtension.buildTypes.forEach {
                    buildType ->
                buildType.extensions.add(
                    dslExtension.dslName,
                    it
                )
            }
        }
        dslExtension.productFlavorExtensionType?.let {
            commonExtension.productFlavors.forEach {
                productFlavor -> productFlavor.extensions.add(
                    dslExtension.dslName,
                    it
                )
            }
        }
    }

    override fun registerSourceType(name: String) {
        variantApiOperations.sourceSetExtensions.add(name)
    }
}
