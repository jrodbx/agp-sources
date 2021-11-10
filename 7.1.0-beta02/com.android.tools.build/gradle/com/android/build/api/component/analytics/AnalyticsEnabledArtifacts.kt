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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.TaskBasedOperation
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.ArtifactAccess
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

open class AnalyticsEnabledArtifacts @Inject constructor(
    val delegate: Artifacts,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
) : Artifacts {

    override fun getBuiltArtifactsLoader(): BuiltArtifactsLoader {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.GET_BUILT_ARTIFACTS_LOADER_VALUE
        return delegate.getBuiltArtifactsLoader()
    }

    override fun <FileTypeT : FileSystemLocation> get(type: SingleArtifact<FileTypeT>): Provider<FileTypeT> {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.GET_ARTIFACT_VALUE
        stats.variantApiAccessBuilder.addArtifactAccessBuilder().also {
            it.inputArtifactType = AnalyticsUtil.getVariantApiArtifactType(type.javaClass).number
            it.type = ArtifactAccess.AccessType.GET
        }
        return delegate.get(type)
    }

    override fun <FileTypeT : FileSystemLocation> getAll(type: MultipleArtifact<FileTypeT>): Provider<List<FileTypeT>> {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.GET_ALL_ARTIFACTS_VALUE
        stats.variantApiAccessBuilder.addArtifactAccessBuilder().also {
            it.inputArtifactType = AnalyticsUtil.getVariantApiArtifactType(type.javaClass).number
            it.type = ArtifactAccess.AccessType.GET_ALL
        }
        return delegate.getAll(type)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <TaskT : Task> use(taskProvider: TaskProvider<TaskT>): TaskBasedOperation<TaskT> {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.USE_TASK_VALUE
        return objectFactory.newInstance(
            AnalyticsEnabledTaskBaseOperation::class.java,
            delegate.use(taskProvider),
            stats,
            objectFactory
        ) as TaskBasedOperation<TaskT>
    }
}
