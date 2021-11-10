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
package com.android.build.gradle.internal.tasks.mlkit

import com.android.SdkConstants.DOT_TFLITE
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ML_MODELS
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.mlkit.codegen.TfliteModelGenerator
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.tools.mlkit.MlConstants
import com.android.tools.mlkit.MlNames
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@CacheableTask
abstract class GenerateMlModelClass : NonIncrementalTask() {

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val modelFileDir: DirectoryProperty

    @get:OutputDirectory
    abstract val sourceOutDir: DirectoryProperty

    @get:Input
    abstract val namespace: Property<String>

    override fun doTaskAction() {
        modelFileDir.asFileTree.visit(
            object : FileVisitor {
                override fun visitDir(fileVisitDetails: FileVisitDetails) {
                    // Do nothing
                }

                override fun visitFile(fileVisitDetails: FileVisitDetails) {
                    val modelFile = fileVisitDetails.file
                    if (modelFile.name.endsWith(DOT_TFLITE)
                        && modelFile.length() <= MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES
                    ) {
                        try {
                            val modelGenerator = TfliteModelGenerator(
                                modelFile,
                                namespace.get() + MlNames.PACKAGE_SUFFIX,
                                fileVisitDetails.relativePath.pathString
                            )
                            modelGenerator.generateBuildClass(sourceOutDir)
                        } catch (e: Exception) {
                            Logging.getLogger(this.javaClass).warn(e.message)
                        }
                    }
                }
            })
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<GenerateMlModelClass, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String = computeTaskName("generate", "MlModelClass")
        override val type: Class<GenerateMlModelClass> = GenerateMlModelClass::class.java

        override fun handleProvider(taskProvider: TaskProvider<GenerateMlModelClass>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateMlModelClass::sourceOutDir
            ).on(InternalArtifactType.ML_SOURCE_OUT)
        }

        override fun configure(task: GenerateMlModelClass) {
            super.configure(task)
            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    MERGED_ML_MODELS, task.modelFileDir
                )
            task.namespace.setDisallowChanges(creationConfig.namespace)
        }
    }
}
