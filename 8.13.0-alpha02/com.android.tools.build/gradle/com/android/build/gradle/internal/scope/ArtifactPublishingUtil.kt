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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.publishing.getAttributes
import com.android.build.gradle.internal.testFixtures.testFixturesClassifier
import com.google.common.base.Preconditions
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

/**
 * Publish an artifact to a configuration.
 *
 * @param configuration The configuration to which the artifact is published.
 * @param file The artifact file published, typically a File or Provider<File>.
 * @param artifactType The ArtifactType of the artifact.
 * @param attributes Other optional Configuration Attributes.
 */
@JvmOverloads
fun publishArtifactToConfiguration(
    configuration: Configuration,
    file: Any,
    artifactType: AndroidArtifacts.ArtifactType,
    attributes: AndroidAttributes? = null
) {
    val type = artifactType.type
    configuration
        .outgoing
        .variants { variants: NamedDomainObjectContainer<ConfigurationVariant> ->
            variants.create(
                getConfigurationVariantName(artifactType, attributes)
            ) { variant ->
                variant.artifact(
                    file
                ) { artifact ->
                    artifact.type = type
                }
                variant.attributes.let { container ->
                    attributes?.addAttributesToContainer(container)
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
    attributes: AndroidAttributes?
): String {
    return artifactType.type + (attributes?.toAttributeMapString() ?: "")
}

/** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
fun publishBuildArtifacts(
    creationConfig: ComponentCreationConfig,
    publishInfo: VariantPublishingInfo?
) {
    for (outputSpec in PublishingSpecs.getVariantPublishingSpec(creationConfig.componentType).outputs) {
        val buildArtifactType = outputSpec.outputType
        // Gradle only support publishing single file.  Therefore, unless Gradle starts
        // supporting publishing multiple files, PublishingSpecs should not contain any
        // OutputSpec with an appendable ArtifactType.
        if (BuildArtifactSpec.has(buildArtifactType) && BuildArtifactSpec.get(buildArtifactType).appendable) {
            throw RuntimeException(
                "Appendable ArtifactType '${buildArtifactType.name()}' cannot be published."
            )
        }
        val artifactProvider = creationConfig.artifacts.get(buildArtifactType)
        val artifactContainer = creationConfig.artifacts.getArtifactContainer(buildArtifactType)
        if (!artifactContainer.needInitialProducer().get()) {
            val isPublicationConfigs =
                outputSpec.publishedConfigTypes.any { it.isPublicationConfig }

            if (isPublicationConfigs) {
                val components = publishInfo!!.components
                for(component in components) {
                    publishIntermediateArtifact(
                        creationConfig,
                        artifactProvider,
                        outputSpec.artifactType,
                        outputSpec.publishedConfigTypes.map {
                            PublishedConfigSpec(it, component) }.toSet(),
                        outputSpec.libraryElements?.let {
                            creationConfig.services.named(LibraryElements::class.java, it)
                        }
                    )
                }
            } else {
                publishIntermediateArtifact(
                    creationConfig,
                    artifactProvider,
                    outputSpec.artifactType,
                    outputSpec.publishedConfigTypes.map { PublishedConfigSpec(it) }.toSet(),
                    outputSpec.libraryElements?.let {
                        creationConfig.services.named(LibraryElements::class.java, it)
                    }
                )
            }
        }
    }
}

/**
 * Publish an intermediate artifact.
 *
 * @param artifact Provider of File or FileSystemLocation to be published.
 * @param artifactType the artifact type.
 * @param configSpecs the PublishedConfigSpec.
 * @param libraryElements the artifact's library elements
 */
private fun publishIntermediateArtifact(
    creationConfig: ComponentCreationConfig,
    artifact: Provider<out FileSystemLocation>,
    artifactType: AndroidArtifacts.ArtifactType,
    configSpecs: Set<PublishedConfigSpec>,
    libraryElements: LibraryElements?
) {
    Preconditions.checkState(configSpecs.isNotEmpty())
    for (configSpec in configSpecs) {
        val config = creationConfig.variantDependencies.getElements(configSpec)
        val configType = configSpec.configType
        if (config != null) {
            if (configType.isPublicationConfig) {
                var classifier: String? = null
                val isSourcePublication = configType == AndroidArtifacts.PublishedConfigType.SOURCE_PUBLICATION
                val isJavaDocPublication =
                    configType == AndroidArtifacts.PublishedConfigType.JAVA_DOC_PUBLICATION
                if (configSpec.isClassifierRequired) {
                    classifier = if (isSourcePublication) {
                        creationConfig.name + "-" + DocsType.SOURCES
                    } else if (isJavaDocPublication) {
                        creationConfig.name + "-" + DocsType.JAVADOC
                    } else {
                        creationConfig.name
                    }
                } else if (creationConfig.componentType.isTestFixturesComponent) {
                    classifier = testFixturesClassifier
                } else if (isSourcePublication) {
                    classifier = DocsType.SOURCES
                } else if (isJavaDocPublication) {
                    classifier = DocsType.JAVADOC
                }
                publishArtifactToDefaultVariant(config, artifact, artifactType, classifier)
            } else {
                publishArtifactToConfiguration(
                    config,
                    artifact,
                    artifactType,
                    artifactType.getAttributes(
                        namedAttributes = libraryElements?.let {
                            mapOf(
                                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE to libraryElements
                            )
                        }
                    ) { type, name ->
                        creationConfig.services.named(type, name)
                    }
                )
            }
        }
    }
}
