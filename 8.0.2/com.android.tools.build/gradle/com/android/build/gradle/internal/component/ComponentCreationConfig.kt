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
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.InternalSources
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.AssetsCreationConfig
import com.android.build.gradle.internal.component.features.BuildConfigCreationConfig
import com.android.build.gradle.internal.component.features.InstrumentationCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.component.legacy.ModelV1LegacySupport
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.core.ComponentType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
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

    val minSdkVersion: AndroidVersion
    val targetSdkVersion: AndroidVersion
    val targetSdkVersionOverride: AndroidVersion?

    // ---------------------------------------------------------------------------------------------
    // OPTIONAL FEATURES
    // ---------------------------------------------------------------------------------------------

    val assetsCreationConfig: AssetsCreationConfig?
    val androidResourcesCreationConfig: AndroidResourcesCreationConfig?
    val resValuesCreationConfig: ResValuesCreationConfig?
    val buildConfigCreationConfig: BuildConfigCreationConfig?
    val instrumentationCreationConfig: InstrumentationCreationConfig?
    val manifestPlaceholdersCreationConfig: ManifestPlaceholdersCreationConfig?

    // TODO figure out whether these properties are needed by all
    // TODO : remove as it is now in Variant.
    // ---------------------------------------------------------------------------------------------
    val outputs: VariantOutputList

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

    /**
     * Access to the global task creation configuration
     */
    val global: GlobalTaskCreationConfig

    // ---------------------------------------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------------------------------------

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

    fun addVariantOutput(
        variantOutputConfiguration: VariantOutputConfiguration,
        outputFileName: Provider<String>? = null
    )

    fun computeLocalFileDependencies(filePredicate: Predicate<File>): FileCollection

    fun computeLocalPackagedJars(): FileCollection

    /**
     * Returns the artifact name modified depending on the component type.
     */
    fun getArtifactName(name: String): String

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
    fun publishBuildArtifacts()

    fun <T: Component> createUserVisibleVariantObject(
        projectServices: ProjectServices,
        operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
        stats: GradleBuildVariant.Builder?
    ): T

    // ---------------------------------------------------------------------------------------------
    // LEGACY SUPPORT
    // ---------------------------------------------------------------------------------------------

    @Deprecated("DO NOT USE, this is just for model v1 legacy support")
    val modelV1LegacySupport: ModelV1LegacySupport

    @Deprecated("DO NOT USE, this is just for old variant API legacy support")
    val oldVariantApiLegacySupport: OldVariantApiLegacySupport?

    /**
     * Notification that the old variant API ran successfully.
     */
    fun oldVariantApiCompleted()

    /**
     * Registers an action to run once the old variant API has completed.
     * The action will run in an undetermined thread.
     *
     * Note that if the variant API has already completed, the action will run
     * immediately in the calling thread.
     *
     * @param action lambda to run once old variant API completed.
     */
    fun registerPostOldVariantApiAction(action: () -> Unit)
}
