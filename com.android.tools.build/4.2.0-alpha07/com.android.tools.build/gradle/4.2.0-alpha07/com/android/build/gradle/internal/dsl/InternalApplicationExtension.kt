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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.coverage.JacocoOptions

/** See [InternalCommonExtension] */
interface InternalApplicationExtension :
    ApplicationExtension<
        AndroidSourceSet,
        BuildType,
        DefaultConfig,
        ProductFlavor,
        SigningConfig>,
        InternalCommonExtension<
                ApplicationBuildFeatures,
                BuildType,
                DefaultConfig,
                ProductFlavor,
                ApplicationVariant<ApplicationVariantProperties>,
                ApplicationVariantProperties> {
    override var dynamicFeatures: MutableSet<String>
    override var assetPacks: MutableSet<String>
}