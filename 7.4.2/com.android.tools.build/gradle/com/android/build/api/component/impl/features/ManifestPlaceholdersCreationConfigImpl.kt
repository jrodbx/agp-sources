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

import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.core.dsl.ConsumableComponentDslInfo
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.provider.MapProperty

class ManifestPlaceholdersCreationConfigImpl(
    placeholders: Map<String, String>,
    internalServices: VariantServices
): ManifestPlaceholdersCreationConfig {
    override val placeholders: MapProperty<String, String> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            placeholders
        )
    }
}
