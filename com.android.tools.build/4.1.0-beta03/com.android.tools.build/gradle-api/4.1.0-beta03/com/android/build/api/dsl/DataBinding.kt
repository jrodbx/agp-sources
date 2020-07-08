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

import org.gradle.api.Incubating

/**
 * DSL object for configuring databinding options.
 */
@Incubating
interface DataBinding {
    /** The version of data binding to use. */
    var version: String?

    /** Whether to add the default data binding adapters. */
    var addDefaultAdapters: Boolean

    /** Whether to run data binding code generation for test projects. */
    var isEnabledForTests: Boolean

    /** Whether to enable data binding. */
    @Deprecated("use android.features.databinding")
    var isEnabled: Boolean
}