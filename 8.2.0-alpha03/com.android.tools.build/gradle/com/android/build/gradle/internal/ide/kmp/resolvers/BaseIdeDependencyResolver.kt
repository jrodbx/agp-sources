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
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver

/**
 * Base class for shared logic between kmp IDE dependency resolvers.
 */
internal abstract class BaseIdeDependencyResolver(
    protected val sourceSetToCreationConfigMap: () -> Map<KotlinSourceSet, KmpComponentCreationConfig>
): IdeDependencyResolver {

    protected fun getArtifactsForComponent(
        component: KmpComponentCreationConfig,
        artifactType: AndroidArtifacts.ArtifactType,
        componentFilter: ((ComponentIdentifier) -> Boolean)?
    ): ArtifactCollection = component
        .variantDependencies
        .compileClasspath
        .incoming
        .artifactView { config ->
            config.lenient(true)

            componentFilter?.let {
                config.componentFilter(it)
            }
            config.attributes.attribute(
                AndroidArtifacts.ARTIFACT_TYPE,
                artifactType.type
            )
        }.artifacts
}
