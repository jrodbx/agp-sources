/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.component

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.LifecycleTasksImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.impl.AndroidResourcesImpl
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.InstrumentationCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.PrivacySandboxCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.component.legacy.ModelV1LegacySupport
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentType
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File
import java.util.function.Predicate

/**
 * Base of the interfaces used internally to access *PropertiesImpl object.
 *
 * This allows a graph hierarchy rather than a strict tree, in order to have multiple
 * supertype and make some tasks receive a generic type that does not fit the actual
 * implementation hierarchy (see for instance ApkCreationConfig)
 */
interface ComponentCreationConfig : ComponentIdentity {
    // ---------------------------------------------------------------------------------------------
    // BASIC INFO
    // ---------------------------------------------------------------------------------------------
    val dirName: String
    val baseName: String
    val componentType: ComponentType
    val description: String
    val productFlavorList: List<ProductFlavor>
    fun computeTaskName(prefix: String, suffix: String): String
    fun computeTaskName(prefix: String): String

    // ---------------------------------------------------------------------------------------------
    // NEEDED BY ALL COMPONENTS
    // ---------------------------------------------------------------------------------------------

    // needed by resource compilation/link
    val applicationId: Provider<String>
    val namespace: Provider<String>
    val debuggable: Boolean
    val minSdk: AndroidVersion

    // ---------------------------------------------------------------------------------------------
    // OPTIONAL FEATURES
    // ---------------------------------------------------------------------------------------------

    /**
     * Will be null when corresponding feature processing is turned off for this component.
     */
    val androidResourcesCreationConfig: AndroidResourcesCreationConfig?
    val resValuesCreationConfig: ResValuesCreationConfig?
    val buildConfigCreationConfig: BuildConfigCreationConfig?
    val instrumentationCreationConfig: InstrumentationCreationConfig?
    val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig?
    val privacySandboxCreationConfig: PrivacySandboxCreationConfig?

    /**
     * android resources can be null for components like KMP that do not support android resources.
     * Having a non null instance does not mean that android resources processing is turned on for
     * this component.
     */
    val androidResources: AndroidResourcesImpl?

    // ---------------------------------------------------------------------------------------------
    // INTERNAL DELEGATES
    // ---------------------------------------------------------------------------------------------
    val buildFeatures: BuildFeatureValues
    val variantDependencies: VariantDependencies
    val artifacts: ArtifactsImpl
    val sources: InternalSources
    val taskContainer: MutableTaskContainer
    val paths: VariantPathHelper
    val services: TaskCreationServices
    val lifecycleTasks: LifecycleTasksImpl

    /**
     * Access to the global task creation configuration
     */
    val global: GlobalTaskCreationConfig

    // ---------------------------------------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------------------------------------

    fun finalizeAndLock()

    /**
     * Get the compile classpath for compiling sources in this component
     */
    fun getJavaClasspath(
        configType: AndroidArtifacts.ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any? = null
    ): FileCollection

    val compileClasspath: FileCollection

    val providedOnlyClasspath: FileCollection

    val javaCompilation: JavaCompilation

    fun computeLocalFileDependencies(filePredicate: Predicate<File>): FileCollection

    fun computeLocalPackagedJars(): FileCollection

    /**
     * Returns the artifact name modified depending on the component type.
     */
    fun getArtifactName(name: String): String

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
    fun publishBuildArtifacts()

    // ---------------------------------------------------------------------------------------------
    // LEGACY SUPPORT
    // ---------------------------------------------------------------------------------------------

    @Deprecated("DO NOT USE, this is just for model v1 legacy support")
    val modelV1LegacySupport: ModelV1LegacySupport?

    @Deprecated("DO NOT USE, this is just for old variant API legacy support")
    val oldVariantApiLegacySupport: OldVariantApiLegacySupport?
}
