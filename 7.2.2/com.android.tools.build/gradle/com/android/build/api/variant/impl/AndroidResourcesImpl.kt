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

package com.android.build.api.variant.impl

import com.android.build.api.variant.AndroidResources
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import org.gradle.api.provider.ListProperty

class AndroidResourcesImpl(
    override val ignoreAssetsPatterns: ListProperty<String>,
    override val aaptAdditionalParameters: ListProperty<String>,
    override val noCompress: ListProperty<String>
) : AndroidResources

internal fun initializeAaptOptionsFromDsl(
    dslAndroidResources: com.android.build.api.dsl.AndroidResources,
    variantPropertiesApiServices: VariantPropertiesApiServices
) : AndroidResources {
    return AndroidResourcesImpl(
        ignoreAssetsPatterns = variantPropertiesApiServices.listPropertyOf(
            String::class.java,
            dslAndroidResources.ignoreAssetsPattern?.split(':') ?: listOf()
        ),
        aaptAdditionalParameters = variantPropertiesApiServices.listPropertyOf(
            String::class.java,
            dslAndroidResources.additionalParameters
        ),
        noCompress = variantPropertiesApiServices.listPropertyOf(
            String::class.java,
            dslAndroidResources.noCompress
        )
    )
}
