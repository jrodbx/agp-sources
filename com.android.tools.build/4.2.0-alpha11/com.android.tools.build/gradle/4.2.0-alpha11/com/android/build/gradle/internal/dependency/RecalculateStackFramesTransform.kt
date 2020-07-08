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

import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.tasks.FixStackFramesDelegate
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Internal

@CacheableTransform
abstract class RecalculateStackFramesTransform :
    TransformAction<RecalculateStackFramesTransform.Parameters> {

    @get:CompileClasspath
    @get:InputArtifactDependencies
    abstract val classpath: FileCollection

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val inputFile = inputArtifact.get().asFile
        val classesHierarchyResolver = parameters.classesHierarchyBuildService.get()
            .getClassesHierarchyResolverBuilder()
            .addSources(parameters.bootClasspath.get().map { it.asFile })
            .addSources(inputArtifact.get().asFile)
            .addSources(classpath.files)
            .build()

        FixStackFramesDelegate.transformJar(
            inputFile,
            outputs.file(inputFile.name),
            classesHierarchyResolver
        )
    }

    interface Parameters : GenericTransformParameters {
        @get:CompileClasspath
        val bootClasspath: ListProperty<RegularFile>

        @get:Internal
        val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>
    }
}
