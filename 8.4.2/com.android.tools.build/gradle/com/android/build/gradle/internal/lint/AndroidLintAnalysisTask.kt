/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.TEST_FIXTURES_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getLintParallelBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelSerialization
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Collections

/** Task to invoke lint with the --analyze-only flag, producing partial results. */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.LINT)
abstract class AndroidLintAnalysisTask : NonIncrementalTask() {

    @get:Nested
    abstract val lintTool: LintTool

    @get:Internal
    abstract val lintModelDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val partialResultsDirectory: DirectoryProperty

    @get:Internal
    abstract val androidSdkHome: Property<String>

    @get:Input
    abstract val androidGradlePluginVersion: Property<String>

    @get:Input
    abstract val offline: Property<Boolean>

    @get:Input
    abstract val android: Property<Boolean>

    @get:Input
    abstract val fatalOnly: Property<Boolean>

    @get:Input
    abstract val checkOnly: ListProperty<String>

    @get:Classpath
    abstract val lintRuleJars: ConfigurableFileCollection

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    @get:Input
    abstract val printStackTrace: Property<Boolean>

    @get:Nested
    abstract val systemPropertyInputs: SystemPropertyInputs

    @get:Nested
    abstract val environmentVariableInputs: EnvironmentVariableInputs

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val desugaredMethodsFiles: ConfigurableFileCollection

    @get:Input
    abstract val useK2Uast: Property<Boolean>

    override fun doTaskAction() {
        lintTool.lintClassLoaderBuildService.get().shouldDispose = true
        writeLintModelFile()
        lintTool.submit(
            mainClass = "com.android.tools.lint.Main",
            workerExecutor = workerExecutor,
            arguments = generateCommandLineArguments(),
            android = android.get(),
            fatalOnly = fatalOnly.get(),
            await = false,
            lintMode = LintMode.ANALYSIS,
            hasBaseline = projectInputs.lintOptions.baseline.orNull != null
        )
    }

    private fun writeLintModelFile() {
        val module = projectInputs.convertToLintModelModule()

        val variant =
            variantInputs.toLintModel(
                module,
                partialResultsDirectory.get().asFile,
                desugaredMethodsFiles.files
            )

        val destination = lintModelDirectory.get().asFile.also { FileUtils.cleanOutputDir(it) }

        LintModelSerialization.writeModule(
            module = module,
            destination = destination,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    @VisibleForTesting
    internal fun generateCommandLineArguments(): List<String> {

        val arguments = mutableListOf<String>()

        arguments += "--analyze-only"
        if (fatalOnly.get()) {
            arguments += "--fatalOnly"
        }
        arguments += listOf("--jdk-home", systemPropertyInputs.javaHome.get())
        androidSdkHome.orNull?.let { arguments.add("--sdk-home", it) }

        arguments += "--lint-model"
        arguments += listOf(lintModelDirectory.get().asFile.absolutePath).asLintPaths()

        for (check in checkOnly.get()) {
            arguments += listOf("--check", check)
        }

        val rules = lintRuleJars.files.filter { it.isFile }.map { it.absolutePath }
        if (rules.isNotEmpty()) {
            arguments += "--lint-rule-jars"
            arguments += rules.asLintPaths()
        }
        if (printStackTrace.get()) {
            arguments += "--stacktrace"
        }
        arguments += lintTool.initializeLintCacheDir()
        if (useK2Uast.get()) {
            arguments += "--XuseK2Uast"
        }

        // Pass information to lint using the --client-id, --client-name, and --client-version flags
        // so that lint can apply gradle-specific and version-specific behaviors.
        arguments.add("--client-id", "gradle")
        arguments.add("--client-name", "AGP")
        arguments.add("--client-version", Version.ANDROID_GRADLE_PLUGIN_VERSION)

        if (offline.get()) {
            arguments += "--offline"
        }

        return Collections.unmodifiableList(arguments)
    }

    // See LintUtils.splitPath: Using `;` as a suffix to avoid triggering the path that uses `:`,
    // even if there is only one path.
    private fun Collection<String>.asLintPaths() = joinToString(separator = ";", postfix = ";")

    private fun MutableList<String>.add(arg: String, value: String) {
        add(arg)
        add(value)
    }

    /**
     * Creates the lintAnalyzeVariant Task. Linting a variant also includes looking at the tests for
     * that variant.
     */
    class SingleVariantCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
        override val name = creationConfig.computeTaskNameInternal("lintAnalyze")
        override val fatalOnly = false
        override val description = "Run lint analysis on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintAnalysisTask>) {
            registerOutputArtifacts(
                taskProvider,
                LINT_PARTIAL_RESULTS,
                creationConfig.artifacts
            )
        }
    }

