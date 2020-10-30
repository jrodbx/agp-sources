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

import com.android.build.api.variant.JniLibsPackagingOptions
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import java.util.concurrent.Callable

open class JniLibsPackagingOptionsImpl(
    dslPackagingOptions: com.android.build.gradle.internal.dsl.PackagingOptions,
    variantPropertiesApiServices: VariantPropertiesApiServices
) : JniLibsPackagingOptions {

    override val excludes =
        variantPropertiesApiServices.setPropertyOf(
            String::class.java,
            Callable<Collection<String>> {
                // subtract defaultExcludes because its patterns are specific to java resources.
                dslPackagingOptions.excludes
                    .minus(defaultExcludes)
                    .union(dslPackagingOptions.jniLibs.excludes)
            }
        )

    override val pickFirsts =
        variantPropertiesApiServices.setPropertyOf(
            String::class.java,
            Callable<Collection<String>> {
                dslPackagingOptions.pickFirsts.union(dslPackagingOptions.jniLibs.pickFirsts)
            }
        )

    override val keepDebugSymbols =
        variantPropertiesApiServices.setPropertyOf(
            String::class.java,
            Callable<Collection<String>> {
                dslPackagingOptions.doNotStrip.union(dslPackagingOptions.jniLibs.keepDebugSymbols)
            }
        )
}
