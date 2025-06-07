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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object used to configure `vector` drawable options.
 */
interface VectorDrawables {
    /**
     * Densities used when generating PNGs from vector drawables at build time. For the PNGs to be
     * generated, minimum SDK has to be below 21.
     *
     * If set to an empty collection, all special handling of vector drawables will be
     * disabled.
     *
     * See
     * [Supporting Multiple Screens](http://developer.android.com/guide/practices/screens_support.html).
     */
    @get:Incubating
    val generatedDensities: MutableSet<String>?

    /**
     * Densities used when generating PNGs from vector drawables at build time. For the PNGs to be
     * generated, minimum SDK has to be below 21.
     *
     * If set to an empty collection, all special handling of vector drawables will be
     * disabled.
     *
     * See
     * [Supporting Multiple Screens](http://developer.android.com/guide/practices/screens_support.html).
     */
    @Incubating
    fun generatedDensities(vararg densities: String)

    /**
     * Whether to use runtime support for `vector` drawables, instead of build-time support.
     *
     * See [Vector Asset Studio](http://developer.android.com/tools/help/vector-asset-studio.html).
     */
    var useSupportLibrary: Boolean?
}
