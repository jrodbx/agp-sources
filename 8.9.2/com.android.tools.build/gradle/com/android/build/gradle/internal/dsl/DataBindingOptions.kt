/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import java.util.function.Supplier
import javax.inject.Inject

/** DSL object for configuring databinding options. */
abstract class DataBindingOptions @Inject @WithLazyInitialization("lateInit") constructor(
    private val featuresProvider: Supplier<BuildFeatures>,
    private val dslServices: DslServices
) : com.android.builder.model.DataBindingOptions, com.android.build.api.dsl.DataBinding {
    override var enable: Boolean
        get() {
            return when (val buildFeatures = featuresProvider.get()) {
                is ApplicationBuildFeatures -> buildFeatures.dataBinding
                is LibraryBuildFeatures -> buildFeatures.dataBinding
                is DynamicFeatureBuildFeatures -> buildFeatures.dataBinding
                else -> false
            } ?: dslServices.projectOptions.get(BooleanOption.BUILD_FEATURE_DATABINDING)
        }
        set(value) {
            when (val buildFeatures = featuresProvider.get()) {
                is ApplicationBuildFeatures -> buildFeatures.dataBinding = value
                is LibraryBuildFeatures -> buildFeatures.dataBinding = value
                is DynamicFeatureBuildFeatures -> buildFeatures.dataBinding = value
                else -> dslServices.logger
                    .warn("dataBinding.setEnabled has no impact on this sub-project type")
            }
        }

    override var isEnabled: Boolean
        get() = enable
        set(value) {
            enable = value
        }

    override var isEnabledForTests: Boolean
        get() = enableForTests
        set(value) {
            enableForTests = value
        }

    @Suppress("unused") // call injected in the constructor by the dsl decorator
    protected fun lateInit() {
        addDefaultAdapters = true
    }
}
