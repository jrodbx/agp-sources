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

import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.VariantSelector
import java.util.regex.Pattern

open class VariantSelectorImpl : VariantSelector {

    override fun all(): VariantSelectorImpl = this

    // By default the selector applies to all variants.
    internal open fun appliesTo(variant: ComponentIdentity): Boolean {
        return true;
    }

    override fun withBuildType(buildType: String): VariantSelectorImpl {
        return object: VariantSelectorImpl() {
            override fun appliesTo(variant: ComponentIdentity): Boolean {
                return buildType == variant.buildType && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>): VariantSelectorImpl {
        return object: VariantSelectorImpl() {
            override fun appliesTo(variant: ComponentIdentity): Boolean {
                return variant.productFlavors.contains(flavorToDimension) && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }

    override fun withFlavor(dimension: String, flavorName: String): VariantSelectorImpl {
        return withFlavor(dimension to flavorName)
    }

    override fun withName(pattern: Pattern): VariantSelectorImpl {
        return object : VariantSelectorImpl() {
            override fun appliesTo(variant: ComponentIdentity): Boolean {
                return pattern.matcher(variant.name).matches() && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }

    override fun withName(name: String): VariantSelectorImpl {
        return object : VariantSelectorImpl() {
            override fun appliesTo(variant: ComponentIdentity): Boolean {
                return variant.name == name && this@VariantSelectorImpl.appliesTo(variant)
            }
        }
    }
}
