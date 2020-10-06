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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.File

abstract class RecalculateStackFramesTask  : IncrementalTask() {

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:Classpath
    lateinit var bootClasspath: FileCollection
        private set

    @get:Classpath
    lateinit var classesToFix: FileCollection
        private set

    @get:Classpath
    lateinit var referencedClasses: FileCollection
        private set

    @get:Internal
    abstract val classesHierarchyBuildService: Property<ClassesHierarchyBuildService>

    private fun createDelegate() = FixStackFramesDelegate(
        bootClasspath.files, classesToFix.files, referencedClasses.files, outFolder.get().asFile
    )

    override val incremental: Boolean = true

    override fun doFullTaskAction() {
        createDelegate().doFullRun(workerExecutor, this, classesHierarchyBuildService)
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        createDelegate().doIncrementalRun(
            workerExecutor,
            changedInputs,
            this,
            classesHierarchyBuildService
        )
    }

    class CreationAction(
        creationConfig: VariantCreationConfig,
        private val isTestCoverageEnabled: Boolean) :
        VariantTaskCreationAction<RecalculateStackFramesTask, VariantCreationConfig>(
            creationConfig
        ) {

        override val name = computeTaskName("fixStackFrames")
        override val type = RecalculateStackFramesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<RecalculateStackFramesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                RecalculateStackFramesTask::outFolder
            ).on(InternalArtifactType.FIXED_STACK_FRAMES)
        }

        override fun configure(
            task: RecalculateStackFramesTask
        ) {
            super.configure(task)

            task.bootClasspath = creationConfig.variantScope.bootClasspath

            val classesToFix = creationConfig.services.fileCollection(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR))

            val referencedClasses =
                creationConfig.services.fileCollection(creationConfig.variantScope.providedOnlyClasspath)

            referencedClasses.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR
                )
            )

            when {
                creationConfig.registeredProjectClassesVisitors.isNotEmpty() -> {
                    referencedClasses.from(creationConfig.allProjectClassesPostAsmInstrumentation)
                }
                isTestCoverageEnabled -> {
                    referencedClasses.from(
                        creationConfig.artifacts.get(
                            InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES
                        ),
                        creationConfig.services.fileCollection(
                            creationConfig.artifacts.get(
                                InternalArtifactType.JACOCO_INSTRUMENTED_JARS
                            )
                        ).asFileTree
                    )
                }
                else -> {
                    referencedClasses.from(creationConfig.artifacts.getAllClasses())
                }
            }

            creationConfig.onTestedConfig {
                referencedClasses.from(
                    it.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR
                    ).artifactFiles
                )

            }

            task.classesToFix = classesToFix

            task.referencedClasses = referencedClasses

            task.classesHierarchyBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}
