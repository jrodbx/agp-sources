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

import com.android.build.gradle.internal.model.CoreCmakeOptions
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

/** See {@link com.android.build.api.dsl.Cmake} */
abstract class CmakeOptions @Inject constructor(private val dslServices: DslServices)
    : CoreCmakeOptions, com.android.build.api.dsl.Cmake {

    override fun path(path: Any?) {
        this.path = path?.let {
            dslServices.file(path)
        }
    }

    fun setPath(path: Any?) {
        path(path)
    }

    override fun buildStagingDirectory(buildStagingDirectory: Any?) {
        this.buildStagingDirectory = buildStagingDirectory?.let {
            dslServices.file(buildStagingDirectory)
        }
    }

    fun setBuildStagingDirectory(buildStagingDirectory: Any?) {
        buildStagingDirectory(buildStagingDirectory)
    }
}
