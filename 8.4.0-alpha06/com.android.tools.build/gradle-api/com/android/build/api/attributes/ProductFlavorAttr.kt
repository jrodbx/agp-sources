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

package com.android.build.api.attributes

import org.gradle.api.Incubating
import org.gradle.api.attributes.Attribute

import org.gradle.api.Named

/**
 * Type for the attribute holding ProductFlavor information.
 *
 *
 * There can be more than one attribute associated to each
 * [org.gradle.api.artifacts.Configuration] object, where each represents a different flavor
 * dimension.
 *
 * The key should be created with `ProductFlavorAttr.of(flavorDimension)`.
 *
 */
interface ProductFlavorAttr : Named {
    @Incubating
    companion object {
        /**
         * Returns a product flavor attribute for the given flavor dimension
         *
         * @param flavorDimension The name of the flavor dimension, as specified in the Android
         *                        Gradle Plugin DSL.
         */
        @Incubating
        @JvmStatic
        fun of(flavorDimension: String) : Attribute<ProductFlavorAttr> {
            return Attribute.of("com.android.build.api.attributes.ProductFlavor:$flavorDimension", ProductFlavorAttr::class.java)
        }
    }
}
