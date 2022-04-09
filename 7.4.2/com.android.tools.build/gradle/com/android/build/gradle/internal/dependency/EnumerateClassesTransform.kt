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

package com.android.build.gradle.internal.dependency

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.TransformAction

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * A Gradle Artifact [TransformAction] that enumerates the classes in each module,
 * so they can be checked for duplicates in CheckDuplicateClassesTask
 */
@CacheableTransform
abstract class EnumerateClassesTransform : TransformAction<GenericTransformParameters> {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val classesJar: Provider<FileSystemLocation>

    override fun transform(transformOutputs: TransformOutputs) {
        val inputFile = classesJar.get().asFile
        val fileName = inputFile.nameWithoutExtension
        val enumeratedClasses = transformOutputs.file(fileName)
        EnumerateClassesDelegate().run(
            inputFile,
            enumeratedClasses
        )
    }
}
