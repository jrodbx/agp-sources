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
import com.android.build.api.variant.impl.SourceDirectoriesImpl
import com.android.build.api.variant.impl.SourceType
import com.android.build.api.variant.impl.SourcesImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.baseName
import com.android.build.api.variant.impl.fullName
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
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
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.get
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.has
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.*
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.compiling.BuildConfigType
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.Callable

abstract class ComponentImpl(
    open val componentIdentity: ComponentIdentity,
    final override val buildFeatures: BuildFeatureValues,
    final override val variantDslInfo: VariantDslInfo,
    override val variantDependencies: VariantDependencies,
    override val variantSources: VariantSources,
    override val paths: VariantPathHelper,
    override val artifacts: ArtifactsImpl,
    override val variantScope: VariantScope,
    override val variantData: BaseVariantData,
    override val transformManager: TransformManager,
    protected val internalServices: VariantPropertiesApiServices,
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
            ::defaultSources,
            internalServices.projectInfo.projectDirectory,
            internalServices,
            variantSources.variantSourceProvider,
        ).also { sourcesImpl ->
            // add all source sets extra directories added by the user
            variantSources.customSourceList.forEach{ (_, srcEntries) ->
                srcEntries.forEach { customSourceDirectory ->
                    sourcesImpl.extras.maybeCreate(customSourceDirectory.sourceTypeName).also {
                        (it as SourceDirectoriesImpl).addSource(
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

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    override val asmApiVersion = ASM_API_VERSION_FOR_INSTRUMENTATION

    // this is technically a public API for the Application Variant (only)
    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    // Move as direct delegates
    override val taskContainer = variantData.taskContainer

    override val variantType: VariantType
        get() = variantDslInfo.variantType

    override val dirName: String
        get() = variantDslInfo.dirName

    override val baseName: String
        get() = variantDslInfo.baseName

    override val resourceConfigurations: ImmutableSet<String>
        get() = variantDslInfo.resourceConfigurations

    override val description: String
        get() = variantData.description

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
        if (variantType.isTestComponent) {
            val tested = testedConfig ?: throw RuntimeException("testedVariant null with type $variantType")
            return action(tested)
        }

        return null
    }

    override fun useResourceShrinker(): Boolean {
        if (variantType.isForTesting || !variantDslInfo.getPostProcessingOptions().resourcesShrinkingEnabled()) {
            return false
        }
        val newResourceShrinker = services.projectOptions[BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER]
        if (variantType.isDynamicFeature) {
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
        if (variantType.isAar) {
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
            variantOutputConfiguration.baseName(variantDslInfo),
            variantOutputConfiguration.fullName(variantDslInfo),
            internalServices.newPropertyBackingDeprecatedApi(
                String::class.java,
                outputFileName
                    ?: variantDslInfo.getOutputFileName(
                        internalServices.projectInfo.getProjectBaseName(),
                        variantOutputConfiguration.baseName(variantDslInfo)
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
            listOf("res", name) + variantDslInfo.directorySegments)
    }

    // Precomputed file paths.
    @JvmOverloads
    override fun getJavaClasspath(
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
                                variantType.isTestFixturesComponent
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
                            variantType.isTestFixturesComponent
                        )
                }
            }
        }
    }

    /**
     * Computes the default sources for a particular [SourceType].
     */
    private fun defaultSources(type: SourceType): List<DirectoryEntry> {
        return when(type) {
            SourceType.JAVA -> defaultJavaSources()
        }
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     * For access to the final list of java sources, use [sources]
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    private fun defaultJavaSources(): List<DirectoryEntry> {
        // Build the list of source folders.
        val sourceSets = ImmutableList.builder<DirectoryEntry>()

        // First the actual source folders.
        val providers = variantSources.sortedSourceProviders
        for (provider  in providers) {
            val sourceSet = provider as AndroidSourceSet
            for (srcDir in sourceSet.java.srcDirs) {
                sourceSets.add(
                    FileBasedDirectoryEntryImpl(
                        name = sourceSet.name,
                        directory = srcDir,
                        filter = (provider as AndroidSourceSet).java.filter,
                    )
                )
            }
        }

        // for the other, there's no duplicate so no issue.
        if (getBuildConfigType() == BuildConfigType.JAVA_CLASS) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_build_config",
                    artifacts.get(GENERATED_BUILD_CONFIG_JAVA),
                )
            )
        }
        if (taskContainer.aidlCompileTask != null) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_aidl",
                    artifacts.get(AIDL_SOURCE_OUTPUT_DIR),
                )
            )
        }
        if (buildFeatures.dataBinding || buildFeatures.viewBinding) {
            // DATA_BINDING_TRIGGER artifact is created for data binding only (not view binding)
            if (buildFeatures.dataBinding) {
                // Under some conditions (e.g., for a unit test variant where
                // includeAndroidResources == false or testedVariantType != AAR, see
                // TaskManager.createUnitTestVariantTasks), the artifact may not have been created,
                // so we need to check its presence first (using internal AGP API instead of Gradle
                // API---see https://android.googlesource.com/platform/tools/base/+/ca24108e58e6e0dc56ce6c6f639cdbd0fa3b812f).
                if (!artifacts.getArtifactContainer(DATA_BINDING_TRIGGER)
                        .needInitialProducer().get()
                ) {
                    sourceSets.add(
                        TaskProviderBasedDirectoryEntryImpl(
                            name = "databinding_generated",
                            directoryProvider = artifacts.get(DATA_BINDING_TRIGGER),
                        )
                    )
                }
            }
            addDataBindingSources(sourceSets)
        }
        addRenderscriptSources(sourceSets)
        if (buildFeatures.mlModelBinding) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "mlModel_generated",
                    directoryProvider = artifacts.get(ML_SOURCE_OUT),
                    shouldBeAddedToIdeModel = false,
                )
            )
        }
        return sourceSets.build()
    }

    /**
     * adds renderscript sources if present.
     */
    open fun addRenderscriptSources(
        sourceSets: ImmutableList.Builder<DirectoryEntry>,
    ) {
        // not active by default, only sub types will have renderscript enabled.
    }

    /**
     * adds databinding sources to the list of sources.
     */
    open fun addDataBindingSources(
        sourceSets: ImmutableList.Builder<DirectoryEntry>
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
            val variantType = variantDslInfo.variantType

            if (testedConfig == null) {
                // TODO(b/138780301): Also use it in android tests.
                val useCompileRClassInApp = (internalServices
                    .projectOptions[BooleanOption
                    .ENABLE_APP_COMPILE_TIME_R_CLASS]
                        && !variantType.isForTesting)
                if (variantType.isAar || useCompileRClassInApp) {
                    if (androidResourcesEnabled) {
                        internalServices.fileCollection(artifacts.get(COMPILE_R_CLASS_JAR)
                        )
                    } else {
                        internalServices.fileCollection()
                    }
                } else {
                    Preconditions.checkState(
                        variantType.isApk,
                        "Expected APK type but found: $variantType"
                    )

                    internalServices.fileCollection(
                        artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                    )
                }
            } else { // Android test or unit test
                if (variantType === VariantTypeImpl.ANDROID_TEST) {
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
            val variantType = variantDslInfo.variantType

            if (testedConfig == null) {
                // TODO(b/138780301): Also use it in android tests.
                val useCompileRClassInApp = (internalServices
                    .projectOptions[BooleanOption
                    .ENABLE_APP_COMPILE_TIME_R_CLASS]
                        && !variantType.isForTesting)
                if (variantType.isAar || useCompileRClassInApp) {
                    if (androidResourcesEnabled) {
                        artifacts.get(COMPILE_R_CLASS_JAR)
                    } else {
                        null
                    }
                } else {
                    Preconditions.checkState(
                        variantType.isApk,
                        "Expected APK type but found: $variantType"
                    )

                    artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)
                }
            } else { // Android test or unit test
                if (variantType === VariantTypeImpl.ANDROID_TEST) {
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
        val isAndroidTest = variantDslInfo.variantType == VariantTypeImpl.ANDROID_TEST
        val isUnitTest = variantDslInfo.variantType == VariantTypeImpl.UNIT_TEST
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
        val manifestClassRequired = variantDslInfo.variantType.requiresManifest &&
                services.projectOptions[BooleanOption.GENERATE_MANIFEST_CLASS]
        val isTest = variantDslInfo.variantType.isForTesting
        val isAar = variantDslInfo.variantType.isAar
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
        return if (taskContainer.generateBuildConfigTask == null || !buildFeatures.buildConfig) {
            BuildConfigType.NONE
        } else if (services.projectOptions[BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE]
            && variantDslInfo.getBuildConfigFields().none()
        ) {
            BuildConfigType.JAR
        } else {
            BuildConfigType.JAVA_CLASS
        }
    }

    override fun configureAndLockAsmClassesVisitors(objectFactory: ObjectFactory) {
        instrumentation.configureAndLockAsmClassesVisitors(objectFactory, asmApiVersion)
    }

    abstract fun <T: com.android.build.api.variant.Component> createUserVisibleVariantObject(
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

    companion object {
        // String to
        final val ENABLE_LEGACY_API: String =
            "Turn on with by putting '${BooleanOption.ENABLE_LEGACY_API.propertyName}=true in gradle.properties'\n" +
                    "Using this deprecated API may still fail, depending on usage of the new Variant API, like computing applicationId via a task output."
    }
}
