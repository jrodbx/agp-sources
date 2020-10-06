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
import com.android.build.api.artifact.Artifact.MultipleArtifact
import com.android.build.api.artifact.Artifact.SingleArtifact
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.CombiningOperationRequest
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.api.artifact.OutOperationRequest
import com.android.build.api.variant.impl.BuiltArtifactsImpl
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
    val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val with: (TaskT) -> FileSystemLocationProperty<FileTypeT>
) : OutOperationRequest<FileTypeT> {

    override fun <ArtifactTypeT> toAppendTo(type: ArtifactTypeT)
            where ArtifactTypeT : MultipleArtifact<FileTypeT>,
                  ArtifactTypeT : Artifact.Appendable =
        toAppend(artifacts, taskProvider, with, type)

    override fun <ArtifactTypeT> toCreate(type: ArtifactTypeT)
            where ArtifactTypeT : SingleArtifact<FileTypeT>,
                  ArtifactTypeT : Artifact.Replaceable =
        toCreate(artifacts, taskProvider, with, type)
}

class InAndOutFileOperationRequestImpl<TaskT: Task>(
    val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val from: (TaskT) -> RegularFileProperty,
    val into: (TaskT) -> RegularFileProperty
): InAndOutFileOperationRequest {

    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : SingleArtifact<RegularFile>,
                  ArtifactTypeT : Artifact.Transformable =
        toTransform(artifacts, taskProvider, from, into, type)

}

class CombiningOperationRequestImpl<TaskT: Task, FileTypeT: FileSystemLocation>(
    val objects: ObjectFactory,
    val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val from: (TaskT) -> ListProperty<FileTypeT>,
    val into: (TaskT) -> FileSystemLocationProperty<FileTypeT>
): CombiningOperationRequest<FileTypeT> {
    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : MultipleArtifact<FileTypeT>,
                  ArtifactTypeT : Artifact.Transformable {
        val artifactContainer = artifacts.getArtifactContainer(type)
        val newList = objects.listProperty(type.kind.dataType().java)
        val currentProviders= artifactContainer.transform(taskProvider.flatMap { newList })
        taskProvider.configure {
            newList.add(into(it))
            into(it).set(artifacts.getOutputPath(type, taskProvider.name))
            from(it).set(currentProviders)
        }
    }
}

class InAndOutDirectoryOperationRequestImpl<TaskT: Task>(
    val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>,
    val from: (TaskT) -> DirectoryProperty,
    val into: (TaskT) -> DirectoryProperty
): InAndOutDirectoryOperationRequest<TaskT> {

    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : SingleArtifact<Directory>,
                  ArtifactTypeT : Artifact.Transformable =
        toTransform(artifacts, taskProvider, from, into, type)

    override fun <ArtifactTypeT> toTransformMany(
        type: ArtifactTypeT
    ): ArtifactTransformationRequest<TaskT>
            where ArtifactTypeT : SingleArtifact<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany {

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
    ): ArtifactTransformationRequest<TaskT>
            where
            ArtifactTypeT : SingleArtifact<Directory>,
            ArtifactTypeT : Artifact.ContainsMany,
            ArtifactTypeU : SingleArtifact<Directory>,
            ArtifactTypeU : Artifact.ContainsMany {
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
    ): ArtifactTransformationRequestImpl<ArtifactTypeU, TaskT>
            where ArtifactTypeT : SingleArtifact<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany,
                  ArtifactTypeU : SingleArtifact<Directory>,
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

    companion object {

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
) where ArtifactTypeT : MultipleArtifact<FileTypeT>,
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
) where ArtifactTypeT : SingleArtifact<FileTypeT>,
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
        where ArtifactTypeT : SingleArtifact<FileTypeT>,
              ArtifactTypeT : Artifact.Transformable {
    val artifactContainer = artifacts.getArtifactContainer(type)
    val currentProvider =  artifactContainer.transform(taskProvider.flatMap { into(it) })
    val fileName = when (type.kind) {
        is ArtifactKind.FILE -> DEFAULT_FILE_NAME_OF_REGULAR_FILE_ARTIFACTS
        else -> ""
    }
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

