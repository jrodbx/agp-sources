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

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.extension.AndroidComponentsExtension
import com.android.build.api.extension.VariantSelector
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action

abstract class AndroidComponentsExtensionImpl<VariantBuilderT: VariantBuilder, VariantT: Variant>(
        private val dslServices: DslServices,
        private val variantBuilderOperations: OperationsRegistrar<VariantBuilderT>,
        private val variantOperations: OperationsRegistrar<VariantT>
): AndroidComponentsExtension<VariantBuilderT, VariantT> {

    override fun beforeVariants(selector: VariantSelector<VariantBuilderT>, callback: (VariantBuilderT) -> Unit) {
        variantBuilderOperations.addOperation(selector, Action {
            callback.invoke(it)
        })
    }

    override fun beforeVariants(selector: VariantSelector<VariantBuilderT>, callback: Action<VariantBuilderT>) {
        variantBuilderOperations.addOperation(selector, callback)
    }

    override fun onVariants(selector: VariantSelector<VariantT>, callback: (VariantT) -> Unit) {
        variantOperations.addOperation(selector, Action {
            callback.invoke(it)
        })
    }

    override fun onVariants(selector: VariantSelector<VariantT>, callback: Action<VariantT>) {
        variantOperations.addOperation(selector, callback)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: ComponentIdentity> selector(): VariantSelectorImpl<T> =
            dslServices.newInstance(VariantSelectorImpl::class.java) as VariantSelectorImpl<T>
}
