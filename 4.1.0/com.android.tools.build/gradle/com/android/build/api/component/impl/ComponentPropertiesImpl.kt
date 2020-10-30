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
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.ComponentProperties
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.api.variant.impl.baseName
import com.android.build.api.variant.impl.fullName
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.get
import com.android.build.gradle.internal.scope.BuildArtifactSpec.Companion.has
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_TRIGGER
import com.android.build.gradle.internal.scope.InternalArtifactType.ML_SOURCE_OUT
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.compiling.BuildConfigType
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.builder.model.ApiVersion
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.Callable

abstract class ComponentPropertiesImpl(
    componentIdentity: ComponentIdentity,
    override val buildFeatures: BuildFeatureValues,
    override val variantDslInfo: VariantDslInfo,
    override val variantDependencies: VariantDependencies,
    override val variantSources: VariantSources,
    override val paths: VariantPathHelper,
    override val artifacts: ArtifactsImpl,
    override val variantScope: VariantScope,
    val variantData: BaseVariantData,
    override val transformManager: TransformManager,
    protected val internalServices: VariantPropertiesApiServices,
    override val services: TaskCreationServices,
    @Deprecated("Do not use if you can avoid it. Check if services has what you need")
    override val globalScope: GlobalScope
): ComponentProperties, BaseCreationConfig, ComponentIdentity by componentIdentity {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------
    override val packageName: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.packageName)

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    // this is technically a public API for the Application Variant (only)
    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    // Move as direct delegates
    override val taskContainer = variantData.taskContainer

    override val variantType: VariantType
        get() = variantDslInfo.variantType

    override val minSdkVersion: AndroidVersion
        get() = variantDslInfo.minSdkVersion

    override val maxSdkVersion: Int?
        get() = variantDslInfo.maxSdkVersion

    override val targetSdkVersion: ApiVersion
        get() = variantDslInfo.targetSdkVersion

    override val dirName: String
        get() = variantDslInfo.dirName

    override val baseName: String
        get() = variantDslInfo.baseName

    override val resourceConfigurations: ImmutableSet<String>
        get() = variantDslInfo.resourceConfigurations

    override val description: String
        get() = variantData.description

    override val dexingType: DexingType
        get() = variantDslInfo.dexingType

    override val needsMainDexList: Boolean
        get() = dexingType.needsMainDexList

    // Resource shrinker expects MergeResources task to have all the resources merged and with
    // overlay rules applied, so we have to go through the MergeResources pipeline in case it's
    // enabled, see b/134766811.
    override val isPrecompileDependenciesResourcesEnabled: Boolean
        get() = internalServices.projectOptions[BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES] && !variantScope.useResourceShrinker()

    /**
     * Returns the tested variant. This is null for [VariantPropertiesImpl] instances
     *

     * This declares is again, even though the public interfaces only have it via
     * [TestComponentProperties]. This is to facilitate places where one cannot use
     * [TestComponentPropertiesImpl].
     *
     * see [onTestedConfig] for a utility function helping deal with nullability
     */
    override val testedConfig: VariantCreationConfig? = null

    /**
     * Runs an action on the tested variant and return the results of the action.
     *
     * if there is no tested variant this does nothing and returns null.
     */
    override fun <T> onTestedConfig(action: (VariantCreationConfig) -> T): T? {
        if (variantType.isTestComponent) {
            val tested = testedConfig ?: throw RuntimeException("testedVariant null with type $variantType")
            return action(tested)
        }

        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Private stuff
    // ---------------------------------------------------------------------------------------------

    private val variantOutputs= mutableListOf<VariantOutputImpl>()

    // FIXME make internal
    fun addVariantOutput(
        variantOutputConfiguration: VariantOutputConfigurationImpl,
        outputFileName: String? = null
    ): VariantOutputImpl {

        return VariantOutputImpl(
            createVersionCodeProperty(),
            createVersionNameProperty(),
            internalServices.newPropertyBackingDeprecatedApi(Boolean::class.java, true, "$name::isEnabled"),
            variantOutputConfiguration,
            variantOutputConfiguration.baseName(variantDslInfo),
            variantOutputConfiguration.fullName(variantDslInfo),
            internalServices.newPropertyBackingDeprecatedApi(
                String::class.java,
                outputFileName
                    ?: variantDslInfo.getOutputFileName(
                        globalScope.projectBaseName,
                        variantOutputConfiguration.baseName(variantDslInfo)
                    ),
                "$name::archivesBaseName")
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
            paths.generatedDir,
            listOf("res", name) + variantDslInfo.directorySegments)
    }

    // Precomputed file paths.
    @JvmOverloads
    fun getJavaClasspath(
        configType: ConsumedConfigType,
        classesType: AndroidArtifacts.ArtifactType,
        generatedBytecodeKey: Any? = null
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
            globalScope.project.path
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
                globalScope.project.path,
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
                variantScope
                    .publishIntermediateArtifact(
                        artifactProvider,
                        outputSpec.artifactType,
                        outputSpec.publishedConfigTypes
                    )
            }
        }
    }

    /**
     * Computes the Java sources to use for compilation.
     *
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    val javaSources: List<ConfigurableFileTree>
        get() {
            // Shortcut for the common cases, otherwise we build the full list below.
            if (variantData.extraGeneratedSourceFileTrees == null
                && variantData.externalAptJavaOutputFileTrees == null
            ) {
                return defaultJavaSources
            }

            // Build the list of source folders.
            val sourceSets = ImmutableList.builder<ConfigurableFileTree>()

            // First the default source folders.
            sourceSets.addAll(defaultJavaSources)

            // then the third party ones
            variantData.extraGeneratedSourceFileTrees?.let {
                sourceSets.addAll(it)
            }
            variantData.externalAptJavaOutputFileTrees?.let {
                sourceSets.addAll(it)
            }

            return sourceSets.build()
        }

    /**
     * Computes the default java sources: source sets and generated sources.
     *
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    private val defaultJavaSources: List<ConfigurableFileTree> by lazy {
        // Build the list of source folders.
        val sourceSets = ImmutableList.builder<ConfigurableFileTree>()

        // First the actual source folders.
        val providers = variantSources.sortedSourceProviders
        for (provider in providers) {
            sourceSets.addAll((provider as AndroidSourceSet).java.getSourceDirectoryTrees())
        }

        // for the other, there's no duplicate so no issue.
        if (getBuildConfigType() == BuildConfigType.JAVA_CLASS) {
            val generatedBuildConfig =
                artifacts.get(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA)
            sourceSets.add(internalServices.fileTree(generatedBuildConfig)
                .builtBy(generatedBuildConfig))
        }
        if (taskContainer.aidlCompileTask != null) {
            val aidlFC = artifacts.get(AIDL_SOURCE_OUTPUT_DIR)
            sourceSets.add(internalServices.fileTree(aidlFC).builtBy(aidlFC))
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
                    sourceSets.add(internalServices.fileTree(artifacts.get(DATA_BINDING_TRIGGER)))
                }
            }
            addDataBindingSources(sourceSets)
        }
        if (!variantDslInfo.renderscriptNdkModeEnabled
            && taskContainer.renderscriptCompileTask != null
        ) {
            val rsFC = artifacts.get(RENDERSCRIPT_SOURCE_OUTPUT_DIR)
            sourceSets.add(internalServices.fileTree(rsFC).builtBy(rsFC))
        }
        if (buildFeatures.mlModelBinding) {
            val mlModelClassSourceOut: Provider<Directory> =
                artifacts.get(ML_SOURCE_OUT)
            sourceSets.add(
                internalServices.fileTree(mlModelClassSourceOut).builtBy(mlModelClassSourceOut)
            )
        }
        sourceSets.build()
    }

    /**
     * adds databinding sources to the list of sources.
     */
    open fun addDataBindingSources(
        sourceSets: ImmutableList.Builder<ConfigurableFileTree>
    ) {
        val baseClassSource = artifacts.get(DATA_BINDING_BASE_CLASS_SOURCE_OUT)
        sourceSets.add(internalServices.fileTree(baseClassSource).builtBy(baseClassSource))
    }

    /** Returns the path(s) to compiled R classes (R.jar). */
    fun getCompiledRClasses(configType: ConsumedConfigType): FileCollection {
        return if (globalScope.extension.aaptOptions.namespaced) {
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
                    if (buildFeatures.androidResources) {
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
                    internalServices.fileCollection(variantScope.rJarForUnitTests)
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
        val isAndroidTest = variantDslInfo.variantType == VariantTypeImpl.ANDROID_TEST
        val isUnitTest = variantDslInfo.variantType == VariantTypeImpl.UNIT_TEST
        val isTest = variantDslInfo.variantType.isForTesting || isUnitTest || isAndroidTest
        val manifestRequired = variantDslInfo.variantType.requiresManifest &&
                services.projectOptions[BooleanOption.GENERATE_MANIFEST_CLASS]
        val isLibrary = variantDslInfo.variantType.isAar
        return if (manifestRequired && !isLibrary && !isTest && testedConfig == null) {
            internalServices.fileCollection(
                    artifacts.get(InternalArtifactType.COMPILE_MANIFEST_JAR)
            )
        } else {
            internalServices.fileCollection()
        }
    }

    fun handleMissingDimensionStrategy(dimension: String, alternatedValues: ImmutableList<String>) {

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        val requestedValue = VariantManager.getModifiedName(name)
        val attributeKey =
            Attribute.of(
                dimension,
                ProductFlavorAttr::class.java
            )
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
            globalScope.project.dependencies.attributesSchema,
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

    companion object {
        // String to
        final val ENABLE_LEGACY_API: String = "Turn on with by putting '${BooleanOption.ENABLE_LEGACY_API.propertyName}=true in gradle.properties'"
    }
}