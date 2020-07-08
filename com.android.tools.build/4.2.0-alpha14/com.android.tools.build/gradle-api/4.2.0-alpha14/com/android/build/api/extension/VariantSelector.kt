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

package com.android.build.api.extension

import com.android.build.api.component.BuildTypedComponentActionRegistrar
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.FlavoredComponentActionRegistrar
import org.gradle.api.Action
import org.gradle.api.Incubating
import java.util.regex.Pattern

/**
 * Selector to reduce the number of variants that are of interests when calling any of the
 * variant API like [AndroidComponentsExtension.beforeVariants].
 */
@Incubating
interface VariantSelector {
    /**
     * Creates a [VariantSelector] of [ComponentIdentity]that includes all the variants for the
     * current module.
     *
     * @return a [VariantSelector] for all variants.
     */
    fun all(): VariantSelector

    /**
     * Returns a new selector for [ComponentIdentity] objects with a given build type.
     *
     * @param buildType Build type to filter [ComponentIdentity] on.
     * @return An instance of [BuildTypedComponentActionRegistrar] to further filter variants.
     */
    fun withBuildType(buildType: String): VariantSelector

    /**
     * Returns a new selector for [ComponentIdentity] objects with a given (dimension, flavorName).
     *
     * @param flavorToDimension Dimension and flavor to filter [ComponentIdentity] on.
     * @return [FlavoredComponentActionRegistrar] instance to further filter instances of [ComponentIdentity]
     */
    fun withFlavor(flavorToDimension: Pair<String, String>): VariantSelector

    /**
     * Registers an [Action] for [ComponentIdentity] objects with a given name pattern.
     *
     * @param pattern [Pattern] to apply on the [org.gradle.api.Named.getName] to filter [ComponentIdentity]
     * instances on
     */
    fun withName(pattern: Pattern): VariantSelector
}


