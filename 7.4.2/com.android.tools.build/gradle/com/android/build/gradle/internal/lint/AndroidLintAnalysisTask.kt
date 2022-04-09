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
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getLintParallelBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.ide.common.repository.GradleVersion
import com.android.tools.lint.model.LintModelSerialization
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaBasePlugin
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
            lintMode = LintMode.ANALYSIS
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

        // Pass information to lint using the --client-id, --client-name, and --client-version flags
        // so that lint can apply gradle-specific and version-specific behaviors.
        arguments.add("--client-id", "gradle")
        arguments.add("--client-name", "AGP")
        arguments.add("--client-version", Version.ANDROID_GRADLE_PLUGIN_VERSION)

        // Pass --offline flag only if lint version is 30.3.0-beta01 or higher because earlier
        // versions of lint don't accept that flag.
        if (offline.get()
            && GradleVersion.tryParse(lintTool.version.get())
                ?.isAtLeast(30, 3, 0, "beta", 1, false) == true) {
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
        override val name = creationConfig.computeTaskName("lintAnalyze")
        override val fatalOnly = false
        override val description = "Run lint analysis on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintAnalysisTask>) {
            registerOutputArtifacts(
                taskProvider,
                InternalArtifactType.LINT_PARTIAL_RESULTS,
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
        override val name = creationConfig.computeTaskName("lintVitalAnalyze")
        override val fatalOnly = true
        override val description =
            "Run lint analysis with only the fatal issues enabled on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintAnalysisTask>) {
            registerOutputArtifacts(
                taskProvider,
                InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS,
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

            task.group = JavaBasePlugin.VERIFICATION_GROUP
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
                checkDependencies = false,
                warnIfProjectTreatedAsExternalDependency = false,
                LintMode.ANALYSIS
            )
            task.lintTool.initialize(creationConfig.services)
            task.desugaredMethodsFiles.from(
                getDesugaredMethods(
                    creationConfig.services,
                    creationConfig.global.compileOptions.isCoreLibraryDesugaringEnabled,
                    creationConfig.minSdkVersion,
                    creationConfig.global.compileSdkHashString,
                    creationConfig.global.bootClasspath
                )
            )
            task.desugaredMethodsFiles.disallowChanges()
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
                    .orElse(false)
            )
        }
        systemPropertyInputs.initialize(project.providers, LintMode.ANALYSIS)
        environmentVariableInputs.initialize(project.providers, LintMode.ANALYSIS)
        this.usesService(
            services.buildServiceRegistry.getLintParallelBuildService(services.projectOptions)
        )
    }

    fun configureForStandalone(
        taskCreationServices: TaskCreationServices,
        javaPluginExtension: JavaPluginExtension,
        customLintChecksConfig: FileCollection,
        lintOptions: Lint,
        fatalOnly: Boolean = false
    ) {
        initializeGlobalInputs(
            services = taskCreationServices,
            isAndroid = false
        )
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(taskCreationServices.buildServiceRegistry))
        this.fatalOnly.setDisallowChanges(fatalOnly)
        this.checkOnly.setDisallowChanges(lintOptions.checkOnly)
        this.lintTool.initialize(taskCreationServices)
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
                taskCreationServices.projectOptions,
                fatalOnly,
                checkDependencies = false,
                LintMode.ANALYSIS
            )
        this.lintRuleJars.fromDisallowChanges(customLintChecksConfig)
        this.lintModelDirectory
            .setDisallowChanges(
                project.layout.buildDirectory.dir("intermediates/${this.name}/android-lint-model")
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
    }
}
