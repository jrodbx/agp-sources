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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
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
internal class ProjectDependencyResolver(
    sourceSetToCreationConfigMap: () -> Map<KotlinSourceSet, KmpComponentCreationConfig>
) : BaseIdeDependencyResolver(
    sourceSetToCreationConfigMap
) {

    override fun resolve(sourceSet: KotlinSourceSet): Set<IdeaKotlinDependency> {
        val component = sourceSetToCreationConfigMap()[sourceSet] ?: return emptySet()

        // The actual artifact type doesn't matter, this will be picked up on the IDE side and
        // mapped to a project dependency. We query for jar artifacts since both android and
        // non-android projects will produce it.
        return getArtifactsForComponent(
            component,
            AndroidArtifacts.ArtifactType.JAR
        ) {
            it is ProjectComponentIdentifier
        }.mapNotNull { artifact ->
            val componentId = artifact.id.componentIdentifier as ProjectComponentIdentifier

            IdeaKotlinProjectArtifactDependency(
                type = IdeaKotlinSourceDependency.Type.Regular,
                coordinates = IdeaKotlinProjectCoordinates(
                    buildId = componentId.build.name,
                    projectPath = componentId.projectPath,
                    projectName = componentId.projectName
                )
            ).also { dependency ->
                // TODO(b/269755640): Kotlin IDE plugin resolvers will not be able to map the
                //  file back to an android project dependency, once we have our own IDE
                //  resolvers, we should send more data to help the resolver map the dependency
                //  back to the project.
                dependency.artifactsClasspath.add(artifact.file)
            }
        }.toSet()
    }
}
