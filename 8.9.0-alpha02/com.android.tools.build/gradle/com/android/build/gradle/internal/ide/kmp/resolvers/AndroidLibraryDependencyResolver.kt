/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.kmp.resolvers

import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.ide.kmp.LibraryResolver
import com.android.build.gradle.internal.ide.proto.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.builder.model.v2.ide.LibraryType
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.models.DependencyInfo
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeAdditionalArtifactResolver

/**
 * An implementation of [IdeAdditionalArtifactResolver] that adds extra data to binary dependencies
 * that are android (aars).
 */
@OptIn(ExternalKotlinTargetApi::class)
internal class AndroidLibraryDependencyResolver(
    libraryResolver: LibraryResolver,
    sourceSetToCreationConfigMap: Lazy<Map<KotlinSourceSet, KmpComponentCreationConfig>>
): IdeAdditionalArtifactResolver, BaseIdeDependencyResolver(
    libraryResolver,
    sourceSetToCreationConfigMap
) {

    override fun resolve(sourceSet: KotlinSourceSet, dependencies: Set<IdeaKotlinDependency>) {
        val component = sourceSetToCreationConfigMap.value[sourceSet] ?: return

        libraryResolver.registerSourceSetArtifacts(sourceSet)

        val artifacts =
            getArtifactsForComponent(
                component,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            ) { it !is ProjectComponentIdentifier && it !is OpaqueComponentArtifactIdentifier }

        val localFileArtifacts =
            getArtifactsForComponent(
                component,
                AndroidArtifacts.ArtifactType.AAR_OR_JAR
            ) { it is OpaqueComponentArtifactIdentifier }.artifacts

        val libraryDependencies = dependencies.filterIsInstance<IdeaKotlinResolvedBinaryDependency>()

        val libraries = artifacts.artifacts.mapNotNull { artifact ->
            val coordinates = if (artifact.variant.owner is ModuleComponentIdentifier) {
                ArtifactCoordinates(
                    (artifact.variant.owner as ModuleComponentIdentifier),
                    artifact.variant.capabilities,
                    artifact.variant.attributes,
                )
            } else {
                return@mapNotNull null
            }
            val library = libraryResolver.getLibrary(
                artifact.variant,
                sourceSet
            )
            if (library?.type == LibraryType.ANDROID_LIBRARY) {
                coordinates to library
            } else {
                null
            }
        }.toMap()

        libraryDependencies.forEach { dependency ->
            if (dependency.coordinates == null) {
                return@forEach
            }
            val library = libraries[ArtifactCoordinates(dependency.coordinates!!)] ?:
                // Check if this is a local file dependency
                localFileArtifacts.find { artifact ->
                    dependency.classpath.contains(artifact.file)
                }?.let { artifact ->
                    libraryResolver.getLibrary(
                        artifact.variant,
                        sourceSet
                    )?.takeIf { it.type == LibraryType.ANDROID_LIBRARY }
                } ?: return@forEach

            dependency.extras[androidDependencyKey] =
                DependencyInfo.newBuilder()
                    .setLibrary(
                        // The key is redundant since we depend on the kotlin definition.
                        library.convert().clearKey()
                    )
                    .build()
        }
    }

    data class ArtifactCoordinates(
        private val group: String,
        private val module: String,
        private val version: String?,
        private val capabilities: Set<String>,
        private val attributes: Map<String, String>
    ) {

        constructor(
            kotlinCoordinates: IdeaKotlinBinaryCoordinates
        ): this(
            group = kotlinCoordinates.group,
            module = kotlinCoordinates.module,
            version = kotlinCoordinates.version,
            capabilities = kotlinCoordinates.capabilities.map {
                "${it.group}:${it.name}:${it.version}"
            }.toSet(),
            attributes = kotlinCoordinates.attributes.toImmutableMap(),
        )

        constructor(
            identifier: ModuleComponentIdentifier,
            capabilities: List<Capability>,
            attributes: AttributeContainer
        ): this(
            group = identifier.group,
            module = identifier.module,
            version = identifier.version,
            capabilities = capabilities.map {
                "${it.group}:${it.name}:${it.version}"
            }.toSet(),
            attributes = attributes.keySet().associate {
                it.name to attributes.getAttribute(it).toString()
            },
        )
    }
}
