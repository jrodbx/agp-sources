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

package com.android.build.api.component.impl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.VariantDslInfoImpl

class OldVariantApiLegacySupportImpl(
    private val variantDslInfo: VariantDslInfoImpl
): OldVariantApiLegacySupport {

    override val buildTypeObj: BuildType
        get() = variantDslInfo.buildTypeObj
    override val productFlavorList: List<ProductFlavor>
        get() = variantDslInfo.productFlavorList
    override val mergedFlavor: MergedFlavor
        get() = variantDslInfo.mergedFlavor
    override val javaCompileOptions: JavaCompileOptions
        get() = variantDslInfo.javaCompileOptions
}
