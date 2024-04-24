/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.AnnotationProcessor
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.baseName
import com.android.build.api.variant.impl.fullName
import com.android.build.gradle.api.AnnotationProcessorOptions
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.ApkProducingComponentDslInfo
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.ManifestPlaceholdersDslInfo
import com.android.build.gradle.internal.core.dsl.impl.ComponentDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.ManifestPlaceholdersDslInfoImpl
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs.Companion.getVariantPublishingSpec
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.process.CommandLineArgumentProvider
import java.io.Serializable
import java.util.concurrent.atomic.AtomicBoolean

class OldVariantApiLegacySupportImpl(
    private val component: ComponentCreationConfig,
    private val dslInfo: ComponentDslInfo,
    override val variantData: BaseVariantData,
    override val variantSources: VariantSources,
    private val internalServices: VariantServices
): OldVariantApiLegacySupport {

    override val buildTypeObj: BuildType
        get() = (dslInfo as ComponentDslInfoImpl).buildTypeObj
    override val productFlavorList: List<ProductFlavor>
        get() = (dslInfo as MultiVariantComponentDslInfo).productFlavorList
    override val mergedFlavor: MergedFlavor
        get() = (dslInfo as ComponentDslInfoImpl).mergedFlavor
    override val dslSigningConfig: com.android.build.gradle.internal.dsl.SigningConfig? =
        (dslInfo as? ApkProducingComponentDslInfo)?.signingConfig

    override val manifestPlaceholdersDslInfo: ManifestPlaceholdersDslInfo? by lazy(LazyThreadSafetyMode.NONE) {
        ManifestPlaceholdersDslInfoImpl(
                mergedFlavor,
                buildTypeObj
        )
    }

    /**
     * The old variant API runs after the new variant API, yet we need to make sure that whatever
     * method used by the users (old variant API or new variant API), we end up with the same
     * storage so the information is visible to both old and new variant API users.
     *
     * The new Variant API does not allow for reading (at least not without doing an explicit
     * [org.gradle.api.provider.Provider.get] call. However, the old variant API was providing
     * reading access.
     *
     * In order to use the same storage, an implementation of the old variant objects (List and Map)
     * need to be proxied to the new Variant API storage (ListProperty and MapProperty). It is not
     * possible to do the reverse proxy'ing since the storage must be able to store
     * [org.gradle.api.provider.Provider<T>] which a plain java List cannot do.
     *
     * When the user reads information using the old variant API, there is no choice but doing a
     * [org.gradle.api.provider.Provider.get] call which can fail during old variant API execution
     * since some of these providers can be obtained from a Task execution.
     *
     * Therefore, only allow access to the old variant API when the compatibility flag is set.
     */
    class JavaCompileOptionsForOldVariantAPI(
        private val services: BaseServices,
        private val annotationProcessor: AnnotationProcessor
    ): JavaCompileOptions {
        // Initialize the wrapper instance that will be returned on each call.
        private val _annotationProcessorOptions = object: AnnotationProcessorOptions {
            private val _classNames = MutableListBackedUpWithListProperty(
                annotationProcessor.classNames,
            "AnnotationProcessorOptions.classNames")
            private val _arguments = MutableMapBackedUpWithMapProperty(
                annotationProcessor.arguments,
                "AnnotationProcessorOptions.arguments",
            )

            override fun getClassNames(): MutableList<String> = _classNames

            override fun getArguments(): MutableMap<String, String> = _arguments

            override fun getCompilerArgumentProviders(): MutableList<CommandLineArgumentProvider> =
                annotationProcessor.argumentProviders
        }
        override val annotationProcessorOptions: AnnotationProcessorOptions
            get() {
                if (!services.projectOptions.get(BooleanOption.ENABLE_LEGACY_API)) {
                    services.issueReporter
                        .reportError(
                            IssueReporter.Type.GENERIC,
                            RuntimeException(
            """
            Access to deprecated legacy com.android.build.gradle.api.BaseVariant.getJavaCompileOptions requires compatibility mode for Property values in new com.android.build.api.variant.AnnotationProcessorOptions
            $ENABLE_LEGACY_API
            """.trimIndent()
                            )
                        )
                    // return default value during sync
                    return object: AnnotationProcessorOptions {
                        override fun getClassNames(): MutableList<String> = mutableListOf()

                        override fun getArguments(): MutableMap<String, String> = mutableMapOf()

                        override fun getCompilerArgumentProviders(): MutableList<CommandLineArgumentProvider>  = mutableListOf()
                    }
                }
                return _annotationProcessorOptions
            }
    }

    override val oldVariantApiJavaCompileOptions: JavaCompileOptions =
        JavaCompileOptionsForOldVariantAPI(
            component.services,
            component.javaCompilation.annotationProcessor
        )

    override val outputs: VariantOutputList by lazy(LazyThreadSafetyMode.NONE) {
        if (component is ApplicationCreationConfig) {
            return@lazy component.outputs
        }

        val versionCodeProperty = if (component is DynamicFeatureCreationConfig) {
            component.baseModuleVersionCode
        } else {
            internalServices.nullablePropertyOf(Int::class.java, null).also {
                it.disallowChanges()
            }
        }

        val versionNameProperty = if (component is DynamicFeatureCreationConfig) {
            component.baseModuleVersionName
        } else {
            internalServices.nullablePropertyOf(String::class.java, null).also {
                it.disallowChanges()
            }
        }

        return@lazy VariantOutputList(
            getVariantOutputs(
                variantOutputConfiguration = VariantOutputConfigurationImpl(),
                versionCodeProperty = versionCodeProperty,
                versionNameProperty = versionNameProperty,
                outputFileName = (component as? LibraryCreationConfig)?.aarOutputFileName
            )
        )
    }

    private fun getVariantOutputs(
        variantOutputConfiguration: VariantOutputConfiguration,
        versionCodeProperty: Property<Int?>,
        versionNameProperty: Property<String?>,
        outputFileName: Property<String>?
    ): List<VariantOutputImpl> {
        return listOf(
            VariantOutputImpl(
                versionCodeProperty,
                versionNameProperty,
                internalServices.newPropertyBackingDeprecatedApi(Boolean::class.java, true),
                variantOutputConfiguration,
                variantOutputConfiguration.baseName(component),
                variantOutputConfiguration.fullName(component),
                outputFileName ?:
                internalServices.newPropertyBackingDeprecatedApi(
                    String::class.java,
                    internalServices.projectInfo.getProjectBaseName().map {
                        component.paths.getOutputFileName(
                            it,
                            variantOutputConfiguration.baseName(component)
                        )
                    },
                )
            )
        )
    }

    override fun getJavaClasspathArtifacts(
        configType: AndroidArtifacts.ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): ArtifactCollection {
        val mainCollection =
            component.variantDependencies.getArtifactCollection(
                configType,
                AndroidArtifacts.ArtifactScope.ALL,
                classesType
            )
        val extraArtifact = component.services.provider {
            variantData.getGeneratedBytecode(generatedBytecodeKey)
        }
        val combinedCollection = component.services.fileCollection(
            mainCollection.artifactFiles, extraArtifact
        )
        val extraCollection = ArtifactCollectionWithExtraArtifact.makeExtraCollection(
            mainCollection,
            combinedCollection,
            extraArtifact,
            component.services.projectInfo.path
        )

        return (component as? TestComponentCreationConfig)?.onTestedVariant { testedVariant ->
            // This is required because of http://b/150500779. Kotlin Gradle plugin relies on
            // TestedComponentIdentifierImpl being present in the returned artifact collection, as
            // artifacts with that identifier type are added to friend paths to kotlinc invocation.
            // Because jar containing all classes of the main artifact is in the classpath when
            // compiling test, we need to add TestedComponentIdentifierImpl artifact with that file.
            // This is needed when compiling test variants that access internal members.
            val internalArtifactType = getVariantPublishingSpec(testedVariant.componentType)
                .getSpec(classesType, configType.publishedTo)!!.outputType

            @Suppress("USELESS_CAST") // Explicit cast needed here.
            val testedAllClasses: Provider<FileCollection> =
                component.services.provider {
                    component.services.fileCollection(
                        testedVariant.artifacts.get(internalArtifactType)
                    ) as FileCollection
                }
            val combinedCollectionForTest = component.services.fileCollection(
                combinedCollection, testedAllClasses, testedAllClasses
            )

            ArtifactCollectionWithExtraArtifact.makeExtraCollectionForTest(
                extraCollection,
                combinedCollectionForTest,
                testedAllClasses,
                component.services.projectInfo.path,
                null
            )
        } ?: extraCollection
    }

    private var allRawAndroidResources: ConfigurableFileCollection? = null

    override fun getAllRawAndroidResources(component: ComponentCreationConfig): FileCollection {
        if (allRawAndroidResources != null) {
            return allRawAndroidResources!!
        }
        allRawAndroidResources = component.services.fileCollection().also { fileCollection ->
            fileCollection.from(
                component.variantDependencies
                    .getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.ANDROID_RES
                    )
                    .artifactFiles
            )

            fileCollection.from(
                component.services.fileCollection(
                    variantData.extraGeneratedResFolders
                ).builtBy(listOfNotNull(variantData.extraGeneratedResFolders.builtBy))
            )

            component.taskContainer.generateApkDataTask?.let {
                fileCollection.from(component.artifacts.get(InternalArtifactType.MICRO_APK_RES))
            }

            component.sources.res { resSources ->
                fileCollection.from(
                    resSources.getVariantSources().map { directoryEntries ->
                        directoryEntries.directoryEntries
                            .map {
                                if (it is TaskProviderBasedDirectoryEntryImpl) {
                                    it.directoryProvider
                                } else {
                                    it.asFiles(
                                      component.services.provider {
                                          component.services.projectInfo.projectDirectory
                                      })
                                }
                            }
                    }
                )
            }
        }

        return allRawAndroidResources!!
    }

    override fun addBuildConfigField(type: String, key: String, value: Serializable, comment: String?) {
        component.buildConfigCreationConfig?.buildConfigFields?.put(
            key, BuildConfigField(type, value, comment)
        )
    }

    override fun handleMissingDimensionStrategy(
        dimension: String,
        alternatedValues: List<String>
    ) {

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        val requestedValue = VariantManager.getModifiedName(component.name)
        val attributeKey = ProductFlavorAttr.of(dimension)
        val attributeValue: ProductFlavorAttr = component.services.named(
            ProductFlavorAttr::class.java, requestedValue
        )

        component.variantDependencies.compileClasspath.attributes.attribute(attributeKey, attributeValue)
        component.variantDependencies.runtimeClasspath.attributes.attribute(attributeKey, attributeValue)
        component.variantDependencies
            .annotationProcessorConfiguration!!
            .attributes
            .attribute(attributeKey, attributeValue)

        // then add the fallbacks which contain the actual requested value
        DependencyConfigurator.addFlavorStrategy(
            component.services.dependencies.attributesSchema,
            dimension,
            ImmutableMap.of(requestedValue, alternatedValues)
        )
    }


    // registrar for all post old variant API actions.
    private val postOldVariantActions = mutableListOf<() -> Unit>()

    private val oldVariantAPICompleted = AtomicBoolean(false)

    override fun oldVariantApiCompleted() {
        synchronized(postOldVariantActions) {
            oldVariantAPICompleted.set(true)
            postOldVariantActions.forEach { action -> action() }
            postOldVariantActions.clear()
        }
    }

    override fun registerPostOldVariantApiAction(action: () -> Unit) {
        synchronized(postOldVariantActions) {
            if (oldVariantAPICompleted.get()) {
                action()
            } else {
                postOldVariantActions.add(action)
            }
        }
    }
}
