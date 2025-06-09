/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Transform from one artifact type to another artifact type without changing the artifact's
 * contents.
 */
@DisableCachingByDefault
abstract class IdentityTransform : TransformAction<IdentityTransform.Parameters> {

    interface Parameters : GenericTransformParameters {

        /**
         * Whether to create an empty output directory if the input file/directory does not exist.
         *
         * Example use case: A Java library subproject without any classes publishes but does not
         * create the classes directory, but we still need to transform it.
         */
        @get:Input
        @get:Optional // false by default if not set
        val acceptNonExistentInputFile: Property<Boolean>
    }

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        when {
            input.isDirectory -> transformOutputs.dir(input)
            input.isFile -> transformOutputs.file(input)
            parameters.acceptNonExistentInputFile.getOrElse(false) -> transformOutputs.dir("empty")
            else -> throw IllegalArgumentException(
                "File/directory does not exist: ${input.absolutePath}")
        }
    }
}
