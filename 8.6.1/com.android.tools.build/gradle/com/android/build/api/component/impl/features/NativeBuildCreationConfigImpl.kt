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

package com.android.build.api.component.impl.features

import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.api.variant.ExternalNdkBuildImpl
import com.android.build.api.variant.impl.ExternalCmakeImpl
import com.android.build.api.variant.impl.ExternalNinjaImpl
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.NativeBuiltType
import com.android.build.gradle.internal.core.dsl.features.NativeBuildDslInfo
import com.android.build.gradle.internal.cxx.configure.externalNativeNinjaOptions
import com.android.build.gradle.internal.dsl.NdkOptions
import com.android.build.gradle.internal.services.VariantServices

class NativeBuildCreationConfigImpl(
    private val component: ConsumableCreationConfig,
    private val dslInfo: NativeBuildDslInfo,
    private val variantServices: VariantServices
): NativeBuildCreationConfig {

    override val ndkConfig: MergedNdkConfig
        get() = dslInfo.ndkConfig
    override val isJniDebuggable: Boolean
        get() = dslInfo.isJniDebuggable
    override val supportedAbis: Set<String>
        get() = dslInfo.supportedAbis
    override val externalNativeExperimentalProperties: Map<String, Any>
        get() = dslInfo.externalNativeExperimentalProperties

    override val externalNativeBuild: ExternalNativeBuild? by lazy {
        dslInfo.nativeBuildSystem?.let { nativeBuildType ->
            when(nativeBuildType) {
                NativeBuiltType.CMAKE ->
                    dslInfo.externalNativeBuildOptions.externalNativeCmakeOptions?.let {
                        ExternalCmakeImpl(
                            it,
                            variantServices
                        )
                    }
                NativeBuiltType.NDK_BUILD ->
                    dslInfo.externalNativeBuildOptions.externalNativeNdkBuildOptions?.let {
                        ExternalNdkBuildImpl(
                            it,
                            variantServices
                        )
                    }
                NativeBuiltType.NINJA -> {
                    ExternalNinjaImpl(
                        externalNativeNinjaOptions,
                        variantServices
                    )
                }
            }
        }
    }

    override val nativeDebugSymbolLevel: NdkOptions.DebugSymbolLevel
        get() {
            val debugSymbolLevelOrNull =
                NdkOptions.DEBUG_SYMBOL_LEVEL_CONVERTER.convert(
                    dslInfo.ndkConfig.debugSymbolLevel
                )
            return debugSymbolLevelOrNull ?: if (component.debuggable) NdkOptions.DebugSymbolLevel.NONE else NdkOptions.DebugSymbolLevel.SYMBOL_TABLE
        }
}
