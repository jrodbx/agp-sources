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

package com.android.build.gradle.internal.core.dsl.features

import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.NativeBuiltType
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by components that support native builds.
 */
interface NativeBuildDslInfo {
    val nativeBuildSystem: NativeBuiltType?

    val ndkConfig: MergedNdkConfig

    val externalNativeBuildOptions: CoreExternalNativeBuildOptions

    /**
     * Returns the ABI filters associated with the artifact, or empty set if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    val supportedAbis: Set<String>

    val isJniDebuggable: Boolean

    val externalNativeExperimentalProperties: Map<String, Any>
}
