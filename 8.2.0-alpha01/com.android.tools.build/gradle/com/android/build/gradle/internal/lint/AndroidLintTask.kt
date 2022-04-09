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

import com.android.SdkConstants.VALUE_TRUE
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
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.repository.GradleVersion
import com.android.tools.lint.model.LintModelSerialization
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
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
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.Collections
import javax.inject.Inject

/** Task to invoke lint in a process isolated worker passing in the new lint models. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.LINT)
abstract class AndroidLintTask : NonIncrementalTask() {

    @get:Nested
    abstract val lintTool: LintTool

    @get:OutputDirectory
    abstract val lintModelDirectory: DirectoryProperty

    /**
     * This task needs the location of the lint model directory produced by [LintModelWriterTask]
     * in order to ensure that that directory is not passed to lint via --lint-model, which would
     * be problematic because [lintModelDirectory] is already being passed to lint via --lint-model.
     * See b/190855628.
     */
    @get:Input
    abstract val lintModelWriterTaskOutputPath: Property<String>

    @get:Input
    abstract val textReportEnabled: Property<Boolean>

    @get:OutputFile
    @get:Optional
    abstract val textReportOutputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val intermediateTextReport: RegularFileProperty

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

    @get:Internal
    abstract val lintFixBuildService: Property<LintFixBuildService>

    @get:Input
    abstract val checkOnly: ListProperty<String>

    @get:Classpath
    abstract val lintRuleJars: ConfigurableFileCollection

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

    @get:Nested
    abstract val systemPropertyInputs: SystemPropertyInputs

    @get:Nested
    abstract val environmentVariableInputs: EnvironmentVariableInputs

    @get:OutputFile
    @get:Optional
    abstract val returnValueOutputFile: RegularFileProperty

    @get:Input
    abstract val lintMode: Property<LintMode>

    @get:Input
    abstract val missingBaselineIsEmptyBaseline: Property<Boolean>

    override fun doTaskAction() {
        lintTool.lintClassLoaderBuildService.get().shouldDispose = true
        if (systemPropertyInputs.lintAutofix.orNull == VALUE_TRUE) {
            logger.warn(
                "Running lint with -Dlint.autofix=true is not supported by the Android Gradle "
                        + "Plugin. Please try running the lintFix task instead."
            )
        }
        writeLintModelFile()
        val baselineFile = projectInputs.lintOptions.baseline.orNull?.asFile
        var originalBaselineFileText: String? = null
        var originalBaselineFileLines: List<String>? = null
        if (lintMode.get() == LintMode.UPDATE_BASELINE) {
            // Warn and return early if no baseline file is specified.
            if (baselineFile == null) {
                logger.warn(getUpdateBaselineWarning())
                return
            }
            if (baselineFile.isFile) {
                originalBaselineFileText = baselineFile.readText()
                originalBaselineFileLines = baselineFile.readLines()
            }
            // Delete existing baseline file if running the updateLintBaseline task.
            FileUtils.deleteIfExists(baselineFile)
        }
        workerExecutor.noIsolation().submit(AndroidLintLauncherWorkAction::class.java) { parameters ->
            parameters.arguments.set(generateCommandLineArguments())
            parameters.lintTool.set(lintTool)
            parameters.android.set(android)
            parameters.fatalOnly.set(fatalOnly)
            parameters.lintFixBuildService.set(lintFixBuildService)
            parameters.returnValueOutputFile.set(returnValueOutputFile)
            parameters.lintMode.set(lintMode)
            parameters.hasBaseline.set(projectInputs.lintOptions.baseline.orNull != null)
        }
        if (lintMode.get() == LintMode.UPDATE_BASELINE
            && originalBaselineFileText != null
            && originalBaselineFileLines != null
            && baselineFile != null) {
            workerExecutor.await()
            // Don't change the baseline file if the only changes are the lint or AGP versions
            // listed in the file (b/248338457).
            if (!isBaselineChanged(originalBaselineFileLines, baselineFile)) {
                baselineFile.writeText(originalBaselineFileText)
            }
        }
    }

    /**
     * Non-isolated work action to launch lint in a possibly process-isolated work action
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
            abstract val returnValueOutputFile: RegularFileProperty
            abstract val lintMode: Property<LintMode>
            abstract val hasBaseline: Property<Boolean>
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
                await = lintFixBuildService != null, // Allow only one lintFix to execute at a time.
                lintMode = parameters.lintMode.get(),
                hasBaseline = parameters.hasBaseline.get(),
                returnValueOutputFile = parameters.returnValueOutputFile.orNull?.asFile
            )
        }
    }

    private fun writeLintModelFile() {
        val module = projectInputs.convertToLintModelModule()

        val variant =
            variantInputs.toLintModel(
                module,
                partialResults.orNull?.asFile,
                desugaredMethodsFiles = listOf()
            )

        LintModelSerialization.writeModule(
            module = module,
            destination = lintModelDirectory.get().asFile,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    private fun getUpdateBaselineWarning(): String {
        val example = if (android.get()) {
            """
                ```
                android {
                    lint {
                        baseline = file("lint-baseline.xml")
                    }
                }
                ```
            """.trimIndent()
        } else {
            """
                ```
                lint {
                    baseline = file("lint-baseline.xml")
                }
                ```
            """.trimIndent()
        }
        return """
            No baseline file is specified, so no baseline file will be created.

            Please specify a baseline file in the build.gradle file like so:

            $example
        """.trimIndent()
    }

    @VisibleForTesting
    internal fun generateCommandLineArguments(): List<String> {

        val arguments = mutableListOf<String>()
        // Some Global flags
        if (autoFix.get()) {
            arguments += "--apply-suggestions"
            arguments += "--abort-if-suggestions-applied"
        }
        if (fatalOnly.get()) {
            arguments += "--fatalOnly"
        }
        if (lintMode.get() == LintMode.UPDATE_BASELINE) {
            arguments += "--update-baseline"
        }
        arguments += "--report-only"
        arguments += listOf("--jdk-home", systemPropertyInputs.javaHome.get())
        androidSdkHome.orNull?.let { arguments.add("--sdk-home", it) }

        if (textReportEnabled.get()) {
            arguments.add("--text", textReportOutputFile.get())
        }
        intermediateTextReport.orNull?.let { arguments.add("--text", it) }
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

        models.remove(lintModelWriterTaskOutputPath.get())

        arguments += "--lint-model"
        arguments += models.asLintPaths()

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
        if (systemPropertyInputs.lintBaselinesContinue.orNull == VALUE_TRUE) {
            arguments += "--continue-after-baseline-created"
        }
        if (missingBaselineIsEmptyBaseline.get()) {
            arguments += "--missing-baseline-is-empty-baseline"
        }

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
        override val name: String = creationConfig.computeTaskName("lintReport")
        override val fatalOnly: Boolean get() = false
        override val autoFix: Boolean get() = false
        override val lintMode: LintMode get() = LintMode.REPORTING
        override val description: String get() = "Run lint on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintTask>) {
            registerLintIntermediateArtifacts(
                taskProvider,
                creationConfig.artifacts,
                variantName = creationConfig.name
            )
            registerLintReportArtifacts(taskProvider, creationConfig.artifacts, creationConfig.name, creationConfig.services.projectInfo.getReportsDir())
        }

        override fun configureOutputSettings(task: AndroidLintTask) {
            task.configureOutputSettings(creationConfig.global.lintOptions)
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
        override val lintMode: LintMode get() = LintMode.REPORTING
        override val description: String get() = "Fix lint on the ${creationConfig.name} variant"

        override fun configureOutputSettings(task: AndroidLintTask) {
            task.textReportToStdOut.setDisallowChanges(true)
        }
    }

    /** CreationAction for the lintVital task. Does not use the variant with tests. */
    class LintVitalCreationAction(variant: VariantCreationConfig) :
            VariantCreationAction(VariantWithTests(
                variant,
                androidTest = null,
                unitTest = null,
                testFixtures = null
            )) {
        override val name: String = creationConfig.computeTaskName("lintVitalReport")
        override val fatalOnly: Boolean get() = true
        override val autoFix: Boolean get() = false
        override val lintMode: LintMode get() = LintMode.REPORTING
        override val description: String get() = "Run lint with only the fatal issues enabled on the ${creationConfig.name} variant"

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintTask>) {
            registerLintIntermediateArtifacts(
                taskProvider,
                creationConfig.artifacts,
                fatalOnly = true,
                variantName = creationConfig.name
            )
        }

        override fun configureOutputSettings(task: AndroidLintTask) {
            // do nothing
        }
    }

    /** Creates the updateLintBaseline task. */
    class UpdateBaselineCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
        override val name: String = creationConfig.computeTaskName("updateLintBaseline")
        override val fatalOnly: Boolean get() = false
        override val autoFix: Boolean get() = false
        override val lintMode: LintMode get() = LintMode.UPDATE_BASELINE
        override val description: String
            get() = "Update the lint baseline using the ${creationConfig.name} variant"

        override fun configureOutputSettings(task: AndroidLintTask) {
            // do nothing
        }
    }

    abstract class VariantCreationAction(val variant: VariantWithTests) :
            VariantTaskCreationAction<AndroidLintTask, ComponentCreationConfig>(variant.main) {
        final override val type: Class<AndroidLintTask> get() = AndroidLintTask::class.java

        abstract val fatalOnly: Boolean
        abstract val autoFix: Boolean
        abstract val description: String
        abstract val lintMode: LintMode

        final override fun configure(task: AndroidLintTask) {
            super.configure(task)

            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description

            task.initializeGlobalInputs(
                variant.main.services,
                isAndroid = true,
                lintMode
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
            task.autoFix.setDisallowChanges(autoFix)
            task.lintMode.setDisallowChanges(lintMode)
            if (autoFix) {
                task.lintFixBuildService.set(getBuildService(creationConfig.services.buildServiceRegistry))
            }
            task.lintFixBuildService.disallowChanges()
            task.checkOnly.setDisallowChanges(creationConfig.services.provider {
                creationConfig.global.lintOptions.checkOnly
            })
            task.projectInputs.initialize(variant, lintMode)
            task.outputs.upToDateWhen {
                // Workaround for b/193244776
                // Ensure the task runs if inputBaselineFile is set and the file doesn't exist,
                // unless missingBaselineIsEmptyBaseline is true.
                task.projectInputs.lintOptions.baseline.orNull?.asFile?.exists() ?: true
                        || task.missingBaselineIsEmptyBaseline.get()
            }
            val hasDynamicFeatures = creationConfig.global.hasDynamicFeatures
            task.variantInputs.initialize(
                variant,
                checkDependencies = true,
                warnIfProjectTreatedAsExternalDependency = true,
                lintMode
            )
            val partialResults = if (fatalOnly) {
                creationConfig.artifacts.get(InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS)
            } else {
                creationConfig.artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS)
            }
            task.partialResults.setDisallowChanges(partialResults)
            val lintModelArtifactType =
                if (fatalOnly) {
                    AndroidArtifacts.ArtifactType.LINT_VITAL_LINT_MODEL
                } else {
                    AndroidArtifacts.ArtifactType.LINT_MODEL
                }
            val lintPartialResultsArtifactType =
                if (fatalOnly) {
                    AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS
                } else {
                    AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS
                }
            if (hasDynamicFeatures) {
                task.dynamicFeatureLintModels.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        lintModelArtifactType
                    )
                )
                task.dependencyPartialResults.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        lintPartialResultsArtifactType
                    )
                )
            }
            task.dynamicFeatureLintModels.disallowChanges()
            task.mainDependencyLintModels.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    lintModelArtifactType
                )
            )
            task.mainDependencyLintModels.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    lintModelArtifactType
                )
            )
            variant.androidTest?.let {
                task.androidTestDependencyLintModels.from(
                    it.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        lintModelArtifactType
                    )
                )
                task.androidTestDependencyLintModels.from(
                    it.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        lintModelArtifactType
                    )
                )
            }
            variant.unitTest?.let {
                task.unitTestDependencyLintModels.from(
                    it.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        lintModelArtifactType
                    )
                )
                task.unitTestDependencyLintModels.from(
                    it.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        lintModelArtifactType
                    )
                )
            }
            task.dependencyPartialResults.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    lintPartialResultsArtifactType
                )
            )
            task.dependencyPartialResults.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    lintPartialResultsArtifactType
                )
            )
            task.mainDependencyLintModels.disallowChanges()
            task.androidTestDependencyLintModels.disallowChanges()
            task.unitTestDependencyLintModels.disallowChanges()
            task.dependencyPartialResults.disallowChanges()
            task.lintTool.initialize(creationConfig.services)
            task.lintModelWriterTaskOutputPath.setDisallowChanges(
                creationConfig.artifacts.getOutputPath(InternalArtifactType.LINT_MODEL).absolutePath
            )
            if (autoFix) {
                task.outputs.upToDateWhen {
                    it.logger.debug("Lint fix task potentially modifies sources so cannot be up-to-date")
                    false
                }
            }
            task.initializeOutputTypesConvention()
            configureOutputSettings(task)
            task.finalizeOutputTypes()
            task.missingBaselineIsEmptyBaseline
                .setDisallowChanges(
                    creationConfig.services
                        .projectOptions
                        .getProvider(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE)
                )
        }

        abstract fun configureOutputSettings(task: AndroidLintTask)

        companion object {
            @JvmStatic
            fun registerLintIntermediateArtifacts(
                taskProvider: TaskProvider<AndroidLintTask>,
                artifacts: ArtifactsImpl,
                fatalOnly: Boolean = false,
                variantName: String? = null
            ) {
                val reportName = "lint-results" + if (variantName != null) "-$variantName" else ""
                artifacts
                    .setInitialProvider(taskProvider, AndroidLintTask::intermediateTextReport)
                    .withName("$reportName.txt")
                    .on(
                        when {
                            fatalOnly -> InternalArtifactType.LINT_VITAL_INTERMEDIATE_TEXT_REPORT
                            else -> InternalArtifactType.LINT_INTERMEDIATE_TEXT_REPORT
                        }
                    )

                val returnValueName =
                    "return-value" + if (variantName != null) "-$variantName" else ""
                artifacts
                    .setInitialProvider(taskProvider, AndroidLintTask::returnValueOutputFile)
                    .withName("$returnValueName.txt")
                    .on(
                        when {
                            fatalOnly -> InternalArtifactType.LINT_VITAL_RETURN_VALUE
                            else -> InternalArtifactType.LINT_RETURN_VALUE
                        }
                    )
            }
        }
    }

    private fun initializeOutputTypesConvention() {
        textReportEnabled.convention(false)
        htmlReportEnabled.convention(false)
        xmlReportEnabled.convention(false)
        sarifReportEnabled.convention(false)
        textReportToStdOut.convention(false)
    }

    private fun finalizeOutputTypes() {
        textReportEnabled.disallowChanges()
        htmlReportEnabled.disallowChanges()
        xmlReportEnabled.disallowChanges()
        sarifReportEnabled.disallowChanges()
        textReportToStdOut.disallowChanges()
    }

    private fun initializeGlobalInputs(
        services: TaskCreationServices,
        isAndroid: Boolean,
        lintMode: LintMode
    ) {
        val buildServiceRegistry = project.gradle.sharedServices
        this.androidGradlePluginVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        this.android.setDisallowChanges(isAndroid)
        if(isAndroid){
            val sdkComponentsBuildService =
                getBuildService(buildServiceRegistry, SdkComponentsBuildService::class.java)
            this.androidSdkHome.set(sdkComponentsBuildService.flatMap { it.sdkDirectoryProvider }.map { it.asFile.absolutePath })
        }
        this.androidSdkHome.disallowChanges()

        this.offline.setDisallowChanges(project.gradle.startParameter.isOffline)

        // Also include Lint jars set via the environment variable ANDROID_LINT_JARS
        val globalLintJarsFromEnvVariable: Provider<List<String>> =
                project.providers.environmentVariable(ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE)
                        .orElse("")
                        .map { it.split(File.pathSeparator).filter(String::isNotEmpty) }
        this.lintRuleJars.from(globalLintJarsFromEnvVariable)

        if (project.gradle.startParameter.showStacktrace != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            printStackTrace.setDisallowChanges(true)
        } else {
            printStackTrace.setDisallowChanges(
                project.providers.environmentVariable(LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE)
                    .map { it.equals("true", ignoreCase = true) }
                    .orElse(
                        services.projectOptions.getProvider(BooleanOption.PRINT_LINT_STACK_TRACE)
                    )
            )
        }
        systemPropertyInputs.initialize(project.providers, lintMode)
        environmentVariableInputs.initialize(project.providers, lintMode)
    }

    fun configureForStandalone(
        taskCreationServices: TaskCreationServices,
        javaPluginExtension: JavaPluginExtension,
        customLintChecksConfig: FileCollection,
        lintOptions: Lint,
        partialResults: Provider<Directory>,
        lintModelWriterTaskOutputDir: File,
        lintMode: LintMode,
        fatalOnly: Boolean = false,
        autoFix: Boolean = false,
    ) {
        val projectInfo = taskCreationServices.projectInfo
        initializeGlobalInputs(
            taskCreationServices,
            isAndroid = false,
            lintMode
        )
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(
            getBuildService(taskCreationServices.buildServiceRegistry)
        )
        this.fatalOnly.setDisallowChanges(fatalOnly)
        this.autoFix.setDisallowChanges(autoFix)
        this.lintMode.setDisallowChanges(lintMode)
        if (autoFix) {
            this.lintFixBuildService.set(getBuildService(taskCreationServices.buildServiceRegistry))
        }
        this.lintFixBuildService.disallowChanges()
        this.checkOnly.setDisallowChanges(lintOptions.checkOnly)
        this.lintTool.initialize(taskCreationServices)
        this.projectInputs
            .initializeForStandalone(project, javaPluginExtension, lintOptions, lintMode)
        // Workaround for b/193244776 - Ensure the task runs if a baseline file is set and the file
        // doesn't exist, unless missingBaselineIsEmptyBaseline is true.
        this.outputs.upToDateWhen {
            this.projectInputs.lintOptions.baseline.orNull?.asFile?.exists() ?: true
                    || this.missingBaselineIsEmptyBaseline.get()
        }
        // The updateLintBaseline task should never be UP-TO-DATE because the baseline file is not
        // annotated as an output
        this.outputs.upToDateWhen { this.lintMode.get() != LintMode.UPDATE_BASELINE }
        // Do not support check dependencies in the standalone lint plugin
        this.variantInputs
            .initializeForStandalone(
                project,
                javaPluginExtension,
                taskCreationServices.projectOptions,
                fatalOnly,
                checkDependencies = true,
                lintMode
            )
        this.lintRuleJars.fromDisallowChanges(customLintChecksConfig)
        this.lintModelDirectory.setDisallowChanges(
            projectInfo.buildDirectory.dir("intermediates/${this.name}/android-lint-model")
        )
        this.partialResults.setDisallowChanges(partialResults)
        this.lintModelWriterTaskOutputPath.setDisallowChanges(
            lintModelWriterTaskOutputDir.absolutePath
        )
        this.initializeOutputTypesConvention()
        when {
            fatalOnly -> {
                // do nothing
            }
            autoFix -> {
                this.textReportToStdOut.setDisallowChanges(true)
                this.outputs.upToDateWhen {
                    it.logger.debug("Lint fix task potentially modifies sources so cannot be up-to-date")
                    false
                }
            }
            lintMode == LintMode.UPDATE_BASELINE -> {
                // do nothing
            }
            else -> {
                configureOutputSettings(lintOptions)
            }
        }
        this.finalizeOutputTypes()
        this.missingBaselineIsEmptyBaseline
            .setDisallowChanges(
                taskCreationServices.projectOptions
                    .getProvider(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE)
            )
    }

    private fun configureOutputSettings(lintOptions: Lint) {
        // Always output the text report for the text output task
        this.textReportEnabled.setDisallowChanges(true)
        this.htmlReportEnabled.setDisallowChanges(lintOptions.htmlReport)
        this.xmlReportEnabled.setDisallowChanges(lintOptions.xmlReport)
        this.sarifReportEnabled.setDisallowChanges(lintOptions.sarifReport)
    }


    companion object {
        private const val LINT_PRINT_STACKTRACE_ENVIRONMENT_VARIABLE = "LINT_PRINT_STACKTRACE"
        private const val ANDROID_LINT_JARS_ENVIRONMENT_VARIABLE = "ANDROID_LINT_JARS"

        /**
         * Return whether [newBaselineFile] has different content than [originalBaselineFileLines],
         * ignoring changes to the attributes of the <issues> element (b/248338457).
         */
        private fun isBaselineChanged(
            originalBaselineFileLines: List<String>,
            newBaselineFile: File
        ): Boolean {
            if (!newBaselineFile.isFile) {
                return true
            }
            val newBaselineFileLines = newBaselineFile.readLines()
            if (originalBaselineFileLines.size != newBaselineFileLines.size) {
                return true
            }
            newBaselineFileLines.forEachIndexed { i, newLine ->
                // ignore changes to the attributes of the <issues> element
                if (!newLine.startsWith("<issues")) {
                    val originalLine = originalBaselineFileLines[i]
                    if (originalLine.trim() != newLine.trim()) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
