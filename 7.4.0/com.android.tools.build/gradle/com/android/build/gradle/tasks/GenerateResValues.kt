/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.generators.ResValueGenerator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.features.ResValuesTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.ResValuesTaskCreationActionImpl
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.File

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.SOURCE_GENERATION])
abstract class GenerateResValues : NonIncrementalTask() {

    // ----- PUBLIC TASK API -----

    @get:Internal
    val resOutputDir: File
        get() {
            return outputDirectory.get().asFile
        }

    // ----- PRIVATE TASK API -----
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val items: MapProperty<ResValue.Key, ResValue>

    override fun doTaskAction() {
        val folder = outputDirectory.get().asFile

        // Always clean up the directory before use.
        FileUtils.cleanOutputDir(folder)

        if (items.get().isNotEmpty()) {
            ResValueGenerator(folder, items.get()).generate()
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<GenerateResValues, ComponentCreationConfig>(
        creationConfig
    ), ResValuesTaskCreationAction by ResValuesTaskCreationActionImpl(creationConfig) {

        override val name = computeTaskName("generate", "ResValues")
        override val type = GenerateResValues::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<GenerateResValues>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.generateResValuesTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider, GenerateResValues::outputDirectory
            ).atLocation(deprecatedGeneratedResOutputDir.get().asFile.absolutePath)
                .on(InternalArtifactType.GENERATED_RES)
        }

        override fun configure(
            task: GenerateResValues
        ) {
            super.configure(task)

            task.items.set(resValuesCreationConfig.resValues)
        }

        // use the old generated res output dir since some released plugins are directly referencing
        // the output folder location to generate resources in.
        val deprecatedGeneratedResOutputDir by lazy {
            creationConfig.paths.getGeneratedResourcesDir("resValues") }
    }
}
