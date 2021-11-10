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

package com.android.build.gradle.internal.feature

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.zip.Deflater

/**
 * Task to jar all classes in a project. This includes pre/post java classes, and compiled
 * namespaced R class (if it exists).
 *
 * It is used for e.g.:
 * - dependent features to compile against these classes without bundling them.
 * - unit tests to compile and run them against these classes.
 */
@CacheableTask
abstract class BundleAllClasses : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Classpath
    abstract val inputDirs: ConfigurableFileCollection

    @get:Classpath
    abstract val inputJars: ConfigurableFileCollection

    @get:Input
    lateinit var modulePath: String
        private set

    @get:Input
    lateinit var jarCreatorType: JarCreatorType
        private set

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleAllClassesWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputDirs.from(inputDirs)
            it.inputJars.from(inputJars)
            it.jarCreatorType.set(jarCreatorType)
            it.outputJar.set(outputJar)
        }
    }

    abstract class BundleAllClassesWorkAction :
        ProfileAwareWorkAction<BundleAllClassesWorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val inputDirs: ConfigurableFileCollection
            abstract val inputJars: ConfigurableFileCollection
            abstract val jarCreatorType: Property<JarCreatorType>
            abstract val outputJar: RegularFileProperty
        }

        override fun run() {
            val files = HashMap<String, File>()
            val collector = object : ReproducibleFileVisitor {
                override fun isReproducibleFileOrder() = true
                override fun visitFile(fileVisitDetails: FileVisitDetails) {
                    addFile(fileVisitDetails.relativePath.pathString, fileVisitDetails.file)
                }

                override fun visitDir(fileVisitDetails: FileVisitDetails) {
                }

                fun addFile(path: String, file: File) {
                    files[path] = file
                }
            }
            parameters.inputDirs.asFileTree.visit(collector)

            JarCreatorFactory.make(
                parameters.outputJar.asFile.get().toPath(),
                null,
                parameters.jarCreatorType.get()
            ).use { out ->
                // Don't compress because compressing takes extra time, and this jar doesn't go
                // into any APKs or AARs.
                out.setCompressionLevel(Deflater.NO_COMPRESSION)
                files.forEach { (path, file) -> out.addFile(path, file.toPath()) }
                parameters.inputJars.forEach {
                    out.addJar(it.toPath())
                }
            }
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<BundleAllClasses, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("bundle", "Classes")
        override val type: Class<BundleAllClasses>
            get() = BundleAllClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<BundleAllClasses>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                BundleAllClasses::outputJar
            ).withName("classes.jar").on(InternalArtifactType.APP_CLASSES)
        }

        override fun configure(task: BundleAllClasses) {
            super.configure(task)
            if (creationConfig.projectClassesAreInstrumented) {
                if (creationConfig.asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                    task.inputDirs.from(
                        creationConfig.artifacts.get(
                            InternalArtifactType.FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_CLASSES
                        )
                    )
                    task.inputJars.from(
                        creationConfig.services.fileCollection(
                            creationConfig.artifacts.get(
                                InternalArtifactType.FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_JARS
                            )
                        ).asFileTree
                    )
                } else {
                    task.inputDirs.from(
                        creationConfig.artifacts.get(
                            InternalArtifactType.ASM_INSTRUMENTED_PROJECT_CLASSES
                        )
                    )
                    task.inputJars.from(
                        creationConfig.services.fileCollection(
                            creationConfig.artifacts.get(
                                InternalArtifactType.ASM_INSTRUMENTED_PROJECT_JARS
                            )
                        ).asFileTree
                    )
                }
            } else {
                task.inputDirs.from(
                    creationConfig.artifacts.get(InternalArtifactType.JAVAC),
                    creationConfig.variantData.allPreJavacGeneratedBytecode,
                    creationConfig.variantData.allPostJavacGeneratedBytecode
                )
                if (creationConfig.services.projectInfo.getExtension().aaptOptions.namespaced) {
                    task.inputJars.from(
                        creationConfig.artifacts.get(
                            InternalArtifactType.COMPILE_R_CLASS_JAR
                        )
                    )
                } else {
                    task.inputJars.from(
                        creationConfig.artifacts.get(
                            InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                        )
                    )
                }
            }
            task.modulePath = task.project.path
            task.jarCreatorType = creationConfig.variantScope.jarCreatorType
        }
    }
}
