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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.File
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ClassesDirFormat.CONTAINS_SINGLE_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ClassesDirFormat.CONTAINS_CLASS_FILES_ONLY
import com.android.builder.dexing.isJarFile
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Transform from [AndroidArtifacts.ArtifactType.CLASSES_DIR] to
 * [AndroidArtifacts.ArtifactType.CLASSES].
 */
@DisableCachingByDefault
abstract class ClassesDirToClassesTransform : TransformAction<GenericTransformParameters> {

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        when (getClassesDirFormat(input)) {
            CONTAINS_SINGLE_JAR -> {
                outputs.file(input.listFiles()!![0])
            }
            CONTAINS_CLASS_FILES_ONLY -> {
                outputs.dir(input)
            }
        }
    }
}

fun getClassesDirFormat(classesDir: File): AndroidArtifacts.ClassesDirFormat {
    check(classesDir.isDirectory) { "Not a directory: ${classesDir.path}"}
    val filesInDir = classesDir.listFiles()!!
    return if (filesInDir.size == 1 && isJarFile(filesInDir[0])) {
        CONTAINS_SINGLE_JAR
    } else {
        CONTAINS_CLASS_FILES_ONLY
    }
}
