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

import android.databinding.tool.DataBindingBuilder
import com.android.build.api.component.impl.AnnotationProcessorImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.findKaptConfigurationsForVariant
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
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

    private lateinit var annotationProcessorArtifacts: ArtifactCollection

    @get:Classpath
    val annotationProcessorArtifactFiles: FileCollection
        get() = annotationProcessorArtifacts.artifactFiles

    @get:Input
    abstract val annotationProcessorClassNames: ListProperty<String>

    @get:OutputFile
    abstract val annotationProcessorListFile: RegularFileProperty

    public override fun doTaskAction() {
        val annotationProcessorArtifacts =
            annotationProcessorArtifacts.artifacts.map { SerializableArtifact(it) }

        workerExecutor.noIsolation().submit(JavaPreCompileWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.annotationProcessorArtifacts.setDisallowChanges(annotationProcessorArtifacts)
            it.annotationProcessorClassNames.setDisallowChanges(annotationProcessorClassNames)
            it.annotationProcessorListFile.setDisallowChanges(annotationProcessorListFile)
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig, private val usingKapt: Boolean) :
        VariantTaskCreationAction<JavaPreCompileTask, ComponentCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("javaPreCompile")

        override val type: Class<JavaPreCompileTask>
            get() = JavaPreCompileTask::class.java

        // Create the configuration early to avoid issues with composite builds (e.g., bug 183952598)
        private val kaptClasspath: Configuration? = if (usingKapt) {
            val project = creationConfig.services.projectInfo.getProject()
            val kaptConfigurations = findKaptConfigurationsForVariant(project, this.creationConfig)
            // This is a private detail, so we want to use a detached configuration, but it's not
            // possible because of https://github.com/gradle/gradle/issues/6881.
            project.configurations
                .create("_agp_internal_${name}_kaptClasspath")
                .setExtendsFrom(kaptConfigurations)
                .apply {
                    isVisible = false
                    isCanBeResolved = true
                    isCanBeConsumed = false
                }
        } else null

        override fun handleProvider(taskProvider: TaskProvider<JavaPreCompileTask>) {
            super.handleProvider(taskProvider)

            creationConfig
                .artifacts
                .setInitialProvider(taskProvider) { it.annotationProcessorListFile }
                .withName(ANNOTATION_PROCESSOR_LIST_FILE_NAME)
                .on(InternalArtifactType.ANNOTATION_PROCESSOR_LIST)
        }

        override fun configure(task: JavaPreCompileTask) {
            super.configure(task)

            // Query for JAR instead of PROCESSED_JAR as this task only cares about the original
            // jars.
            if (usingKapt) {
                task.annotationProcessorArtifacts = kaptClasspath!!.incoming
                    .artifactView { config: ArtifactView.ViewConfiguration ->
                        config.attributes { it.attribute(ARTIFACT_TYPE, ArtifactType.JAR.type) }
                    }
                    .artifacts
            } else {
                task.annotationProcessorArtifacts = creationConfig.variantDependencies
                    .getArtifactCollection(
                        ConsumedConfigType.ANNOTATION_PROCESSOR,
                        ArtifactScope.ALL,
                        ArtifactType.JAR
                    )
            }
            task.annotationProcessorClassNames.setDisallowChanges(
                (creationConfig.javaCompilation.annotationProcessor as AnnotationProcessorImpl)
                    .finalListOfClassNames
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

@VisibleForTesting
const val ANNOTATION_PROCESSOR_LIST_FILE_NAME = "annotationProcessors.json"
