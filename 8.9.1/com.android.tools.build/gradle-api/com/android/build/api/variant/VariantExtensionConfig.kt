/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Configuration object passed to the lambda responsible for creating a
 * [com.android.build.api.variant.VariantExtension] for each [com.android.build.api.variant.Variant]
 * instance.
 *
 * @param VariantT the type of [com.android.build.api.variant.Variant] object.
 */
@Incubating
interface VariantExtensionConfig<VariantT: Variant> {

    /**
     * Gets the variant object the [com.android.build.api.variant.VariantExtension] should be
     * associated with.
     */
    val variant: VariantT

    /**
     * Returns the project (across variants) extension registered through the
     * [com.android.build.api.extension.DslExtension.projectExtensionType] API.
     */
    fun <T> projectExtension(extensionType: Class<T>): T

    /**
     * Returns the [variant] specific extension registered through the
     * [com.android.build.api.extension.DslExtension.buildTypeExtensionType] API.
     *
     * @return the custom extension for the [variant]'s build type.
     */
    fun <T> buildTypeExtension(extensionType: Class<T>): T

    /**
     * Returns the [variant] specific extension registered through the
     * [com.android.build.api.extension.DslExtension.productFlavorExtensionType] API.
     *
     * @return a [List] of [T] extension for all the defined product flavors in the project.
     * The order of the elements is the same as the order of product flavors returned by the
     * [Variant.productFlavors]
     */
    fun <T> productFlavorsExtensions(extensionType: Class<T>): List<T>
}
