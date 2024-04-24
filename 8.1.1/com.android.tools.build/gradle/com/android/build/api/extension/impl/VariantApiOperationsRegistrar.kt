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

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder

/**
 * Holder of various [OperationsRegistrar] for all the variant API related operations to a plugin.
 */
class VariantApiOperationsRegistrar<CommonExtensionT: CommonExtension<*, *, *, *, *>, VariantBuilderT: VariantBuilder, VariantT: Variant>(
        extension: CommonExtensionT,
) : DslLifecycleComponentsOperationsRegistrar<CommonExtensionT>(extension) {

    internal val variantBuilderOperations = OperationsRegistrar<VariantBuilderT>()
    internal val variantOperations = OperationsRegistrar<VariantT>()
    internal val dslExtensions = mutableListOf<AndroidComponentsExtensionImpl.RegisteredApiExtension<VariantT>>()
    internal val sourceSetExtensions = mutableListOf<String>()

    fun onEachSourceSetExtensions(action: (name: String) -> Unit) {
        sourceSetExtensions.forEach(action)
    }
}
