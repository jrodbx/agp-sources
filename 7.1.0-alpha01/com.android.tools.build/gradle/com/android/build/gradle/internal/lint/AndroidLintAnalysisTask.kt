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
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.AndroidProject
import com.android.tools.lint.model.LintModelSerialization
import com.android.utils.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.util.Collections
import javax.inject.Inject

/** Task to invoke lint with the --analyze-only flag, producing partial results. */
abstract class AndroidLintAnalysisTask : NonIncrementalTask() {

    @get:Nested
    abstract val lintTool: LintTool

    @get:Internal
    abstract val lintModelDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val partialResultsDirectory: DirectoryProperty

    @get:Internal
    abstract val javaHome: Property<String>

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

    @get:Internal
    abstract val lintCacheDirectory: DirectoryProperty

    @get:Classpath
    abstract val lintRulesJar: ConfigurableFileCollection

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    @get:Input
    abstract val printStackTrace: Property<Boolean>

    override fun doTaskAction() {
        writeLintModelFile()
        workerExecutor.noIsolation().submit(AndroidLintAnalysisWorkAction::class.java) {
            it.arguments.set(generateCommandLineArguments())
            it.lintTool.set(lintTool)
            it.android.set(android)
            it.fatalOnly.set(fatalOnly)
        }
    }

    /**
     * Non-isolated work action to launch lint in a process-isolated work action
     */
    abstract class AndroidLintAnalysisWorkAction:
        WorkAction<AndroidLintAnalysisWorkAction.LauncherParameters>
    {
        abstract class LauncherParameters: WorkParameters {
            @get:Nested
            abstract val lintTool: Property<LintTool>
            abstract val arguments: ListProperty<String>
            abstract val android: Property<Boolean>
            abstract val fatalOnly: Property<Boolean>
        }

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun execute() {
            parameters.lintTool.get().submit(
                mainClass = "com.android.tools.lint.Main",
                workerExecutor = workerExecutor,
                arguments = parameters.arguments.get(),
                android = parameters.android.get(),
                fatalOnly = parameters.fatalOnly.get(),
                await = false
            )
        }
    }

    private fun writeLintModelFile() {
        val module = projectInputs.convertToLintModelModule()

        val variant = variantInputs.toLintModel(module, partialResultsDirectory.get().asFile)

        val destination = lintModelDirectory.get().asFile.also { FileUtils.cleanOutputDir(it) }

        LintModelSerialization.writeModule(
            module = module,
            destination = destination,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    private fun generateCommandLineArguments(): List<String> {

        val arguments = mutableListOf<String>()

        arguments += "--analyze-only"
        if (fatalOnly.get()) {
            arguments += "--fatalOnly"
        }
        arguments += listOf("--jdk-home", javaHome.get())
        arguments += listOf("--sdk-home", androidSdkHome.get())

        arguments += "--lint-model"
        arguments += listOf(lintModelDirectory.get().asFile.absolutePath).asLintPaths()

        for (check in checkOnly.get()) {
            arguments += listOf("--check", check)
        }

        val rules = lintRulesJar.files.filter { it.isFile }.map { it.absolutePath }
        if (rules.isNotEmpty()) {
            arguments += "--lint-rule-jars"
            arguments += rules.asLintPaths()
        }
        if (printStackTrace.get()) {
            arguments += "--stacktrace"
        }

        return Collections.unmodifiableList(arguments)
    }

    // See LintUtils.splitPath: Using `;` as a suffix to avoid triggering the path that uses `:`,
    // even if there is only one path.
    private fun Collection<String>.asLintPaths() = joinToString(separator = ";", postfix = ";")

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
    class LintVitalCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
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
                    ComponentCreationConfig>(variant.main)
    {
        final override val type: Class<AndroidLintAnalysisTask>
            get() = AndroidLintAnalysisTask::class.java

        abstract val fatalOnly: Boolean
        abstract val description: String

        final override fun configure(task: AndroidLintAnalysisTask) {
            super.configure(task)

            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description

            task.initializeGlobalInputs(creationConfig.globalScope, task.project)
            task.lintModelDirectory.set(variant.main.paths.getIncrementalDir(task.name))
            task.lintRulesJar.from(creationConfig.globalScope.localCustomLintChecks)
            task.lintRulesJar.from(
                creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRulesJar.from(
                creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.LINT
                    )
            )
            task.lintRulesJar.disallowChanges()
            task.fatalOnly.setDisallowChanges(fatalOnly)
            task.checkOnly.set(
                creationConfig.services.provider {
                    creationConfig.globalScope.extension.lintOptions.checkOnly
                }
            )
            task.projectInputs.initialize(variant)
            task.variantInputs.initialize(
                variant,
                checkDependencies = false,
                warnIfProjectTreatedAsExternalDependency = false
            )
            task.lintTool
                .initialize(
                    creationConfig.services.projectInfo.getProject(),
                    creationConfig.services.projectOptions
                )
        }
    }

    private fun initializeGlobalInputs(globalScope: GlobalScope, project: Project) {
        initializeGlobalInputs(project, isAndroid = true)
    }

    private fun initializeGlobalInputs(
        project: Project,
        isAndroid: Boolean
    ) {
        val buildServiceRegistry = project.gradle.sharedServices
        this.androidGradlePluginVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        val sdkComponentsBuildService =
            getBuildService<SdkComponentsBuildService>(buildServiceRegistry)
        this.androidSdkHome.setDisallowChanges(
            sdkComponentsBuildService.flatMap { it.sdkDirectoryProvider }
                .map { it.asFile.absolutePath }
        )
        this.javaHome.setDisallowChanges(project.providers.systemProperty("java.home"))
        this.offline.setDisallowChanges(project.gradle.startParameter.isOffline)
        this.android.setDisallowChanges(isAndroid)
        this.lintCacheDirectory.setDisallowChanges(
            project.layout.projectDirectory.dir(AndroidProject.FD_INTERMEDIATES).dir("lint-cache")
        )
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
    }

    fun configureForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        javaPluginConvention: JavaPluginConvention,
        customLintChecksConfig: FileCollection,
        lintOptions: LintOptions,
        fatalOnly: Boolean = false
    ) {
        initializeGlobalInputs(project = project, isAndroid = false)
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        this.fatalOnly.setDisallowChanges(fatalOnly)
        this.checkOnly.setDisallowChanges(lintOptions.checkOnly)
        this.lintTool.initialize(project, projectOptions)
        this.projectInputs.initializeForStandalone(project, javaPluginConvention, lintOptions)
        this.variantInputs
            .initializeForStandalone(
                project,
                javaPluginConvention,
                projectOptions,
                checkDependencies=false
            )
        this.lintRulesJar.fromDisallowChanges(customLintChecksConfig)
        this.lintModelDirectory
            .setDisallowChanges(
                project.layout.buildDirectory.dir("intermediates/android-lint-model")
            )
    }

    companion object {
        private const val LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE = "LINT_PRINT_STACKTRACE"
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
