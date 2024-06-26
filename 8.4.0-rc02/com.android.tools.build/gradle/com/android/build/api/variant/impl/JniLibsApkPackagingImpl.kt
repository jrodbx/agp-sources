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

import com.android.build.api.dsl.Packaging
import com.android.build.api.variant.JniLibsApkPackaging
import com.android.build.gradle.internal.services.VariantServices
import com.android.sdklib.AndroidVersion.VersionCodes.M

open class JniLibsApkPackagingImpl(
    dslPackaging: Packaging,
    variantServices: VariantServices,
    minSdk: Int
) : JniLibsPackagingImpl(dslPackaging, variantServices),
    JniLibsApkPackaging {

    override val useLegacyPackaging =
        variantServices.propertyOf(
            Boolean::class.java,
            dslPackaging.jniLibs.useLegacyPackaging ?: (minSdk < M)
        )

    override val useLegacyPackagingFromBundle =
        variantServices.propertyOf(
            Boolean::class.java,
            dslPackaging.jniLibs.useLegacyPackaging ?: false
        )
}
