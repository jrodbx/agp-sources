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
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.ArtifactAccess
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import javax.inject.Inject

open class AnalyticsEnabledInAndOutFileOperationRequest @Inject constructor(
    val delegate: InAndOutFileOperationRequest,
    val stats: GradleBuildVariant.Builder
): InAndOutFileOperationRequest {
    override fun <ArtifactTypeT> toTransform(type: ArtifactTypeT)
            where ArtifactTypeT : Artifact.Single<RegularFile>,
                  ArtifactTypeT : Artifact.Transformable {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.TO_TRANSFORM_FILE_VALUE
        stats.variantApiAccessBuilder.addArtifactAccessBuilder().also {
            it.inputArtifactType = AnalyticsUtil.getVariantApiArtifactType(type.javaClass).number
            it.type = ArtifactAccess.AccessType.TRANSFORM
        }
        delegate.toTransform(type)
    }
}
