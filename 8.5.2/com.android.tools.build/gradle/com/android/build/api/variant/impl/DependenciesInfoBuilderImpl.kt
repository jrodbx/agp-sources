/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.variant.DependenciesInfoBuilder
import com.android.build.gradle.internal.services.VariantBuilderServices
import javax.inject.Inject

open class DependenciesInfoBuilderImpl @Inject constructor(
    variantBuilderServices: VariantBuilderServices,
    dslDependencyInfo: DependenciesInfo,
): DependenciesInfoBuilder {

    private val includeInApkValue = variantBuilderServices.valueOf(dslDependencyInfo.includeInApk)
    private val includeInBundleValue = variantBuilderServices.valueOf(dslDependencyInfo.includeInBundle)

    @Deprecated(
        "This property is renamed to includeInApk",
        replaceWith = ReplaceWith("includeInApk")
    )
    override var includedInApk: Boolean
        set(value) = includeInApkValue.set(value)
        get() = includeInApkValue.get()

    @Deprecated(
        "This property is renamed to includeInBundle",
        replaceWith = ReplaceWith("includeInBundle")
    )
    override var includedInBundle: Boolean
        set(value) = includeInBundleValue.set(value)
        get() = includeInBundleValue.get()
    override var includeInApk: Boolean
        set(value) = includeInApkValue.set(value)
        get() = includeInApkValue.get()
    override var includeInBundle: Boolean
        set(value) = includeInBundleValue.set(value)
        get() = includeInBundleValue.get()
}
