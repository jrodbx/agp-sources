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

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.ArtifactAccess
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.Directory
import javax.inject.Inject

open class AnalyticsEnabledInAndOutDirectoryOperationRequest<TaskT: Task> @Inject constructor(
    val delegate: InAndOutDirectoryOperationRequest<TaskT>,
    val stats: GradleBuildVariant.Builder
): InAndOutDirectoryOperationRequest<TaskT> {
    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : Artifact.Single<Directory>,
                  ArtifactTypeT : Artifact.Transformable {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.TO_TRANSFORM_DIRECTORY_VALUE
        stats.variantApiAccessBuilder.addArtifactAccessBuilder().also {
            it.inputArtifactType = AnalyticsUtil.getVariantApiArtifactType(type.javaClass).number
            it.type = ArtifactAccess.AccessType.TRANSFORM
        }
        delegate.toTransform(type)
    }

    override fun <ArtifactTypeT> toTransformMany(type: ArtifactTypeT): ArtifactTransformationRequest<TaskT>
            where ArtifactTypeT : Artifact.Single<Directory>,
                  ArtifactTypeT : Artifact.ContainsMany {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.TO_TRANSFORM_MANY_VALUE
        stats.variantApiAccessBuilder.addArtifactAccessBuilder().also {
            it.inputArtifactType = AnalyticsUtil.getVariantApiArtifactType(type.javaClass).number
            it.type = ArtifactAccess.AccessType.TRANSFORM_MANY
        }
        return delegate.toTransformMany(type)
    }
}
