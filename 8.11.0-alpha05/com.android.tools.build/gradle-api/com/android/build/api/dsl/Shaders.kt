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

import com.google.common.collect.ListMultimap

/**
 * Options for configuring scoped shader options.
 */
interface Shaders {
    /**
     * The list of glslc args.
     */
    val glslcArgs: MutableList<String>

    /**
     * Adds options to the list of glslc args.
     */
    fun glslcArgs(vararg options: String)

    /**
     * The list of scoped glsl args.
     */
    val scopedGlslcArgs: ListMultimap<String, String>

    /**
     * Adds options to the list of scoped glsl args.
     */
    fun glslcScopedArgs(key: String, vararg options: String)
}
