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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/** Task that runs before JavaCompile to collect information about annotation processors. */
@CacheableTask
abstract class JavaPreCompileTask : NonIncrementalTask() {

    private lateinit var annotationProcessorArtifactCollection: ArtifactCollection

    @get:Classpath
    val annotationProcessorArtifactFiles: FileCollection
        get() = annotationProcessorArtifactCollection.artifactFiles

    @get:Input
    abstract val annotationProcessorClassNames: ListProperty<String>

    @get:OutputFile
    abstract val annotationProcessorListFile: RegularFileProperty

    public override fun doTaskAction() {
        val annotationProcessorArtifacts =
            annotationProcessorArtifactCollection.artifacts.map { SerializableArtifact(it) }

        workerExecutor.noIsolation().submit(JavaPreCompileWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.annotationProcessorArtifacts.setDisallowChanges(annotationProcessorArtifacts)
            it.annotationProcessorClassNames.setDisallowChanges(annotationProcessorClassNames)
            it.annotationProcessorListFile.setDisallowChanges(annotationProcessorListFile)
        }
    }

    class CreationAction(creationConfig: BaseCreationConfig) :
        VariantTaskCreationAction<JavaPreCompileTask, BaseCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("javaPreCompile")

        override val type: Class<JavaPreCompileTask>
            get() = JavaPreCompileTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<JavaPreCompileTask>) {
            super.handleProvider(taskProvider)

            creationConfig
                .artifacts
                .setInitialProvider(taskProvider) { it.annotationProcessorListFile }
                .withName("annotationProcessors.json")
                .on(InternalArtifactType.ANNOTATION_PROCESSOR_LIST)
        }

        override fun configure(task: JavaPreCompileTask) {
            super.configure(task)

            task.annotationProcessorArtifactCollection =
                creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                        ConsumedConfigType.ANNOTATION_PROCESSOR,
                        ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.PROCESSED_JAR
                    )
            task.annotationProcessorClassNames.setDisallowChanges(
                creationConfig
                    .variantDslInfo
                    .javaCompileOptions
                    .annotationProcessorOptions
                    .classNames
            )
        }
    }
}

abstract class JavaPreCompileParameters : ProfileAwareWorkAction.Parameters() {
    abstract val annotationProcessorArtifacts: ListProperty<SerializableArtifact>
    abstract val annotationProcessorClassNames: ListProperty<String>
    abstract val annotationProcessorListFile: RegularFileProperty
}

abstract class JavaPreCompileWorkAction : ProfileAwareWorkAction<JavaPreCompileParameters>() {

    override fun run() {
        val annotationProcessors =
            detectAnnotationProcessors(
                parameters.annotationProcessorClassNames.get(),
                parameters.annotationProcessorArtifacts.get()
            )
        writeAnnotationProcessorsToJsonFile(
            annotationProcessors, parameters.annotationProcessorListFile.get().asFile
        )
    }
}
