/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.api.dsl.ExternalNativeBuildFlags
import com.android.build.gradle.internal.services.DslServices
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Action
import javax.inject.Inject

open class ExternalNativeBuildOptions : CoreExternalNativeBuildOptions, ExternalNativeBuildFlags {

    final override val ndkBuild: ExternalNativeNdkBuildOptions
    final override val cmake: ExternalNativeCmakeOptions
    final override val experimentalProperties: MutableMap<String, Any> = mutableMapOf()

    @VisibleForTesting
    constructor() {
        ndkBuild =
            ExternalNativeNdkBuildOptions()
        cmake =
            ExternalNativeCmakeOptions()
    }

    @Inject
    constructor(dslServices: DslServices) {
        ndkBuild = dslServices.newInstance(ExternalNativeNdkBuildOptions::class.java)
        cmake = dslServices.newInstance(ExternalNativeCmakeOptions::class.java)
    }

    fun _initWith(that: ExternalNativeBuildOptions) {
        ndkBuild._initWith(that.externalNativeNdkBuildOptions)
        cmake._initWith(that.externalNativeCmakeOptions)
        experimentalProperties.putAll(that.externalNativeExperimentalProperties)
    }

    override fun getExternalNativeNdkBuildOptions(): ExternalNativeNdkBuildOptions? = ndkBuild

    override fun getExternalNativeCmakeOptions(): ExternalNativeCmakeOptions? = cmake

    override fun getExternalNativeExperimentalProperties(): MutableMap<String, Any> =
        experimentalProperties

    fun ndkBuild(action: Action<ExternalNativeNdkBuildOptions>) {
        action.execute(ndkBuild)
    }

    fun cmake(action: Action<ExternalNativeCmakeOptions>) {
        action.execute(cmake)
    }

    override fun ndkBuild(action: com.android.build.api.dsl.NdkBuildFlags.() -> Unit) {
        action.invoke(ndkBuild)
    }

    override fun cmake(action: com.android.build.api.dsl.CmakeFlags.() -> Unit) {
        action.invoke(cmake)
    }
}
