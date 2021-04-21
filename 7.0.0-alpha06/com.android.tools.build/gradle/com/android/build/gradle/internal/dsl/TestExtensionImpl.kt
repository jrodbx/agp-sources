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

import com.android.build.api.dsl.TestBuildFeatures
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantBuilder
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices

/** Internal implementation of the 'new' DSL interface */
class TestExtensionImpl(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
) :
    CommonExtensionImpl<
            TestBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            TestVariantBuilder,
            TestVariant>(
        dslServices,
        dslContainers
    ),
    InternalTestExtension {

    override val buildFeatures: TestBuildFeatures =
        dslServices.newInstance(TestBuildFeaturesImpl::class.java)

    override var targetProjectPath: String? = null
}
