/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.ToolExecutionOptions
import com.android.build.gradle.internal.dependency.ShrinkerVersion
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.manifest.parseManifest
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.DUPLICATE_CLASSES_CHECK
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.services.R8D8ThreadPoolBuildService
import com.android.build.gradle.internal.services.R8MaxParallelTasksBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.doClose
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.LibraryArtifactType
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.getFilteredFiles
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.PackageAndroidArtifact.Companion.THROW_ON_ERROR_ISSUE_REPORTER
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.PartialShrinkingConfig
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.ProguardOutputFiles
import com.android.builder.dexing.R8OutputType
import com.android.builder.dexing.ResourceShrinkingConfig
import com.android.builder.dexing.ToolConfig
import com.android.builder.dexing.runR8
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import javax.inject.Inject

/**
 * Task that uses R8 to convert class files to dex. In case of a library variant, this
 * task outputs class files.
 *
 * R8 task inputs are: program class files, library class files (e.g. android.jar), java resourcesJar,
 * Proguard configuration files, main dex list configuration files, other tool-specific parameters.
 * Output is dex or class files, depending on whether we are building an APK, or AAR.
 */

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class R8Task @Inject constructor(
    projectLayout: ProjectLayout
): ProguardConfigurableTask(projectLayout) {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val multiDexKeepFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val multiDexKeepProguard: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainDexRulesFiles: ConfigurableFileCollection

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val duplicateClassesCheck: ConfigurableFileCollection

    @get:Input
    lateinit var proguardConfigurations: MutableList<String>
        private set

    @get:Input
    abstract val legacyMultiDexEnabled: Property<Boolean>

    @get:Internal
    abstract val executionOptions: Property<ToolExecutionOptions>

    @get:Optional
    @get:Classpath
    abstract val featureClassJars: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val featureJavaResourceJars: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val baseJar: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val coreLibDesugarConfig: Property<String>

    // R8 will produce either classes or dex
    @get:Optional
    @get:OutputFile
    abstract val outputClasses: RegularFileProperty

    @get:Optional
    @get:OutputDirectory
    abstract val outputDex: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val featureDexDir: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val featureJavaResourceOutputDir: DirectoryProperty

    @get:OutputFile
    abstract val outputResources: RegularFileProperty

    @OutputFile
    fun getProguardSeedsOutput(): Provider<File> =
            mappingFile.flatMap {
                providerFactory.provider { it.asFile.resolveSibling("seeds.txt") }
            }

    @OutputFile
    fun getProguardUsageOutput(): Provider<File> =
            mappingFile.flatMap {
                providerFactory.provider { it.asFile.resolveSibling("usage.txt") }
            }

    @OutputFile
    fun getProguardConfigurationOutput(): Provider<File> =
            mappingFile.flatMap {
                providerFactory.provider { it.asFile.resolveSibling("configuration.txt") }
            }

    @OutputFile
    fun getMissingKeepRulesOutput(): Provider<File> =
            mappingFile.flatMap {
                providerFactory.provider { it.asFile.resolveSibling("missing_rules.txt") }
            }

    @get:Optional
    @get:OutputFile
    abstract val mainDexListOutput: RegularFileProperty

    @get:Input
    abstract val artProfileRewriting: Property<Boolean>

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val inputArtProfile: RegularFileProperty

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val inputProfileForDexStartupOptimization: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val outputArtProfile: RegularFileProperty

    @get:Inject
    abstract val providerFactory: ProviderFactory

    @get:OutputFile
    abstract val r8Metadata: RegularFileProperty

    @get:Nested
    abstract val toolParameters: R8ToolParameters

    @get:Nested
    abstract val resourceShrinkingParams: R8ResourceShrinkingParameters

    @get:Input
    @get:Optional
    abstract val partialShrinkingConfig: Property<PartialShrinkingConfig>

    @get:ServiceReference
    abstract val r8D8ThreadPoolBuildService: Property<R8D8ThreadPoolBuildService>

    @get:Input
    abstract val r8ThreadPoolSize: Property<Int>

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val packageList: RegularFileProperty

    @get:Input
    abstract val failOnMissingProguardFiles: Property<Boolean>

    class PrivacySandboxSdkCreationAction(
        val creationConfig: PrivacySandboxSdkVariantScope,
        addCompileRClass: Boolean,
    ): ProguardConfigurableTask.PrivacySandboxSdkCreationAction<R8Task, PrivacySandboxSdkVariantScope>(
        creationConfig, addCompileRClass
    ) {

        override val type = R8Task::class.java
        override val name =  "minifyBundleWithR8"

        private var disableTreeShaking: Boolean = false
        private var disableMinification: Boolean = false

        private val proguardConfigurations: MutableList<String> = mutableListOf()

        override fun handleProvider(
            taskProvider: TaskProvider<R8Task>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                R8Task::outputDex
            ).on(PrivacySandboxSdkInternalArtifactType.DEX)

            creationConfig.artifacts.use(taskProvider)
                .wiredWithFiles(R8Task::resourcesJar, R8Task::outputResources)
                .toTransform(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                R8Task::r8Metadata
            ).on(InternalArtifactType.R8_METADATA)
        }

        override fun configure(
            task: R8Task
        ) {
            super.configure(task)

            task.artProfileRewriting.set(false)

            useR8D8BuildServices(task, creationConfig.services)
            task.r8ThreadPoolSize.setDisallowChanges(
                // This `IntegerOption` has a default value so get() should return not-null
                creationConfig.services.projectOptions.get(IntegerOption.R8_THREAD_POOL_SIZE)!!
            )

            task.executionOptions.setDisallowChanges(
                ToolExecutionOptions(emptyList(), false)
            )

            setBootClasspathForCodeShrinker(task)

            task.errorFormatMode.set(SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
            task.legacyMultiDexEnabled.setDisallowChanges(
                false
            )
            task.toolParameters.let {
                it.minSdkVersion.setDisallowChanges(creationConfig.minSdkVersion.apiLevel)
                it.debuggable.setDisallowChanges(false)
                it.disableTreeShaking.setDisallowChanges(disableTreeShaking)
                it.disableMinification.setDisallowChanges(disableMinification)
                it.disableDesugaring.setDisallowChanges(false)
                it.fullMode.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.FULL_R8])
                it.strictFullModeForKeepRules.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.R8_STRICT_FULL_MODE_FOR_KEEP_RULES])
                it.packagedManifestDirectory.setDisallowChanges(null) // Not used for privacy sandbox SDK
                it.r8OutputType.setDisallowChanges(R8OutputType.DEX)
                it.mainDexListDisallowed.set(
                    creationConfig.services.projectOptions.get(
                        BooleanOption.R8_MAIN_DEX_LIST_DISALLOWED
                    )
                )
            }
            task.proguardConfigurations = proguardConfigurations

            task.failOnMissingProguardFiles.setDisallowChanges(
                creationConfig.services.projectOptions.get(
                    BooleanOption.FAIL_ON_MISSING_PROGUARD_FILES
                )
            )

            task.baseJar.disallowChanges()
            task.featureClassJars.disallowChanges()
            task.featureJavaResourceJars.disallowChanges()

            // TODO(b/326190433): Support resource shrinking for privacy sandbox SDKs
            task.resourceShrinkingParams.enabled.setDisallowChanges(false)
        }

        override fun keep(keep: String) {
            proguardConfigurations.add("-keep $keep")
        }

        override fun keepAttributes() {
            proguardConfigurations.add("-keepattributes *")
        }

        override fun dontWarn(dontWarn: String) {
            proguardConfigurations.add("-dontwarn $dontWarn")
        }

        override fun setActions(actions: PostprocessingFeatures) {
            disableTreeShaking = !actions.isRemoveUnusedCode
            disableMinification = !actions.isObfuscate
            if (!actions.isOptimize) {
                proguardConfigurations.add("-dontoptimize")
            }
        }

        private fun setBootClasspathForCodeShrinker(task: R8Task) {
            task.bootClasspath.from(creationConfig.bootClasspath)
        }
    }

    class CreationAction(
            creationConfig: ConsumableCreationConfig,
            isTestApplication: Boolean = false,
            addCompileRClass: Boolean
    ) : ProguardConfigurableTask.CreationAction<R8Task, ConsumableCreationConfig>(
            creationConfig, isTestApplication, addCompileRClass) {
        override val type = R8Task::class.java
        override val name =  computeTaskName("minify", "WithR8")

        private var disableTreeShaking: Boolean = false
        private var disableMinification: Boolean = false

        private val proguardConfigurations: MutableList<String> = mutableListOf()

        override fun handleProvider(
            taskProvider: TaskProvider<R8Task>
        ) {
            super.handleProvider(taskProvider)

            when {
                componentType.isAar -> {
                    creationConfig.artifacts
                        .setInitialProvider(taskProvider, R8Task::outputClasses)
                        .withName("shrunkClasses.jar")
                        .on(InternalArtifactType.SHRUNK_CLASSES)
                }

                componentType.isApk -> {
                    creationConfig as ApkCreationConfig
                    creationConfig.artifacts
                        .use(taskProvider).wiredWith(R8Task::outputDex)
                        .toAppendTo(InternalMultipleArtifactType.DEX)
                }

                else -> error("Unexpected component type: $componentType")
            }

            creationConfig.artifacts.use(taskProvider)
                .wiredWithFiles(R8Task::resourcesJar, R8Task::outputResources)
                .toTransform(InternalArtifactType.MERGED_JAVA_RES)

            if ((creationConfig as? ApplicationCreationConfig)?.runResourceShrinkingWithR8() == true) {
                creationConfig.artifacts.setInitialProvider(taskProvider) {
                    it.resourceShrinkingParams.shrunkResourcesOutputDir
                }.on(InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT)
            }

            if ((creationConfig as? ApplicationCreationConfig)?.shrinkingWithDynamicFeatures == true) {
                creationConfig.artifacts
                    .setInitialProvider(taskProvider, R8Task::featureDexDir)
                    .on(InternalArtifactType.FEATURE_DEX)

                creationConfig.artifacts
                    .setInitialProvider(taskProvider, R8Task::featureJavaResourceOutputDir)
                    .on(InternalArtifactType.FEATURE_SHRUNK_JAVA_RES)

                if (creationConfig.runResourceShrinkingWithR8()) {
                    creationConfig.artifacts.setInitialProvider(taskProvider) {
                            it.resourceShrinkingParams.featureShrunkResourcesOutputDir
                    }.on(InternalArtifactType.FEATURE_SHRUNK_RESOURCES_PROTO_FORMAT)
                }
            }

            if (creationConfig is ApkCreationConfig) {
                when {
                    creationConfig.dexing.needsMainDexListForBundle -> {
                        creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            R8Task::mainDexListOutput
                        ).withName("mainDexList.txt")
                            .on(InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE)
                    }
                    creationConfig.dexing.dexingType.isLegacyMultiDex -> {
                        creationConfig.artifacts.setInitialProvider(
                            taskProvider,
                            R8Task::mainDexListOutput
                        ).withName("mainDexList.txt")
                            .on(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
                    }
                }
            }
            if (creationConfig is VariantCreationConfig && !creationConfig.debuggable) {
                creationConfig.artifacts
                    .use(taskProvider)
                    .wiredWithFiles(R8Task::inputArtProfile, R8Task::outputArtProfile)
                    .toTransform(InternalArtifactType.R8_ART_PROFILE)
            }
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                R8Task::r8Metadata
            ).on(InternalArtifactType.R8_METADATA)
        }

        override fun configure(
            task: R8Task
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts

            if (creationConfig is VariantCreationConfig) {
                task.artProfileRewriting.set(
                    creationConfig.experimentalProperties.map {
                        ModulePropertyKey.BooleanWithDefault.ART_PROFILE_R8_REWRITING.getValue(it)
                    }
                )
                if (!creationConfig.debuggable) {
                    task.inputProfileForDexStartupOptimization.set(
                        artifacts.get(InternalArtifactType.MERGED_STARTUP_PROFILE)
                    )
                }
            } else {
                task.artProfileRewriting.set(false)
            }

            useR8D8BuildServices(task, creationConfig.services)
            task.r8ThreadPoolSize.setDisallowChanges(
                // This `IntegerOption` has a default value so get() should return not-null
                creationConfig.services.projectOptions.get(IntegerOption.R8_THREAD_POOL_SIZE)!!
            )

            setBootClasspathForCodeShrinker(task)

            task.errorFormatMode.set(SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
            task.legacyMultiDexEnabled.setDisallowChanges(
                creationConfig is ApkCreationConfig &&
                        creationConfig.dexing.dexingType == DexingType.LEGACY_MULTIDEX
            )

            task.executionOptions.setDisallowChanges(
                creationConfig.global.settingsOptions.executionProfile?.r8Options)

            task.proguardConfigurations = proguardConfigurations

            task.failOnMissingProguardFiles.setDisallowChanges(
                creationConfig.services.projectOptions.get(
                    BooleanOption.FAIL_ON_MISSING_PROGUARD_FILES
                )
            )

            if (creationConfig is ApkCreationConfig) {
                // options applicable only when building APKs, do not apply with AARs
                task.duplicateClassesCheck.from(artifacts.get(DUPLICATE_CLASSES_CHECK))

                task.mainDexRulesFiles.from(
                    artifacts.getAll(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
                )

                if (creationConfig.dexing.dexingType.isLegacyMultiDex &&
                    !creationConfig.global.namespacedAndroidResources) {
                    task.mainDexRulesFiles.from(
                        artifacts.get(
                            InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES
                        )
                    )
                }
                task.multiDexKeepFile.setDisallowChanges(
                    creationConfig.dexing.multiDexKeepFile
                )

                if ((creationConfig as? ApplicationCreationConfig)?.shrinkingWithDynamicFeatures == true) {
                    creationConfig.artifacts.setTaskInputToFinalProduct(
                        InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES,
                        task.baseJar
                    )
                    task.featureClassJars.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_CLASSES
                        )
                    )
                    task.featureJavaResourceJars.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_JAVA_RES
                        )
                    )
                }
                if (creationConfig.dexing.isCoreLibraryDesugaringEnabled) {
                    task.coreLibDesugarConfig.set(getDesugarLibConfig(creationConfig.services))
                }
            }

            task.baseJar.disallowChanges()
            task.featureClassJars.disallowChanges()
            task.featureJavaResourceJars.disallowChanges()

            task.toolParameters.let {
                it.minSdkVersion.setDisallowChanges(
                    if (creationConfig is ApkCreationConfig) {
                        creationConfig.dexing.minSdkVersionForDexing
                    } else {
                        creationConfig.minSdk.apiLevel
                    }
                )
                it.debuggable.setDisallowChanges(creationConfig.debuggable)
                it.disableTreeShaking.setDisallowChanges(disableTreeShaking)
                it.disableMinification.setDisallowChanges(disableMinification)
                it.disableDesugaring.setDisallowChanges(
                    !(creationConfig is ApkCreationConfig && creationConfig.dexing.java8LangSupportType == Java8LangSupport.R8)
                )
                it.fullMode.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.FULL_R8])
                it.strictFullModeForKeepRules.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.R8_STRICT_FULL_MODE_FOR_KEEP_RULES])
                it.packagedManifestDirectory.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.PACKAGED_MANIFESTS))
                it.r8OutputType.setDisallowChanges(
                    if (componentType.isAar) {
                        R8OutputType.CLASSES
                    } else {
                        R8OutputType.DEX
                    }
                )
                it.mainDexListDisallowed.set(
                    creationConfig.services.projectOptions.get(BooleanOption.R8_MAIN_DEX_LIST_DISALLOWED)
                )
            }

            if ((creationConfig as? ApplicationCreationConfig)?.runResourceShrinkingWithR8() == true) {
                task.resourceShrinkingParams.initialize(creationConfig, task.mappingFile)
            } else {
                task.resourceShrinkingParams.enabled.setDisallowChanges(false)
            }

            task.partialShrinkingConfig.setDisallowChanges(creationConfig.getPartialShrinkingConfig())
            task.packageList.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.MERGED_PACKAGES_FOR_R8)
            )
        }

        override fun keep(keep: String) {
            proguardConfigurations.add("-keep $keep")
        }

        override fun keepAttributes() {
            proguardConfigurations.add("-keepattributes *")
        }

        override fun dontWarn(dontWarn: String) {
            proguardConfigurations.add("-dontwarn $dontWarn")
        }

        override fun setActions(actions: PostprocessingFeatures) {
            disableTreeShaking = !actions.isRemoveUnusedCode
            disableMinification = !actions.isObfuscate
            if (!actions.isOptimize) {
                proguardConfigurations.add("-dontoptimize")
            }
        }

        private fun setBootClasspathForCodeShrinker(task: R8Task) {
            val javaTarget = creationConfig.global.compileOptions.targetCompatibility

            task.bootClasspath.from(creationConfig.global.fullBootClasspath)
            when {
                javaTarget.isJava9Compatible ->
                    task.bootClasspath.from(creationConfig.global.versionedSdkLoader.flatMap {
                        it.coreForSystemModulesProvider })
                javaTarget.isJava8Compatible ->
                    task.bootClasspath.from(creationConfig.global.versionedSdkLoader.flatMap {
                        it.coreLambdaStubsProvider })
            }
        }
    }

    override fun doTaskAction() {
        val output: Property<out FileSystemLocation> =
            when {
                componentType.orNull?.isAar == true -> outputClasses
                else -> outputDex
            }
        // Check for duplicate java resourcesJar if there are dynamic features. We allow duplicate
        // META-INF/services/** entries.
        val featureJavaResourceJarsList = featureJavaResourceJars.toList()
        if (featureJavaResourceJarsList.isNotEmpty()) {
            val paths: MutableSet<String> = mutableSetOf()
            featureJavaResourceJarsList.plus(resourcesJar.asFile.get()).forEach { file ->
                ZipArchive(file.toPath()).use { jar ->
                    jar.listEntries().forEach { path ->
                        if (!path.startsWith("META-INF/services/") && !paths.add(path)) {
                            throw RuntimeException(
                                "Multiple dynamic-feature and/or base APKs will contain entries "
                                        + "with the same path, '$path', which can cause unexpected "
                                        + "behavior or errors at runtime. Please consider using "
                                        + "android.packagingOptions in the dynamic-feature and/or "
                                        + "application modules to ensure that only one of the APKs "
                                        + "contains this path."
                            )
                        }
                    }
                }
            }
        }

        val logger = LoggerWrapper.getLogger(R8Task::class.java)
        logger
            .info(
                """
                |R8 is a new Android code shrinker. If you experience any issues, please file a bug at
                |https://issuetracker.google.com, using 'Shrinker (R8)' as component name.
                |Current version is: ${ShrinkerVersion.R8.asString()}.
                |""".trimMargin()
            )

        val finalListOfConfigurationFiles = projectLayout.files(
                configurationFiles,
                generatedProguardFile.asFileTree,
        )

        // If inputArtProfile exists but artProfileRewriting is false, we need to copy it over
        // to outputArtProfile.
        val inputArtProfileFile = inputArtProfile.orNull?.asFile
        if (inputArtProfileFile?.exists() == true && artProfileRewriting.get() == false) {
            outputArtProfile.orNull?.asFile?.let { FileUtils.copyFile(inputArtProfileFile, it) }
        }

        if (resourceShrinkingParams.enabled.get()) {
            resourceShrinkingParams.saveOutputBuiltArtifactsMetadata()
        }

        val workerAction = { it: R8Runnable.Params ->
            it.bootClasspath.from(bootClasspath.toList())
            it.mainDexListFiles.from(
                mutableListOf<File>().also {
                    if (multiDexKeepFile.isPresent) {
                        it.add(multiDexKeepFile.get().asFile)
                    }
                })
            it.mainDexRulesFiles.from(
                mutableListOf<File>().also {
                    it.addAll(mainDexRulesFiles.toList())
                    if (multiDexKeepProguard.isPresent) {
                        it.add(multiDexKeepProguard.get().asFile)
                    }
                })
            it.mainDexListOutput.set(mainDexListOutput.orNull?.asFile)
            it.proguardConfigurationFiles.from(
                reconcileDefaultProguardFile(
                    getFilteredFiles(
                        ignoreFromInKeepRules.get(),
                        ignoreFromAllExternalDependenciesInKeepRules.get(),
                        libraryKeepRules,
                        finalListOfConfigurationFiles,
                        LoggerWrapper.getLogger(R8Task::class.java),
                        LibraryArtifactType.KEEP_RULES),
                    extractedDefaultProguardFile,
                    failOnMissingProguardFiles.get()
                )
            )
            it.inputProguardMapping.set(
                if (testedMappingFile.isEmpty) {
                    null
                } else {
                    testedMappingFile.singleFile
                })
            it.proguardConfigurations.set(proguardConfigurations)
            it.legacyMultiDexEnabled.set(legacyMultiDexEnabled)
            it.referencedInputs.from((referencedClasses + referencedResources).toList())
            it.classes.from(
                if (shrinkingWithDynamicFeatures.get() && !hasAllAccessTransformers.get()) {
                    listOf(baseJar.get().asFile)
                } else {
                    classes.toList()
                })
            it.resourcesJar.set(resourcesJar)
            it.mappingFile.set(mappingFile.get().asFile)
            it.proguardSeedsOutput.set(getProguardSeedsOutput().get())
            it.proguardUsageOutput.set(getProguardUsageOutput().get())
            it.proguardConfigurationOutput.set(getProguardConfigurationOutput().get())
            it.missingKeepRulesOutput.set(getMissingKeepRulesOutput().get())
            it.output.set(output.get().asFile)
            it.outputResources.set(outputResources.get().asFile)
            it.featureClassJars.from(featureClassJars.toList())
            it.featureJavaResourceJars.from(featureJavaResourceJarsList)
            it.featureDexDir.set(featureDexDir.asFile.orNull)
            it.featureJavaResourceOutputDir.set(featureJavaResourceOutputDir.asFile.orNull)
            it.libConfiguration.set(coreLibDesugarConfig.orNull)
            it.errorFormatMode.set(errorFormatMode.get())
            if (artProfileRewriting.get()) {
                it.inputArtProfile.set(inputArtProfile)
                it.outputArtProfile.set(outputArtProfile)
            }
            it.inputProfileForDexStartupOptimization.set(inputProfileForDexStartupOptimization)
            it.r8Metadata.set(r8Metadata)
            it.toolConfig.set(toolParameters.toToolConfig())
            it.resourceShrinkingConfig.set(resourceShrinkingParams.toConfig())
            it.partialShrinkingConfig.set(aggregatePartialShrinkingConfig())
            // Note: Build service can only be passed in Gradle worker non-isolation mode
            if (executionOptions.get().runInSeparateProcess) {
                it.r8ThreadPoolSizeIfIsolationMode.set(r8ThreadPoolSize)
            } else {
                it.r8D8ThreadPoolBuildServiceIfNonIsolationMode.set(r8D8ThreadPoolBuildService)
            }
        }
        if (executionOptions.get().runInSeparateProcess) {
            workerExecutor.processIsolation { spec ->
                spec.forkOptions { forkOptions ->
                    forkOptions.jvmArgs(executionOptions.get().jvmArgs)
                    // Also copy over system properties (see b/380110863).
                    // Once we have a list of R8-specific system properties (tracked at b/383727630),
                    // we'll copy over those properties only (and also define those properties as
                    // task inputs).
                    forkOptions.systemProperties(System.getProperties().mapKeys { it.key.toString() })
                }
            }.submit(R8Runnable::class.java, workerAction)
        } else {
            workerExecutor.noIsolation().submit(R8Runnable::class.java, workerAction)
        }
    }

    // Merge creation config included/excluded patterns with package.txt with merged R8 packages
    private fun aggregatePartialShrinkingConfig(): PartialShrinkingConfig? {
        val creationConfig = partialShrinkingConfig.orNull
        return if (packageList.isPresent) {
            val packages = loadR8AllowedPackages()
            val updatedPackages = creationConfig?.includedPatterns?.split(",")?.let {
                packages + it
            } ?: packages
            PartialShrinkingConfig(
                updatedPackages.joinToString(","),
                creationConfig?.excludedPatterns
            )
        } else creationConfig
    }

    private fun loadR8AllowedPackages(): List<String> {
        val packageFile = packageList.get()
        return packageFile.asFile.readLines()
    }

    companion object {
        fun shrink(
            bootClasspath: List<File>,
            mainDexListFiles: List<File>,
            mainDexRulesFiles: List<File>,
            mainDexListOutput: File?,
            legacyMultiDexEnabled: Boolean,
            referencedInputs: List<File>,
            classes: List<File>,
            resourcesJar: File,
            proguardConfigurationFiles: Collection<File>,
            inputProguardMapping: File?,
            proguardConfigurations: MutableList<String>,
            mappingFile: File,
            proguardSeedsOutput: File,
            proguardUsageOutput: File,
            proguardConfigurationOutput: File,
            missingKeepRulesOutput: File,
            output: File,
            outputResources: File,
            featureClassJars: List<File>,
            featureJavaResourceJars: List<File>,
            featureDexDir: File?,
            featureJavaResourceOutputDir: File?,
            libConfiguration: String?,
            errorFormatMode: SyncOptions.ErrorFormatMode,
            inputArtProfile: File?,
            outputArtProfile: File?,
            inputProfileForDexStartupOptimization: File?,
            r8Metadata: File?,
            toolConfig: ToolConfig,
            resourceShrinkingConfig: ResourceShrinkingConfig?,
            partialShrinkingConfig: PartialShrinkingConfig?,
            r8ThreadPool: ExecutorService
        ) {
            val logger = LoggerWrapper.getLogger(R8Task::class.java)

            FileUtils.deleteIfExists(outputResources)
            if (toolConfig.r8OutputType == R8OutputType.CLASSES) {
                FileUtils.deleteIfExists(output)
            } else {
                FileUtils.cleanOutputDir(output)
                featureDexDir?.let { FileUtils.cleanOutputDir(it) }
                featureJavaResourceOutputDir?.let { FileUtils.cleanOutputDir(it) }
            }

            val proguardOutputFiles =
                ProguardOutputFiles(
                    mappingFile.toPath(),
                    proguardSeedsOutput.toPath(),
                    proguardUsageOutput.toPath(),
                    proguardConfigurationOutput.toPath(),
                    missingKeepRulesOutput.toPath())

            val proguardConfig = ProguardConfig(
                proguardConfigurationFiles.map { it.toPath() },
                inputProguardMapping?.toPath(),
                proguardConfigurations,
                proguardOutputFiles
            )

            val mainDexListConfig = if (legacyMultiDexEnabled) {
                MainDexListConfig(
                    mainDexRulesFiles.map { it.toPath() },
                    mainDexListFiles.map { it.toPath() },
                    getPlatformRules(),
                    mainDexListOutput?.toPath()
                )
            } else {
                MainDexListConfig()
            }

            // When invoking R8 we filter out missing files. E.g. javac output may not exist if
            // there are no Java sources. See b/151605314 for details.
            runR8(
                filterMissingFiles(classes, logger),
                output.toPath(),
                resourcesJar.toPath(),
                outputResources.toPath(),
                bootClasspath.map { it.toPath() },
                filterMissingFiles(referencedInputs, logger),
                toolConfig,
                proguardConfig,
                mainDexListConfig,
                resourceShrinkingConfig,
                MessageReceiverImpl(errorFormatMode, Logging.getLogger(R8Runnable::class.java)),
                featureClassJars.map { it.toPath() },
                featureJavaResourceJars.map { it.toPath() },
                featureDexDir?.toPath(),
                featureJavaResourceOutputDir?.toPath(),
                libConfiguration,
                inputArtProfile?.toPath(),
                outputArtProfile?.toPath(),
                inputProfileForDexStartupOptimization?.toPath(),
                r8Metadata?.toPath(),
                partialShrinkingConfig,
                r8ThreadPool
            )
        }

        private fun filterMissingFiles(files: List<File>, logger: LoggerWrapper): List<Path> {
            return files.mapNotNull { file ->
                if (file.exists()) file.toPath()
                else {
                    logger.verbose("$file is ignored as it does not exist.")
                    null
                }
            }
        }

        private fun useR8D8BuildServices(
            task: R8Task,
            services: TaskCreationServices
        ) {
            task.usesService(
                getBuildService(
                    services.buildServiceRegistry,
                    R8MaxParallelTasksBuildService::class.java
                )
            )
            task.r8D8ThreadPoolBuildService.setDisallowChanges(
                getBuildService(
                    services.buildServiceRegistry,
                    R8D8ThreadPoolBuildService::class.java
                )
            )
        }
    }

    abstract class R8Runnable : WorkAction<R8Runnable.Params> {

        abstract class Params : WorkParameters {
            abstract val bootClasspath: ConfigurableFileCollection
            abstract val mainDexListFiles: ConfigurableFileCollection
            abstract val mainDexRulesFiles: ConfigurableFileCollection
            abstract val mainDexListOutput: RegularFileProperty
            abstract val legacyMultiDexEnabled: Property<Boolean>
            abstract val referencedInputs: ConfigurableFileCollection
            abstract val classes: ConfigurableFileCollection
            abstract val resourcesJar: RegularFileProperty
            abstract val proguardConfigurationFiles: ConfigurableFileCollection
            abstract val inputProguardMapping: RegularFileProperty
            abstract val proguardConfigurations: ListProperty<String>
            abstract val mappingFile: RegularFileProperty
            abstract val proguardSeedsOutput: RegularFileProperty
            abstract val proguardUsageOutput: RegularFileProperty
            abstract val proguardConfigurationOutput: RegularFileProperty
            abstract val missingKeepRulesOutput: RegularFileProperty
            abstract val output: RegularFileProperty
            abstract val outputResources: RegularFileProperty
            abstract val featureClassJars: ConfigurableFileCollection
            abstract val featureJavaResourceJars: ConfigurableFileCollection
            abstract val featureDexDir: DirectoryProperty
            abstract val featureJavaResourceOutputDir: DirectoryProperty
            abstract val libConfiguration: Property<String>
            abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
            abstract val inputArtProfile: RegularFileProperty
            abstract val outputArtProfile: RegularFileProperty
            abstract val inputProfileForDexStartupOptimization: RegularFileProperty
            abstract val r8Metadata: RegularFileProperty
            abstract val toolConfig: Property<ToolConfig>
            abstract val resourceShrinkingConfig: Property<ResourceShrinkingConfig>
            abstract val partialShrinkingConfig: Property<PartialShrinkingConfig>
            abstract val r8ThreadPoolSizeIfIsolationMode: Property<Int> // Set iff in Gradle worker isolation mode
            abstract val r8D8ThreadPoolBuildServiceIfNonIsolationMode: Property<R8D8ThreadPoolBuildService> // Set iff in Gradle worker non-isolation mode
        }

        override fun execute() {
            // In Gradle worker isolation mode, use a new thread pool for each R8 task.
            // In non-isolation mode, use the shared thread pool for all R8 tasks.
            val isolationMode = parameters.r8ThreadPoolSizeIfIsolationMode.isPresent
            val r8ThreadPool = if (isolationMode) {
                R8D8ThreadPoolBuildService.newThreadPool(parameters.r8ThreadPoolSizeIfIsolationMode.get())
            } else {
                parameters.r8D8ThreadPoolBuildServiceIfNonIsolationMode.get().threadPool
            }
            try {
                shrink(
                    parameters.bootClasspath.files.toList(),
                    parameters.mainDexListFiles.files.toList(),
                    parameters.mainDexRulesFiles.files.toList(),
                    parameters.mainDexListOutput.orNull?.asFile,
                    parameters.legacyMultiDexEnabled.get(),
                    parameters.referencedInputs.files.toList(),
                    parameters.classes.files.toList(),
                    parameters.resourcesJar.asFile.get(),
                    parameters.proguardConfigurationFiles.files.toList(),
                    parameters.inputProguardMapping.orNull?.asFile,
                    parameters.proguardConfigurations.get(),
                    parameters.mappingFile.get().asFile,
                    parameters.proguardSeedsOutput.get().asFile,
                    parameters.proguardUsageOutput.get().asFile,
                    parameters.proguardConfigurationOutput.get().asFile,
                    parameters.missingKeepRulesOutput.get().asFile,
                    parameters.output.get().asFile,
                    parameters.outputResources.get().asFile,
                    parameters.featureClassJars.files.toList(),
                    parameters.featureJavaResourceJars.files.toList(),
                    parameters.featureDexDir.orNull?.asFile,
                    parameters.featureJavaResourceOutputDir.orNull?.asFile,
                    parameters.libConfiguration.orNull,
                    parameters.errorFormatMode.get(),
                    parameters.inputArtProfile.orNull?.asFile,
                    parameters.outputArtProfile.orNull?.asFile,
                    parameters.inputProfileForDexStartupOptimization.orNull?.asFile,
                    parameters.r8Metadata.orNull?.asFile,
                    parameters.toolConfig.get(),
                    parameters.resourceShrinkingConfig.orNull,
                    parameters.partialShrinkingConfig.orNull,
                    r8ThreadPool
                )
            } finally {
                // In isolation mode, we use a separate thread pool, so we need to close it now.
                // In non-isolation mode, we use a shared thread pool, and we will close it in the
                // build service.
                if (isolationMode) {
                    r8ThreadPool.doClose()
                }
            }
        }
    }
}

