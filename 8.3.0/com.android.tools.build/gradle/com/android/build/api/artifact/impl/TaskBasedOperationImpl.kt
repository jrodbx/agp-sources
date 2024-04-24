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

import com.android.build.api.artifact.CombiningOperationRequest
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.api.artifact.MultipleArtifactTypeOutOperationRequest
import com.android.build.api.artifact.OutOperationRequest
import com.android.build.api.artifact.TaskBasedOperation
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider

class TaskBasedOperationImpl<TaskT: Task>(
    val objects: ObjectFactory,
    override val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>
): TaskBasedOperation<TaskT>, ArtifactOperationRequest {

    override fun <FileTypeT : FileSystemLocation> wiredWith(
        taskOutput: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ): OutOperationRequest<FileTypeT> =
        OutOperationRequestImpl(artifacts, taskProvider, taskOutput).also {
            artifacts.addRequest(it)
            closeRequest()
        }

    override fun <FileTypeT : FileSystemLocation> wiredWithMultiple(
        taskInput: (TaskT) -> ListProperty<FileTypeT>
    ): MultipleArtifactTypeOutOperationRequest<FileTypeT> =
        MultipleArtifactTypeOutOperationRequestImpl(artifacts, taskProvider, taskInput).also {
            artifacts.addRequest(it)
            closeRequest()
        }


    override fun wiredWithFiles(
        taskInput: (TaskT) -> RegularFileProperty,
        taskOutput: (TaskT) -> RegularFileProperty
    ): InAndOutFileOperationRequest =
        InAndOutFileOperationRequestImpl(artifacts, taskProvider, taskInput, taskOutput).also {
            artifacts.addRequest(it)
            closeRequest()
        }

    override fun <FileTypeT : FileSystemLocation> wiredWith(
        taskInput: (TaskT) -> ListProperty<FileTypeT>,
        taskOutput: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ): CombiningOperationRequest<FileTypeT> =
        CombiningOperationRequestImpl(objects, artifacts, taskProvider, taskInput, taskOutput).also {
            artifacts.addRequest(it)
            closeRequest()
        }

    override fun wiredWithDirectories(
        taskInput: (TaskT) -> DirectoryProperty,
        taskOutput: (TaskT) -> DirectoryProperty
    ): InAndOutDirectoryOperationRequestImpl<TaskT> =
        InAndOutDirectoryOperationRequestImpl(artifacts, taskProvider, taskInput, taskOutput).also {
            artifacts.addRequest(it)
            closeRequest()
        }

    override val description: String
        get() = "Task ${taskProvider.name} was passed to Artifacts::use method without wiring any " +
            "input and/or output to an artifact."
}
