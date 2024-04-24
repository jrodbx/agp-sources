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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.Artifact.Multiple
import com.android.build.api.artifact.Artifact.Single
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.CombiningOperationRequest
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.api.artifact.MultipleArtifactTypeOutOperationRequest
import com.android.build.api.artifact.OutOperationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.atomic.AtomicReference

open class OutOperationRequestImpl<TaskT: Task, FileTypeT: FileSystemLocation>(
    override val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val with: (TaskT) -> FileSystemLocationProperty<FileTypeT>
) : OutOperationRequest<FileTypeT>, ArtifactOperationRequest {

    override fun <ArtifactTypeT> toAppendTo(type: ArtifactTypeT)
            where ArtifactTypeT : Multiple<FileTypeT>,
                  ArtifactTypeT : Artifact.Appendable {
        closeRequest()
        toAppend(artifacts, taskProvider, with, type)
    }

    override fun <ArtifactTypeT> toCreate(type: ArtifactTypeT)
            where ArtifactTypeT : Single<FileTypeT>,
                  ArtifactTypeT : Artifact.Replaceable {
        closeRequest()
        toCreate(artifacts, taskProvider, with, type)
    }

    override fun <ArtifactTypeT : Single<FileTypeT>> toListenTo(type: ArtifactTypeT) {
        closeRequest()
        toListenTo(
            artifacts,
            taskProvider,
            with,
            type
        )
    }

    override val description: String
        get() = "Task ${taskProvider.name} was wired with an output but neither toAppend, toCreate, toListenTo " +
            "methods were invoked."
}

class MultipleArtifactTypeOutOperationRequestImpl<TaskT: Task, FileTypeT: FileSystemLocation>(
    override val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val with: (TaskT) -> ListProperty<FileTypeT>
) : MultipleArtifactTypeOutOperationRequest<FileTypeT>, ArtifactOperationRequest {

    override fun <ArtifactTypeT : Multiple<FileTypeT>> toListenTo(type: ArtifactTypeT) {
        closeRequest()
        toListenTo(
            artifacts,
            taskProvider,
            with,
            type
        )
    }

    override val description: String
        get() = "Task ${taskProvider.name} was wired with an output but neither toListenTo " +
                "methods were invoked."
}

class InAndOutFileOperationRequestImpl<TaskT: Task>(
    override val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val from: (TaskT) -> RegularFileProperty,
    val into: (TaskT) -> RegularFileProperty
): InAndOutFileOperationRequest, ArtifactOperationRequest {

    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : Single<RegularFile>,
                  ArtifactTypeT : Artifact.Transformable {
        closeRequest()
        toTransform(artifacts, taskProvider, from, into, type)
    }

    override val description: String
        get() = "Task ${taskProvider.name} was wired with an Input and an Output but " +
            "toTransform method was never invoked"
}

class CombiningOperationRequestImpl<TaskT: Task, FileTypeT: FileSystemLocation>(
    val objects: ObjectFactory,
    override val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val from: (TaskT) -> ListProperty<FileTypeT>,
    val into: (TaskT) -> FileSystemLocationProperty<FileTypeT>
): CombiningOperationRequest<FileTypeT>, ArtifactOperationRequest {
    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : Multiple<FileTypeT>,
                  ArtifactTypeT : Artifact.Transformable {
        closeRequest()
        val artifactContainer = artifacts.getArtifactContainer(type)
        val newList = objects.listProperty(type.kind.dataType().java)
        val currentProviders = artifactContainer.transform(taskProvider, taskProvider.flatMap { newList })
        taskProvider.configure {
            newList.add(into(it))
            into(it).set(artifacts.getOutputPath(type, taskProvider.name))
            from(it).set(currentProviders)
        }
    }

    override val description: String
        get() = "Task ${taskProvider.name} was wired to combine multiple inputs into an output but " +
                "toTransform method was never invoked"
}

