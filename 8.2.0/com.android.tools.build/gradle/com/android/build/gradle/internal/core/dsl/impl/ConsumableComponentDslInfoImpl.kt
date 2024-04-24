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
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.dsl.ConsumableComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.BuildConfigDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.core.dsl.features.RenderscriptDslInfo
import com.android.build.gradle.internal.core.dsl.features.ShadersDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.BuildConfigDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.ManifestPlaceholdersDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.OptimizationDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.RenderscriptDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.ShadersDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import org.gradle.api.file.DirectoryProperty

internal abstract class ConsumableComponentDslInfoImpl internal constructor(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    extension: CommonExtension<*, *, *, *, *>
) : ComponentDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    services,
    buildDirectory,
    extension
), ConsumableComponentDslInfo {

    override val shadersDslInfo: ShadersDslInfo? by lazy(LazyThreadSafetyMode.NONE) {
        ShadersDslInfoImpl(
            defaultConfig, buildTypeObj, productFlavorList
        )
    }

    override val optimizationDslInfo: OptimizationDslInfo by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationDslInfoImpl(
            componentType,
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            services,
            buildDirectory
        )
    }

    override val renderscriptDslInfo: RenderscriptDslInfo? by lazy(LazyThreadSafetyMode.NONE) {
        RenderscriptDslInfoImpl(
            mergedFlavor,
            buildTypeObj
        )
    }

    override val buildConfigDslInfo: BuildConfigDslInfo? by lazy(LazyThreadSafetyMode.NONE) {
        BuildConfigDslInfoImpl(
            defaultConfig,
            buildTypeObj,
            productFlavorList
        )
    }

    override val manifestPlaceholdersDslInfo: ManifestPlaceholdersDslInfo? by lazy(LazyThreadSafetyMode.NONE) {
        ManifestPlaceholdersDslInfoImpl(
            mergedFlavor,
            buildTypeObj
        )
    }
}
