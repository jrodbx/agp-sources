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

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.impl.initializeAaptOptionsFromDsl
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.AssetsCreationConfig
import com.android.build.gradle.internal.core.dsl.features.AndroidResourcesDslInfo
import com.android.build.gradle.internal.services.VariantServices

class AssetsCreationConfigImpl(
    private val dslInfo: AndroidResourcesDslInfo,
    private val internalServices: VariantServices,
    private val androidResourcesCreationConfig: () -> AndroidResourcesCreationConfig?
): AssetsCreationConfig {

    override val androidResources: AndroidResources by lazy {
        androidResourcesCreationConfig.invoke()?.androidResources
            ?: initializeAaptOptionsFromDsl(dslInfo.androidResources, internalServices)
    }
}
