/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType

class ArtifactMetadataProcessor {

    companion object {

        /**
         * It would be great to use [kotlin.reflect.KClass.sealedSubclasses] to get all the
         * artifacts types that have [InternalArtifactType.finalizingArtifact] set. However, the API
         * uses reflection and is slow, therefore you must give the list of annotated types here.
         *
         * There is a test that will ensure that all annotated types are present in this list.
         */
        val internalTypesFinalizingArtifacts: List<InternalArtifactType<*>> = listOf(
            InternalArtifactType.APK_IDE_REDIRECT_FILE,
            InternalArtifactType.BUNDLE_IDE_REDIRECT_FILE,
            InternalArtifactType.APK_FROM_BUNDLE_IDE_REDIRECT_FILE
        )

        fun wireAllFinalizedBy(component: ComponentCreationConfig) {
            internalTypesFinalizingArtifacts.forEach { kClass ->
                handleFinalizedByForType(component, kClass)
            }
        }

        private fun handleFinalizedByForType(
            component: ComponentCreationConfig,
            artifact: InternalArtifactType<*>
        ) {
            artifact.finalizingArtifact.forEach { artifactFinalizedBy ->
                val artifactContainer = when (artifactFinalizedBy) {
                    is Artifact.Single -> component.artifacts.getArtifactContainer(artifactFinalizedBy)
                    is Artifact.Multiple -> component.artifacts.getArtifactContainer(artifactFinalizedBy)
                    else -> throw RuntimeException("Unhandled artifact type : $artifactFinalizedBy")
                }
                artifactContainer.getTaskProviders().forEach { taskProvider ->
                    taskProvider.configure {
                        it.finalizedBy(component.artifacts.get(artifact))
                    }
                }
            }
        }
    }
}