fun ConsumableCreationConfig.getPartialShrinkingConfig(): PartialShrinkingConfig? {
    if (this !is VariantCreationConfig) return null
    val properties = experimentalProperties.get()
    if (ModulePropertyKey.OptionalBoolean.R8_EXPERIMENTAL_PARTIAL_SHRINKING_ENABLED.getValue(
            properties
        ) != true
    ) return null
    return PartialShrinkingConfig(
        includedPatterns = ModulePropertyKey.OptionalString.R8_EXPERIMENTAL_PARTIAL_SHRINKING_INCLUDE_PATTERNS.getValue(
            properties
        ),
        excludedPatterns = ModulePropertyKey.OptionalString.R8_EXPERIMENTAL_PARTIAL_SHRINKING_EXCLUDE_PATTERNS.getValue(
            properties
        )
    )
}

/** Similar to [ToolConfig] but containing Gradle types. */
abstract class R8ToolParameters {

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val disableTreeShaking: Property<Boolean>

    @get:Input
    abstract val disableMinification: Property<Boolean>

    @get:Input
    abstract val disableDesugaring: Property<Boolean>

    @get:Input
    abstract val fullMode: Property<Boolean>

    @get:Input
    abstract val strictFullModeForKeepRules: Property<Boolean>

    /** Used to compute [ToolConfig.isolatedSplits] */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val packagedManifestDirectory: DirectoryProperty