    /**
     * CreationAction for the lintVitalAnalyzeVariant task. Does not use the variant with tests
     */
    class LintVitalCreationAction(variant: VariantCreationConfig) :
        VariantCreationAction(VariantWithTests(
            variant,
            androidTest = null,
            unitTest = null,
            testFixtures = null
        ))
    {
        override val name = creationConfig.computeTaskNameInternal("lintVitalAnalyze")
        override val fatalOnly = true
        override val description =
            "Run lint analysis with only the fatal issues enabled on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintAnalysisTask>) {
            registerOutputArtifacts(
                taskProvider,
                LINT_VITAL_PARTIAL_RESULTS,
                creationConfig.artifacts
            )
        }
    }

    abstract class VariantCreationAction(val variant: VariantWithTests) :
            VariantTaskCreationAction<AndroidLintAnalysisTask,
                    ComponentCreationConfig>(variant.main) {

        final override val type: Class<AndroidLintAnalysisTask>
            get() = AndroidLintAnalysisTask::class.java

        abstract val fatalOnly: Boolean
        abstract val description: String

        final override fun configure(task: AndroidLintAnalysisTask) {
            super.configure(task)

            task.description = description

            task.initializeGlobalInputs(
                services = variant.main.services,
                isAndroid = true
            )
            task.lintModelDirectory.set(variant.main.paths.getIncrementalDir(task.name))
            task.lintRuleJars.from(creationConfig.global.localCustomLintChecks)
            task.lintRuleJars.from(
                creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRuleJars.from(
                creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRuleJars.disallowChanges()
            task.fatalOnly.setDisallowChanges(fatalOnly)
            task.checkOnly.set(
                creationConfig.services.provider {
                    creationConfig.global.lintOptions.checkOnly
                }
            )
            task.projectInputs.initialize(variant, LintMode.ANALYSIS)
            task.variantInputs.initialize(
                variant,
                useModuleDependencyLintModels = false,
                warnIfProjectTreatedAsExternalDependency = false,
                LintMode.ANALYSIS,
                fatalOnly = fatalOnly
            )
            task.lintTool.initialize(creationConfig.services, name)
            task.desugaredMethodsFiles.from(
                getDesugaredMethods(
                    creationConfig.services,
                    (creationConfig as? ConsumableCreationConfig)
                        ?.isCoreLibraryDesugaringEnabledLintCheck ?: false,
                    creationConfig.minSdk,
                    creationConfig.global
                )
            )
            task.desugaredMethodsFiles.disallowChanges()
            task.useK2Uast.setDisallowChanges(variant.main.useK2Uast)
        }
    }

    class PerComponentCreationAction(
        creationConfig: ComponentCreationConfig,
        private val fatalOnly: Boolean
    ) : VariantTaskCreationAction<AndroidLintAnalysisTask,
            ComponentCreationConfig>(creationConfig) {

        override val type: Class<AndroidLintAnalysisTask>
            get() = AndroidLintAnalysisTask::class.java

        override val name =
            if (fatalOnly) {
                creationConfig.computeTaskNameInternal("lintVitalAnalyze")
            } else {
                creationConfig.computeTaskNameInternal("lintAnalyze")

            }
        private val description =
            "Run lint analysis " +
                    if (fatalOnly) "with only the fatal issues enabled " else "" +
                            "on the ${creationConfig.name} component"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintAnalysisTask>) {
            val artifactType =
                when (creationConfig) {
                    is HostTestCreationConfig -> UNIT_TEST_LINT_PARTIAL_RESULTS
                    is AndroidTestCreationConfig -> ANDROID_TEST_LINT_PARTIAL_RESULTS
                    is TestFixturesCreationConfig -> TEST_FIXTURES_LINT_PARTIAL_RESULTS
                    else -> if (fatalOnly) {
                        LINT_VITAL_PARTIAL_RESULTS
                    } else {
                        LINT_PARTIAL_RESULTS
                    }
                }
            val mainVariant =
                if (creationConfig is NestedComponentCreationConfig) {
                    creationConfig.mainVariant
                } else {
                    creationConfig
                }
            registerOutputArtifacts(taskProvider, artifactType, mainVariant.artifacts)
        }

        override fun configure(task: AndroidLintAnalysisTask) {
            super.configure(task)

            task.description = description

            task.initializeGlobalInputs(
                services = creationConfig.services,
                isAndroid = true
            )
            task.lintModelDirectory.set(creationConfig.paths.getIncrementalDir(task.name))
            val mainVariant =
                if (creationConfig is NestedComponentCreationConfig) {
                    creationConfig.mainVariant
                } else {
                    creationConfig as VariantCreationConfig
                }
            task.lintRuleJars.from(mainVariant.global.localCustomLintChecks)
            task.lintRuleJars.from(
                mainVariant
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRuleJars.from(
                mainVariant
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRuleJars.disallowChanges()
            task.fatalOnly.setDisallowChanges(fatalOnly)
            task.checkOnly.set(
                mainVariant.services.provider {
                    creationConfig.global.lintOptions.checkOnly
                }
            )
            task.projectInputs.initialize(mainVariant, LintMode.ANALYSIS)
            task.variantInputs.initialize(
                mainVariant,
                creationConfig as? HostTestCreationConfig,
                creationConfig as? AndroidTestCreationConfig,
                creationConfig as? TestFixturesCreationConfig,
                creationConfig.services,
                mainVariant.name,
                useModuleDependencyLintModels = false,
                warnIfProjectTreatedAsExternalDependency = false,
                lintMode = LintMode.ANALYSIS,
                addBaseModuleLintModel = false,
                fatalOnly = fatalOnly,
                includeMainArtifact = creationConfig is VariantCreationConfig,
                isPerComponentLintAnalysis = true
            )

            task.lintTool.initialize(mainVariant.services, name)
            task.desugaredMethodsFiles.from(
                getDesugaredMethods(
                    mainVariant.services,
                    (mainVariant as? ConsumableCreationConfig)
                        ?.isCoreLibraryDesugaringEnabledLintCheck ?: false,
                    mainVariant.minSdk,
                    mainVariant.global
                )
            )
            task.desugaredMethodsFiles.disallowChanges()
            task.useK2Uast.setDisallowChanges(mainVariant.useK2Uast)
        }
    }

    private fun initializeGlobalInputs(
        services: TaskCreationServices,
        isAndroid: Boolean
    ) {
        val buildServiceRegistry = services.buildServiceRegistry
        this.androidGradlePluginVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        val sdkComponentsBuildService =
            getBuildService(buildServiceRegistry, SdkComponentsBuildService::class.java)

        this.android.setDisallowChanges(isAndroid)
        if(isAndroid) {
            this.androidSdkHome.set(
                sdkComponentsBuildService.flatMap { it.sdkDirectoryProvider }
                    .map { it.asFile.absolutePath }
            )
        }
        this.androidSdkHome.disallowChanges()
        this.offline.setDisallowChanges(project.gradle.startParameter.isOffline)

        // Include Lint jars set via the environment variable ANDROID_LINT_JARS
        val globalLintJarsFromEnvVariable: Provider<List<String>> =
                project.providers.environmentVariable(ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE)
                        .orElse("")
                        .map { it.split(File.pathSeparator).filter(String::isNotEmpty) }
        this.lintRuleJars.from(globalLintJarsFromEnvVariable)

        if (project.gradle.startParameter.showStacktrace != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            printStackTrace.setDisallowChanges(true)
        } else {
            printStackTrace.setDisallowChanges(
                project.providers
                    .environmentVariable(LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE)
                    .map { it.equals("true", ignoreCase = true) }
                    .orElse(
                        services.projectOptions.getProvider(BooleanOption.PRINT_LINT_STACK_TRACE)
                    )
            )
        }
        systemPropertyInputs.initialize(project.providers, LintMode.ANALYSIS)
        environmentVariableInputs.initialize(project.providers, LintMode.ANALYSIS)
        this.usesService(
            services.buildServiceRegistry.getLintParallelBuildService(services.projectOptions)
        )
    }

    /**
     * If [lintModelArtifactType] is not null, only the corresponding artifact is initialized; if
     * it's null, both the main and test artifacts are initialized.
     *
     * If [testCompileClasspath] is null, the standard "testCompileClasspath" is used; otherwise,
     * [testCompileClasspath] is used in order to properly include the main jar.
     *
     * If [testRuntimeClasspath] is null, the standard "testRuntimeClasspath" is used; otherwise,
     * [testRuntimeClasspath] is used in order to properly include the main jar.
     */
    fun configureForStandalone(
        taskCreationServices: TaskCreationServices,
        javaPluginExtension: JavaPluginExtension,
        kotlinExtensionWrapper: KotlinMultiplatformExtensionWrapper?,
        customLintChecksConfig: FileCollection,
        lintOptions: Lint,
        lintModelArtifactType: LintModelArtifactType?,
        fatalOnly: Boolean = false,
        jvmTargetName: String?,
        testCompileClasspath: Configuration? = null,
        testRuntimeClasspath: Configuration? = null
    ) {
        initializeGlobalInputs(
            services = taskCreationServices,
            isAndroid = false
        )
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(taskCreationServices.buildServiceRegistry))
        this.fatalOnly.setDisallowChanges(fatalOnly)
        this.checkOnly.setDisallowChanges(lintOptions.checkOnly)
        this.lintTool.initialize(taskCreationServices, this.name)
        this.projectInputs
            .initializeForStandalone(
                project,
                javaPluginExtension,
                lintOptions,
                LintMode.ANALYSIS
            )
        this.variantInputs
            .initializeForStandalone(
                project,
                javaPluginExtension,
                kotlinExtensionWrapper,
                taskCreationServices.projectOptions,
                fatalOnly,
                useModuleDependencyLintModels = false,
                LintMode.ANALYSIS,
                lintModelArtifactType,
                jvmTargetName,
                testCompileClasspath,
                testRuntimeClasspath
            )
        this.lintRuleJars.fromDisallowChanges(customLintChecksConfig)
        this.lintModelDirectory
            .setDisallowChanges(
                project.layout.buildDirectory.dir("intermediates/${this.name}/android-lint-model")
            )
        this.useK2Uast
            .setDisallowChanges(
                taskCreationServices.projectOptions.getProvider(BooleanOption.LINT_USE_K2_UAST)
            )
    }

    companion object {
        private const val LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE = "LINT_PRINT_STACKTRACE"
        private const val ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE = "ANDROID_LINT_JARS"
        const val PARTIAL_RESULTS_DIR_NAME = "out"

        fun registerOutputArtifacts(
            taskProvider: TaskProvider<AndroidLintAnalysisTask>,
            internalArtifactType: InternalArtifactType<Directory>,
            artifacts: ArtifactsImpl
        ) {
            artifacts
                .setInitialProvider(taskProvider, AndroidLintAnalysisTask::partialResultsDirectory)
                .withName(PARTIAL_RESULTS_DIR_NAME)
                .on(internalArtifactType)
        }

        fun registerOutputArtifacts(
            taskProvider: TaskProvider<AndroidLintAnalysisTask>,
            internalArtifactType: InternalMultipleArtifactType<Directory>,
            artifacts: ArtifactsImpl
        ) {
            artifacts.use(taskProvider)
                .wiredWith(AndroidLintAnalysisTask::partialResultsDirectory)
                .toAppendTo(internalArtifactType)
        }
    }
}
