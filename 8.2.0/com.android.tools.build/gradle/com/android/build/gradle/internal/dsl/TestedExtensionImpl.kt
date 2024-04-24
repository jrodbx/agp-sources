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

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.TestFixtures
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.Action

/** Internal implementation of the 'new' DSL interface */
abstract class TestedExtensionImpl<
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
        AndroidResourcesT : AndroidResources>(
            dslServices: DslServices,
            dslContainers: DslContainerProvider<DefaultConfigT, BuildTypeT, ProductFlavorT, SigningConfig>
        ) : CommonExtensionImpl<
        BuildFeaturesT,
        BuildTypeT,
        DefaultConfigT,
        ProductFlavorT,
        AndroidResourcesT>(
    dslServices,
    dslContainers
), com.android.build.api.dsl.TestedExtension {
    override var testBuildType = "debug"
    override var testNamespace: String? = null

    override val testFixtures: TestFixtures =
        dslServices.newInstance(
            TestFixturesImpl::class.java,
            dslServices.projectOptions[BooleanOption.ENABLE_TEST_FIXTURES]
        )

    override fun testFixtures(action: TestFixtures.() -> Unit) {
        action.invoke(testFixtures)
    }

    fun testFixtures(action: Action<TestFixtures>) {
        action.execute(testFixtures)
    }
}