    @get:Input
    abstract val r8OutputType: Property<R8OutputType>

    @get:Input
    abstract val mainDexListDisallowed: Property<Boolean>

    fun toToolConfig() = ToolConfig(
        minSdkVersion = minSdkVersion.get(),
        debuggable = debuggable.get(),
        disableTreeShaking = disableTreeShaking.get(),
        disableMinification = disableMinification.get(),
        disableDesugaring = disableDesugaring.get(),
        fullMode = fullMode.get(),
        strictFullModeForKeepRules = strictFullModeForKeepRules.get(),
        isolatedSplits = getIsolatedSplitsValue(),
        r8OutputType = r8OutputType.get(),
        mainDexListDisallowed = mainDexListDisallowed.get()
    )

    private fun getIsolatedSplitsValue(): Boolean? {
        if (!packagedManifestDirectory.isPresent) return null

        val packagedManifests = BuiltArtifactsLoaderImpl().load(packagedManifestDirectory)?.elements
            ?: error("Failed to load manifests from: ${packagedManifestDirectory.get().asFile}")

        val isolatedSplitsValues: Set<Boolean?> = packagedManifests.map {
            parseManifest(
                File(it.outputFile).readText(),
                it.outputFile,
                manifestFileRequired = true,
                manifestParsingAllowedProvider = null, // Always allow manifest parsing as this should be called only in the execution phase
                THROW_ON_ERROR_ISSUE_REPORTER
            ).isolatedSplits
        }.toSet()

        return when (isolatedSplitsValues.size) {
            0 -> error("No manifests found in: ${packagedManifestDirectory.get().asFile}")
            1 -> isolatedSplitsValues.single()
            else -> error("Multiple isolatedSplits values found in ${packagedManifestDirectory.get().asFile}: $isolatedSplitsValues")
        }
    }
}
