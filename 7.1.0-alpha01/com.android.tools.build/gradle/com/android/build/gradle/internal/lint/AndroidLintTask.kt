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

@file:Suppress("UnstableApiUsage")

package com.android.build.gradle.internal.lint

import com.android.SdkConstants.DOT_JAR
import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.lint.LintTaskManager.Companion.isLintStderr
import com.android.build.gradle.internal.lint.LintTaskManager.Companion.isLintStdout
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption.USE_LINT_PARTIAL_ANALYSIS
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.AndroidProject
import com.android.tools.lint.model.LintModelSerialization
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
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
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.Collections
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileTree

/** Task to invoke lint in a process isolated worker passing in the new lint models. */
abstract class AndroidLintTask : NonIncrementalTask() {

    @get:Nested
    abstract val lintTool: LintTool

    @get:OutputDirectory
    abstract val lintModelDirectory: DirectoryProperty

    @get:Input
    abstract val textReportEnabled: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val textReportOutputFile: RegularFileProperty

    @get:Input
    abstract val htmlReportEnabled: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val htmlReportOutputFile: RegularFileProperty

    @get:Input
    abstract val xmlReportEnabled: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val xmlReportOutputFile: RegularFileProperty

    @get:Input
    abstract val sarifReportEnabled: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val sarifReportOutputFile: RegularFileProperty

    @get:Input
    abstract val textReportToStdOut: Property<Boolean>
    @get:Input
    abstract val textReportToStderr: Property<Boolean>

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
    abstract val autoFix: Property<Boolean>

    @get:Input
    abstract val reportOnly: Property<Boolean>

    @get:Internal
    abstract val lintFixBuildService: Property<LintFixBuildService>

    @get:Input
    abstract val checkDependencies: Property<Boolean>

    @get:Input
    abstract val checkOnly: ListProperty<String>

    @get:Internal
    abstract val lintCacheDirectory: DirectoryProperty

    @get:Classpath
    abstract val lintRulesJar: ConfigurableFileCollection

    @get:Classpath
    abstract val globalRuleJars: ConfigurableFileCollection

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val mainDependencyLintModels: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val androidTestDependencyLintModels: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val unitTestDependencyLintModels: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val dynamicFeatureLintModels: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:Optional
    abstract val partialResults: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:Optional
    abstract val dependencyPartialResults: ConfigurableFileCollection

    @get:Input
    abstract val printStackTrace: Property<Boolean>

    override fun doTaskAction() {
        writeLintModelFile()
        workerExecutor.noIsolation().submit(AndroidLintLauncherWorkAction::class.java) { parameters ->
            parameters.arguments.set(generateCommandLineArguments())
            parameters.lintTool.set(lintTool)
            parameters.android.set(android)
            parameters.fatalOnly.set(fatalOnly)
            parameters.lintFixBuildService.set(lintFixBuildService)
        }
    }

    /**
     * Non-isolated work action to launch lint in a process-isolated work action
     *
     * This extra layer exists to use the LintFixBuildService to only run one lint fix at a time.
     */
    abstract class AndroidLintLauncherWorkAction: WorkAction<AndroidLintLauncherWorkAction.LauncherParameters> {
        abstract class LauncherParameters: WorkParameters {
            @get:Nested
            abstract val lintTool: Property<LintTool>
            abstract val arguments: ListProperty<String>
            abstract val android: Property<Boolean>
            abstract val fatalOnly: Property<Boolean>
            // Build service to prevent multiple lint fix runs from happening concurrently.
            abstract val lintFixBuildService: Property<LintFixBuildService>
        }

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun execute() {
            val lintFixBuildService: LintFixBuildService? = parameters.lintFixBuildService.orNull
            parameters.lintTool.get().submit(
                mainClass = "com.android.tools.lint.Main",
                workerExecutor = workerExecutor,
                arguments = parameters.arguments.get(),
                android = parameters.android.get(),
                fatalOnly = parameters.fatalOnly.get(),
                await = lintFixBuildService != null // Allow only one lintFix to execute at a time.
            )
        }
    }

