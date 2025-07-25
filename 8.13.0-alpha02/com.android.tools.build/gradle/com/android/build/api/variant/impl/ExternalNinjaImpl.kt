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

package com.android.build.api.variant.impl

import com.android.build.api.variant.ExternalNativeBuild
import com.android.build.gradle.internal.cxx.configure.CoreExternalNativeNinjaOptions
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty

/**
 * A common [ExternalNativeBuild] abstraction over [CoreExternalNativeNinjaOptions].
 * This class is analogous to [ExternalCmakeImpl] and [ExternalNdkBuildImpl] which wrap
 * [CoreExternalNativeCmakeOptions] and [CoreExternalNativeNdkBuildOptions] respectively.
 */
class ExternalNinjaImpl(
    mergedExternalNativeNinjaOptions: CoreExternalNativeNinjaOptions,
    variantServices: VariantServices
): ExternalNativeBuild {

    override val abiFilters: SetProperty<String> =
        variantServices.setPropertyOf(
            type = String::class.java,
            value = mergedExternalNativeNinjaOptions.abiFilters,
            disallowUnsafeRead = false
        )

    override val arguments: ListProperty<String> =
        variantServices.listPropertyOf(
            type = String::class.java,
            value = mergedExternalNativeNinjaOptions.arguments,
            disallowUnsafeRead = false
        )

    override val cFlags: ListProperty<String> =
        variantServices.listPropertyOf(
            type = String::class.java,
            value = mergedExternalNativeNinjaOptions.cFlags,
            disallowUnsafeRead = false
        )

    override val cppFlags: ListProperty<String> =
        variantServices.listPropertyOf(
            type = String::class.java,
            value = mergedExternalNativeNinjaOptions.cppFlags,
            disallowUnsafeRead = false
        )

    override val targets: SetProperty<String> =
        variantServices.setPropertyOf(
            type = String::class.java,
            value = mergedExternalNativeNinjaOptions.targets,
            disallowUnsafeRead = false
        )
}
