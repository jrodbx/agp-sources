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

package com.android.build.gradle.internal.variant

import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig

/**
 * Model for the variants and their inputs.
 *
 * Can also compute the default variant to be used during sync.
 */
interface VariantModel {

    val inputs: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>

    /**
     * the main variants. This is the output of the plugin (apk, aar, etc...) and does not
     * include the test components (android test, unit test)
     */
    val variants: List<VariantImpl>

    /**
     * the test components (android test, unit test)
     */
    val testComponents: List<TestComponentImpl>

    val defaultVariant: String?
}
