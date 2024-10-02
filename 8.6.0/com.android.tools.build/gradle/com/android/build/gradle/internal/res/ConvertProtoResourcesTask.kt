/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.aapt.AaptConvertConfig
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import javax.inject.Inject

/**
 * Converts resources from proto format to binary format
 * (see https://developer.android.com/tools/aapt2#convert).
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class ConvertProtoResourcesTask : NonIncrementalTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protoResourcesInputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val binaryResourcesOutputDir: DirectoryProperty

    @get:Internal
    abstract val artifactTransformationRequest: Property<ArtifactTransformationRequest<ConvertProtoResourcesTask>>

    @get:Nested
    abstract val multiOutputHandler: Property<MultiOutputHandler>

    @get:Nested
    abstract val aapt2Input: Aapt2Input

    override fun doTaskAction() {
        artifactTransformationRequest.get().submit(
            task = this,
            workQueue = workerExecutor.noIsolation(),
            actionType = ConvertProtoResourcesAction::class.java
        ) { builtArtifact: BuiltArtifact, outputDir: Directory, parameters: ConvertProtoResourcesParams ->

            parameters.protoResourcesInputFile.set(File(builtArtifact.outputFile))
            parameters.binaryResourcesOutputFile.set(
                File(
                    outputDir.asFile,
                    multiOutputHandler.get().getOutputNameForSplit(
                        prefix = "shrunk-resources",
                        suffix = "binary-format${SdkConstants.DOT_RES}",
                        outputType = builtArtifact.outputType,
                        filters = builtArtifact.filters
                    )
                )
            )
            parameters.aapt2Input.set(aapt2Input)

            return@submit parameters.binaryResourcesOutputFile.get().asFile
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig)
        : VariantTaskCreationAction<ConvertProtoResourcesTask, ApkCreationConfig>(creationConfig) {

        override val type = ConvertProtoResourcesTask::class.java
        override val name = computeTaskName("convert", "ProtoResources")

        lateinit var transformationRequest: ArtifactTransformationRequest<ConvertProtoResourcesTask>

        override fun handleProvider(taskProvider: TaskProvider<ConvertProtoResourcesTask>) {
            super.handleProvider(taskProvider)
            transformationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    ConvertProtoResourcesTask::protoResourcesInputDir,
                    ConvertProtoResourcesTask::binaryResourcesOutputDir
                )
                .toTransformMany(
                    InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT,
                    InternalArtifactType.SHRUNK_RESOURCES_BINARY_FORMAT
                )
        }

        override fun configure(task: ConvertProtoResourcesTask) {
            super.configure(task)

            task.artifactTransformationRequest.setDisallowChanges(transformationRequest)
            task.multiOutputHandler.setDisallowChanges(MultiOutputHandler.create(creationConfig))
            creationConfig.services.initializeAapt2Input(task.aapt2Input)
        }
    }
}

abstract class ConvertProtoResourcesParams : DecoratedWorkParameters {
    abstract val protoResourcesInputFile: RegularFileProperty
    abstract val binaryResourcesOutputFile: RegularFileProperty
    abstract val aapt2Input: Property<Aapt2Input>
}

abstract class ConvertProtoResourcesAction @Inject constructor() :
    WorkActionAdapter<ConvertProtoResourcesParams> {

    override fun doExecute() {
        parameters.aapt2Input.get().getLeasingAapt2().convert(
            AaptConvertConfig(
                inputFile = parameters.protoResourcesInputFile.get().asFile,
                outputFile = parameters.binaryResourcesOutputFile.get().asFile,
                convertToProtos = false
            ),
            LoggerWrapper(Logging.getLogger(this::class.java))
        )
    }
}
