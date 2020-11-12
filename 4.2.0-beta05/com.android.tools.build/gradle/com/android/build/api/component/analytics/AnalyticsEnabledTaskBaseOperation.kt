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

package com.android.build.api.component.analytics

import com.android.build.api.artifact.CombiningOperationRequest
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.api.artifact.OutOperationRequest
import com.android.build.api.artifact.TaskBasedOperation
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

open class AnalyticsEnabledTaskBaseOperation<TaskT: Task> @Inject constructor(
    val delegate: TaskBasedOperation<TaskT>,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
) : TaskBasedOperation<TaskT> {

    override fun <FileTypeT : FileSystemLocation> wiredWith(
        taskOutput: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ): OutOperationRequest<FileTypeT> {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.WIRED_WITH_VALUE
        @Suppress("UNCHECKED_CAST")
        return objectFactory.newInstance(
            AnalyticsEnabledOutOperationRequest::class.java as Class<OutOperationRequest<FileTypeT>>,
            delegate.wiredWith(taskOutput),
            stats)
    }

    override fun wiredWithFiles(
        taskInput: (TaskT) -> RegularFileProperty,
        taskOutput: (TaskT) -> RegularFileProperty
    ): InAndOutFileOperationRequest {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.WIRED_WITH_FILES_VALUE
        return objectFactory.newInstance(
            AnalyticsEnabledInAndOutFileOperationRequest::class.java,
            delegate.wiredWithFiles(taskInput, taskOutput),
            stats
        )
    }

    override fun <FileTypeT : FileSystemLocation> wiredWith(
        taskInput: (TaskT) -> ListProperty<FileTypeT>,
        taskOutput: (TaskT) -> FileSystemLocationProperty<FileTypeT>
    ): CombiningOperationRequest<FileTypeT> {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.WIRED_WITH_LIST_VALUE
        @Suppress("UNCHECKED_CAST")
        return objectFactory.newInstance(
            AnalyticsEnabledCombiningOperationRequest::class.java
                    as Class<CombiningOperationRequest<FileTypeT>>,
            delegate.wiredWith(taskInput, taskOutput),
            stats
        )
    }

    override fun wiredWithDirectories(
        taskInput: (TaskT) -> DirectoryProperty,
        taskOutput: (TaskT) -> DirectoryProperty
    ): InAndOutDirectoryOperationRequest<TaskT> {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.WIRED_WITH_DIRECTORIES_VALUE
        @Suppress("UNCHECKED_CAST")
        return objectFactory.newInstance(
            AnalyticsEnabledInAndOutDirectoryOperationRequest::class.java as
                    Class<InAndOutDirectoryOperationRequest<TaskT>>,
            delegate.wiredWithDirectories(taskInput, taskOutput),
            stats
        )
    }
}