    private fun writeLintModelFile() {
        val module = projectInputs.convertToLintModelModule()

        val variant = variantInputs.toLintModel(module, partialResults.orNull?.asFile)

        LintModelSerialization.writeModule(
            module = module,
            destination = lintModelDirectory.get().asFile,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    private fun generateCommandLineArguments(): List<String> {

        val arguments = mutableListOf<String>()
        // Some Global flags
        if (autoFix.get()) {
            arguments += "--apply-suggestions"
            arguments += "--abort-if-suggestions-applied"
        }
        if (fatalOnly.get()) {
            arguments += "--fatalOnly"
        }
        if (reportOnly.get()) {
            arguments += "--report-only"
        }
        arguments += listOf("--jdk-home", javaHome.get())
        arguments += listOf("--sdk-home", androidSdkHome.get())

        if (textReportEnabled.get()) {
            arguments.add("--text", textReportOutputFile.get())
        }
        if (htmlReportEnabled.get()) {
            arguments.add("--html", htmlReportOutputFile.get())
        }
        if (xmlReportEnabled.get()) {
            arguments.add("--xml", xmlReportOutputFile.get())
        }
        if (sarifReportEnabled.get()) {
            arguments.add("--sarif", sarifReportOutputFile.get())
        }
        if (textReportToStdOut.get()) {
            arguments.add("--text", "stdout")
        }
        if (textReportToStderr.get()) {
            arguments.add("--text", "stderr")
        }

        val models = LinkedHashSet<String>(1)
        models += lintModelDirectory.get().asFile.absolutePath

        for (model in dynamicFeatureLintModels) {
            models.add(model.absolutePath)
        }
        for (model in mainDependencyLintModels.files) {
            models.add(model.absolutePath)
        }
        for (model in androidTestDependencyLintModels.files) {
            models.add(model.absolutePath)
        }
        for (model in unitTestDependencyLintModels.files) {
            models.add(model.absolutePath)
        }

        check(checkDependencies.get()
                || models.size == 1 + dynamicFeatureLintModels.files.size) {
            "Library dependency models should not be an input unless check dependencies is being used."
        }

        arguments += "--lint-model"
        arguments += models.asLintPaths()

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

    private fun MutableList<String>.add(arg: String, path: RegularFile) {
        add(arg)
        add(path.asFile.absolutePath)
    }

    private fun MutableList<String>.add(arg: String, value: String) {
        add(arg)
        add(value)
    }

    /** Creates the lintVariant Task. Linting a variant also includes looking at the tests for that variant. */
    class SingleVariantCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
        override val name: String = creationConfig.computeTaskName("lint")
        override val fatalOnly: Boolean get() = false
        override val autoFix: Boolean get() = false
        override val description: String get() = "Run lint on the ${creationConfig.name} variant"
        override val checkDependencies: Boolean
            get() =
                creationConfig.globalScope.extension.lintOptions.isCheckDependencies
                        && !variant.main.variantType.isDynamicFeature
        override val reportOnly: Boolean
            get() = creationConfig.services.projectOptions.get(USE_LINT_PARTIAL_ANALYSIS)

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintTask>) {
            registerLintReportArtifacts(taskProvider, creationConfig.artifacts, creationConfig.name, creationConfig.services.projectInfo.getReportsDir())
        }

        override fun configureOutputSettings(task: AndroidLintTask) {
            task.configureOutputSettings(creationConfig.globalScope.extension.lintOptions)
        }

        companion object {
            fun registerLintReportArtifacts(
                taskProvider: TaskProvider<AndroidLintTask>,
                artifacts: ArtifactsImpl,
                variantName: String?,
                reportsDirectory: File,
            ) {
                val name = "lint-results" + if (variantName != null) "-$variantName" else ""
                artifacts
                    .setInitialProvider(taskProvider, AndroidLintTask::textReportOutputFile)
                    .atLocation(reportsDirectory.absolutePath)
                    .withName("$name.txt")
                    .on(InternalArtifactType.LINT_TEXT_REPORT)
                artifacts
                    .setInitialProvider(taskProvider, AndroidLintTask::htmlReportOutputFile)
                    .atLocation(reportsDirectory.absolutePath)
                    .withName("$name.html")
                    .on(InternalArtifactType.LINT_HTML_REPORT)
                artifacts
                    .setInitialProvider(taskProvider, AndroidLintTask::xmlReportOutputFile)
                    .atLocation(reportsDirectory.absolutePath)
                    .withName("$name.xml")
                    .on(InternalArtifactType.LINT_XML_REPORT)
                artifacts
                    .setInitialProvider(taskProvider, AndroidLintTask::sarifReportOutputFile)
                    .atLocation(reportsDirectory.absolutePath)
                    .withName("$name.sarif")
                    .on(InternalArtifactType.LINT_SARIF_REPORT)
            }
        }
    }

    /** Creates the lintFix task. . */
    class FixSingleVariantCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
        override val name: String = creationConfig.computeTaskName("lintFix")
        override val fatalOnly: Boolean get() = false
        override val autoFix: Boolean get() = true
        override val description: String get() = "Fix lint on the ${creationConfig.name} variant"
        override val checkDependencies: Boolean
            get() =
                creationConfig.globalScope.extension.lintOptions.isCheckDependencies
                        && !variant.main.variantType.isDynamicFeature
        override val reportOnly: Boolean
            get() = creationConfig.services.projectOptions.get(USE_LINT_PARTIAL_ANALYSIS)

        override fun configureOutputSettings(task: AndroidLintTask) {
            task.textReportToStdOut.setDisallowChanges(true)
        }
    }

