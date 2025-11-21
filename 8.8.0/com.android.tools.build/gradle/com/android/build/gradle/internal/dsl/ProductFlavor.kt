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

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.DynamicFeatureProductFlavor
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.TestProductFlavor
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.BaseConfig
import com.google.common.collect.ImmutableList
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ProductFlavor @Inject constructor(name: String, dslServices: DslServices) :
    BaseFlavor(name, dslServices),
    VariantDimensionBinaryCompatibilityFix,
    ApplicationProductFlavor,
    DynamicFeatureProductFlavor,
    LibraryProductFlavor,
    TestProductFlavor {

    // FIXME remove: b/149431538
    @Suppress("DEPRECATION")
    private val _isDefaultProperty =
        dslServices.property(Boolean::class.java).convention(false)

    override var isDefault: Boolean
        get() = _isDefaultProperty.get()
        set(isDefault) = _isDefaultProperty.set(isDefault)

    override val matchingFallbacks: MutableList<String> = mutableListOf()

    override fun setMatchingFallbacks(fallbacks: List<String>) {
        val newFallbacks = ArrayList(fallbacks)
        matchingFallbacks.clear()
        matchingFallbacks.addAll(newFallbacks)
    }

    override fun setMatchingFallbacks(vararg fallbacks: String) {
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

    override var signingConfig: ApkSigningConfig?
        get() = super.signingConfig
        set(value) { super.signingConfig = value }

    override fun _internal_getSigingConfig(): ApkSigningConfig? {
        return signingConfig
    }

    abstract var _dimension: String?

    // The DimensionCombinator initializes the flavor dimension in cases where it is unset,
    // as later configuration expects it to always be non-null, but it does this after the DSL is
    // locked, so if it sets it directly it will fail, so this indirection is added to support
    // overriding a null value after the DSL is locked.
    // Once the use of DSL objects is cleaned up a bit more this might be able to be removed.
    internal var internalDimensionDefault: String? = null
        set(value) {
            check(dimension == null) { "Default should only be set if the dimension is unset" }
            field = value
        }

    override var dimension: String?
        get() = _dimension ?: internalDimensionDefault
        set(value) { _dimension = value }

    override fun computeRequestedAndFallBacks(requestedValues: List<String>): DimensionRequest {
        // in order to have different fallbacks per variant for missing dimensions, we are
        // going to actually have the flavor request itself (in the other dimension).
        // So we will always fail to find the actual request and try for
        // the fallbacks.
        return DimensionRequest(name, ImmutableList.copyOf(requestedValues))
    }

    override fun _initWith(that: BaseConfig) { // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (this === that) {
            return
        }
        super._initWith(that)
        if (that is ProductFlavor) {
            signingConfig = that.signingConfig
            setMatchingFallbacks(that.matchingFallbacks)
        }
        if (that is ExtensionAware) {
            initExtensions(from = that, to = this)
        }
    }
}
