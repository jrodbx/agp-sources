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

import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryCacheImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryServiceImpl
import com.android.build.gradle.internal.ide.kmp.KotlinAndroidSourceSetMarker.Companion.android
import com.android.build.gradle.internal.ide.kmp.resolvers.AndroidLibraryDependencyResolver
import com.android.build.gradle.internal.ide.kmp.resolvers.KotlinModelBuildingHook
import com.android.build.gradle.internal.ide.kmp.resolvers.ProjectDependencyResolver
import com.android.build.gradle.internal.ide.kmp.serialization.AndroidExtrasSerializationExtension
import com.android.build.gradle.internal.ide.v2.GlobalSyncService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.getBuildService
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport
import org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeBinaryDependencyResolver

/**
 * A singleton that is responsible for registering services that runs during the kotlin model
 * building phase to resolve dependencies, serialize and deserialize extras.
 */
@OptIn(ExternalKotlinTargetApi::class)
internal object KotlinIdeImportConfigurator {

    /**
     * Configures IDE import hooks. Since this is called on creating the target which happens on
     * applying the plugin, we pass in lazy values that will not be available until afterEvaluate.
     *
     * @param sourceSetToCreationConfigMap the default android sourceSets that map to a single
     *          compilation that maps then to an android component.
     * @param extraSourceSetsToIncludeInResolution this includes sourceSets that only exist in
     *          android compilation, such as a sourceSet that was added to an android compilation
     *          or the common sourceSet in an android-only kmp module. Those sourceSets we treat
     *          as fully android in order to have full IDE android support for them.
     */
    fun configure(
        project: Project,
        androidTarget: Lazy<KotlinMultiplatformAndroidTarget>,
        androidExtension: KotlinMultiplatformAndroidExtensionImpl,
        service: IdeMultiplatformImport,
        sourceSetToCreationConfigMap: Lazy<Map<KotlinSourceSet, KmpComponentCreationConfig>>,
        extraSourceSetsToIncludeInResolution: Lazy<Set<KotlinSourceSet>>
    ) {
        registerDependencyResolvers(
            project,
            androidTarget,
            androidExtension,
            service,
            sourceSetToCreationConfigMap,
            extraSourceSetsToIncludeInResolution
        )

        registerExtrasSerializers(
            service
        )
    }

    private fun registerDependencyResolvers(
        project: Project,
        androidTarget: Lazy<KotlinMultiplatformAndroidTarget>,
        androidExtension: KotlinMultiplatformAndroidExtensionImpl,
        service: IdeMultiplatformImport,
        sourceSetToCreationConfigMap: Lazy<Map<KotlinSourceSet, KmpComponentCreationConfig>>,
        extraSourceSetsToIncludeInResolution: Lazy<Set<KotlinSourceSet>>
    ) {
        val libraryResolver = LibraryResolver(
            project = project,
            libraryService = getBuildService(
                project.gradle.sharedServices,
                GlobalSyncService::class.java
            ).get().let { globalLibraryBuildService ->
                LibraryServiceImpl(
                    LibraryCacheImpl(
                        globalLibraryBuildService.stringCache,
                        globalLibraryBuildService.localJarCache
                    )
                )
            },
            sourceSetToCreationConfigMap = sourceSetToCreationConfigMap
        )

        val resolutionPhase =
            IdeMultiplatformImport.DependencyResolutionPhase.BinaryDependencyResolution
        // we want to completely control IDE resolution, so specify a very high priority for all our
        // resolvers.
        val resolutionPriority = IdeMultiplatformImport.Priority.veryHigh

        val androidSourceSetFilter = IdeMultiplatformImport.SourceSetConstraint { sourceSet ->
            sourceSet.android != null ||
                    extraSourceSetsToIncludeInResolution.value.contains(sourceSet)
        }

        // Use the kotlin class IdeBinaryDependencyResolver to resolve dependencies on binary
        // artifacts, we resolve the compile classpath configuration to the classes jar artifact
        // to run the artifact transforms if necessary.
        service.registerDependencyResolver(
            resolver = IdeBinaryDependencyResolver(
                artifactResolutionStrategy = IdeBinaryDependencyResolver.ArtifactResolutionStrategy.ResolvableConfiguration(
                    configurationSelector = { sourceSet ->
                        sourceSetToCreationConfigMap.value[sourceSet]?.variantDependencies?.compileClasspath
                    },
                    setupArtifactViewAttributes = {
                        attribute(
                            AndroidArtifacts.ARTIFACT_TYPE,
                            AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                        )
                    },
                    componentFilter = {
                        it !is ProjectComponentIdentifier
                    }
                )
            ),
            constraint = androidSourceSetFilter,
            phase = resolutionPhase,
            priority = resolutionPriority
        )

        // Register a resolver to resolve dependencies on modules.
        service.registerDependencyResolver(
            resolver = ProjectDependencyResolver(
                libraryResolver = libraryResolver,
                sourceSetToCreationConfigMap = sourceSetToCreationConfigMap
            ),
            constraint = androidSourceSetFilter,
            phase = resolutionPhase,
            priority = resolutionPriority
        )

        // Register an additional artifact resolver that adds extra data to dependencies on android
        // libraries (AARs), the extra data is needed as the IDE needs to watch the exploded
        // content of the AAR such as the android manifest and the R.txt.
        service.registerAdditionalArtifactResolver(
            resolver = AndroidLibraryDependencyResolver(
                libraryResolver = libraryResolver,
                sourceSetToCreationConfigMap = sourceSetToCreationConfigMap
            ),
            constraint = androidSourceSetFilter,
            phase = IdeMultiplatformImport.AdditionalArtifactResolutionPhase.PreAdditionalArtifactResolution,
            priority = resolutionPriority
        )

        // Register a resolver that exists as a workaround for the kotlin plugin not providing hooks
        // to run code in the model building phase. We need to run in the model building phase
        // explicitly since we access values that will not be finalized until model building, such
        // as sourceSet dependency graphs and DSL values.
        service.registerAdditionalArtifactResolver(
            resolver = KotlinModelBuildingHook(
                project = project,
                mainVariant = lazy(LazyThreadSafetyMode.NONE) {
                    sourceSetToCreationConfigMap.value.values.filterIsInstance<KmpVariantImpl>().first()
               },
                androidTarget = androidTarget,
                androidExtension = androidExtension
            ),
            constraint = IdeMultiplatformImport.SourceSetConstraint.unconstrained,
            phase = IdeMultiplatformImport.AdditionalArtifactResolutionPhase.PreAdditionalArtifactResolution,
            priority = resolutionPriority
        )

        // Add an empty resolver to bypass sources and documentation resolution since we have our
        // own mechanism in android studio to fetch these using the extra information sent with
        // the libraries.
        service.registerDependencyResolver(
            resolver = IdeDependencyResolver.empty,
            constraint = androidSourceSetFilter,
            phase = IdeMultiplatformImport.DependencyResolutionPhase.SourcesAndDocumentationResolution,
            priority = resolutionPriority
        )
    }

    private fun registerExtrasSerializers(service: IdeMultiplatformImport) {
        service.registerExtrasSerializationExtension(
            AndroidExtrasSerializationExtension()
        )
    }
}
