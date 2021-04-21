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

import com.android.build.api.variant.DexPackagingOptions
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.sdklib.AndroidVersion.VersionCodes.P

class DexPackagingOptionsImpl(
        dslPackagingOptions: com.android.build.gradle.internal.dsl.PackagingOptions,
        variantPropertiesApiServices: VariantPropertiesApiServices,
        minSdk: Int
) : DexPackagingOptions {

    // Default to false for P+ because uncompressed dex files yield smaller installation sizes
    // because ART doesn't need to store an extra uncompressed copy on disk.
    override val useLegacyPackaging =
            variantPropertiesApiServices.propertyOf(
                    Boolean::class.java,
                    dslPackagingOptions.dex.useLegacyPackaging ?: (minSdk < P)
            )
}
