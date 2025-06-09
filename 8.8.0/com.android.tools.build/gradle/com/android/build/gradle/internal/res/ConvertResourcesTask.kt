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
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_RESOURCES_BINARY_FORMAT
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
import org.gradle.api.tasks.Input
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
 * Converts resources from binary format to proto format or vice versa
 * (see https://developer.android.com/tools/aapt2#convert).
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
sealed class ConvertResourcesTask<TaskT: ConvertResourcesTask<TaskT>> : NonIncrementalTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesInputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resourcesOutputDir: DirectoryProperty

    @get:Internal
    abstract val convertToProto: Boolean

    @get:Internal
    abstract val outputArtifactType: InternalArtifactType<Directory>

    @get:Internal
    abstract val artifactTransformationRequest: Property<ArtifactTransformationRequest<TaskT>>

    @get:Nested
    abstract val multiOutputHandler: Property<MultiOutputHandler>

    @get:Nested
    abstract val aapt2Input: Aapt2Input

    override fun doTaskAction() {
        @Suppress("UNCHECKED_CAST")
        artifactTransformationRequest.get().submit(
            task = this as TaskT,
            workQueue = workerExecutor.noIsolation(),
            actionType = ConvertResourcesAction::class.java
        ) { builtArtifact: BuiltArtifact, outputDir: Directory, parameters: ConvertResourcesParams ->

            parameters.resourcesInputFile.set(File(builtArtifact.outputFile))
            parameters.resourcesOutputFile.set(
                File(
                    outputDir.asFile,
                    multiOutputHandler.get().getOutputNameForSplit(
                        prefix = outputArtifactType.name().lowercase().replace("_", "-"),
                        suffix = "",
                        outputType = builtArtifact.outputType,
                        filters = builtArtifact.filters
                    ) + SdkConstants.DOT_RES
                )
            )
            parameters.convertToProto.set(convertToProto)
            parameters.aapt2Input.set(aapt2Input)

            return@submit parameters.resourcesOutputFile.get().asFile
        }
    }

    sealed class CreationAction<TaskT: ConvertResourcesTask<TaskT>>(creationConfig: ApkCreationConfig)
        : VariantTaskCreationAction<TaskT, ApkCreationConfig>(creationConfig) {

        lateinit var transformationRequest: ArtifactTransformationRequest<TaskT>

        override fun configure(task: TaskT) {
            super.configure(task)

            task.artifactTransformationRequest.setDisallowChanges(transformationRequest)
            task.multiOutputHandler.setDisallowChanges(MultiOutputHandler.create(creationConfig))
            creationConfig.services.initializeAapt2Input(task.aapt2Input, task)
        }
    }
}

abstract class ConvertResourcesParams : DecoratedWorkParameters {
    abstract val resourcesInputFile: RegularFileProperty
    abstract val resourcesOutputFile: RegularFileProperty
    abstract val convertToProto: Property<Boolean>
    abstract val aapt2Input: Property<Aapt2Input>
}

abstract class ConvertResourcesAction @Inject constructor() :
    WorkActionAdapter<ConvertResourcesParams> {

    override fun doExecute() {
        parameters.aapt2Input.get().getLeasingAapt2().convert(
            AaptConvertConfig(
                inputFile = parameters.resourcesInputFile.get().asFile,
                outputFile = parameters.resourcesOutputFile.get().asFile,
                convertToProtos = parameters.convertToProto.get()
            ),
            LoggerWrapper(Logging.getLogger(this::class.java))
        )
    }
}

/** Converts [LINKED_RESOURCES_BINARY_FORMAT] to [LINKED_RESOURCES_PROTO_FORMAT]. */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class ConvertLinkedResourcesToProtoTask : ConvertResourcesTask<ConvertLinkedResourcesToProtoTask>() {

    override val convertToProto = true
    override val outputArtifactType = LINKED_RESOURCES_PROTO_FORMAT

    class CreationAction(creationConfig: ApkCreationConfig)
        : ConvertResourcesTask.CreationAction<ConvertLinkedResourcesToProtoTask>(creationConfig) {

        override val type = ConvertLinkedResourcesToProtoTask::class.java
        override val name = computeTaskName("convertLinkedResourcesToProto")

        override fun handleProvider(taskProvider: TaskProvider<ConvertLinkedResourcesToProtoTask>) {
            super.handleProvider(taskProvider)
            transformationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    ConvertLinkedResourcesToProtoTask::resourcesInputDir,
                    ConvertLinkedResourcesToProtoTask::resourcesOutputDir
                )
                .toTransformMany(LINKED_RESOURCES_BINARY_FORMAT, LINKED_RESOURCES_PROTO_FORMAT)
        }
    }
}

/** Converts [SHRUNK_RESOURCES_PROTO_FORMAT] to [SHRUNK_RESOURCES_BINARY_FORMAT]. */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class ConvertShrunkResourcesToBinaryTask : ConvertResourcesTask<ConvertShrunkResourcesToBinaryTask>() {

    override val convertToProto = false
    override val outputArtifactType = SHRUNK_RESOURCES_BINARY_FORMAT

    class CreationAction(creationConfig: ApkCreationConfig)
        : ConvertResourcesTask.CreationAction<ConvertShrunkResourcesToBinaryTask>(creationConfig) {

        override val type = ConvertShrunkResourcesToBinaryTask::class.java
        override val name = computeTaskName("convertShrunkResourcesToBinary")

        override fun handleProvider(taskProvider: TaskProvider<ConvertShrunkResourcesToBinaryTask>) {
            super.handleProvider(taskProvider)
            transformationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    ConvertShrunkResourcesToBinaryTask::resourcesInputDir,
                    ConvertShrunkResourcesToBinaryTask::resourcesOutputDir
                )
                .toTransformMany(SHRUNK_RESOURCES_PROTO_FORMAT, SHRUNK_RESOURCES_BINARY_FORMAT)
        }
    }
}
