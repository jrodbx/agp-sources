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
import com.android.build.api.variant.JniLibsPackaging
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.services.VariantServices

open class JniLibsPackagingImpl(
    dslPackaging: Packaging,
    variantServices: VariantServices
) : JniLibsPackaging {

    override val excludes =
        variantServices.setPropertyOf(String::class.java) {
            // subtract defaultExcludes because its patterns are specific to java resources.
            dslPackaging.excludes
                .minus(defaultExcludes)
                .union(dslPackaging.jniLibs.excludes)
        }

    override val pickFirsts =
        variantServices.setPropertyOf(String::class.java) {
            dslPackaging.pickFirsts.union(dslPackaging.jniLibs.pickFirsts)
        }

    override val keepDebugSymbols =
        variantServices.setPropertyOf(String::class.java) {
            dslPackaging.doNotStrip.union(dslPackaging.jniLibs.keepDebugSymbols)
        }
}
