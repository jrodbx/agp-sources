/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationActionImpl
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.PathUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * Task to collect package names for R8 task from local libraries when a consumer proguard rule file
 * is present, for use in the R8 task for gradual R8 shrinking.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class CollectPackagesForR8Task : NonIncrementalTask() {

    @get:OutputFile
    abstract val packageList: RegularFileProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val consumerProguardFiles: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val classes: ConfigurableFileCollection

    public override fun doTaskAction() {

        // Only collect package names if a consumer proguard file is present
        if (!consumerProguardFiles.files.any { it.exists() }) {
            return
        }

        val packageNames = mutableSetOf<String>()
        for (classesFolder in classes.files) {
            if (!classesFolder.exists()) {
                continue
            }
            val filesInClassesFolder = classesFolder.walkTopDown().toList()
            filesInClassesFolder.filter {
                it.name.endsWith(SdkConstants.DOT_CLASS)
            }.forEach { classFile ->
                val relativePath = classFile.relativeTo(classesFolder).toPath()
                val systemIndependentPath = PathUtils.toSystemIndependentPath(relativePath)
                val lastSeparatorIndex = systemIndependentPath.lastIndexOf("/")
                if (lastSeparatorIndex != -1) {
                    val packagePath = systemIndependentPath.substring(0, lastSeparatorIndex)
                    packageNames.add(packagePath.replace("/", ".") + ".*")
                }
            }
        }
        packageList.get().asFile.writeText(packageNames.joinToString(separator = "\n"))
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<CollectPackagesForR8Task, ComponentCreationConfig>(
        creationConfig
    ), OptimizationTaskCreationAction by OptimizationTaskCreationActionImpl(
        creationConfig
    ) {
        override val name: String
            get() = computeTaskName("collect", "PackagesForR8")
        override val type: Class<CollectPackagesForR8Task>
            get() = CollectPackagesForR8Task::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CollectPackagesForR8Task>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CollectPackagesForR8Task::packageList
            ).on(InternalArtifactType.PACKAGES_FOR_R8)
        }

        override fun configure(
            task: CollectPackagesForR8Task
        ) {
            super.configure(task)

            task.classes.from(
                creationConfig.artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.CLASSES)
            )

            task.consumerProguardFiles.from(
                optimizationCreationConfig.consumerProguardFiles
            )
            task.consumerProguardFiles.disallowChanges()
        }
    }
}
