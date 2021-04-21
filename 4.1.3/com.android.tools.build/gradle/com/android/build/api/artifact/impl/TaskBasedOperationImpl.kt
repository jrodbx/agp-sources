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
    val artifacts: ArtifactsImpl,
    val taskProvider: TaskProvider<TaskT>
): TaskBasedOperation<TaskT> {

    override fun <FileTypeT : FileSystemLocation> wiredWith(
        input: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ): OutOperationRequest<FileTypeT> =
        OutOperationRequestImpl(artifacts, taskProvider, input)

    override fun wiredWithFiles(
        taskInput: (TaskT) -> RegularFileProperty,
        taskOutput: (TaskT) -> RegularFileProperty
    ): InAndOutFileOperationRequest
            = InAndOutFileOperationRequestImpl(artifacts, taskProvider, taskInput, taskOutput)

    override fun <FileTypeT : FileSystemLocation> wiredWith(
        input: (TaskT) -> ListProperty<FileTypeT>,
        output: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ): CombiningOperationRequest<FileTypeT> =
        CombiningOperationRequestImpl(objects, artifacts, taskProvider, input, output)

    override fun wiredWithDirectories(
        taskInput: (TaskT) -> DirectoryProperty,
        taskOutput: (TaskT) -> DirectoryProperty
    ): InAndOutDirectoryOperationRequestImpl<TaskT> =
        InAndOutDirectoryOperationRequestImpl(artifacts, taskProvider, taskInput, taskOutput)
}