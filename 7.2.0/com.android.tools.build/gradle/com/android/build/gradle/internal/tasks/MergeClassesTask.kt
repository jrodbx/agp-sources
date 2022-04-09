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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.dexing.ClassFileInput.CLASS_MATCHER
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.util.zip.Deflater

/**
 * A task that merges the project and runtime dependency class files into a single jar.
 *
 * This task is necessary in a feature or base module when minification is enabled in the base
 * because the base needs to know which classes came from which modules to eventually split the
 * classes to the correct APKs via the Dex Splitter.
 */
@CacheableTask
abstract class MergeClassesTask : NonIncrementalTask() {

    @get:Classpath
    abstract var inputFiles: FileCollection
        protected set

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    lateinit var jarCreatorType: JarCreatorType
        private set

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(MergeClassesWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputFiles.from(inputFiles)
            it.outputFile.set(outputFile)
            it.jarCreatorType.set(jarCreatorType)
        }
    }

    abstract class MergeClassesWorkAction :
        ProfileAwareWorkAction<MergeClassesWorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val inputFiles: ConfigurableFileCollection
            abstract val outputFile: RegularFileProperty
            abstract val jarCreatorType: Property<JarCreatorType>
        }

        override fun run() {
            JarCreatorFactory.make(
                parameters.outputFile.asFile.get().toPath(),
                CLASS_MATCHER,
                parameters.jarCreatorType.get()
            ).use { out ->
                // Don't compress because compressing takes extra time, and this jar doesn't go
                // into any APKs or AARs.
                out.setCompressionLevel(Deflater.NO_COMPRESSION)
                parameters.inputFiles.forEach {
                    if (it.isFile && it.name.endsWith(DOT_JAR)) {
                        out.addJar(it.toPath())
                    } else if (it.isDirectory) {
                        out.addDirectory(it.toPath())
                    }
                }
            }
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<MergeClassesTask, VariantCreationConfig>(
        creationConfig
    ) {
        override val type = MergeClassesTask::class.java
        override val name: String = computeTaskName("merge", "Classes")

        // Because ordering matters for the transform pipeline, we need to fetch the classes as soon
        // as this creation action is instantiated.
        @Suppress("DEPRECATION") // Legacy support (b/195153220)
        private val inputFiles =
            creationConfig
                .transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.intersect(TransformManager.SCOPE_FULL_PROJECT).isNotEmpty()
                }

        override fun handleProvider(
            taskProvider: TaskProvider<MergeClassesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeClassesTask::outputFile
            ).withName(if (creationConfig.variantType.isBaseModule) {
                "base.jar"
            } else {
                TaskManager.getFeatureFileName(creationConfig.services.projectInfo.path, DOT_JAR)
            }).on(InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES)
        }

        override fun configure(
            task: MergeClassesTask
        ) {
            super.configure(task)
            task.inputFiles = inputFiles
            task.jarCreatorType = creationConfig.variantScope.jarCreatorType
        }
    }
}
