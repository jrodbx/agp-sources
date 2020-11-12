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

import com.android.build.api.component.AndroidTest
import com.android.build.api.component.AndroidTestBuilder
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.UnitTest
import com.android.build.api.component.UnitTestBuilder
import com.android.build.api.extension.AndroidComponentsExtension
import com.android.build.api.extension.VariantSelector
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action

abstract class AndroidComponentsExtensionImpl<VariantBuilderT: VariantBuilder, VariantT: Variant>(
        private val dslServices: DslServices,
        private val variantApiOperations: VariantApiOperationsRegistrar<VariantBuilderT, VariantT>
): AndroidComponentsExtension<VariantBuilderT, VariantT> {

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
            dslServices.newInstance(VariantSelectorImpl::class.java) as VariantSelectorImpl

    override fun beforeUnitTest(
            selector: VariantSelector,
            callback: Action<UnitTestBuilder>) {
        variantApiOperations.unitTestBuilderOperations.addOperation(
                callback,
                selector)
    }

    override fun beforeUnitTest(
            selector: VariantSelector,
            callback: (UnitTestBuilder) -> Unit) {
        variantApiOperations.unitTestBuilderOperations.addOperation(
                {
                    callback.invoke(it)
                },
                selector
        )
    }

    override fun beforeAndroidTest(
            selector: VariantSelector,
            callback: Action<AndroidTestBuilder>) {
        variantApiOperations.androidTestBuilderOperations.addOperation(
                callback,
                selector
        )
    }

    override fun beforeAndroidTest(
            selector: VariantSelector,
            callback: (AndroidTestBuilder) -> Unit) {
        variantApiOperations.androidTestBuilderOperations.addOperation(
                {
                    callback.invoke(it)
                },
                selector
        )
    }

    override fun unitTest(
            selector: VariantSelector,
            callback: Action<UnitTest>) {
        variantApiOperations.unitTestOperations.addOperation(
                callback,
                selector
        )
    }

    override fun unitTest(
            selector: VariantSelector,
            callback: (UnitTest) -> Unit) {
        variantApiOperations.unitTestOperations.addOperation(
                {
                    callback.invoke(it)
                },
                selector
        )
    }

    override fun androidTest(
            selector: VariantSelector,
            callback: Action<AndroidTest>) {
        variantApiOperations.androidTestOperations.addOperation(
                callback,
                selector
        )
    }

    override fun androidTest(
            selector: VariantSelector,
            callback: (AndroidTest) -> Unit) {
        variantApiOperations.androidTestOperations.addOperation(
                {
                    callback.invoke(it)
                },
                selector
        )
    }
}
