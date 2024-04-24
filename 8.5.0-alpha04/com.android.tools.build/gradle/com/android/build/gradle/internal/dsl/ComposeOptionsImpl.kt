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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ComposeOptions
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

open class ComposeOptionsImpl @Inject constructor(private val dslServices: DslServices) :
        ComposeOptions {
    override var kotlinCompilerVersion: String?
        get() = null
        set(s: String?) { dslServices.logger.warn("ComposeOptions.kotlinCompilerVersion is deprecated. Compose now uses the kotlin compiler defined in your buildscript.") }
    override var kotlinCompilerExtensionVersion: String? = null

    // Live Literal is now replaced by Live Edit.
    // Support for useLiveLiterals in the Compose compiler will be removed
    // in the future.
    override var useLiveLiterals: Boolean = false
}
