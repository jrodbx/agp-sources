/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ArtifactPublishingUtil")
package com.android.build.gradle.internal.scope

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.Attribute

/**
 * Publish an artifact to a configuration.
 *
 * @param configuration The configuration to which the artifact is published.
 * @param file The artifact file published, typically a File or Provider<File>.
 * @param builtBy The object which created this artifact, typically a Task or BuildableArtifact.
 * @param artifactType The ArtifactType of the artifact.
 * @param attributeMap Other optional Configuration Attributes.
 */
@JvmOverloads
fun publishArtifactToConfiguration(
    configuration: Configuration,
    file: Any,
    artifactType: AndroidArtifacts.ArtifactType,
    attributeMap: Map<Attribute<String>, String>? = null
) {
    val type = artifactType.type
    configuration
        .outgoing
        .variants { variants: NamedDomainObjectContainer<ConfigurationVariant> ->
            variants.create(
                getConfigurationVariantName(artifactType, attributeMap)
            ) { variant ->
                variant.artifact(
                    file
                ) { artifact ->
                    artifact.type = type
                }
                variant.attributes.let { container ->
                    attributeMap?.keys?.forEach { key ->
                        attributeMap[key]?.let { container.attribute(key, it) }
                    }
                }
            }
        }
}

@JvmOverloads
fun publishArtifactToDefaultVariant(
    configuration: Configuration,
    file: Any,
    artifactType: AndroidArtifacts.ArtifactType,
    classifier: String? = null
) {
    val type = artifactType.type

    configuration.outgoing.artifact(
        file
    ) { artifact ->
        artifact.type = type
        classifier?.let { artifact.classifier = classifier }
    }
}


/**
 * This method creates a unique ConfigurationVariant name based on the artifactType and
 * attributeMap, which is important because all items in a NamedDomainObjectContainer must have
 * unique names.
 */
private fun getConfigurationVariantName(
    artifactType: AndroidArtifacts.ArtifactType,
    attributeMap: Map<Attribute<String>, String>?
) : String {
    val suffix: String? =
        attributeMap
            ?.entries
            ?.sortedBy {it.key.name}
            ?.fold("-") { it, entry -> "$it-${entry.key.name}-${entry.value}"}
    return "${artifactType.type}${suffix?:""}"
}

