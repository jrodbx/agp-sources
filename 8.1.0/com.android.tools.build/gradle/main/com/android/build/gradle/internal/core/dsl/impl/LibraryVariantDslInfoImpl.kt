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
import com.android.build.api.dsl.LibraryVariantDimension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.MergedAarMetadata
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalLibraryExtension
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentType
import org.gradle.api.file.DirectoryProperty

internal class LibraryVariantDslInfoImpl internal constructor(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    dataProvider: ManifestDataProvider,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    override val publishInfo: VariantPublishingInfo,
    extension: InternalLibraryExtension
) : TestedVariantDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    dataProvider,
    services,
    buildDirectory,
    extension
), LibraryVariantDslInfo {

    override val aarMetadata = MergedAarMetadata()

    init {
        mergeOptions()
    }

    private fun mergeOptions() {
        computeMergedOptions(
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            aarMetadata,
            { (this as LibraryVariantDimension).aarMetadata },
            { (this as LibraryVariantDimension).aarMetadata }
        )
    }

    // TODO: Library variant doesn't have isDebuggable dsl in the build type, we should only have
    //  `debug` variants be debuggable
    override val isDebuggable: Boolean
        get() = ProfilingMode.getProfilingModeType(
            services.projectOptions[StringOption.PROFILING_MODE]
        ).isDebuggable
            ?: (buildTypeObj as? com.android.build.gradle.internal.dsl.BuildType)?.isDebuggable
            ?: false
}
