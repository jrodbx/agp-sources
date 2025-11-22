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
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
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
): Sync(), VariantTask {

    fun zipTree(jarFile: File): FileTree = archiveOperations.zipTree(jarFile)

    @get:OutputDirectory
    abstract val outDirectory: DirectoryProperty

    @get:Internal
    override lateinit var variantName: String

    /**
     * Configuration Action for process*JavaRes tasks.
     */
    class CreationAction(
        creationConfig: ProcessJavaResCreationConfig
    ) : VariantTaskCreationAction<ProcessJavaResTask, ProcessJavaResCreationConfig>(
        creationConfig,
        dependsOnPreBuildTask = false
    ) {

        override val name: String
            get() = computeTaskName("process", "JavaRes")

        override val type: Class<ProcessJavaResTask>
            get() = ProcessJavaResTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessJavaResTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.setJavaResTask(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessJavaResTask::outDirectory
            ).withName("out").on(InternalArtifactType.JAVA_RES)
        }

        override fun configure(
            task: ProcessJavaResTask
        ) {
            super.configure(task)
            task.from(getProjectJavaRes(creationConfig, task))
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            task.into(task.outDirectory)
        }
    }
}

private fun getProjectJavaRes(
    creationConfig: ProcessJavaResCreationConfig,
    task: ProcessJavaResTask,
): FileCollection {
    val javaRes = creationConfig.services.fileCollection()
    javaRes.from(creationConfig.sources?.getAsFileTrees())
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

    creationConfig.extraClasses.forEach {
        javaRes.from(it.filter { file -> !file.name.endsWith(DOT_JAR) })

        javaRes.from(it.filter { file -> file.name.endsWith(DOT_JAR) }.elements.map { jars ->
            jars.map { jar ->
                task.zipTree(jar.asFile)
            }
        })
    }

    if (creationConfig.useBuiltInKotlinSupport) {
        // Also collect `.kotlin_module` files (see b/446696613)
        javaRes.from(creationConfig.artifacts.get(InternalArtifactType.BUILT_IN_KOTLINC))
    }

    if (creationConfig.packageJacocoRuntime) {
        javaRes.from(creationConfig.artifacts.get(InternalArtifactType.JACOCO_CONFIG_RESOURCES))
    }
    return javaRes.asFileTree.matching(MergeJavaResourceTask.patternSet)
}
