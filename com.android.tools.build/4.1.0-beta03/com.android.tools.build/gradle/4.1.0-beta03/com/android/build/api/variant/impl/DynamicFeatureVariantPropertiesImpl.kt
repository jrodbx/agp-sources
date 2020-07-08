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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.AaptOptions
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class DynamicFeatureVariantPropertiesImpl @Inject constructor(
    componentIdentity: ComponentIdentity,
    buildFeatureValues: BuildFeatureValues,
    variantDslInfo: VariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    variantScope: VariantScope,
    variantData: BaseVariantData,
    transformManager: TransformManager,
    internalServices: VariantPropertiesApiServices,
    taskCreationServices: TaskCreationServices,
    globalScope: GlobalScope
) : VariantPropertiesImpl(
    componentIdentity,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    internalServices,
    taskCreationServices,
    globalScope
), DynamicFeatureVariantProperties, DynamicFeatureCreationConfig {

    /*
     * Providers of data coming from the base modules. These are loaded just once and finalized.
     */
    private val baseModuleMetadata: Provider<ModuleMetadata> = instantiateBaseModuleMetadata(variantDependencies)
    private val featureSetMetadata: Provider<FeatureSetMetadata>  = instantiateFeatureSetMetadata(variantDependencies)

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, baseModuleMetadata.map { it.applicationId })

    override val aaptOptions: AaptOptions by lazy {
        initializeAaptOptionsFromDsl(
            globalScope.extension.aaptOptions,
            internalServices
        )
    }

    override fun aaptOptions(action: AaptOptions.() -> Unit) {
        action.invoke(aaptOptions)
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // always false for this type
    override val embedsMicroApp: Boolean
        get() = false

    override val testOnlyApk: Boolean
        get() = variantScope.isTestOnly

    override val baseModuleDebuggable: Provider<Boolean> = internalServices.providerOf(
        Boolean::class.java,
        baseModuleMetadata.map { it.debuggable })

    override val featureName: Provider<String> =
        internalServices.providerOf(String::class.java, featureSetMetadata.map {
            val path = globalScope.project.path
            it.getFeatureNameFor(path)
                ?: throw RuntimeException("Failed to find feature name for $path in ${it.sourceFile}")
        })

    /**
     * resource offset for resource compilation of a feature.
     * This is computed by the base module and consumed by the features. */
    override val resOffset: Provider<Int> =
        internalServices.providerOf(Int::class.java, featureSetMetadata.map {
            val path = globalScope.project.path
            it.getResOffsetFor(path)
                ?: throw RuntimeException("Failed to find resource offset for $path in ${it.sourceFile}")
        })

    override val shouldPackageDesugarLibDex: Boolean = false

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private fun instantiateBaseModuleMetadata(
        variantDependencies: VariantDependencies
    ): Provider<ModuleMetadata> {
        val artifact = variantDependencies
            .getArtifactFileCollection(
                ConsumedConfigType.COMPILE_CLASSPATH,
                ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA
            )

        // Have to wrap the return of artifact.elements.map because we cannot call
        // finalizeValueOnRead directly on Provider
        return internalServices.providerOf(
            ModuleMetadata::class.java,
            artifact.elements.map { ModuleMetadata.load(it.single().asFile) })
    }


    private fun instantiateFeatureSetMetadata(
        variantDependencies: VariantDependencies
    ): Provider<FeatureSetMetadata> {
        val artifact = variantDependencies.getArtifactFileCollection(
            ConsumedConfigType.COMPILE_CLASSPATH,
            ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.FEATURE_SET_METADATA
        )

        // Have to wrap the return of artifact.elements.map because we cannot call
        // finalizeValueOnRead directly on Provider
        return internalServices.providerOf(
            FeatureSetMetadata::class.java,
            artifact.elements.map { FeatureSetMetadata.load(it.single().asFile) })
    }

    // version name is coming from the base module via a published artifact, and therefore
    // is ready only
    // The public API does not expose this so this is ok, but it's safer to make it read-only
    // directly to catch potential errors.
    // The old API has a check for this type of plugins to avoid calling set() on it.
    override fun createVersionNameProperty(): Property<String?> =
        internalServices.nullablePropertyOf(
            String::class.java,
            baseModuleMetadata.map { it.versionName },
            "$name::versionName"
        ).also {
            it.disallowChanges()
            it.finalizeValueOnRead()
        }

    // version code is coming from the base module via a published artifact, and therefore
    // is ready only
    // The public API does not expose this so this is ok, but it's safer to make it read-only
    // directly to catch potential errors.
    // The old API has a check for this type of plugins to avoid calling set() on it.
    override fun createVersionCodeProperty() : Property<Int?> =
        internalServices.nullablePropertyOf(
            Int::class.java,
            baseModuleMetadata.map { it.versionCode?.toInt() },
            id = "$name::versionCode"
        ).also {
            it.disallowChanges()
            it.finalizeValueOnRead()
        }
}
