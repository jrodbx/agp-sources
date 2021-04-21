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

import com.android.build.api.variant.JniLibsPackaging
import com.android.build.api.variant.LibraryPackaging
import com.android.build.gradle.internal.services.VariantPropertiesApiServices

class LibraryPackagingImpl(
    dslPackagingOptions: com.android.build.gradle.internal.dsl.PackagingOptions,
    variantPropertiesApiServices: VariantPropertiesApiServices
) : PackagingImpl(dslPackagingOptions, variantPropertiesApiServices),
    LibraryPackaging {

    override val jniLibs =
        JniLibsPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

    override fun jniLibs(action: JniLibsPackaging.() -> Unit) {
        action.invoke(jniLibs)
    }
}
