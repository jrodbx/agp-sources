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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec

class FusedLibraryDependencies(private val fusedLibsConfigurations: FusedLibraryConfigurations) {

    private fun getComponentFilter(scope: AndroidArtifacts.ArtifactScope): Spec<ComponentIdentifier> {
        return when (scope) {
            AndroidArtifacts.ArtifactScope.ALL -> Spec { true }
            AndroidArtifacts.ArtifactScope.EXTERNAL ->
                // since we want both Module dependencies and file based dependencies in this case
                // the best thing to do is search for non ProjectComponentIdentifier.
                Spec { it !is ProjectComponentIdentifier }
            AndroidArtifacts.ArtifactScope.PROJECT -> Spec { it is ProjectComponentIdentifier }
            AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE -> Spec { it is ModuleComponentIdentifier }
            AndroidArtifacts.ArtifactScope.FILE -> Spec {
                !(it is ProjectComponentIdentifier || it is ModuleComponentIdentifier)
            }
        }
    }

    fun getArtifactFileCollection(
        usage: String,
        scope: AndroidArtifacts.ArtifactScope,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ) : FileCollection {
        return getArtifactCollection(usage, scope, artifactType, attributes).artifactFiles
    }

    fun getArtifactFileCollection(
        usage: String,
        spec: Spec<ComponentIdentifier>,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ) : FileCollection {
        return getArtifactCollection(usage, spec, artifactType, attributes).artifactFiles
    }

    fun getArtifactCollection(
        usage: String,
        scope: AndroidArtifacts.ArtifactScope,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ) : ArtifactCollection {
        return getArtifactCollection(usage, getComponentFilter(scope), artifactType, attributes)
    }

    fun getArtifactCollection(
            usage: String,
            spec: Spec<ComponentIdentifier>,
            artifactType: AndroidArtifacts.ArtifactType,
            attributes: AndroidAttributes? = null
    ) : ArtifactCollection {
        return getArtifactView(usage, spec, artifactType, attributes).artifacts
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
