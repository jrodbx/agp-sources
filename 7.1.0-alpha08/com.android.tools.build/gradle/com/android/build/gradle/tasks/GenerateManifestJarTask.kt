/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.generators.ManifestClassData
import com.android.build.gradle.internal.generators.ManifestClassGenerator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.packaging.JarFlinger
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * Creates a Manifest Class as a compiled JAR file as [InternalArtifactType.COMPILE_MANIFEST_JAR].
 * This manifest class is used for accessing Android Manifest custom permission names.
 */
@CacheableTask
abstract class GenerateManifestJarTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifests: RegularFileProperty

    @get:Input
    abstract val namespace: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    override fun doTaskAction() {
        ManifestClassGenerator(
                ManifestClassData(
                        manifestFile = mergedManifests.get().asFile,
                        namespace = namespace.get(),
                        outputFilePath = outputJar.get().asFile
                )
        ).apply {
            if (customPermissions.any()) {
                generate()
            } else {
                // create an empty jar
                JarFlinger(outputJar.get().asFile.toPath()).close()
            }
        }
    }

    class CreationAction(
            creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<GenerateManifestJarTask, ApkCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("generate", "ManifestClass")
        override val type: Class<GenerateManifestJarTask>
            get() = GenerateManifestJarTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<GenerateManifestJarTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            GenerateManifestJarTask::outputJar
                    ).withName("Manifest.jar")
                    .on(InternalArtifactType.COMPILE_MANIFEST_JAR)
        }

        override fun configure(task: GenerateManifestJarTask) {
            super.configure(task)
            creationConfig
                    .artifacts
                    .setTaskInputToFinalProduct(
                            SingleArtifact.MERGED_MANIFEST,
                            task.mergedManifests
                    )
            task.namespace.setDisallowChanges(creationConfig.namespace)
        }
    }
}
