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

import com.android.build.gradle.internal.model.CoreNdkBuildOptions
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

/** See {@link com.android.build.api.dsl.NdkBuild} */
abstract class NdkBuildOptions @Inject constructor(private val dslServices: DslServices)
    : CoreNdkBuildOptions, com.android.build.api.dsl.NdkBuild {

    override fun path(any: Any) {
        this.path = dslServices.file(any)
    }

    fun setPath(path: Any) {
        path(path)
    }

    override fun buildStagingDirectory(any: Any) {
        this.buildStagingDirectory = dslServices.file(any)
    }

    fun setBuildStagingDirectory(buildStagingDirectory: Any?) {
        this.buildStagingDirectory = buildStagingDirectory?.let {
            dslServices.file(buildStagingDirectory)
        }
    }
}
