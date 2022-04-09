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

package com.android.build.gradle.internal.ide.kmp

import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker.Companion.android
import com.android.build.gradle.internal.ide.kmp.resolvers.BinaryDependencyResolver
import com.android.build.gradle.internal.ide.kmp.resolvers.ProjectDependencyResolver
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport

@OptIn(ExternalKotlinTargetApi::class)
object KotlinIdeImportConfigurator {

    fun configure(
        service: IdeMultiplatformImport,
        sourceSetToCreationConfigMapProvider: () -> Map<KotlinSourceSet, KmpComponentCreationConfig>,
        extraSourceSetsToIncludeInResolution: () -> Set<KotlinSourceSet>
    ) {
        registerDependencyResolvers(
            service,
            sourceSetToCreationConfigMapProvider,
            extraSourceSetsToIncludeInResolution
        )
    }

    private fun registerDependencyResolvers(
        service: IdeMultiplatformImport,
        sourceSetToCreationConfigMapProvider: () -> Map<KotlinSourceSet, KmpComponentCreationConfig>,
        extraSourceSetsToIncludeInResolution: () -> Set<KotlinSourceSet>
    ) {
        val resolutionPhase =
            IdeMultiplatformImport.DependencyResolutionPhase.BinaryDependencyResolution
        // we want to completely control IDE resolution, so specify a high priority for all our
        // resolvers.
        val resolutionLevel = IdeMultiplatformImport.DependencyResolutionLevel.Overwrite

        val androidSourceSetFilter = IdeMultiplatformImport.SourceSetConstraint { sourceSet ->
            sourceSet.android != null || extraSourceSetsToIncludeInResolution().contains(sourceSet)
        }

        service.registerDependencyResolver(
            resolver = BinaryDependencyResolver(
                sourceSetToCreationConfigMap = sourceSetToCreationConfigMapProvider
            ),
            constraint = androidSourceSetFilter,
            phase = resolutionPhase,
            level = resolutionLevel
        )

        service.registerDependencyResolver(
            resolver = ProjectDependencyResolver(
                sourceSetToCreationConfigMap = sourceSetToCreationConfigMapProvider
            ),
            constraint = androidSourceSetFilter,
            phase = resolutionPhase,
            level = resolutionLevel
        )

        service.registerDependencyResolver(
            resolver = IdeDependencyResolver.Empty,
            constraint = androidSourceSetFilter,
            phase = IdeMultiplatformImport.DependencyResolutionPhase.SourcesAndDocumentationResolution,
            level = resolutionLevel
        )
    }
}
