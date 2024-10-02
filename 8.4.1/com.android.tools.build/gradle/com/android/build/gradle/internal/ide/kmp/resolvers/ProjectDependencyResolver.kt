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

import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.ide.dependencies.getBuildTreePath
import com.android.build.gradle.internal.ide.kmp.LibraryResolver
import com.android.build.gradle.internal.ide.proto.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.models.DependencyInfo
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.artifactsClasspath
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver

/**
 * An implementation of [IdeDependencyResolver] that resolves project dependencies.
 */
@OptIn(ExternalKotlinTargetApi::class)
internal class ProjectDependencyResolver(
    libraryResolver: LibraryResolver,
    sourceSetToCreationConfigMap: Lazy<Map<KotlinSourceSet, KmpComponentCreationConfig>>
) : BaseIdeDependencyResolver(
    libraryResolver,
    sourceSetToCreationConfigMap
), IdeDependencyResolver {
    override fun resolve(sourceSet: KotlinSourceSet): Set<IdeaKotlinDependency> {
        val component = sourceSetToCreationConfigMap.value[sourceSet] ?: return emptySet()

        libraryResolver.registerSourceSetArtifacts(sourceSet)

        // The actual artifact type doesn't matter, this will be picked up on the IDE side and
        // mapped to a project dependency. We query for jar artifacts since both android and
        // non-android projects will produce it.
        val artifacts = getArtifactsForComponent(
            component,
            AndroidArtifacts.ArtifactType.JAR
        ) {
            it is ProjectComponentIdentifier
        }

        return artifacts.mapNotNull { artifact ->
            val componentId = artifact.id.componentIdentifier as ProjectComponentIdentifier

            // This is a dependency on the same module, usually from unitTest/instrumentationTest on
            // the main module. This should be handled as a friend dependency which will allow the
            // test components to view the internals of the main. So we just ignore this case here.
            val buildTreePath = getBuildTreePath(component.variantDependencies).get()
            if (buildTreePath == componentId.buildTreePath) {
                return@mapNotNull null
            }

            val buildPath = componentId.build.buildPath

            IdeaKotlinProjectArtifactDependency(
                type = IdeaKotlinSourceDependency.Type.Regular,
                coordinates = IdeaKotlinProjectCoordinates(
                    buildName = if (buildPath == ":") ":" else buildPath.split(":").last(),
                    buildPath = buildPath,
                    projectPath = componentId.projectPath,
                    projectName = componentId.projectName
                )
            ).also { dependency ->
                val library = libraryResolver.getLibrary(
                    variant = artifact.variant,
                    sourceSet = sourceSet
                )
                if (library != null && artifact.variant.attributes.contains(AgpVersionAttr.ATTRIBUTE)) {
                    // Android project, could be kmp or android lib, let the IDE extension points
                    // handle each case
                    dependency.extras[androidDependencyKey] =
                        DependencyInfo.newBuilder()
                            .setLibrary(
                                // The key is redundant since we depend on the kotlin definition.
                                library.convert().clearKey()
                            )
                            .build()
                } else {
                    // Not android, let kmp resolvers handle the resolution
                    dependency.artifactsClasspath.add(artifact.file)
                }
            }
        }.toSet()
    }
}
