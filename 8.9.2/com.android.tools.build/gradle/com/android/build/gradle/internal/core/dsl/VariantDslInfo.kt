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
package com.android.build.gradle.internal.core.dsl

import com.android.build.api.dsl.Packaging
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.core.dsl.features.NativeBuildDslInfo

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by main variants.
 */
interface VariantDslInfo: ComponentDslInfo, ConsumableComponentDslInfo {
    val packaging: Packaging

    val experimentalProperties: Map<String, Any>

    val nativeBuildDslInfo: NativeBuildDslInfo?

    val maxSdkVersion: Int?

    /**
     * Return the targetSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the targetSdkVersion
     */
    val targetSdkVersion: MutableAndroidVersion?
}
