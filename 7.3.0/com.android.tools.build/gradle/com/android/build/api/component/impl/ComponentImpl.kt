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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.baseName
import com.android.build.api.variant.impl.fullName
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.component.legacy.ModelV1LegacySupport
import com.android.build.gradle.internal.component.legacy.OldVariantApiLegacySupport
import com.android.build.gradle.internal.core.MergedNdkConfig
import com.android.build.gradle.internal.core.ProductFlavor
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.dependency.AsmClassesTransform
import com.android.build.gradle.internal.dependency.RecalculateStackFramesTransform
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.InstrumentationImpl
import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION_FOR_INSTRUMENTATION
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.get
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.has
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.*
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.compiling.BuildConfigType
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.builder.model.VectorDrawablesOptions
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.Callable

abstract class ComponentImpl(
    open val componentIdentity: ComponentIdentity,
    final override val buildFeatures: BuildFeatureValues,
    protected val variantDslInfo: VariantDslInfo,
    final override val variantDependencies: VariantDependencies,
    override val variantSources: VariantSources,
    override val paths: VariantPathHelper,
    override val artifacts: ArtifactsImpl,
    override val variantScope: VariantScope,
    override val variantData: BaseVariantData,
    override val transformManager: TransformManager,
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
            value = variantDslInfo.namespace
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
            variantDslInfo.javaCompileOptions,
            buildFeatures.dataBinding,
            internalServices)

    override val sources: SourcesImpl by lazy {
        SourcesImpl(
            DefaultSourcesProviderImpl(this, variantSources),
            internalServices.projectInfo.projectDirectory,
            internalServices,
            variantSources.variantSourceProvider,
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

    override val instrumentation = InstrumentationImpl(
        services,
        internalServices,
        isLibraryVariant = false
    )

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
        variantDependencies.annotationProcessorConfiguration

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val asmApiVersion = ASM_API_VERSION_FOR_INSTRUMENTATION

    // this is technically a public API for the Application Variant (only)
    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    // Move as direct delegates
    override val taskContainer = variantData.taskContainer

    override val componentType: ComponentType
        get() = variantDslInfo.componentType

    override val dirName: String
        get() = paths.dirName

    override val baseName: String
        get() = paths.baseName

    override val resourceConfigurations: ImmutableSet<String>
        get() = variantDslInfo.resourceConfigurations

    override val description: String
        get() = variantData.description

    override val productFlavorList: List<ProductFlavor> = variantDslInfo.productFlavorList.map {
        ProductFlavor(it)
    }

    // Resource shrinker expects MergeResources task to have all the resources merged and with
    // overlay rules applied, so we have to go through the MergeResources pipeline in case it's
    // enabled, see b/134766811.
    override val isPrecompileDependenciesResourcesEnabled: Boolean
        get() = internalServices.projectOptions[BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES] &&
                !useResourceShrinker()

    override val registeredProjectClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = instrumentation.registeredProjectClassesVisitors

    override val registeredDependenciesClassesVisitors: List<AsmClassVisitorFactory<*>>
        get() = instrumentation.registeredDependenciesClassesVisitors

    override val asmFramesComputationMode: FramesComputationMode
        get() = instrumentation.finalAsmFramesComputationMode

    override val allProjectClassesPostAsmInstrumentation: FileCollection
        get() =
            if (projectClassesAreInstrumented) {
                if (asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                    services.fileCollection(
                            artifacts.get(
                                    FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_CLASSES
                            ),
                            services.fileCollection(
                                    artifacts.get(
                                            FIXED_STACK_FRAMES_ASM_INSTRUMENTED_PROJECT_JARS
                                    )
                            ).asFileTree
                    )
                } else {
                    services.fileCollection(
                            artifacts.get(ASM_INSTRUMENTED_PROJECT_CLASSES),
                            services.fileCollection(
                                    artifacts.get(ASM_INSTRUMENTED_PROJECT_JARS)
                            ).asFileTree
                    )
                }
            } else {
                artifacts.getAllClasses()
            }

    override val projectClassesAreInstrumented: Boolean
        get() = registeredProjectClassesVisitors.isNotEmpty() ||
                (this is ApkCreationConfig && advancedProfilingTransforms.isNotEmpty())

    override val dependenciesClassesAreInstrumented: Boolean
        get() = registeredDependenciesClassesVisitors.isNotEmpty() ||
                (this is ApkCreationConfig && advancedProfilingTransforms.isNotEmpty())

    /**
     * Returns the tested variant. This is null for [VariantImpl] instances
     *

     * This declares is again, even though the public interfaces only have it via
     * [TestComponentProperties]. This is to facilitate places where one cannot use
     * [TestComponentImpl].
     *
     * see [onTestedConfig] for a utility function helping deal with nullability
     */
    override val testedConfig: VariantCreationConfig? = null

    /**
     * Runs an action on the tested variant and return the results of the action.
     *
     * if there is no tested variant this does nothing and returns null.
     */
    override fun <T> onTestedConfig(action: (VariantCreationConfig) -> T?): T? {
        if (componentType.isTestComponent) {
            val tested = testedConfig ?: throw RuntimeException("testedVariant null with type $componentType")
            return action(tested)
        }

        return null
    }

    override fun useResourceShrinker(): Boolean {
        if (componentType.isForTesting || !variantDslInfo.getPostProcessingOptions().resourcesShrinkingEnabled()) {
            return false
        }
        val newResourceShrinker = services.projectOptions[BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER]
        if (componentType.isDynamicFeature) {
            internalServices
                .issueReporter
                .reportError(
                        IssueReporter.Type.GENERIC,
                        "Resource shrinking must be configured for base module.")
            return false
        }
        if (!newResourceShrinker && global.hasDynamicFeatures) {
            val message = String.format(
                "Resource shrinker for multi-apk applications can be enabled via " +
                        "experimental flag: '%s'.",
                BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER.propertyName)
            internalServices
                    .issueReporter
                    .reportError(IssueReporter.Type.GENERIC, message)
            return false
        }
        if (componentType.isAar) {
            internalServices
                .issueReporter
                .reportError(IssueReporter.Type.GENERIC, "Resource shrinker cannot be used for libraries.")
            return false
        }
        if (!variantDslInfo.getPostProcessingOptions().codeShrinkerEnabled()) {
            internalServices
                .issueReporter
                .reportError(
                        IssueReporter.Type.GENERIC,
                        "Removing unused resources requires unused code shrinking to be turned on. See "
                                + "http://d.android.com/r/tools/shrink-resources.html "
                                + "for more information.")
            return false
        }
        return true
    }

    override val androidResourcesEnabled = buildFeatures.androidResources

    // by default, we delegate to the build features flags.
    override val buildConfigEnabled: Boolean
        get() = buildFeatures.buildConfig
    override val externalNativeExperimentalProperties: Map<String, Any>
        get() = variantDslInfo.externalNativeExperimentalProperties

    override val manifestPlaceholders: MapProperty<String, String> by lazy {
        internalServices.mapPropertyOf(
            String::class.java,
            String::class.java,
            variantDslInfo.manifestPlaceholders
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private val variantOutputs = mutableListOf<VariantOutputImpl>()

    // FIXME make internal
    fun addVariantOutput(
            variantOutputConfiguration: VariantOutputConfigurationImpl,
            outputFileName: String? = null
    ): VariantOutputImpl {

        return VariantOutputImpl(
            createVersionCodeProperty(),
            createVersionNameProperty(),
            internalServices.newPropertyBackingDeprecatedApi(Boolean::class.java, true),
            variantOutputConfiguration,
            variantOutputConfiguration.baseName(this),
            variantOutputConfiguration.fullName(this),
            internalServices.newPropertyBackingDeprecatedApi(
                String::class.java,
                outputFileName
                    ?: paths.getOutputFileName(
                        internalServices.projectInfo.getProjectBaseName(),
                        variantOutputConfiguration.baseName(this)
                    ),
            )
        ).also {
            variantOutputs.add(it)
        }
    }

    // default impl for variants that don't actually have versionName
    protected open fun createVersionNameProperty(): Property<String?> {
        val stringValue: String? = null
        return internalServices.nullablePropertyOf(String::class.java, stringValue).also {
            it.disallowChanges()
        }
    }

    // default impl for variants that don't actually have versionCode
    protected open fun createVersionCodeProperty() : Property<Int?> {
        val intValue: Int? = null
        return internalServices.nullablePropertyOf(Int::class.java, intValue).also {
            it.disallowChanges()
        }
    }

    fun computeTaskName(prefix: String): String =
        prefix.appendCapitalized(name)

    override fun computeTaskName(prefix: String, suffix: String): String =
        prefix.appendCapitalized(name, suffix)

    // -------------------------
    // File location computation. Previously located in VariantScope, these are here
    // temporarily until we fully move away from them.

    val generatedResOutputDir: File
        get() = getGeneratedResourcesDir("resValues")

    private fun getGeneratedResourcesDir(name: String): File {
        return FileUtils.join(
            paths.generatedDir().get().asFile,
            listOf("res", name) + paths.directorySegments
        )
    }

    // Precomputed file paths.
    @JvmOverloads
    final override fun getJavaClasspath(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): FileCollection {
        var mainCollection = variantDependencies
            .getArtifactFileCollection(configType, ArtifactScope.ALL, classesType)
        mainCollection = mainCollection.plus(variantData.getGeneratedBytecode(generatedBytecodeKey))
        // Add R class jars to the front of the classpath as libraries might also export
        // compile-only classes. This behavior is verified in CompileRClassFlowTest
        // While relying on this order seems brittle, it avoids doubling the number of
        // files on the compilation classpath by exporting the R class separately or
        // and is much simpler than having two different outputs from each library, with
        // and without the R class, as AGP publishing code assumes there is exactly one
        // artifact for each publication.
        mainCollection =
            internalServices.fileCollection(
                getCompiledRClasses(configType),
                getCompiledBuildConfig(),
                getCompiledManifest(),
                mainCollection
            )
        return mainCollection
    }

    fun getJavaClasspathArtifacts(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any?
    ): ArtifactCollection {
        val mainCollection =
            variantDependencies.getArtifactCollection(configType, ArtifactScope.ALL, classesType)
        val extraArtifact = internalServices.provider(Callable {
            variantData.getGeneratedBytecode(generatedBytecodeKey);
        })
        val combinedCollection = internalServices.fileCollection(
            mainCollection.artifactFiles, extraArtifact
        )
        val extraCollection = ArtifactCollectionWithExtraArtifact.makeExtraCollection(
            mainCollection,
            combinedCollection,
            extraArtifact,
            internalServices.projectInfo.path
        )

        return onTestedConfig { testedVariant ->
            // This is required because of http://b/150500779. Kotlin Gradle plugin relies on
            // TestedComponentIdentifierImpl being present in the returned artifact collection, as
            // artifacts with that identifier type are added to friend paths to kotlinc invocation.
            // Because jar containing all classes of the main artifact is in the classpath when
            // compiling test, we need to add TestedComponentIdentifierImpl artifact with that file.
            // This is needed when compiling test variants that access internal members.
            val internalArtifactType = testedVariant.variantScope.publishingSpec
                .getSpec(classesType, configType.publishedTo)!!.outputType

            @Suppress("USELESS_CAST") // Explicit cast needed here.
            val testedAllClasses: Provider<FileCollection> =
                internalServices.provider(Callable {
                    internalServices.fileCollection(
                        testedVariant.artifacts.get(internalArtifactType)
                    ) as FileCollection
                })
            val combinedCollectionForTest = internalServices.fileCollection(
                combinedCollection, testedAllClasses, testedAllClasses
            )

            ArtifactCollectionWithExtraArtifact.makeExtraCollectionForTest(
                extraCollection,
                combinedCollectionForTest,
                testedAllClasses,
                internalServices.projectInfo.path,
                null
            )
        } ?: extraCollection
    }

    // TODO Move these outside of Variant specific class (maybe GlobalTaskScope?)

    override val manifestArtifactType: InternalArtifactType<Directory>
        get() = if (internalServices.projectOptions[BooleanOption.IDE_DEPLOY_AS_INSTANT_APP])
            InternalArtifactType.INSTANT_APP_MANIFEST
        else
            InternalArtifactType.PACKAGED_MANIFESTS

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs.  */
    open fun publishBuildArtifacts() {
        for (outputSpec in variantScope.publishingSpec.outputs) {
            val buildArtifactType = outputSpec.outputType
            // Gradle only support publishing single file.  Therefore, unless Gradle starts
            // supporting publishing multiple files, PublishingSpecs should not contain any
            // OutputSpec with an appendable ArtifactType.
            if (has(buildArtifactType) && get(buildArtifactType).appendable) {
                throw RuntimeException(
                    "Appendable ArtifactType '${buildArtifactType.name()}' cannot be published."
                )
            }
            val artifactProvider = artifacts.get(buildArtifactType)
            val artifactContainer = artifacts.getArtifactContainer(buildArtifactType)
            if (!artifactContainer.needInitialProducer().get()) {
                val isPublicationConfigs =
                    outputSpec.publishedConfigTypes.any { it.isPublicationConfig }

                if (isPublicationConfigs) {
                    val components = variantDslInfo.publishInfo!!.components
                    for(component in components) {
                        variantScope
                            .publishIntermediateArtifact(
                                artifactProvider,
                                outputSpec.artifactType,
                                outputSpec.publishedConfigTypes.map {
                                    PublishedConfigSpec(it, component) }.toSet(),
                                outputSpec.libraryElements?.let {
                                    internalServices.named(LibraryElements::class.java, it)
                                },
                                componentType.isTestFixturesComponent
                            )
                    }
                } else {
                    variantScope
                        .publishIntermediateArtifact(
                            artifactProvider,
                            outputSpec.artifactType,
                            outputSpec.publishedConfigTypes.map { PublishedConfigSpec(it) }.toSet(),
                            outputSpec.libraryElements?.let {
                                internalServices.named(LibraryElements::class.java, it)
                            },
                            componentType.isTestFixturesComponent
                        )
                }
            }
        }
    }

    // Deprecated, DO NOT USE, this will be removed once we can remove the old variant API.
    // TODO : b/214316660
    internal val allRawAndroidResources: FileCollection by lazy {
        val allRes: ConfigurableFileCollection = services.fileCollection()

        allRes.from(
            variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.ANDROID_RES
                )
                .artifactFiles
        )

        allRes.from(
            services.fileCollection(
                variantData.extraGeneratedResFolders
            ).builtBy(listOfNotNull(variantData.extraGeneratedResFolders.builtBy))
        )

        taskContainer.generateApkDataTask?.let {
            allRes.from(artifacts.get(MICRO_APK_RES))
        }

        allRes.from(sources.res.getVariantSources().map { allRes ->
            allRes.map { directoryEntries ->
                directoryEntries.directoryEntries
                    .map { it.asFiles(services::directoryProperty) }
            }
        })
        allRes
    }

    /**
     * adds databinding sources to the list of sources.
     */
    open fun addDataBindingSources(
        sourceSets: MutableList<DirectoryEntry>
    ) {
        sourceSets.add(
            TaskProviderBasedDirectoryEntryImpl(
                "databinding_generated",
                artifacts.get(DATA_BINDING_BASE_CLASS_SOURCE_OUT),
            )
        )
    }

    /**
     * Returns the path(s) to compiled R classes (R.jar).
     */
    fun getCompiledRClasses(configType: ConsumedConfigType): FileCollection {
        return if (global.namespacedAndroidResources) {
            internalServices.fileCollection().also { fileCollection ->
                val namespacedRClassJar = artifacts.get(COMPILE_R_CLASS_JAR)
                val fileTree = internalServices.fileTree(namespacedRClassJar).builtBy(namespacedRClassJar)
                fileCollection.from(fileTree)
                fileCollection.from(
                    variantDependencies.getArtifactFileCollection(
                        configType, ArtifactScope.ALL, AndroidArtifacts.ArtifactType.SHARED_CLASSES
                    )
                )
                testedConfig?.let {
                    fileCollection.from(it.artifacts.get(COMPILE_R_CLASS_JAR).get())
                }
            }
        } else {
            val componentType = variantDslInfo.componentType

            if (testedConfig == null) {
                // TODO(b/138780301): Also use it in android tests.
                val useCompileRClassInApp = (internalServices
                    .projectOptions[BooleanOption
                    .ENABLE_APP_COMPILE_TIME_R_CLASS]
                        && !componentType.isForTesting)
                if (componentType.isAar || useCompileRClassInApp) {
                    if (androidResourcesEnabled) {
                        internalServices.fileCollection(artifacts.get(COMPILE_R_CLASS_JAR)
                        )
                    } else {
                        internalServices.fileCollection()
                    }
                } else {
                    Preconditions.checkState(
                        componentType.isApk,
                        "Expected APK type but found: $componentType"
                    )

                    internalServices.fileCollection(
                        artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                    )
                }
            } else { // Android test or unit test
                if (componentType === ComponentTypeImpl.ANDROID_TEST) {
                    internalServices.fileCollection(
                        artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                    )
                } else {
                    if (androidResourcesEnabled) {
                        internalServices.fileCollection(variantScope.rJarForUnitTests)
                    } else {
                        internalServices.fileCollection()
                    }
                }
            }
        }
    }

    /**
     * Returns the artifact for the compiled R class
     *
     * This can be null for unit tests without resource support.
     */
    fun getCompiledRClassArtifact(): Provider<RegularFile>? {
        return if (global.namespacedAndroidResources) {
            artifacts.get(COMPILE_R_CLASS_JAR)
        } else {
            val componentType = variantDslInfo.componentType

            if (testedConfig == null) {
                // TODO(b/138780301): Also use it in android tests.
                val useCompileRClassInApp = (internalServices
                    .projectOptions[BooleanOption
                    .ENABLE_APP_COMPILE_TIME_R_CLASS]
                        && !componentType.isForTesting)
                if (componentType.isAar || useCompileRClassInApp) {
                    if (androidResourcesEnabled) {
                        artifacts.get(COMPILE_R_CLASS_JAR)
                    } else {
                        null
                    }
                } else {
                    Preconditions.checkState(
                        componentType.isApk,
                        "Expected APK type but found: $componentType"
                    )

                    artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                }
            } else { // Android test or unit test
                if (componentType === ComponentTypeImpl.ANDROID_TEST) {
                    artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                } else {
                    if (androidResourcesEnabled) {
                        variantScope.rJarForUnitTests
                    } else {
                        null
                    }
                }
            }
        }
    }

     fun getCompiledBuildConfig(): FileCollection {
        val isBuildConfigJar = getBuildConfigType() == BuildConfigType.JAR
        val isAndroidTest = variantDslInfo.componentType == ComponentTypeImpl.ANDROID_TEST
        val isUnitTest = variantDslInfo.componentType == ComponentTypeImpl.UNIT_TEST
        // BuildConfig JAR is not required to be added as a classpath for ANDROID_TEST and UNIT_TEST
        // variants as the tests will use JAR from GradleTestProject which doesn't use testedConfig.
        return if (isBuildConfigJar && !isAndroidTest && !isUnitTest && testedConfig == null) {
            internalServices.fileCollection(
                artifacts.get(
                    InternalArtifactType.COMPILE_BUILD_CONFIG_JAR
                )
            )
        } else {
            internalServices.fileCollection()
        }
    }

    private fun getCompiledManifest(): FileCollection {
        val manifestClassRequired = variantDslInfo.componentType.requiresManifest &&
                services.projectOptions[BooleanOption.GENERATE_MANIFEST_CLASS]
        val isTest = variantDslInfo.componentType.isForTesting
        val isAar = variantDslInfo.componentType.isAar
        return if (manifestClassRequired && !isAar && !isTest && testedConfig == null) {
            internalServices.fileCollection(artifacts.get(COMPILE_MANIFEST_JAR))
        } else {
            internalServices.fileCollection()
        }
    }

    fun handleMissingDimensionStrategy(dimension: String, alternatedValues: ImmutableList<String>) {

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        val requestedValue = VariantManager.getModifiedName(name)
        val attributeKey = ProductFlavorAttr.of(dimension)
        val attributeValue: ProductFlavorAttr = internalServices.named(
            ProductFlavorAttr::class.java, requestedValue
        )

        variantDependencies.compileClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies.runtimeClasspath.attributes.attribute(attributeKey, attributeValue)
        variantDependencies
            .annotationProcessorConfiguration
            .attributes
            .attribute(attributeKey, attributeValue)

        // then add the fallbacks which contain the actual requested value
        DependencyConfigurator.addFlavorStrategy(
            services.projectInfo.getProject().dependencies.attributesSchema,
            dimension,
            ImmutableMap.of(requestedValue, alternatedValues)
        )
    }

    fun getBuildConfigType() : BuildConfigType {
        return if (!buildConfigEnabled) {
            BuildConfigType.NONE
        } else if (services.projectOptions[BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE]
            // TODO(b/224758957): This is wrong we need to check the final build config fields from
            //  the variant API
            && variantDslInfo.getBuildConfigFields().none()
        ) {
            BuildConfigType.JAR
        } else {
            BuildConfigType.JAVA_SOURCE
        }
    }

    override fun configureAndLockAsmClassesVisitors(objectFactory: ObjectFactory) {
        instrumentation.configureAndLockAsmClassesVisitors(objectFactory, asmApiVersion)
    }

    abstract fun <T: Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<out CommonExtension<*, *, *, *>, out VariantBuilder, out Variant>,
            stats: GradleBuildVariant.Builder?
    ): T

    override fun getDependenciesClassesJarsPostAsmInstrumentation(scope: ArtifactScope): FileCollection {
        return if (dependenciesClassesAreInstrumented) {
            if (asmFramesComputationMode == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                variantDependencies.getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        scope,
                        AndroidArtifacts.ArtifactType.CLASSES_FIXED_FRAMES_JAR,
                        RecalculateStackFramesTransform.getAttributesForConfig(this)
                )
            } else {
                variantDependencies.getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        scope,
                        AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS,
                        AsmClassesTransform.getAttributesForConfig(this)
                )
            }
        } else {
            variantDependencies.getArtifactFileCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                scope,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        }
    }

    override val packageJacocoRuntime: Boolean
        get() = false

    override val isUnitTestCoverageEnabled: Boolean
        get() = variantDslInfo.isUnitTestCoverageEnabled
    override val isAndroidTestCoverageEnabled: Boolean
        get() = variantDslInfo.isAndroidTestCoverageEnabled
    override val publishInfo: VariantPublishingInfo?
        get() = variantDslInfo.publishInfo
    override val supportedAbis: Set<String>
        get() = variantDslInfo.supportedAbis
    override val vectorDrawables: VectorDrawablesOptions
        get() = variantDslInfo.vectorDrawables
    override val ndkConfig: MergedNdkConfig
        get() = variantDslInfo.ndkConfig
    override val renderscriptNdkModeEnabled: Boolean
        get() = variantDslInfo.renderscriptNdkModeEnabled
    override val isJniDebuggable: Boolean
        get() = variantDslInfo.isJniDebuggable
    override val defaultGlslcArgs: List<String>
        get() = variantDslInfo.defaultGlslcArgs
    override val scopedGlslcArgs: Map<String, List<String>>
        get() = variantDslInfo.scopedGlslcArgs
    override val isWearAppUnbundled: Boolean?
        get() = variantDslInfo.isWearAppUnbundled

    override fun addDataBindingArgsToOldVariantApi(args: DataBindingCompilerArguments) {
        variantDslInfo.javaCompileOptions.annotationProcessorOptions
            .compilerArgumentProviders.add(args)
    }

    override val modelV1LegacySupport =
        ModelV1LegacySupportImpl(variantDslInfo as VariantDslInfoImpl)
    override val oldVariantApiLegacySupport =
        OldVariantApiLegacySupportImpl(variantDslInfo as VariantDslInfoImpl)

    companion object {
        // String to
        final val ENABLE_LEGACY_API: String =
            "Turn on with by putting '${BooleanOption.ENABLE_LEGACY_API.propertyName}=true in gradle.properties'\n" +
                    "Using this deprecated API may still fail, depending on usage of the new Variant API, like computing applicationId via a task output."
    }

    override fun getArtifactName(name: String) = name
}
