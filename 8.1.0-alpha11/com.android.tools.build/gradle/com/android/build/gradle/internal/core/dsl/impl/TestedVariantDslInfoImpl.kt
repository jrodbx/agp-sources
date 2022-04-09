/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.TestFixtures
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.dsl.TestedVariantDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestedExtension
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import org.gradle.api.file.DirectoryProperty

internal abstract class TestedVariantDslInfoImpl internal constructor(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    dataProvider: ManifestDataProvider,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    extension: InternalTestedExtension<*, *, *, *, *>
) : VariantDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    dataProvider,
    services,
    buildDirectory,
    extension
), TestedVariantDslInfo {

    override val testFixtures: TestFixtures = extension.testFixtures

    override val testInstrumentationRunnerArguments: Map<String, String>
        get() = mergedFlavor.testInstrumentationRunnerArguments
}
