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

import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.model.CoreExternalNativeBuild
import org.gradle.api.Action
import javax.inject.Inject

/** See [com.android.build.api.dsl.ExternalNativeBuild]  */
open class ExternalNativeBuild @Inject constructor(dslScope: DslScope) :
    CoreExternalNativeBuild,
    ExternalNativeBuild<CmakeOptions, NdkBuildOptions> {
    override val ndkBuild: NdkBuildOptions =
        dslScope.objectFactory.newInstance(
            NdkBuildOptions::class.java, dslScope
        )
    override val cmake: CmakeOptions  =
        dslScope.objectFactory.newInstance(
            CmakeOptions::class.java, dslScope
        )

    /* Not directly in interface as having a non-void return type is unconventional */
    fun ndkBuild(action: Action<NdkBuildOptions>): NdkBuildOptions {
        action.execute(ndkBuild)
        return ndkBuild
    }

    override fun ndkBuild(action: NdkBuildOptions.() -> Unit) {
        action.invoke(ndkBuild)
    }

    /* Not directly in interface as having a non-void return type is unconventional */
    fun cmake(action: Action<CmakeOptions>): CmakeOptions {
        action.execute(cmake)
        return cmake
    }

    override fun cmake(action: CmakeOptions.() -> Unit) {
        action.invoke(cmake)
    }
}