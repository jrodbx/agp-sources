/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import java.util.function.Supplier
import javax.inject.Inject

abstract class LibraryBuildFeaturesImpl @Inject constructor(
    private val androidResourcesSupplier: Supplier<LibraryAndroidResources>,
    val dslServices: DslServices
) : BuildFeaturesImpl(), LibraryBuildFeatures {

    override var androidResources: Boolean?
        get() = androidResourcesSupplier.get().enable
        set(value) {
            androidResourcesSupplier.get().enable = value ?: dslServices.projectOptions[BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES]
        }

    override var dataBinding: Boolean? = null
    override var mlModelBinding: Boolean? = null
    override var prefabPublishing: Boolean? = null
}