    /** CreationAction for the lintVital task. Does not use the variant with tests. */
    class LintVitalCreationAction(variant: ConsumableCreationConfig) :
            VariantCreationAction(VariantWithTests(variant, androidTest = null, unitTest = null)) {
        override val name: String = creationConfig.computeTaskName("lintVital")
        override val fatalOnly: Boolean get() = true
        override val autoFix: Boolean get() = false
        override val description: String get() = "Run lint with only the fatal issues enabled on the ${creationConfig.name} variant"
        override val checkDependencies: Boolean
            get() = false
        override val reportOnly: Boolean
            get() = creationConfig.services.projectOptions.get(USE_LINT_PARTIAL_ANALYSIS)

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintTask>) {
            variant.main.taskContainer.assembleTask.dependsOn(taskProvider)
        }

        override fun configureOutputSettings(task: AndroidLintTask) {
            task.textReportToStderr.setDisallowChanges(true)
        }
    }

    abstract class VariantCreationAction(val variant: VariantWithTests) :
            VariantTaskCreationAction<AndroidLintTask, ComponentCreationConfig>(variant.main) {
        final override val type: Class<AndroidLintTask> get() = AndroidLintTask::class.java

        abstract val fatalOnly: Boolean
        abstract val autoFix: Boolean
        abstract val description: String
        abstract val checkDependencies: Boolean
        abstract val reportOnly: Boolean

        final override fun configure(task: AndroidLintTask) {
            super.configure(task)

            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description

            task.initializeGlobalInputs(creationConfig.globalScope, creationConfig.services.projectInfo.getProject())
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
            task.autoFix.setDisallowChanges(autoFix)
            if (autoFix) {
                task.lintFixBuildService.set(getBuildService(creationConfig.services.buildServiceRegistry))
            }
            task.lintFixBuildService.disallowChanges()
            task.checkDependencies.setDisallowChanges(checkDependencies)
            task.reportOnly.setDisallowChanges(reportOnly)
            task.checkOnly.set(creationConfig.services.provider {
                creationConfig.globalScope.extension.lintOptions.checkOnly
            })
            task.projectInputs.initialize(variant)
            val hasDynamicFeatures = creationConfig.globalScope.hasDynamicFeatures()
            val includeDynamicFeatureSourceProviders = !reportOnly && hasDynamicFeatures
            task.variantInputs.initialize(
                variant,
                checkDependencies,
                warnIfProjectTreatedAsExternalDependency = true,
                includeDynamicFeatureSourceProviders = includeDynamicFeatureSourceProviders
            )
            if (reportOnly) {
                val partialResults = if (fatalOnly) {
                    creationConfig.artifacts.get(InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS)
                } else {
                    creationConfig.artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS)
                }
                task.partialResults.set(partialResults)
                if (hasDynamicFeatures) {
                    task.dynamicFeatureLintModels.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            if (fatalOnly) {
                                AndroidArtifacts.ArtifactType.LINT_VITAL_LINT_MODEL
                            } else {
                                AndroidArtifacts.ArtifactType.LINT_MODEL
                            }
                        )
                    )
                    task.dependencyPartialResults.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            if (fatalOnly) {
                                AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS
                            } else {
                                AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS
                            }
                        )
                    )
                }
            }
            task.partialResults.disallowChanges()
            task.dynamicFeatureLintModels.disallowChanges()
            if (checkDependencies) {
                task.mainDependencyLintModels.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.LINT_MODEL
                    )
                )
                task.mainDependencyLintModels.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.LINT_MODEL
                    )
                )
                variant.androidTest?.let {
                    task.androidTestDependencyLintModels.from(
                        it.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_MODEL
                        )
                    )
                    task.androidTestDependencyLintModels.from(
                        it.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_MODEL
                        )
                    )
                }
                variant.unitTest?.let {
                    task.unitTestDependencyLintModels.from(
                        it.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_MODEL
                        )
                    )
                    task.unitTestDependencyLintModels.from(
                        it.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_MODEL
                        )
                    )
                }
                if (reportOnly) {
                    task.dependencyPartialResults.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS
                        )
                    )
                    task.dependencyPartialResults.from(
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS
                        )
                    )
                }
            }
            task.mainDependencyLintModels.disallowChanges()
            task.androidTestDependencyLintModels.disallowChanges()
            task.unitTestDependencyLintModels.disallowChanges()
            task.dependencyPartialResults.disallowChanges()
            task.lintTool.initialize(creationConfig.services.projectInfo.getProject(), creationConfig.services.projectOptions)
            if (checkDependencies && !reportOnly) {
                task.outputs.upToDateWhen {
                    it.logger.debug("Lint with checkDependencies does not model all of its inputs yet.")
                    false
                }
            }
            if (autoFix) {
                task.outputs.upToDateWhen {
                    it.logger.debug("Lint fix task potentially modifies sources so cannot be up-to-date")
                    false
                }
            }
            if (includeDynamicFeatureSourceProviders) {
                task.outputs.upToDateWhen {
                    it.logger.debug("Lint with dynamic feature source providers does not model all of its inputs.")
                    false
                }
            }
            task.initializeOutputTypesConvention()
            configureOutputSettings(task)
            task.finalizeOutputTypes()
        }

        abstract fun configureOutputSettings(task: AndroidLintTask)
    }

    private fun initializeOutputTypesConvention() {
        textReportEnabled.convention(false)
        htmlReportEnabled.convention(false)
        xmlReportEnabled.convention(false)
        sarifReportEnabled.convention(false)
        textReportToStdOut.convention(false)
        textReportToStderr.convention(false)
    }

    private fun finalizeOutputTypes() {
        textReportEnabled.disallowChanges()
        htmlReportEnabled.disallowChanges()
        xmlReportEnabled.disallowChanges()
        sarifReportEnabled.disallowChanges()
        textReportToStdOut.disallowChanges()
        textReportToStderr.disallowChanges()
    }

    private fun initializeGlobalInputs(globalScope: GlobalScope, project: Project) {
        initializeGlobalInputs(
            project = project,
            isAndroid = true
        )
    }

    private fun initializeGlobalInputs(
        project: Project,
        isAndroid: Boolean
    ) {
        val buildServiceRegistry = project.gradle.sharedServices
        this.androidGradlePluginVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        val sdkComponentsBuildService =
            getBuildService<SdkComponentsBuildService>(buildServiceRegistry)
        this.androidSdkHome.setDisallowChanges(sdkComponentsBuildService.flatMap { it.sdkDirectoryProvider }.map { it.asFile.absolutePath })
        this.javaHome.setDisallowChanges(project.providers.systemProperty("java.home"))
        this.offline.setDisallowChanges(project.gradle.startParameter.isOffline)
        this.android.setDisallowChanges(isAndroid)
        this.lintCacheDirectory.setDisallowChanges(
            project.layout.projectDirectory.dir(
                AndroidProject.FD_INTERMEDIATES
            ).dir("lint-cache")
        )

        val locationBuildService = getBuildService<AndroidLocationsBuildService>(buildServiceRegistry)

        val globalLintJarsInPrefsDir: ConfigurableFileTree =
            project.fileTree(locationBuildService.map {
                it.prefsLocation.resolve("lint")
            }).also { it.include("*$DOT_JAR") }
        this.globalRuleJars.from(globalLintJarsInPrefsDir)

        this.globalRuleJars.disallowChanges()
        if (project.gradle.startParameter.showStacktrace != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            printStackTrace.setDisallowChanges(true)
        } else {
            printStackTrace.setDisallowChanges(
                project.providers.environmentVariable(LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE)
                    .map { it.equals("true", ignoreCase = true) }.orElse(false)
            )
        }
    }

    fun configureForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        javaPluginConvention: JavaPluginConvention,
        customLintChecksConfig: FileCollection,
        lintOptions: LintOptions,
        partialResults: Provider<Directory>,
        fatalOnly: Boolean = false,
        autoFix: Boolean = false,
    ) {
        initializeGlobalInputs(
            project = project,
            isAndroid = false)
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        this.fatalOnly.setDisallowChanges(fatalOnly)
        this.autoFix.setDisallowChanges(autoFix)
        if (autoFix) {
            this.lintFixBuildService.set(getBuildService(project.gradle.sharedServices))
        }
        this.lintFixBuildService.disallowChanges()
        this.checkDependencies.setDisallowChanges(false)
        this.reportOnly.setDisallowChanges(true)
        this.checkOnly.setDisallowChanges(lintOptions.checkOnly)
        this.lintTool.initialize(project, projectOptions)
        this.projectInputs.initializeForStandalone(project, javaPluginConvention, lintOptions)
        // Do not support check dependencies in the standalone lint plugin
        this.variantInputs.initializeForStandalone(project, javaPluginConvention, projectOptions, checkDependencies=false)
        this.lintRulesJar.fromDisallowChanges(customLintChecksConfig)
        this.lintModelDirectory.setDisallowChanges(project.layout.buildDirectory.dir("intermediates/android-lint-model"))
        this.partialResults.setDisallowChanges(partialResults)
        this.initializeOutputTypesConvention()
        when {
            fatalOnly -> {
                this.textReportToStderr.setDisallowChanges(true)
            }
            autoFix -> {
                this.textReportToStdOut.setDisallowChanges(true)
                this.outputs.upToDateWhen {
                    it.logger.debug("Lint fix task potentially modifies sources so cannot be up-to-date")
                    false
                }
            }
            else -> {
                configureOutputSettings(lintOptions)
            }
        }
        this.finalizeOutputTypes()

    }

    private fun configureOutputSettings(lintOptions: LintOptions) {
        this.textReportEnabled.setDisallowChanges(lintOptions.textReport)
        // If text report is requested, but no path specified, output to stdout, hence the ?: true
        this.textReportToStdOut.setDisallowChanges(
            lintOptions.textReport && lintOptions.textOutput?.isLintStdout() ?: true
        )
        this.textReportToStderr.setDisallowChanges(
            lintOptions.textReport && lintOptions.textOutput?.isLintStderr() ?: false
        )
        this.htmlReportEnabled.setDisallowChanges(lintOptions.htmlReport)
        this.xmlReportEnabled.setDisallowChanges(lintOptions.xmlReport)
        this.sarifReportEnabled.setDisallowChanges(lintOptions.sarifReport)
    }


    companion object {
        private const val LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE = "LINT_PRINT_STACKTRACE"
        const val LINT_CLASS_PATH = "lintClassPath"
    }
}