class InAndOutDirectoryOperationRequestImpl<TaskT: Task>(
    override val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val from: (TaskT) -> DirectoryProperty,
    val into: (TaskT) -> DirectoryProperty
): InAndOutDirectoryOperationRequest<TaskT>, ArtifactOperationRequest {

    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : Single<Directory>,
                  ArtifactTypeT : Artifact.Transformable {
        closeRequest()
        toTransform(artifacts, taskProvider, from, into, type)
    }

    override fun <ArtifactTypeT> toTransformMany(
        type: ArtifactTypeT
    ): ArtifactTransformationRequest<TaskT>
            where ArtifactTypeT : Single<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany {

        closeRequest()
        val artifactContainer = artifacts.getArtifactContainer(type)
        val currentProvider =  artifactContainer.transform(taskProvider, taskProvider.flatMap { into(it) })
        val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()

        initializeInput(
            type,
            type,
            taskProvider,
            from,
            currentProvider,
            into,
            builtArtifactsReference
        )

        // set the output location, so public uses of the API do not have to do it.
        taskProvider.configure { task ->
            into(task).set(artifacts.calculateOutputPath(type, task))
        }

        // if this is a public type with an associated listing file used by studio, automatically
        // adjust the listing file provider to be the new task. In order for Studio to use this
        // new location, a successful sync must be performed.
        publicTypesToIdeModelTypeMap[type]?.let {
            val ideModelContainer = artifacts.getArtifactContainer(it)
            ideModelContainer.replace(taskProvider,
                taskProvider.flatMap { task ->
                    into(task).file(BuiltArtifactsImpl.METADATA_FILE_NAME)
                })
        }

        return ArtifactTransformationRequestImpl(
            builtArtifactsReference,
            inputLocation = from,
            outputArtifactType = type,
            outputLocation = into
        )
    }

    /**
     * Initiates a transform request of a single [Directory] artifact type that can
     * contain more than one artifact. The transformed artifact will be registered under a different
     * type.
     *
     * @param sourceType The [Artifact] type of the artifact to transform.
     * @param targetType Desired type for the transformed [Artifact]
     * @param atLocation relative location within the project's build folder to place the
     * transformed [Directory] at.
     * @param builtArtifactsCustomizer optional lambda to post-process the [BuiltArtifactsImpl]
     * before it is saved in the target directory along with the transformed [Artifact]s. This only
     * runs during task execution, so it should only use task inputs
     * @return [ArtifactTransformationRequest] that will allow processing of individual artifacts
     * located in the input directory.
     *
     * Both source and target artifact types must be [Artifact.Single], and [Artifact.ContainsMany].
     */
    internal fun <ArtifactTypeT, ArtifactTypeU> toTransformMany(
        sourceType: ArtifactTypeT,
        targetType: ArtifactTypeU,
        atLocation: String? = null,
        builtArtifactsCustomizer: ((TaskT, BuiltArtifactsImpl) -> BuiltArtifactsImpl)? = null,
    ): ArtifactTransformationRequestImpl<TaskT>
            where
            ArtifactTypeT : Single<Directory>,
            ArtifactTypeT : Artifact.ContainsMany,
            ArtifactTypeU : Single<Directory>,
            ArtifactTypeU : Artifact.ContainsMany {
        closeRequest()
        val initialProvider = artifacts.setInitialProvider(taskProvider, into)
        if (atLocation != null) {
            initialProvider.atLocation(atLocation)
        }
        initialProvider.on(targetType)

        return initializeTransform(sourceType, targetType, from, into, builtArtifactsCustomizer)
    }

    private fun <ArtifactTypeT, ArtifactTypeU> initializeTransform(
        sourceType: ArtifactTypeT,
        targetType: ArtifactTypeU,
        inputLocation: (TaskT) -> DirectoryProperty,
        outputLocation: (TaskT) -> DirectoryProperty,
        builtArtifactsCustomizer: ((TaskT, BuiltArtifactsImpl) -> BuiltArtifactsImpl)?
    ): ArtifactTransformationRequestImpl<TaskT>
            where ArtifactTypeT : Single<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany,
                  ArtifactTypeU : Single<Directory>,
                  ArtifactTypeU : Artifact.ContainsMany {

        val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()
        val inputProvider = artifacts.get(sourceType)

        initializeInput(
            sourceType,
            targetType,
            taskProvider,
            inputLocation,
            inputProvider,
            outputLocation,
            builtArtifactsReference,
            builtArtifactsCustomizer
        )

        return ArtifactTransformationRequestImpl(
            builtArtifactsReference,
            inputLocation = inputLocation,
            outputArtifactType = targetType,
            outputLocation = outputLocation
        )
    }

    override val description: String
        get() = "Task ${taskProvider.name} was wired with an Input and an Output but " +
            "toTransform or toTransformMany methods were never invoked"

    companion object {

        /**
         * Map of public [SingleArtifact] of [Directory] that have an associated listing file used
         * by Android Studio and passed through the model. The key is the artifact type and the
         * value is the [InternalArtifactType] of [RegularFile] for the listing file artifact.
         */
        val publicTypesToIdeModelTypeMap:
                Map<Single<Directory>, InternalArtifactType<RegularFile>>
                = mapOf(SingleArtifact.APK to InternalArtifactType.APK_IDE_MODEL,
        )

        /**
         * Keep this method as a static to avoid all possible unwanted variable capturing from
         * lambdas.
         */
        fun <T : Task, ArtifactTypeT, ArtifactTypeU> initializeInput(
            sourceType: ArtifactTypeT,
            targetType: ArtifactTypeU,
            taskProvider: TaskProvider<T>,
            inputLocation: (T) -> FileSystemLocationProperty<Directory>,
            inputProvider: Provider<Directory>,
            buildMetadataFileLocation: (T) -> FileSystemLocationProperty<Directory>,
            builtArtifactsReference: AtomicReference<BuiltArtifactsImpl>,
            builtArtifactsCustomizer: ((T , BuiltArtifactsImpl) -> BuiltArtifactsImpl)? = null,
        ) where ArtifactTypeT : Single<Directory>,
                ArtifactTypeT : Artifact.ContainsMany,
                ArtifactTypeU : Single<Directory>,
                ArtifactTypeU : Artifact.ContainsMany {

            taskProvider.configure { task: T ->
                inputLocation(task).set(inputProvider)
                task.doLast {
                    if (builtArtifactsReference.get() == null) {
                        throw IllegalArgumentException(
                            "Unable to transform artifact ${sourceType.name()} " +
                                    ("to ${targetType.name() }".takeIf { sourceType != targetType } ?: "") +
                                    "using registered task ${task.name}.\n" +
                                    "No builtArtifact output was found, did you forget to call " +
                                    "ArtifactTransformationRequest.submit in the task action?"
                        )
                    } else {
                        val builtArtifacts =
                            builtArtifactsCustomizer?.let {
                                it(task, builtArtifactsReference.get())
                            } ?: builtArtifactsReference.get()

                        builtArtifacts.save(buildMetadataFileLocation(task).get())
                    }
                }
            }
        }
    }
}

