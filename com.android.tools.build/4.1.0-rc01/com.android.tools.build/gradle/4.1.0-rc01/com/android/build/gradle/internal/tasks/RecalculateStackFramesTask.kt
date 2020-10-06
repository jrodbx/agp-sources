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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
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

    private fun createDelegate() = FixStackFramesDelegate(
        bootClasspath.files, classesToFix.files, referencedClasses.files, outFolder!!.get().asFile
    )

    override val incremental: Boolean = true

    override fun doFullTaskAction() {
        createDelegate().doFullRun(getWorkerFacadeWithWorkers())
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        createDelegate().doIncrementalRun(getWorkerFacadeWithWorkers(), changedInputs)
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl,
        private val isTestCoverageEnabled: Boolean) :
        VariantTaskCreationAction<RecalculateStackFramesTask, ComponentPropertiesImpl>(
            componentProperties
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

            val globalScope = creationConfig.globalScope

            val classesToFix = globalScope.project.files(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR))

            val referencedClasses =
                globalScope.project.files(creationConfig.variantScope.providedOnlyClasspath)

            referencedClasses.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.CLASSES_JAR))

            if (isTestCoverageEnabled) {
                referencedClasses.from(
                    creationConfig.artifacts.get(
                        InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES),
                    creationConfig.globalScope.project.files(
                        creationConfig.artifacts.get(
                            InternalArtifactType.JACOCO_INSTRUMENTED_JARS)).asFileTree)
            } else {
                referencedClasses.from(creationConfig.artifacts.getAllClasses())
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
        }
    }
}