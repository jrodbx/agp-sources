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

package com.android.build.gradle.internal.fusedlibrary

import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConfigurations
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec

class FusedLibraryDependencies(private val fusedLibsConfigurations: FusedLibraryConfigurations) {

    fun getArtifactFileCollection(
        usage: String,
        spec: Spec<ComponentIdentifier>,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ) : FileCollection {
        return getArtifactView(usage, spec, artifactType, attributes).artifacts.artifactFiles
    }

    private fun getArtifactView(
        usage: String,
        spec: Spec<ComponentIdentifier>,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ): ArtifactView {
        val attributesAction =
            Action { container: AttributeContainer ->
                container.attribute(AndroidArtifacts.ARTIFACT_TYPE, artifactType.type)
                attributes?.addAttributesToContainer(container)
            }
        return fusedLibsConfigurations.getConfiguration(usage)
            .incoming
            .artifactView { config: ArtifactView.ViewConfiguration ->
                config.attributes(attributesAction)
                config.componentFilter(spec)
            }
    }
}
