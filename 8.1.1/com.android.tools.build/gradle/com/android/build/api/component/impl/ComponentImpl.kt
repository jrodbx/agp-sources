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

package com.android.build.api.component.impl

import com.android.SdkConstants
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.features.AndroidResourcesCreationConfigImpl
import com.android.build.api.component.impl.features.AssetsCreationConfigImpl
import com.android.build.api.component.impl.features.InstrumentationCreationConfigImpl
import com.android.build.api.component.impl.features.ManifestPlaceholdersCreationConfigImpl
import com.android.build.api.component.impl.features.ResValuesCreationConfigImpl
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.features.AndroidResourcesCreationConfig
import com.android.build.gradle.internal.component.features.AssetsCreationConfig
import com.android.build.gradle.internal.component.features.InstrumentationCreationConfig
import com.android.build.gradle.internal.component.features.ManifestPlaceholdersCreationConfig
import com.android.build.gradle.internal.component.features.ResValuesCreationConfig
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.PublishableComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dependency.getProvidedClasspath
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.OptionalBooleanOption.ENABLE_API_MODELING_AND_GLOBAL_SYNTHETICS
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import org.gradle.api.JavaVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File
import java.util.Locale
import java.util.function.Predicate

abstract class ComponentImpl<DslInfoT: ComponentDslInfo>(
    open val componentIdentity: ComponentIdentity,
    final override val buildFeatures: BuildFeatureValues,
    protected val dslInfo: DslInfoT,
    final override val variantDependencies: VariantDependencies,
    private val variantSources: VariantSources,
    override val paths: VariantPathHelper,
    override val artifacts: ArtifactsImpl,
    private val variantData: BaseVariantData? = null,
    override val taskContainer: MutableTaskContainer,
    protected val internalServices: VariantServices,
    final override val services: TaskCreationServices,
    final override val global: GlobalTaskCreationConfig,
): Component, ComponentCreationConfig, ComponentIdentity by componentIdentity {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------
    override val namespace: Provider<String> =
        internalServices.providerOf(
            type = String::class.java,
            value = dslInfo.namespace
        )

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        instrumentation.transformClassesWith(
            classVisitorFactoryImplClass,
            scope,
            instrumentationParamsConfig
        )
    }

    override fun setAsmFramesComputationMode(mode: FramesComputationMode) {
        instrumentation.setAsmFramesComputationMode(mode)
    }

    override val javaCompilation: JavaCompilation =
        JavaCompilationImpl(
            dslInfo.javaCompileOptionsSetInDSL,
            buildFeatures.dataBinding,
            internalServices)

    override val sources by lazy {
        SourcesImpl(
            DefaultSourcesProviderImpl(this, variantSources),
            internalServices,
            multiFlavorSourceProvider = variantSources.multiFlavorSourceProvider,
            variantSourceProvider = variantSources.variantSourceProvider,
        ).also { sourcesImpl ->
            // add all source sets extra directories added by the user
            variantSources.customSourceList.forEach{ (_, srcEntries) ->
                srcEntries.forEach { customSourceDirectory ->
                    sourcesImpl.extras.maybeCreate(customSourceDirectory.sourceTypeName).also {
                        (it as FlatSourceDirectoriesImpl).addSource(
                                FileBasedDirectoryEntryImpl(
                                    customSourceDirectory.sourceTypeName,
                                    customSourceDirectory.directory,
                                )
                            )
                    }
                }
            }
        }
    }

    override val instrumentation: Instrumentation
        get() = instrumentationCreationConfig.instrumentation

    override val compileClasspath: FileCollection by lazy {
        getJavaClasspath(
            ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactType.CLASSES_JAR,
            generatedBytecodeKey = null
        )
    }

    override val compileConfiguration = variantDependencies.compileClasspath

    override val runtimeConfiguration = variantDependencies.runtimeClasspath

    override val annotationProcessorConfiguration =
        variantDependencies.annotationProcessorConfiguration!!

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val componentType: ComponentType
        get() = dslInfo.componentType

    override val dirName: String
        get() = paths.dirName

    final override val baseName: String
        get() = paths.baseName

    override val productFlavorList: List<ProductFlavor> = dslInfo.componentIdentity.productFlavors.map {
        ProductFlavor(it.first, it.second)
    }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    override fun computeTaskName(prefix: String): String =
        prefix.appendCapitalized(name)

    override fun computeTaskName(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    // -------------------------
    // File location computation. Previously located in VariantScope, these are here
    // temporarily until we fully move away from them.

    // Precomputed file paths.
    final override fun getJavaClasspath(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): FileCollection = getJavaClasspath(
        this, configType, classesType, generatedBytecodeKey
    )

    override val providedOnlyClasspath: FileCollection by lazy {
        getProvidedClasspath(
            compileClasspath = variantDependencies.getArtifactCollection(
                ConsumedConfigType.COMPILE_CLASSPATH,
                ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            ),
            runtimeClasspath = variantDependencies.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        )
    }

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
    override fun publishBuildArtifacts() {
        com.android.build.gradle.internal.scope.publishBuildArtifacts(
            this,
            (dslInfo as? PublishableComponentDslInfo)?.publishInfo
        )
    }

    override val modelV1LegacySupport = ModelV1LegacySupportImpl(dslInfo, variantSources)

    override val oldVariantApiLegacySupport: OldVariantApiLegacySupport? by lazy {
        OldVariantApiLegacySupportImpl(
            this,
            dslInfo,
            variantData!!,
            variantSources,
            internalServices
        )
    }

    override val assetsCreationConfig: AssetsCreationConfig by lazy {
        AssetsCreationConfigImpl(
            dslInfo.androidResourcesDsl!!,
            internalServices
        ) { androidResourcesCreationConfig }
    }

    override val androidResourcesCreationConfig: AndroidResourcesCreationConfig? by lazy {
        if (buildFeatures.androidResources) {
            AndroidResourcesCreationConfigImpl(
                this,
                dslInfo,
                dslInfo.androidResourcesDsl!!,
                internalServices,
            )
        } else {
            null
        }
    }

    override val resValuesCreationConfig: ResValuesCreationConfig? by lazy {
        if (buildFeatures.resValues) {
            ResValuesCreationConfigImpl(
                dslInfo.androidResourcesDsl!!,
                internalServices
            )
        } else {
            null
        }
    }

    override val instrumentationCreationConfig: InstrumentationCreationConfig by lazy {
        InstrumentationCreationConfigImpl(
            this,
            internalServices
        )
    }

    /**
     * Returns the direct (i.e., non-transitive) local file dependencies matching the given
     * predicate
     *
     * @return a non null, but possibly empty FileCollection
     * @param filePredicate the file predicate used to filter the local file dependencies
     */
    override fun computeLocalFileDependencies(filePredicate: Predicate<File>): FileCollection =
        variantDependencies.computeLocalFileDependencies(
            internalServices,
            filePredicate
        )

    /**
     * Returns the packaged local Jars
     *
     * @return a non null, but possibly empty set.
     */
    override fun computeLocalPackagedJars(): FileCollection =
        computeLocalFileDependencies { file ->
            file
                .name
                .lowercase(Locale.US)
                .endsWith(SdkConstants.DOT_JAR)
        }

    override fun getArtifactName(name: String) = name

    protected fun createManifestPlaceholdersCreationConfig(
            placeholders: Map<String, String>?): ManifestPlaceholdersCreationConfig {
        val legacyApiManifestPlaceholders = oldVariantApiLegacySupport?.manifestPlaceholdersDslInfo?.placeholders
                ?: mapOf()
        val allPlaceholders = (placeholders ?: mapOf()) + legacyApiManifestPlaceholders
        return ManifestPlaceholdersCreationConfigImpl(
                allPlaceholders,
                internalServices
        )
    }


    fun isApiModelingEnabled(): Boolean {
        return internalServices.projectOptions.get(ENABLE_API_MODELING_AND_GLOBAL_SYNTHETICS)
            ?: !debuggable
    }

    fun isGlobalSyntheticsEnabled(): Boolean {
        return internalServices.projectOptions.get(ENABLE_API_MODELING_AND_GLOBAL_SYNTHETICS)
            ?: (!debuggable || isJavaLanguageLevelAbove14())
    }

    private fun isJavaLanguageLevelAbove14(): Boolean {
        return global.compileOptions.sourceCompatibility.isCompatibleWith(JavaVersion.VERSION_14) &&
                global.compileOptions.targetCompatibility.isCompatibleWith(JavaVersion.VERSION_14)
    }
}
