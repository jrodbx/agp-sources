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
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.DUPLICATE_CLASSES_CHECK
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.services.R8ParallelBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.LibraryArtifactType
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.getFilteredFiles
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.MainDexListConfig
import com.android.builder.dexing.ProguardConfig
import com.android.builder.dexing.ProguardOutputFiles
import com.android.builder.dexing.R8OutputType
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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
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

    @get:Input
    abstract val enableDesugaring: Property<Boolean>

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

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val disableTreeShaking: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val duplicateClassesCheck: ConfigurableFileCollection

    @get:Input
    abstract val disableMinification: Property<Boolean>

    @get:Input
    lateinit var proguardConfigurations: MutableList<String>
        private set

    @get:Input
    abstract val useFullR8: Property<Boolean>

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
    abstract val baseDexDir: DirectoryProperty

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

    @get:Optional
    @get:OutputFile
    abstract val mergedStartupProfile: RegularFileProperty

    @get:OutputFile
    abstract val r8Metadata: RegularFileProperty

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

            task.usesService(
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    R8ParallelBuildService::class.java
                )
            )

            task.enableDesugaring.setDisallowChanges(true)
            task.executionOptions.setDisallowChanges(
                ToolExecutionOptions(emptyList(), false)
            )

            setBootClasspathForCodeShrinker(task)
            task.minSdkVersion.setDisallowChanges(creationConfig.minSdkVersion.apiLevel)

            task.debuggable.setDisallowChanges(false)
            task.disableTreeShaking.set(disableTreeShaking)
            task.disableMinification.set(disableMinification)
            task.errorFormatMode.set(SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
            task.legacyMultiDexEnabled.setDisallowChanges(
                false
            )
            task.useFullR8.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.FULL_R8])

            task.proguardConfigurations = proguardConfigurations

            task.baseJar.disallowChanges()
            task.featureClassJars.disallowChanges()
            task.featureJavaResourceJars.disallowChanges()
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
                componentType.isAar -> creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    R8Task::outputClasses)
                    .withName("shrunkClasses.jar")
                    .on(InternalArtifactType.SHRUNK_CLASSES)

                (creationConfig as? ApplicationCreationConfig)?.consumesFeatureJars == true -> {
                    creationConfig.artifacts.setInitialProvider(
                        taskProvider,
                        R8Task::baseDexDir
                    ).on(InternalArtifactType.BASE_DEX)

                    creationConfig.artifacts.setInitialProvider(
                        taskProvider,
                        R8Task::featureDexDir
                    ).on(InternalArtifactType.FEATURE_DEX)

                    creationConfig.artifacts.setInitialProvider(
                        taskProvider,
                        R8Task::featureJavaResourceOutputDir
                    ).on(InternalArtifactType.FEATURE_SHRUNK_JAVA_RES)
                }
                creationConfig is ApkCreationConfig -> {
                    creationConfig.artifacts.use(taskProvider)
                        .wiredWith(R8Task::outputDex)
                        .toAppendTo(InternalMultipleArtifactType.DEX)
                }
                else -> {
                    throw RuntimeException("Unrecognized type")
                }
            }

            creationConfig.artifacts.use(taskProvider)
                .wiredWithFiles(R8Task::resourcesJar, R8Task::outputResources)
                .toTransform(InternalArtifactType.MERGED_JAVA_RES)

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

            task.usesService(
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    R8ParallelBuildService::class.java
                )
            )

            task.enableDesugaring.setDisallowChanges(
                creationConfig is ApkCreationConfig &&
                        creationConfig.dexing.java8LangSupportType == Java8LangSupport.R8
            )

            setBootClasspathForCodeShrinker(task)
            if (creationConfig is ApkCreationConfig) {
                task.minSdkVersion.setDisallowChanges(creationConfig.dexing.minSdkVersionForDexing)
            } else {
                task.minSdkVersion.setDisallowChanges(creationConfig.minSdk.apiLevel)
            }

            task.debuggable
                .setDisallowChanges(creationConfig.debuggable)
            task.disableTreeShaking.set(disableTreeShaking)
            task.disableMinification.set(disableMinification)
            task.errorFormatMode.set(SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
            task.legacyMultiDexEnabled.setDisallowChanges(
                creationConfig is ApkCreationConfig &&
                        creationConfig.dexing.dexingType == DexingType.LEGACY_MULTIDEX
            )
            task.useFullR8.setDisallowChanges(creationConfig.services.projectOptions[BooleanOption.FULL_R8])

            task.executionOptions.setDisallowChanges(
                creationConfig.global.settingsOptions.executionProfile?.r8Options)

            task.proguardConfigurations = proguardConfigurations

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

                if ((creationConfig as? ApplicationCreationConfig)?.consumesFeatureJars == true) {
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
                includeFeaturesInScopes.get() -> baseDexDir
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

        val workerAction = { it: R8Runnable.Params ->
            it.bootClasspath.from(bootClasspath.toList())
            it.minSdkVersion.set(minSdkVersion.get())
            it.debuggable.set(debuggable.get())
            it.disableTreeShaking.set(disableTreeShaking.get())
            it.enableDesugaring.set(enableDesugaring.get())
            it.disableMinification.set(disableMinification.get())
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
                    extractedDefaultProguardFile))
            it.inputProguardMapping.set(
                if (testedMappingFile.isEmpty) {
                    null
                } else {
                    testedMappingFile.singleFile
                })
            it.proguardConfigurations.set(proguardConfigurations)
            it.aar.set(componentType.orNull?.isAar == true)
            it.legacyMultiDexEnabled.set(legacyMultiDexEnabled)
            it.useFullR8.set(useFullR8.get())
            it.referencedInputs.from((referencedClasses + referencedResources).toList())
            it.classes.from(
                if (includeFeaturesInScopes.get() && !hasAllAccessTransformers.get()) {
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
        }
        if (executionOptions.get().runInSeparateProcess) {
            workerExecutor.processIsolation { spec ->
                spec.forkOptions { forkOptions ->
                    forkOptions.jvmArgs(executionOptions.get().jvmArgs)
                }
            }.submit(R8Runnable::class.java, workerAction)
        } else {
            workerExecutor.noIsolation().submit(R8Runnable::class.java, workerAction)
        }
    }

    companion object {
        fun shrink(
            bootClasspath: List<File>,
            minSdkVersion: Int,
            isDebuggable: Boolean,
            enableDesugaring: Boolean,
            disableTreeShaking: Boolean,
            disableMinification: Boolean,
            mainDexListFiles: List<File>,
            mainDexRulesFiles: List<File>,
            mainDexListOutput: File?,
            legacyMultiDexEnabled: Boolean,
            useFullR8: Boolean,
            referencedInputs: List<File>,
            classes: List<File>,
            resourcesJar: File,
            proguardConfigurationFiles: Collection<File>,
            inputProguardMapping: File?,
            proguardConfigurations: MutableList<String>,
            isAar: Boolean,
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
            r8Metadata: File?
        ) {
            val logger = LoggerWrapper.getLogger(R8Task::class.java)

            val r8OutputType = if (isAar) {
                R8OutputType.CLASSES
            } else {
                R8OutputType.DEX
            }

            FileUtils.deleteIfExists(outputResources)
            if (isAar) {
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

            val toolConfig = ToolConfig(
                minSdkVersion = minSdkVersion,
                isDebuggable = isDebuggable,
                disableTreeShaking = disableTreeShaking,
                disableDesugaring = !enableDesugaring,
                disableMinification = disableMinification,
                r8OutputType = r8OutputType,
            )

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
                MessageReceiverImpl(errorFormatMode, Logging.getLogger(R8Runnable::class.java)),
                useFullR8,
                featureClassJars.map { it.toPath() },
                featureJavaResourceJars.map { it.toPath() },
                featureDexDir?.toPath(),
                featureJavaResourceOutputDir?.toPath(),
                libConfiguration,
                inputArtProfile?.toPath(),
                outputArtProfile?.toPath(),
                inputProfileForDexStartupOptimization?.toPath(),
                r8Metadata?.toPath(),
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
    }

    abstract class R8Runnable : WorkAction<R8Runnable.Params> {

        abstract class Params : WorkParameters {
            abstract val bootClasspath: ConfigurableFileCollection
            abstract val minSdkVersion: Property<Int>
            abstract val debuggable: Property<Boolean>
            abstract val disableTreeShaking: Property<Boolean>
            abstract val enableDesugaring: Property<Boolean>
            abstract val disableMinification: Property<Boolean>
            abstract val mainDexListFiles: ConfigurableFileCollection
            abstract val mainDexRulesFiles: ConfigurableFileCollection
            abstract val mainDexListOutput: RegularFileProperty
            abstract val legacyMultiDexEnabled: Property<Boolean>
            abstract val useFullR8: Property<Boolean>
            abstract val referencedInputs: ConfigurableFileCollection
            abstract val classes: ConfigurableFileCollection
            abstract val resourcesJar: RegularFileProperty
            abstract val proguardConfigurationFiles: ConfigurableFileCollection
            abstract val inputProguardMapping: RegularFileProperty
            abstract val proguardConfigurations: ListProperty<String>
            abstract val aar: Property<Boolean>
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
        }

        override fun execute() {
            shrink(
                parameters.bootClasspath.files.toList(),
                parameters.minSdkVersion.get(),
                parameters.debuggable.get(),
                parameters.enableDesugaring.get(),
                parameters.disableTreeShaking.get(),
                parameters.disableMinification.get(),
                parameters.mainDexListFiles.files.toList(),
                parameters.mainDexRulesFiles.files.toList(),
                parameters.mainDexListOutput.orNull?.asFile,
                parameters.legacyMultiDexEnabled.get(),
                parameters.useFullR8.get(),
                parameters.referencedInputs.files.toList(),
                parameters.classes.files.toList(),
                parameters.resourcesJar.asFile.get(),
                parameters.proguardConfigurationFiles.files.toList(),
                parameters.inputProguardMapping.orNull?.asFile,
                parameters.proguardConfigurations.get(),
                parameters.aar.get(),
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
            )
        }
    }
}
