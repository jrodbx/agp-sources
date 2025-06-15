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

import com.android.build.api.component.impl.AnnotationProcessorImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.findKaptOrKspConfigurationsForVariant
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/** Task that runs before JavaCompile to collect information about annotation processors. */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA, secondaryTaskCategories = [TaskCategory.COMPILATION])
abstract class JavaPreCompileTask : NonIncrementalTask() {

    private var annotationProcessorArtifacts: ArtifactCollection? = null
    private var kspProcessorArtifacts: ArtifactCollection? = null

    @get:Optional
    @get:Classpath
    val annotationProcessorArtifactFiles: FileCollection?
        get() = annotationProcessorArtifacts?.artifactFiles

    @get:Optional
    @get:Classpath
    val kspProcessorArtifactFiles: FileCollection?
        get() = kspProcessorArtifacts?.artifactFiles

    @get:Input
    abstract val annotationProcessorClassNames: ListProperty<String>

    @get:OutputFile
    abstract val annotationProcessorListFile: RegularFileProperty

    public override fun doTaskAction() {
        val annotationProcessorArtifacts =
            annotationProcessorArtifacts?.artifacts?.map { SerializableArtifact(it) } ?: emptyList()
        val kspProcessorArtifacts =
            kspProcessorArtifacts?.artifacts?.map { SerializableArtifact(it) } ?: emptyList()

        workerExecutor.noIsolation().submit(JavaPreCompileWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.annotationProcessorArtifacts.setDisallowChanges(annotationProcessorArtifacts)
            it.kspProcessorArtifacts.setDisallowChanges(kspProcessorArtifacts)
            it.annotationProcessorClassNames.setDisallowChanges(annotationProcessorClassNames)
            it.annotationProcessorListFile.setDisallowChanges(annotationProcessorListFile)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val usingKapt: Boolean,
        private val usingKsp: Boolean
    ) :
        VariantTaskCreationAction<JavaPreCompileTask, ComponentCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("javaPreCompile")

        override val type: Class<JavaPreCompileTask>
            get() = JavaPreCompileTask::class.java

        // Create the configuration early to avoid issues with composite builds (e.g., bug 183952598)
        private fun createKaptOrKspClassPath(kaptOrKsp: String): Configuration {
            val configurations = findKaptOrKspConfigurationsForVariant(
                this.creationConfig,
                kaptOrKsp
            )
            // This is a private detail, so we want to use a detached configuration, but it's not
            // possible because of https://github.com/gradle/gradle/issues/6881.
            return creationConfig.services.configurations
                .create("_agp_internal_${name}_${kaptOrKsp}Classpath")
                .setExtendsFrom(configurations)
                .apply {
                    isVisible = false
                    isCanBeResolved = true
                    isCanBeConsumed = false
                }
        }

        private val kaptClasspath: Configuration? = if (usingKapt) {
            createKaptOrKspClassPath("kapt")
        } else null

        // Create the configuration early to avoid issues with composite builds (e.g., bug 183952598)
        private val kspClasspath: Configuration? = if (usingKsp) {
            createKaptOrKspClassPath("ksp")
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
                task.annotationProcessorArtifacts = if (creationConfig is KmpComponentCreationConfig) {
                    null
                } else {
                    creationConfig.variantDependencies
                        .getArtifactCollection(
                            ConsumedConfigType.ANNOTATION_PROCESSOR,
                            ArtifactScope.ALL,
                            ArtifactType.JAR
                        )
                }
            }

            task.kspProcessorArtifacts = kspClasspath?.incoming
                ?.artifactView { config: ArtifactView.ViewConfiguration ->
                    config.attributes { it.attribute(ARTIFACT_TYPE, ArtifactType.JAR.type) }
                }
                ?.artifacts

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
    abstract val kspProcessorArtifacts: ListProperty<SerializableArtifact>
}

abstract class JavaPreCompileWorkAction : ProfileAwareWorkAction<JavaPreCompileParameters>() {

    override fun run() {
        val processors =
            detectAnnotationAndKspProcessors(
                parameters.annotationProcessorClassNames.get(),
                parameters.annotationProcessorArtifacts.get(),
                parameters.kspProcessorArtifacts.get()
            )
        writeAnnotationProcessorsToJsonFile(
            processors, parameters.annotationProcessorListFile.get().asFile
        )
    }
}

@VisibleForTesting
const val ANNOTATION_PROCESSOR_LIST_FILE_NAME = "annotationProcessors.json"
