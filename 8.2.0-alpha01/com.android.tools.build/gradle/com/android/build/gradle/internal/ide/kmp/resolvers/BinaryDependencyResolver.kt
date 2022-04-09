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
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService.Companion.getMavenCoordForLocalFile
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.logging.Logging
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinClasspath
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinUnresolvedBinaryDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeaKotlinBinaryCoordinates
import org.jetbrains.kotlin.tooling.core.mutableExtrasOf

/**
 * An implementation of [IdeDependencyResolver] that resolves library dependencies.
 */
internal class BinaryDependencyResolver(
    sourceSetToCreationConfigMap: () -> Map<KotlinSourceSet, KmpComponentCreationConfig>
) : BaseIdeDependencyResolver(
    sourceSetToCreationConfigMap
) {
    private val logger = Logging.getLogger(BinaryDependencyResolver::class.java)

    override fun resolve(sourceSet: KotlinSourceSet): Set<IdeaKotlinDependency> {
        val component = sourceSetToCreationConfigMap()[sourceSet] ?: return emptySet()

        val artifacts =
            getArtifactsForComponent(
                component,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            ) { it !is ProjectComponentIdentifier }

        val unresolvedDependencies = artifacts.failures
            .onEach { reason -> logger.error("Failed to resolve platform dependency on ${sourceSet.name}", reason) }
            .map { reason ->
                val selector = (reason as? ModuleVersionResolveException)?.selector as? ModuleComponentSelector
                    ?: return@map IdeaKotlinUnresolvedBinaryDependency(
                        coordinates = null, cause = reason.message?.takeIf { it.isNotBlank() }, extras = mutableExtrasOf()
                    )

                IdeaKotlinUnresolvedBinaryDependency(
                    coordinates = IdeaKotlinBinaryCoordinates(selector.group, selector.module, selector.version, null),
                    cause = reason.message?.takeIf { it.isNotBlank() },
                    extras = mutableExtrasOf()
                )
            }.toSet()

        val resolvedDependencies = artifacts.artifacts.mapNotNull { artifact ->
            when (val componentId = artifact.id.componentIdentifier) {
                is ModuleComponentIdentifier -> {
                    IdeaKotlinResolvedBinaryDependency(
                        coordinates = IdeaKotlinBinaryCoordinates(componentId),
                        binaryType = IdeaKotlinBinaryDependency.KOTLIN_COMPILE_BINARY_TYPE,
                        classpath = IdeaKotlinClasspath(artifact.file),
                    )
                }

                is LibraryBinaryIdentifier -> {
                    IdeaKotlinResolvedBinaryDependency(
                        binaryType = IdeaKotlinBinaryDependency.KOTLIN_COMPILE_BINARY_TYPE,
                        coordinates = IdeaKotlinBinaryCoordinates(
                            group = componentId.projectPath + "(${componentId.variant})",
                            module = componentId.libraryName,
                            version = null,
                            sourceSetName = null
                        ),
                        classpath = IdeaKotlinClasspath(artifact.file)
                    )
                }

                is OpaqueComponentArtifactIdentifier -> {
                    IdeaKotlinResolvedBinaryDependency(
                        binaryType = IdeaKotlinBinaryDependency.KOTLIN_COMPILE_BINARY_TYPE,
                        coordinates = getMavenCoordForLocalFile(
                            artifact.file
                        ).let {
                            IdeaKotlinBinaryCoordinates(
                                group = it.groupId,
                                module = it.artifactId,
                                version = it.version,
                            )
                        },
                        classpath = IdeaKotlinClasspath(artifact.file)
                    )
                }

                is ProjectComponentIdentifier -> {
                    logger.warn("Unexpected project dependency ${artifact.id}")
                    null
                }

                else -> {
                    logger.warn("Unhandled componentId: ${componentId.javaClass}")
                    null
                }
            }
        }.toSet()

        return resolvedDependencies + unresolvedDependencies
    }
}
