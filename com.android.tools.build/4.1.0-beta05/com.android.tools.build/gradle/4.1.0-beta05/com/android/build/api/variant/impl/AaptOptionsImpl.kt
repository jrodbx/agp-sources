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

import com.android.build.api.variant.AaptOptions
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import org.gradle.api.provider.ListProperty

class AaptOptionsImpl(
    override val ignoreAssetsPatterns: ListProperty<String>,
    override val additionalParameters: ListProperty<String>
) : AaptOptions

internal fun initializeAaptOptionsFromDsl(
    dslAaptOptions: com.android.build.gradle.internal.dsl.AaptOptions,
    variantPropertiesApiServices: VariantPropertiesApiServices
) : AaptOptions {
    return AaptOptionsImpl(
        ignoreAssetsPatterns = variantPropertiesApiServices.listPropertyOf(
            String::class.java,
            dslAaptOptions.ignoreAssetsPattern?.split(':') ?: listOf(),
            "ignoreAssetsPatterns"
        ),
        additionalParameters = variantPropertiesApiServices.listPropertyOf(
            String::class.java,
            dslAaptOptions.additionalParameters,
            "additionalParameters"
        )
    )
}
