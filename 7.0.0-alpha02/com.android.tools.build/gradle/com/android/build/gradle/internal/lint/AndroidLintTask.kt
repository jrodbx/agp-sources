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

package com.android.build.gradle.internal.lint

import com.android.Version
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.AndroidProject
import com.android.tools.lint.model.LintModelSerialization
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Collections

/** Task to invoke lint in a process isolated worker passing in the new lint models. */
abstract class AndroidLintTask : NonIncrementalTask() {

    /** Lint itself */
    @get:Classpath
    abstract val lintClasspath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val lintModelDirectory: DirectoryProperty

    @get:Internal
    abstract val javaHome: Property<String>

    @get:Internal
    abstract val androidSdkHome: Property<String>

    @get:Internal // TODO(160392650): Model lint outputs correctly
    abstract val reportsDir: DirectoryProperty

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
    abstract val checkDependencies: Property<Boolean>

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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val mainDependencyLintModels: ConfigurableFileCollection


    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val androidTestDependencyLintModels: ConfigurableFileCollection


    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val unitTestDependencyLintModels: ConfigurableFileCollection

    override fun doTaskAction() {
        writeLintModelFile()
        workerExecutor.noIsolation().submit(AndroidLintWorkAction::class.java) { parameters ->
            parameters.arguments.set(generateCommandLineArguments())
            parameters.classpath.from(lintClasspath)
            parameters.android.set(android)
            parameters.fatalOnly.set(fatalOnly)
            parameters.lintFixBuildService.set(lintFixBuildService)
        }
    }

    private fun writeLintModelFile() {
        val module = projectInputs.convertToLintModelModule()

        val variant = variantInputs.toLintModel(module)

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
        arguments += listOf("--jdk-home", javaHome.get())
        arguments += listOf("--sdk-home", androidSdkHome.get())

        val models = LinkedHashSet<String>(1)
        models += lintModelDirectory.get().asFile.absolutePath

        for (model in mainDependencyLintModels) {
            models.add(model.absolutePath)
        }
        for (model in androidTestDependencyLintModels) {
            models.add(model.absolutePath)
        }
        for (model in unitTestDependencyLintModels) {
            models.add(model.absolutePath)
        }

        check(checkDependencies.get() || models.size == 1) {
            "Dependency models should not be an input unless check dependencies is being used."
        }

        arguments += "--lint-model"
        arguments += models.asLintPaths()

        for (check in checkOnly.get()) {
            arguments += listOf("--check", check)
        }

        arguments += listOf("--variant", variantInputs.name.get())

        val rules = lintRulesJar.files.filter { it.isFile }.map { it.absolutePath }
        if (rules.isNotEmpty()) {
            arguments += "--lint-rule-jars"
            arguments += rules.asLintPaths()
        }

        return Collections.unmodifiableList(arguments)
    }

    // See LintUtils.splitPath: Using `;` as a suffix to avoid triggering the path that uses `:`,
    // even if there is only one path.
    private fun Collection<String>.asLintPaths() = joinToString(separator = ";", postfix = ";")

