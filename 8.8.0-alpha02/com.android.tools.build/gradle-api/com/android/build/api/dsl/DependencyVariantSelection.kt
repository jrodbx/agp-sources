/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.dsl

import org.gradle.api.Incubating
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty

/**
 * Specifies options for doing variant selection for external Android dependencies
 * based on build types and product flavours
 */
@Incubating
interface DependencyVariantSelection {
    /**
     * Specifies a list of build types that the plugin should try to use when a
     * direct variant match with a dependency is not possible.
     *
     * If the list is left empty, the default variant for the dependencies being
     * consumed will be of build type "debug"
     */
    @get:Incubating
    val buildTypes: ListProperty<String>

    /**
     * Specifies a map of product flavors that the plugin should try to use when a
     * direct variant match with a dependency is not possible.
     */
    @get:Incubating
    val productFlavors: MapProperty<String, List<String>>
}