private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> toAppend(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    with: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    type: ArtifactTypeT
) where ArtifactTypeT : Multiple<FileTypeT>,
        ArtifactTypeT: Artifact.Appendable {

    val artifactContainer = artifacts.getArtifactContainer(type)
    taskProvider.configure {
        with(it).set(artifacts.getOutputPath(type, taskProvider.name))
    }
    // all producers of a multiple artifact type are added to the initial list (just like
    // the AGP producers) since the transforms always operate on the complete list of added
    // providers.
    artifactContainer.addInitialProvider(taskProvider, taskProvider.flatMap { with(it) })
}


private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> toCreate(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    with: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    type: ArtifactTypeT
) where ArtifactTypeT : Single<FileTypeT>,
        ArtifactTypeT: Artifact.Replaceable {

    val artifactContainer = artifacts.getArtifactContainer(type)
    taskProvider.configure {
        with(it).set(artifacts.getOutputPath(type, taskProvider.name))
    }
    artifactContainer.replace(taskProvider, taskProvider.flatMap { with(it) })
}

private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> toTransform(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    from: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    into: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    type: ArtifactTypeT)
        where ArtifactTypeT : Single<FileTypeT>,
              ArtifactTypeT : Artifact.Transformable {
    if (type is Artifact.ContainsMany) {
        throw IllegalArgumentException(
            "${type.name()} is a `com.android.build.api.artifact.ContainsMany` artifact.\n" +
                    "you cannot transform ContainsMany artifacts" +
                    " using the `toTransform` method,\nyou must instead use the" +
                    " `OutOperationRequest.toTransformMany` method."
        )
    }
    val artifactContainer = artifacts.getArtifactContainer(type)
    val currentProvider =  artifactContainer.transform(taskProvider, taskProvider.flatMap { into(it) })
    taskProvider.configure { it ->
        from(it).set(currentProvider)
        // since the task will now execute, resolve its output path.
        val outputAbsolutePath:File = artifacts.calculateOutputPath(type, it)
        into(it).set(outputAbsolutePath)
    }
}

private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> toListenTo(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    with: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    type: ArtifactTypeT
) where ArtifactTypeT : Single<FileTypeT> {

    val artifactContainer = artifacts.getArtifactContainer(type)
    taskProvider.configure {
        with(it).set(artifactContainer.get())
    }
    _toListenTo(
        artifacts,
        taskProvider,
        type,
        artifactContainer
    )
}

private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> toListenTo(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    with: (TaskT) -> ListProperty<FileTypeT>,
    type: ArtifactTypeT
) where ArtifactTypeT : Multiple<FileTypeT> {

    val artifactContainer= artifacts.getArtifactContainer(type)
    taskProvider.configure {
        with(it).set(artifactContainer.get())
    }

    _toListenTo(
        artifacts,
        taskProvider,
        type,
        artifactContainer
    )
}

private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> _toListenTo(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    type: ArtifactTypeT,
    artifactContainer: ArtifactContainer<*, *>,
) where ArtifactTypeT : Artifact<FileTypeT> {
    // Do not register the listener on the current final provider as it may change as long as
    // the variant API callbacks are running so instead register a deferred action.
    artifacts.listenerManager.addAction {
        artifactContainer.getFinalProvider()?.configure {
            it.finalizedBy(taskProvider)
        } ?: artifacts.logger.warn("${taskProvider.name} was registered to listen to the " +
                "production of the ${type.name()} artifact, but there are no producer for this " +
                "type. The ${taskProvider.name} will never be invoked.")
    }
}
