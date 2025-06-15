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

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.ViewBinding
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import java.util.function.Supplier
import javax.inject.Inject

/** DSL object for configuring view binding options.  */
abstract class ViewBindingOptionsImpl @Inject constructor(
    private val features: Supplier<BuildFeatures>,
    private val dslServices: DslServices
) : ViewBindingOptions, ViewBinding {

    override var enable: Boolean
        get() {
            val bool = features.get().viewBinding
            if (bool != null) {
                return bool
            }
            return dslServices.projectOptions[BooleanOption.BUILD_FEATURE_VIEWBINDING]
        }
        set(value) {
            features.get().viewBinding = value
        }

    /** Whether to enable view binding.  */
    override var isEnabled: Boolean
        get() = enable
        set(value) {
            enable = value
        }
}
