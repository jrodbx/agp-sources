/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.api

import com.android.build.api.variant.VariantFilter
import com.android.build.gradle.internal.core.dsl.impl.computeName
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.builder.core.ComponentType
import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor

/**
 * Exposes a read-only view of a variant as well as a flag that can be used to signal that the
 * variant should be ignored.
 */
class VariantFilter(
    private val readOnlyObjectProvider: ReadOnlyObjectProvider
) : VariantFilter {

    private lateinit var _dimensionCombination: DimensionCombination
    private lateinit var _defaultConfig: ProductFlavor
    private lateinit var _buildType: BuildType
    private var _flavors: List<ProductFlavor>? = null
    private lateinit var _type: ComponentType
    private var _name: String? = null

    fun reset(
        dimensionCombination: DimensionCombination,
        defaultConfig: ProductFlavor,
        buildType: BuildType,
        type: ComponentType,
        flavors: List<ProductFlavor>?
    ) {
        ignore = false
        _dimensionCombination = dimensionCombination
        _defaultConfig = defaultConfig
        _buildType = buildType
        _flavors = flavors
        _type = type
        _name = null
    }

    override var ignore: Boolean = false

    override val defaultConfig: ProductFlavor
        get() = readOnlyObjectProvider.getDefaultConfig(_defaultConfig)

    override val buildType: BuildType
        get() = readOnlyObjectProvider.getBuildType(_buildType)

    override val flavors: List<ProductFlavor>
        get() {
            val fList = _flavors ?: return emptyList()
            return ImmutableFlavorList(fList, readOnlyObjectProvider)
        }

    override val name: String
        get() {
            val currentName = _name
            if (currentName == null) {
                val newName = computeName(_dimensionCombination, _type)
                _name = newName
                return newName
            }
            return currentName
        }
}
