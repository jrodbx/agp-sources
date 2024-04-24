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
import com.android.build.gradle.internal.ide.kmp.LibraryResolver
import com.android.build.gradle.internal.ide.proto.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.model.v2.ide.LibraryType
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.models.DependencyInfo
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinClasspath
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver

/**
 * An implementation of [IdeDependencyResolver] that resolves local file dependencies.
 */
@OptIn(ExternalKotlinTargetApi::class)
internal class LocalFileDependencyResolver(
    libraryResolver: LibraryResolver,
    sourceSetToCreationConfigMap: Lazy<Map<KotlinSourceSet, KmpComponentCreationConfig>>
) : BaseIdeDependencyResolver(
    libraryResolver,
    sourceSetToCreationConfigMap
), IdeDependencyResolver {

    override fun resolve(sourceSet: KotlinSourceSet): Set<IdeaKotlinDependency> {
        val component = sourceSetToCreationConfigMap.value[sourceSet] ?: return emptySet()

        libraryResolver.registerSourceSetArtifacts(sourceSet)

        val artifacts =
            getArtifactsForComponent(
                component,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            ) { it is OpaqueComponentArtifactIdentifier }

        return artifacts.artifacts.map { artifact ->
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
            ).also { dependency ->
                val library = libraryResolver.getLibrary(
                    artifact.variant,
                    sourceSet
                )

                if (library?.type == LibraryType.ANDROID_LIBRARY) {
                    dependency.extras[androidDependencyKey] =
                        DependencyInfo.newBuilder()
                            .setLibrary(
                                // The key is redundant since we depend on the kotlin definition.
                                library.convert().clearKey()
                            )
                            .build()
                }
            }
        }.toSet()
    }
}
