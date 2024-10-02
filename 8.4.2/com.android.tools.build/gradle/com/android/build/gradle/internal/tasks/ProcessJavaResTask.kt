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

import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.JAVA_RESOURCES)
abstract class ProcessJavaResTask @Inject constructor(
    private val archiveOperations: ArchiveOperations
): Sync(), VariantAwareTask {

    fun zipTree(jarFile: File): FileTree = archiveOperations.zipTree(jarFile)

    @get:OutputDirectory
    abstract val outDirectory: DirectoryProperty

    // override to remove the @OutputDirectory annotation
    @Internal
    override fun getDestinationDir(): File {
        return outDirectory.get().asFile
    }

    @get:Internal
    override lateinit var variantName: String

    /** Configuration Action for a process*JavaRes tasks.  */
    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<ProcessJavaResTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("process", "JavaRes")

        override val type: Class<ProcessJavaResTask>
            get() = ProcessJavaResTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessJavaResTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processJavaResourcesTask = taskProvider

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessJavaResTask::outDirectory
            ).withName("out").on(InternalArtifactType.JAVA_RES)
        }

        override fun configure(
            task: ProcessJavaResTask
        ) {
            super.configure(task)

            task.from(
                getProjectJavaRes(creationConfig).asFileTree.matching(MergeJavaResourceTask.patternSet)
            )
            task.fromProjectJavaResJars(creationConfig)
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        private fun getProjectJavaRes(
            creationConfig: ComponentCreationConfig
        ): FileCollection {
            val javaRes = creationConfig.services.fileCollection()
            creationConfig.sources.resources {
                javaRes.from(it.getAsFileTrees())
            }
            // use lazy file collection here in case an annotationProcessor dependency is add via
            // Configuration.defaultDependencies(), for example.
            javaRes.from(
                Callable {
                    if (projectHasAnnotationProcessors(creationConfig)) {
                        creationConfig.artifacts.get(InternalArtifactType.JAVAC)
                    } else {
                        listOf<File>()
                    }
                }
            )
            listOfNotNull(
                creationConfig.oldVariantApiLegacySupport?.variantData?.allPreJavacGeneratedBytecode,
                creationConfig.oldVariantApiLegacySupport?.variantData?.allPostJavacGeneratedBytecode
            ).forEach {
                javaRes.from(
                    it.filter { file ->
                        !file.name.endsWith(DOT_JAR)
                    }
                )
            }
            if (creationConfig.global.namespacedAndroidResources) {
                javaRes.from(creationConfig.artifacts.get(InternalArtifactType.RUNTIME_R_CLASS_CLASSES))
            }
            if ((creationConfig as? ApkCreationConfig)?.packageJacocoRuntime == true) {
                javaRes.from(creationConfig.artifacts.get(InternalArtifactType.JACOCO_CONFIG_RESOURCES))
            }
            return javaRes
        }

        private fun ProcessJavaResTask.fromProjectJavaResJars(
            creationConfig: ComponentCreationConfig
        ) {
            listOfNotNull(
                creationConfig.oldVariantApiLegacySupport?.variantData?.allPreJavacGeneratedBytecode,
                creationConfig.oldVariantApiLegacySupport?.variantData?.allPostJavacGeneratedBytecode
            ).forEach {
                from(
                    it.filter { file ->
                        file.name.endsWith(DOT_JAR)
                    }.elements.map { jars ->
                        jars.map { jar ->
                            zipTree(jar.asFile).matching(
                                MergeJavaResourceTask.patternSet
                            )
                        }
                    }
                )
            }
        }
    }
}
