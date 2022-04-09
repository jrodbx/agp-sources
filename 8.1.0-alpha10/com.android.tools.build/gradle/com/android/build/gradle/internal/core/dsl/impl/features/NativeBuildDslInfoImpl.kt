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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.core.MergedExternalNativeBuildOptions
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.NativeBuiltType
import com.android.build.gradle.internal.core.dsl.features.NativeBuildDslInfo
import com.android.build.gradle.internal.core.dsl.impl.computeMergedOptions
import com.android.build.gradle.internal.cxx.configure.ninja
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.CoreNdkOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.builder.core.ComponentType

class NativeBuildDslInfoImpl(
    private val componentType: ComponentType,
    private val defaultConfig: DefaultConfig,
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
    private val extension: CommonExtension<*, *, *, *, *>
): NativeBuildDslInfo {

    override val ndkConfig: MergedNdkConfig = MergedNdkConfig()
    override val externalNativeBuildOptions = MergedExternalNativeBuildOptions()

    override val externalNativeExperimentalProperties: Map<String, Any>
        get() {
            // merge global and variant properties
            val mergedProperties = mutableMapOf<String, Any>()
            mergedProperties.putAll(extension.externalNativeBuild.experimentalProperties)
            mergedProperties.putAll(
                externalNativeBuildOptions.externalNativeExperimentalProperties
            )
            return mergedProperties
        }

    init {
        mergeOptions()
    }

    private fun mergeOptions() {
        computeMergedOptions(
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            ndkConfig,
            { ndk as CoreNdkOptions },
            { ndk as CoreNdkOptions }
        )
        computeMergedOptions(
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            externalNativeBuildOptions,
            { externalNativeBuild as CoreExternalNativeBuildOptions },
            { externalNativeBuild as CoreExternalNativeBuildOptions }
        )
    }

    override val isJniDebuggable: Boolean
        get() = buildTypeObj.isJniDebuggable

    override val nativeBuildSystem: NativeBuiltType?
        get() {
            if (externalNativeExperimentalProperties.ninja.path != null) return NativeBuiltType.NINJA
            if (extension.externalNativeBuild.ndkBuild.path != null) return NativeBuiltType.NDK_BUILD
            if (extension.externalNativeBuild.cmake.path != null) return NativeBuiltType.CMAKE
            return null
        }

    override val supportedAbis: Set<String>
        get() = if (componentType.isDynamicFeature) setOf() else ndkConfig.abiFilters
}
