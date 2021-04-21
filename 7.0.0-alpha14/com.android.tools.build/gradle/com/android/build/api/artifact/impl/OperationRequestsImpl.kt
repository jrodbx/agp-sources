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
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.CombiningOperationRequest
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.api.artifact.OutOperationRequest
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputPath
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
import java.util.concurrent.atomic.AtomicReference

class OutOperationRequestImpl<TaskT: Task, FileTypeT: FileSystemLocation>(
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

    override val description: String
        get() = "Task ${taskProvider.name} was wired with an output but neither toAppend or toCreate " +
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
        val currentProviders= artifactContainer.transform(taskProvider.flatMap { newList })
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
        val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
        val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()

        initializeInput(
            taskProvider,
            from,
            into,
            currentProvider,
            builtArtifactsReference
        )

        // set the output location, so public uses of the API do not have to do it.
        taskProvider.configure { task ->
            into(task).set(type.getOutputPath(artifacts.buildDirectory, taskProvider.name))
        }

        // if this is a public type with an associated listing file used by studio, automatically
        // adjust the listing file provider to be the new task. In order for Studio to use this
        // new location, a successful sync must be performed.
        publicTypesToIdeModelTypeMap[type]?.let {
            val ideModelContainer = artifacts.getArtifactContainer(it)
            ideModelContainer.replace(taskProvider.flatMap { task ->
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

    fun <ArtifactTypeT, ArtifactTypeU> toTransformMany(
        sourceType: ArtifactTypeT,
        targetType: ArtifactTypeU,
        atLocation: String? = null
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

        return initializeTransform(sourceType, targetType, from, into)
    }

    private fun <ArtifactTypeT, ArtifactTypeU> initializeTransform(
        sourceType: ArtifactTypeT,
        targetType: ArtifactTypeU,
        inputLocation: (TaskT) -> DirectoryProperty,
        outputLocation: (TaskT) -> DirectoryProperty
    ): ArtifactTransformationRequestImpl<TaskT>
            where ArtifactTypeT : Single<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany,
                  ArtifactTypeU : Single<Directory>,
                  ArtifactTypeU : Artifact.ContainsMany {

        val builtArtifactsReference = AtomicReference<BuiltArtifactsImpl>()
        val inputProvider = artifacts.get(sourceType)

        initializeInput(taskProvider, inputLocation, outputLocation, inputProvider, builtArtifactsReference)

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
        fun <T : Task> initializeInput(
            taskProvider: TaskProvider<T>,
            inputLocation: (T) -> FileSystemLocationProperty<Directory>,
            outputLocation: (T) -> FileSystemLocationProperty<Directory>,
            inputProvider: Provider<Directory>,
            builtArtifactsReference: AtomicReference<BuiltArtifactsImpl>
        ) {
            taskProvider.configure { task: T ->
                inputLocation(task).set(inputProvider)
                task.doLast {
                    builtArtifactsReference.get().save(outputLocation(task).get())
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
    artifactContainer.addInitialProvider(taskProvider.flatMap { with(it) })
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
    artifactContainer.replace(taskProvider.flatMap { with(it) })
}

private fun <TaskT: Task, FileTypeT: FileSystemLocation, ArtifactTypeT> toTransform(
    artifacts: ArtifactsImpl,
    taskProvider: TaskProvider<TaskT>,
    from: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    into: (TaskT) -> FileSystemLocationProperty<FileTypeT>,
    type: ArtifactTypeT)
        where ArtifactTypeT : Single<FileTypeT>,
              ArtifactTypeT : Artifact.Transformable {
    val artifactContainer = artifacts.getArtifactContainer(type)
    val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
    val fileName = if (type.kind is ArtifactKind.FILE
        && type.getFileSystemLocationName().isNullOrEmpty()) {
        DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS
    } else ""
    taskProvider.configure {
        from(it).set(currentProvider)
        // since the task will now execute, resolve its output path.
        into(it).set(
            artifacts.getOutputPath(type,
                taskProvider.name,
                fileName
            )
        )
    }
}

