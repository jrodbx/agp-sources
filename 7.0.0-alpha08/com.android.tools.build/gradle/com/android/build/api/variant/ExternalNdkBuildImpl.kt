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

package com.android.build.api.variant

import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty

class ExternalNdkBuildImpl(
    mergedExternalNativeNdkBuildOptions: CoreExternalNativeNdkBuildOptions,
    variantPropertiesApiServices: VariantPropertiesApiServices
): ExternalNdkBuild {

    override val abiFilters: SetProperty<String> =
            variantPropertiesApiServices.setPropertyOf(
                    String::class.java,
                    mergedExternalNativeNdkBuildOptions.abiFilters
            )

    override val arguments: ListProperty<String> =
            variantPropertiesApiServices.listPropertyOf(
                    String::class.java,
                    mergedExternalNativeNdkBuildOptions.arguments
            )

    override val cFlags: ListProperty<String> =
            variantPropertiesApiServices.listPropertyOf(
                    String::class.java,
                    mergedExternalNativeNdkBuildOptions.getcFlags()
            )

    override val cppFlags: ListProperty<String> =
            variantPropertiesApiServices.listPropertyOf(
                    String::class.java,
                    mergedExternalNativeNdkBuildOptions.cppFlags
            )

    override val targets: SetProperty<String> =
            variantPropertiesApiServices.setPropertyOf(
                    String::class.java,
                    mergedExternalNativeNdkBuildOptions.targets
            )
}
