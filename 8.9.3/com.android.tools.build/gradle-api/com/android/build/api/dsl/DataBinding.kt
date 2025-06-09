/*
 * Copyright (C) 2019 The Android Open Source Project
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

/**
 * DSL object for configuring databinding options.
 */
interface DataBinding {
    /** The version of data binding to use. */
    var version: String?

    /** Whether to add the default data binding adapters. */
    var addDefaultAdapters: Boolean

    /**
     * Whether to add the data binding KTX features.
     * A null value means that the user hasn't specified any value in the DSL.
     * The default value can be tweaked globally using the
     * `android.defaults.databinding.addKtx` gradle property.
     */
    var addKtx: Boolean?

    @Deprecated("deprecated, use enableForTests", ReplaceWith("enableForTests"))
    var isEnabledForTests: Boolean

    /** Whether to run data binding code generation for test projects. */
    var enableForTests: Boolean

    @Deprecated("deprecated, use enable", ReplaceWith("enable"))
    var isEnabled: Boolean

    /** Whether to enable data binding. */
    var enable: Boolean
}
