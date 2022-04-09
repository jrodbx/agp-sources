/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import java.io.File
import java.io.Serializable
import com.android.utils.FileUtils
import org.gradle.tooling.BuildException
import java.io.IOException
import javax.inject.Inject

/**
 * The parameters that will be injected to the BuildElementsTransformRunnable object.
 */
abstract class BuildElementsTransformParams : Serializable {
    /**
     * The output file of the transform
     */
    abstract val output: File?
}

/**
 * Runs the logic to transform the current BuildElements into new BuildElements
 *
 * @param params the parameters that will be injected to the runnable
 */
abstract class BuildElementsTransformRunnable constructor(protected val params: BuildElementsTransformParams) :
    Runnable

/**
 * A transform class that copies a file to a new destination
 */
class BuildElementsCopyRunnable @Inject internal constructor(params: BuildElementsCopyParams) :
    BuildElementsTransformRunnable(params) {

    override fun run() {
        try {
            FileUtils.copyFile(
                (params as BuildElementsCopyParams).input, params.output
            )
        } catch (e: IOException) {
            throw BuildException(e.message, e)
        }

    }
}

class BuildElementsCopyParams internal constructor(
    val input: File,
    override val output: File
) : BuildElementsTransformParams()