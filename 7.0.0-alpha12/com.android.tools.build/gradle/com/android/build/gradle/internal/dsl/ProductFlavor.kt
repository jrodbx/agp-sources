/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.DynamicFeatureProductFlavor
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.TestProductFlavor
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.BaseConfig
import com.google.common.collect.ImmutableList
import javax.inject.Inject
import org.gradle.api.provider.Property

abstract class ProductFlavor @Inject constructor(name: String, dslServices: DslServices) :
    BaseFlavor(name, dslServices),
    ApplicationProductFlavor<SigningConfig>,
    DynamicFeatureProductFlavor,
    LibraryProductFlavor<SigningConfig>,
    TestProductFlavor<SigningConfig> {

    // FIXME remove: b/149431538
    @Suppress("DEPRECATION")
    private val _isDefaultProperty =
        dslServices.property(Boolean::class.java).convention(false)

    override var isDefault: Boolean
        get() = _isDefaultProperty.get()
        set(isDefault) = _isDefaultProperty.set(isDefault)

    override val matchingFallbacks: MutableList<String> = mutableListOf()

    fun setMatchingFallbacks(fallbacks: List<String>) {
        val newFallbacks = ArrayList(fallbacks)
        matchingFallbacks.clear()
        matchingFallbacks.addAll(newFallbacks)
    }

    fun setMatchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks.clear()
        for (fallback in fallbacks) {
            matchingFallbacks.add(fallback)
        }
    }

    fun setMatchingFallbacks(fallback: String) {
        matchingFallbacks.clear()
        matchingFallbacks.add(fallback)
    }

    fun setIsDefault(isDefault: Boolean) {
        this.isDefault = isDefault
    }

    fun getIsDefault(): Property<Boolean> {
        return this._isDefaultProperty
    }

    override fun computeRequestedAndFallBacks(requestedValues: List<String>): DimensionRequest { // in order to have different fallbacks per variant for missing dimensions, we are
        // going to actually have the flavor request itself (in the other dimension), with
        // a modified name (in order to not have collision in case 2 dimensions have the same
        // flavor names). So we will always fail to find the actual request and try for
        // the fallbacks.
        return DimensionRequest(
            VariantManager.getModifiedName(name),
            ImmutableList.copyOf(requestedValues)
        )
    }

    override fun _initWith(that: BaseConfig) { // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (this === that) {
            return
        }
        super._initWith(that)
        if (that is ProductFlavor) {
            setMatchingFallbacks(that.matchingFallbacks)
        }
    }
}
