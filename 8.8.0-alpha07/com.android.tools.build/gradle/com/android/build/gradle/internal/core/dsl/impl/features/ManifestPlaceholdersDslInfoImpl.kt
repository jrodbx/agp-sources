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
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo

class ManifestPlaceholdersDslInfoImpl(
    private val mergedFlavor: MergedFlavor,
    private val buildTypeObj: BuildType
): ManifestPlaceholdersDslInfo {

    override val placeholders: Map<String, String> by lazy {
        val mergedFlavorsPlaceholders: MutableMap<String, String> = mutableMapOf()
        mergedFlavor.manifestPlaceholders.forEach { (key, value) ->
            mergedFlavorsPlaceholders[key] = value.toString()
        }
        // so far, blindly override the build type placeholders
        buildTypeObj.manifestPlaceholders.forEach { (key, value) ->
            mergedFlavorsPlaceholders[key] = value.toString()
        }
        mergedFlavorsPlaceholders
    }
}