    /** Creates the lintVariant Task. Linting a variant also includes looking at the tests for that variant. */
    class SingleVariantCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
        override val name: String = creationConfig.computeTaskName("lint")
        override val fatalOnly: Boolean get() = false
        override val autoFix: Boolean get() = false
        override val description: String get() = "Run lint on the ${creationConfig.name} variant"
        override val checkDependencies: Boolean
            get() = creationConfig.globalScope.extension.lintOptions.isCheckDependencies
    }

    /** Creates the lintFix task. . */
    class FixSingleVariantCreationAction(variant: VariantWithTests) : VariantCreationAction(variant) {
        override val name: String = creationConfig.computeTaskName("lintFix")
        override val fatalOnly: Boolean get() = false
        override val autoFix: Boolean get() = true
        override val description: String get() = "Fix lint on the ${creationConfig.name} variant"
        override val checkDependencies: Boolean
            get() = creationConfig.globalScope.extension.lintOptions.isCheckDependencies
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

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintTask>) {
            variant.main.taskContainer.assembleTask.dependsOn(taskProvider)
        }
    }

    abstract class VariantCreationAction(val variant: VariantWithTests) :
            VariantTaskCreationAction<AndroidLintTask, ComponentCreationConfig>(variant.main) {
        final override val type: Class<AndroidLintTask> get() = AndroidLintTask::class.java

        abstract val fatalOnly: Boolean
        abstract val autoFix: Boolean
        abstract val description: String
        abstract val checkDependencies: Boolean

        final override fun configure(task: AndroidLintTask) {
            super.configure(task)

            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description

            task.initializeGlobalInputs(creationConfig.globalScope)
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
            task.lintRulesJar.disallowChanges()
            task.fatalOnly.setDisallowChanges(fatalOnly)
            task.autoFix.setDisallowChanges(autoFix)
            if (autoFix) {
                task.lintFixBuildService.set(getBuildService(creationConfig.services.buildServiceRegistry))
            }
            task.lintFixBuildService.disallowChanges()
            task.checkDependencies.setDisallowChanges(checkDependencies)
            task.checkOnly.set(creationConfig.services.provider {
                creationConfig.globalScope.extension.lintOptions.checkOnly
            })
            task.projectInputs.initialize(variant)
            task.variantInputs.initialize(variant, checkDependencies)
            if (checkDependencies) {
                task.mainDependencyLintModels.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.LINT_MODEL
                    )
                )
                variant.androidTest?.let {
                    task.androidTestDependencyLintModels.from(
                        it.variantDependencies.getArtifactFileCollection (
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_MODEL
                        )
                    )
                }
                variant.unitTest?.let {
                    task.unitTestDependencyLintModels.from(
                        it.variantDependencies.getArtifactFileCollection (
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.LINT_MODEL
                        )
                    )
                }
            }
            task.mainDependencyLintModels.disallowChanges()
            task.androidTestDependencyLintModels.disallowChanges()
            task.unitTestDependencyLintModels.disallowChanges()
            // TODO(b/160392650) Clean this up to use a detached configuration
            task.lintClasspath.fromDisallowChanges(
                creationConfig.globalScope.project.configurations.getByName(
                    "lintClassPath"
                )
            )
            task.outputs.upToDateWhen { task ->
                task.logger.debug("Lint does not model all of its inputs yet.")
                return@upToDateWhen false
            }
        }
    }

    private fun initializeGlobalInputs(globalScope: GlobalScope) {
        initializeGlobalInputs(
            project = globalScope.project,
            reportsDir = globalScope.reportsDir,
            isAndroid = true
        )
    }

    private fun initializeGlobalInputs(
        project: Project,
        reportsDir: File,
        isAndroid: Boolean
    ) {
        val buildServiceRegistry = project.gradle.sharedServices
        this.reportsDir.set(reportsDir)
        this.reportsDir.disallowChanges()
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
    }

    fun configureForStandalone(
        project: Project,
        projectOptions : ProjectOptions,
        javaPluginConvention: JavaPluginConvention,
        customLintChecksConfig: FileCollection,
        lintOptions: LintOptions,
        fatalOnly: Boolean = false,
        autoFix: Boolean = false,
    ) {
        initializeGlobalInputs(
            project = project,
            reportsDir = javaPluginConvention.testResultsDir,
            isAndroid = false
        )
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
        this.checkOnly.setDisallowChanges(project.provider { lintOptions.checkOnly })
        this.lintClasspath.fromDisallowChanges(project.configurations.getByName("lintClassPath"))
        this.projectInputs.initializeForStandalone(project, javaPluginConvention, lintOptions)
        this.variantInputs.initializeForStandalone(project, javaPluginConvention, projectOptions, customLintChecksConfig, lintOptions)
        this.lintRulesJar.fromDisallowChanges(customLintChecksConfig)
        this.lintModelDirectory.setDisallowChanges(project.layout.buildDirectory.dir("intermediates/android-lint-model"))
        this.outputs.upToDateWhen { task ->
            task.logger.debug("Lint does not model all of its inputs yet.")
            return@upToDateWhen false
        }
    }
}
