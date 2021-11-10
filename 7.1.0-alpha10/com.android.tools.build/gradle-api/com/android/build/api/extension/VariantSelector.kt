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

@file:Suppress("DEPRECATION")

package com.android.build.api.extension

import com.android.build.api.component.ComponentIdentity
import java.util.regex.Pattern

@Deprecated(
    message= "Use the com.android.build.api.variant package",
    replaceWith = ReplaceWith(
        "VariantSelector",
        "com.android.build.api.variant.VariantSelector"),
    level = DeprecationLevel.WARNING
)
interface VariantSelector: com.android.build.api.variant.VariantSelector {
    /**
     * Creates a [VariantSelector] of [ComponentIdentity] that includes all the variants for the
     * current module.
     *
     * @return a [VariantSelector] for all variants.
     */
    override fun all(): VariantSelector

    /**
     * Returns a new selector for [ComponentIdentity] objects with a given build type.
     *
     * @param buildType Build type to filter [ComponentIdentity] on.
     * @return An instance of [VariantSelector] to further filter variants.
     */
    override fun withBuildType(buildType: String): VariantSelector

    /**
     * Returns a new selector for [ComponentIdentity] objects with a given (dimension, flavorName).
     *
     * @param flavorToDimension Dimension and flavor to filter [ComponentIdentity] on.
     * @return [VariantSelector] instance to further filter instances of [ComponentIdentity]
     */
    override fun withFlavor(flavorToDimension: Pair<String, String>): VariantSelector

    /**
     * Returns a new selector for [ComponentIdentity]  objects with a given name pattern.
     *
     * @param pattern [Pattern] to apply on the [org.gradle.api.Named.getName] to filter [ComponentIdentity]
     * instances on
     */
    override fun withName(pattern: Pattern): VariantSelector

    /**
     * Returns a new selector for [ComponentIdentity]  objects with a given name.
     *
     * @param name [String] to test against the [org.gradle.api.Named.getName] for equality.
     */
    override fun withName(name: String): VariantSelector
}
