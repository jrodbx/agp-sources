/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.tasks.ProcessApplicationManifest
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import java.io.File

/**
 * Intended to provide consistent API for accessing configuration artifacts across plugins.
 */
interface PluginDependencies {
    val configurations: PluginConfigurations
    val spec: Spec<ComponentIdentifier>

    fun getArtifactFileCollection(
        configType: AndroidArtifacts.ConsumedConfigType,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ) : FileCollection {
        return getArtifactCollection(configType, artifactType, attributes).artifactFiles
    }

    fun getArtifactCollection(
        configType: AndroidArtifacts.ConsumedConfigType,
        artifactType: AndroidArtifacts.ArtifactType,
        attributes: AndroidAttributes? = null
    ) : ArtifactCollection {
        val attributesAction =
            Action { container: AttributeContainer ->
                container.attribute(AndroidArtifacts.ARTIFACT_TYPE, artifactType.type)
                attributes?.addAttributesToContainer(container)
            }
        return configurations.getByConfigType(configType).incoming
            .artifactView { config: ArtifactView.ViewConfiguration ->
                config.attributes(attributesAction)
                config.componentFilter(spec)
            }.artifacts
    }
}
