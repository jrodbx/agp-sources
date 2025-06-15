/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.dsl.TestFixtures
import com.android.build.api.dsl.TestedExtension
import org.gradle.api.Action

/** See [InternalCommonExtension] */
interface InternalTestedExtension<BuildFeaturesT : com.android.build.api.dsl.BuildFeatures,
        BuildTypeT : com.android.build.api.dsl.BuildType,
        DefaultConfigT : com.android.build.api.dsl.DefaultConfig,
        ProductFlavorT : com.android.build.api.dsl.ProductFlavor,
        AndroidResourcesT : com.android.build.api.dsl.AndroidResources,
        InstallationT: com.android.build.api.dsl.Installation>
    : TestedExtension,
    InternalCommonExtension<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidResourcesT, InstallationT> {
    fun testFixtures(action: Action<TestFixtures>)
}
