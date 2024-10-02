/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.dependency.DexingRegistration
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.DexMergingAction
import org.gradle.api.file.FileCollection

fun getGlobalSyntheticsInput(
    creationConfig: ApkCreationConfig,
    mergeAction: DexMergingAction,
    dexingUsingArtifactTransforms: Boolean,
    separateFileDependenciesTask: Boolean
): FileCollection {
    if (mergeAction != DexMergingAction.MERGE_ALL) {
        return creationConfig.services.fileCollection()
    }

    val globals = creationConfig.services.fileCollection()
    val attributes = DexingRegistration.ComponentSpecificParameters(creationConfig).getAttributes()

    globals.from(
        creationConfig.artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_PROJECT),
        creationConfig.artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_MIXED_SCOPE)
    )
    if (dexingUsingArtifactTransforms) {
        globals.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.GLOBAL_SYNTHETICS,
                attributes
            )
        )
        val artifactScope = if (separateFileDependenciesTask) {
            AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE
        } else {
            AndroidArtifacts.ArtifactScope.EXTERNAL
        }
        globals.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                artifactScope,
                AndroidArtifacts.ArtifactType.GLOBAL_SYNTHETICS,
                attributes
            )
        )
    } else {
        globals.from(creationConfig.artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_SUBPROJECT))
        globals.from(
            creationConfig.artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_EXTERNAL_LIB),
            creationConfig.artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_EXTERNAL_LIBS_ARTIFACT_TRANSFORM)
        )
    }
    if (separateFileDependenciesTask) {
        globals.from(
            creationConfig.artifacts.get(InternalArtifactType.GLOBAL_SYNTHETICS_FILE_LIB)
        )
    }
    return globals
}

