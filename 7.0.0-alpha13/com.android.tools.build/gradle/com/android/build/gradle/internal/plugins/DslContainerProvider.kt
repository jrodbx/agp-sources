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

package com.android.build.gradle.internal.plugins

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.gradle.internal.dependency.SourceSetManager
import org.gradle.api.NamedDomainObjectContainer

/**
 * Provider of the various containers that the extensions uses.
 *
 * This is using Type parameters as the objects in the container will vary depending on
 * the type of the plugin.
 *
 */
interface DslContainerProvider<
        DefaultConfigT : DefaultConfig,
        BuildTypeT : BuildType,
        ProductFlavorT : ProductFlavor,
        SigningConfigT : ApkSigningConfig> {

    val defaultConfig: DefaultConfigT

    val buildTypeContainer: NamedDomainObjectContainer<BuildTypeT>
    val productFlavorContainer: NamedDomainObjectContainer<ProductFlavorT>
    val signingConfigContainer: NamedDomainObjectContainer<SigningConfigT>

    val sourceSetManager: SourceSetManager
}
