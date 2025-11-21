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
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
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
     * Configuration Action for kotlin multiplatform processAndroidMainJavaRes task.
     */
    class KotlinMultiplatformCreationAction(
        creationConfig: KmpCreationConfig
    ) : VariantTaskCreationAction<ProcessJavaResTask, KmpCreationConfig>(
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

            val projectClasses = creationConfig
                    .artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.CLASSES)

            task.from(getProjectJavaRes(creationConfig, task, listOf(projectClasses)))
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            task.handleDestinationDirIncompatibility(
                creationConfig, InternalArtifactType.JAVA_RES,
                task.outDirectory
            )
        }
    }

    /**
     * Configuration Action for process*JavaRes tasks.
     */
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

            task.from(getProjectJavaRes(creationConfig, task, listOfNotNull(
                creationConfig.oldVariantApiLegacySupport?.variantData?.allPreJavacGeneratedBytecode,
                creationConfig.oldVariantApiLegacySupport?.variantData?.allPostJavacGeneratedBytecode
            )))
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            task.handleDestinationDirIncompatibility(
                creationConfig, InternalArtifactType.JAVA_RES,
                task.outDirectory)
        }
    }
}

private fun getProjectJavaRes(
    creationConfig: ComponentCreationConfig,
    task: ProcessJavaResTask,
    classes: List<FileCollection>
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

    classes.forEach {
        javaRes.from(it.filter { file -> !file.name.endsWith(DOT_JAR) })

        javaRes.from(it.filter { file -> file.name.endsWith(DOT_JAR) }.elements.map { jars ->
            jars.map { jar ->
                task.zipTree(jar.asFile)
            }
        })
    }

    if (creationConfig.global.namespacedAndroidResources) {
        javaRes.from(creationConfig.artifacts.get(InternalArtifactType.RUNTIME_R_CLASS_CLASSES))
    }
    if ((creationConfig as? ApkCreationConfig)?.packageJacocoRuntime == true) {
        javaRes.from(creationConfig.artifacts.get(InternalArtifactType.JACOCO_CONFIG_RESOURCES))
    }
    return javaRes.asFileTree.matching(MergeJavaResourceTask.patternSet)
}

/**
 * Starting in Gradle 9.0, the Sync.destinationDir APIs will use a DirectoryProperty rather
 * than a File in previous releases.
 *
 * Handles compiling and running against pre-9.0 and 9.0 Gradle runtimes.
 *
 * Once we switch to Gradle 9.0 as a minimum version, we should stop defining
 * [currentOutputProperty] and instead use the destinationDir everywhere12.
 */
fun Sync.handleDestinationDirIncompatibility(
    creationConfig: ComponentCreationConfig,
    type: InternalArtifactType<*>,
    currentOutputProperty: DirectoryProperty,
) {
    val getDestinationDirMethod = Sync::class.java.getMethod("getDestinationDir")
    if (getDestinationDirMethod.returnType.isAssignableFrom(File::class.java)) {
        Sync::class.java.getMethod("setDestinationDir", File::class.java)
            .invoke(
                this,
                creationConfig.artifacts.getOutputPath(
                    type,
                    name,
                    "out"
                )
            )
    } else {
        Sync::class.java.getMethod("setDestinationDir", DirectoryProperty::class.java)
            .invoke(
                this,
                currentOutputProperty
            )
    }
}
