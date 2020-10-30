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

import com.android.build.api.component.ActionableComponentObject
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.extension.FilteredVariantSelector
import com.android.build.api.extension.GenericVariantSelector
import com.android.build.api.extension.VariantSelector
import java.util.regex.Pattern

open class VariantSelectorImpl<ComponentT>
    : GenericVariantSelector<ComponentT>
        where ComponentT: ComponentIdentity {

    override fun all(): VariantSelector<ComponentT> = this

    // By default the selector applies to all variants.
    internal open fun appliesTo(variant: ComponentT): Boolean {
        return true;
    }

    override fun <NewTypeT : ComponentT> withType(newType: Class<NewTypeT>): FilteredVariantSelector<NewTypeT> {
        return object: VariantSelectorImpl<NewTypeT>() {
            override fun appliesTo(variant: NewTypeT): Boolean {
                return newType.isInstance(variant) && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }

    override fun withBuildType(buildType: String): FilteredVariantSelector<ComponentT> {
        return object: VariantSelectorImpl<ComponentT>() {
            override fun appliesTo(variant: ComponentT): Boolean {
                return buildType == variant.buildType && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>): FilteredVariantSelector<ComponentT> {
        return object: VariantSelectorImpl<ComponentT>() {
            override fun appliesTo(variant: ComponentT): Boolean {
                return variant.productFlavors.contains(flavorToDimension) && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }

    override fun withName(pattern: Pattern): VariantSelector<ComponentT> {
        return object : VariantSelectorImpl<ComponentT>() {
            override fun appliesTo(variant: ComponentT): Boolean {
                return pattern.matcher(variant.name).matches() && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }
}
