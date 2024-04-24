package com.android.build.gradle.internal.lint

import com.android.build.api.dsl.Lint
import com.android.build.api.variant.impl.HasTestFixtures
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.utils.createTargetSdkVersion
import com.android.build.gradle.internal.variant.VariantModel
import com.android.builder.core.ComponentType
import com.android.builder.errors.IssueReporter
import com.android.utils.appendCapitalized
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Locale

/** Factory for the LintModel based lint tasks */
class LintTaskManager constructor(
    private val globalTaskCreationConfig: GlobalTaskCreationConfig,
    private val taskFactory: TaskFactory,
    private val project: Project
) {

    fun createBeforeEvaluateLintTasks() {
        // LintFix task
        taskFactory.register(AndroidLintGlobalTask.LintFixCreationAction(globalTaskCreationConfig))

        // LintGlobalTask
        val globalTask = taskFactory.register(AndroidLintGlobalTask.GlobalCreationAction(globalTaskCreationConfig))
        taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME) { it.dependsOn(globalTask) }

        // updateLintBaseline task
        taskFactory.register(
            AndroidLintGlobalTask.UpdateBaselineCreationAction(globalTaskCreationConfig)
        )
    }

    fun createLintTasks(
        componentType: ComponentType,
        variantModel: VariantModel,
        variantPropertiesList: List<VariantCreationConfig>,
        testComponentPropertiesList: Collection<TestComponentCreationConfig>,
        isPerComponent: Boolean
    ) {
        if (globalTaskCreationConfig.avoidTaskRegistration) {
            return
        }
        return createLintTasks(
            componentType,
            defaultVariant = variantModel.defaultVariant,
            variantPropertiesList,
            testComponentPropertiesList,
            isPerComponent
        )
    }


    fun createLintTasks(
        componentType: ComponentType,
        defaultVariant: String?,
        variantPropertiesList: List<VariantCreationConfig>,
        testComponentPropertiesList: Collection<TestComponentCreationConfig>,
        isPerComponent: Boolean
    ) {
        runConfigurationValidation(componentType, variantPropertiesList)

        if (componentType.isForTesting) {
            return // Don't  create lint tasks in test-only projects
        }

        val variantsWithTests = attachTestsToVariants(
            variantPropertiesList = variantPropertiesList,
            testComponentPropertiesList = if (globalTaskCreationConfig.lintOptions.ignoreTestSources) {
                listOf()
            } else {
                testComponentPropertiesList
            },
            ignoreTestFixturesSources = globalTaskCreationConfig.lintOptions.ignoreTestFixturesSources
        )

        // Map of task path to the providers for tasks that that task subsumes,
        // and therefore should be disabled if both are in the task graph.
        // e.g. Running `lintRelease` should cause `lintVitalRelease` to be skipped,
        val variantLintTaskToLintVitalTask = mutableMapOf<String, TaskProvider<out Task>>()

        val needsCopyReportTask = needsCopyReportTask(globalTaskCreationConfig.lintOptions)

        for (variantWithTests in variantsWithTests.values) {
            val mainVariant = variantWithTests.main
            if (componentType.isAar || componentType.isDynamicFeature) {
                if (isPerComponent) {
                    taskFactory.register(
                        LintModelWriterTask.PerComponentCreationAction(
                            mainVariant,
                            useModuleDependencyLintModels = componentType.isAar,
                            fatalOnly = true,
                            isMainModelForLocalReportTask = false
                        )
                    )
                } else {
                    taskFactory.register(
                        LintModelWriterTask.CreationAction(
                            VariantWithTests(mainVariant, null, null, null),
                            useModuleDependencyLintModels = componentType.isAar,
                            fatalOnly = true,
                            isForLocalReportTask = false
                        )
                    )
                }
                taskFactory.register(
                    AndroidLintAnalysisTask.LintVitalCreationAction(mainVariant)
                )
                if (componentType.isAar) {
                    // We need the library lint model metadata if checkDependencies is false
                    taskFactory.register(LintModelMetadataTask.CreationAction(mainVariant))

                }
            }
            // We need app and dynamic feature models if there are dynamic features.
            // TODO (b/180672373) consider also publishing dynamic feature and app lint models
            //  with useModuleDependencyLintModels = true if that's necessary to properly run lint
            //  from an app or dynamic feature module with checkDependencies = true.
            if (isPerComponent) {
                taskFactory.register(
                    LintModelWriterTask.PerComponentCreationAction(
                        mainVariant,
                        useModuleDependencyLintModels = componentType.isAar,
                        fatalOnly = false,
                        isMainModelForLocalReportTask = false
                    )
                )
                taskFactory.register(
                    AndroidLintAnalysisTask.PerComponentCreationAction(
                        mainVariant,
                        fatalOnly = false
                    )
                )
            } else {
                taskFactory.register(
                    LintModelWriterTask.CreationAction(
                        variantWithTests,
                        useModuleDependencyLintModels = componentType.isAar,
                        fatalOnly = false,
                        isForLocalReportTask = false
                    )
                )
                taskFactory.register(
                    AndroidLintAnalysisTask.SingleVariantCreationAction(variantWithTests)
                )
            }

            if (componentType.isDynamicFeature) {
                // Don't register any lint reporting tasks or lintFix task for dynamic features
                // because any reporting and/or fixing is done when lint runs from the base app.
                continue
            }

            if (isPerComponent) {
                taskFactory.register(
                    LintModelWriterTask.PerComponentCreationAction(
                        mainVariant,
                        useModuleDependencyLintModels = true,
                        fatalOnly = false,
                        isMainModelForLocalReportTask = true
                    )
                )
            } else {
                taskFactory.register(
                    LintModelWriterTask.CreationAction(
                        variantWithTests,
                        useModuleDependencyLintModels = true,
                        fatalOnly = false,
                        isForLocalReportTask = true
                    )
                )
            }
            val updateLintBaselineTask =
                taskFactory.register(AndroidLintTask.UpdateBaselineCreationAction(variantWithTests))
            val variantLintTask =
                taskFactory.register(AndroidLintTask.SingleVariantCreationAction(variantWithTests))
                    .also { it.configure { task -> task.mustRunAfter(updateLintBaselineTask) } }
            val variantLintTextOutputTask =
                taskFactory.register(
                    AndroidLintTextOutputTask.SingleVariantCreationAction(mainVariant)
                )

            if (needsCopyReportTask) {
                val copyLintReportTask =
                    taskFactory.register(AndroidLintCopyReportTask.CreationAction(mainVariant))
                variantLintTextOutputTask.configure {
                    it.finalizedBy(copyLintReportTask)
                }
            }

            if (mainVariant.componentType.isBaseModule &&
                !mainVariant.debuggable &&
                !(mainVariant as ApplicationCreationConfig).profileable &&
                globalTaskCreationConfig.lintOptions.checkReleaseBuilds
            ) {
                if (isPerComponent) {
                    taskFactory.register(
                        AndroidLintAnalysisTask.PerComponentCreationAction(
                            mainVariant,
                            fatalOnly = true
                        )
                    )
                    taskFactory.register(
                        LintModelWriterTask.PerComponentCreationAction(
                            mainVariant,
                            useModuleDependencyLintModels = true,
                            fatalOnly = true,
                            isMainModelForLocalReportTask = true
                        )
                    )
                } else {
                    taskFactory.register(
                        AndroidLintAnalysisTask.LintVitalCreationAction(mainVariant)
                    )
                    taskFactory.register(
                        LintModelWriterTask.CreationAction(
                            VariantWithTests(mainVariant, null, null, null),
                            useModuleDependencyLintModels = true,
                            fatalOnly = true,
                            isForLocalReportTask = true
                        )
                    )
                }
                val lintVitalTask =
                    taskFactory.register(AndroidLintTask.LintVitalCreationAction(mainVariant))
                        .also { it.configure { task -> task.mustRunAfter(updateLintBaselineTask) } }
                val lintVitalTextOutputTask =
                    taskFactory.register(
                        AndroidLintTextOutputTask.LintVitalCreationAction(mainVariant)
                    )

                // If lint is being run, we do not need to run lint vital.
                variantLintTaskToLintVitalTask[getTaskPath(variantLintTask)] = lintVitalTask
                variantLintTaskToLintVitalTask[getTaskPath(variantLintTextOutputTask)] =
                    lintVitalTextOutputTask
            }
            taskFactory.register(AndroidLintTask.FixSingleVariantCreationAction(variantWithTests))
                .also { it.configure { task -> task.mustRunAfter(updateLintBaselineTask) } }
        }

        // Nothing left to do for dynamic features because they don't have global lint or lintFix
        // tasks, and they don't have any lint reporting tasks.
        if (componentType.isDynamicFeature) {
            return
        }

        if (defaultVariant != null) {
            taskFactory.configure(AndroidLintGlobalTask.GlobalCreationAction.name, AndroidLintGlobalTask::class.java) { globalTask ->
                globalTask.dependsOn("lint".appendCapitalized(defaultVariant))
            }
            taskFactory.configure(AndroidLintGlobalTask.LintFixCreationAction.name, AndroidLintGlobalTask::class.java) { globalFixTask ->
                globalFixTask.dependsOn("lintFix".appendCapitalized(defaultVariant))
            }
            taskFactory.configure(
                AndroidLintGlobalTask.UpdateBaselineCreationAction.name,
                AndroidLintGlobalTask::class.java
            ) { updateLintBaselineTask ->
                updateLintBaselineTask.dependsOn(
                    "updateLintBaseline".appendCapitalized(defaultVariant)
                )
            }
        }

        val lintTaskPath = getTaskPath("lint")

        project.gradle.taskGraph.whenReady {
            variantLintTaskToLintVitalTask.forEach { (taskPath, taskToDisable) ->
                if (it.hasTask(taskPath)) {
                    taskToDisable.configure { it.enabled = false }
                }
            }
            if (it.hasTask(lintTaskPath)) {
                variantLintTaskToLintVitalTask.forEach { (_, lintVitalTask) ->
                    lintVitalTask.configure { it.enabled = false }
                }
            }
        }
    }

    private fun attachTestsToVariants(
        variantPropertiesList: List<VariantCreationConfig>,
        testComponentPropertiesList: Collection<TestComponentCreationConfig>,
        ignoreTestFixturesSources: Boolean
    ): LinkedHashMap<String, VariantWithTests> {
        val variantsWithTests = LinkedHashMap<String, VariantWithTests>()
        for (variant in variantPropertiesList) {
            variantsWithTests[variant.name] = VariantWithTests(
                variant,
                testFixtures = if (ignoreTestFixturesSources) {
                    null
                } else {
                    (variant as? HasTestFixtures)?.testFixtures
                }
            )
        }
        for (testComponent in testComponentPropertiesList) {
            val key = testComponent.mainVariant.name
            val current = variantsWithTests[key]!!
            when (testComponent) {
                is AndroidTestCreationConfig -> {
                    check(current.androidTest == null) {
                        "Component ${current.main} appears to have two conflicting android test components ${current.androidTest} and $testComponent"
                    }
                    variantsWithTests[key] =
                        VariantWithTests(
                            current.main,
                            testComponent,
                            current.unitTest,
                            current.testFixtures
                        )
                }
                is HostTestCreationConfig -> {
                    check(current.unitTest == null) {
                        "Component ${current.main} appears to have two conflicting unit test components ${current.unitTest} and $testComponent"
                    }
                    variantsWithTests[key] =
                        VariantWithTests(
                            current.main,
                            current.androidTest,
                            testComponent,
                            current.testFixtures
                        )
                }
                else -> throw IllegalStateException("Unexpected test component type")
            }
        }
        return variantsWithTests
    }

    private fun getTaskPath(task: TaskProvider<out Task>) = getTaskPath(task.name)

    private fun getTaskPath(taskName: String) = TaskManager.getTaskPath(project, taskName)

    private fun runConfigurationValidation(componentType: ComponentType, variantPropertiesList: List<VariantCreationConfig>) {
        val targetSdkVersion = globalTaskCreationConfig.lintOptions.run { createTargetSdkVersion(targetSdk,targetSdkPreview) }
        if (targetSdkVersion != null && !componentType.isAar) {
            val versionToName = mutableMapOf<Int, MutableList<String>>()
            for (variant in variantPropertiesList) {
                val variantTargetSdkVersion = when (variant) {
                    is ApkCreationConfig -> variant.targetSdk
                    is TestCreationConfig -> variant.targetSdkVersion
                    else -> null
                }
                if(variantTargetSdkVersion != null &&
                    targetSdkVersion.apiLevel < variantTargetSdkVersion.apiLevel){
                    versionToName.getOrPut(variantTargetSdkVersion.apiLevel, ::mutableListOf).add(variant.name)
                }
            }

            versionToName.forEach { (variantSdkLevel, names) ->
                globalTaskCreationConfig.services.issueReporter
                    .reportError(
                        IssueReporter.Type.GENERIC, String.format(
                            Locale.US,
                            "lint.targetSdk (%d) for non library is smaller than android.targetSdk (%d)"
                                    + " for variants %s. Please change the"
                                    + " values such that lint.targetSdk is greater than or"
                                    + " equal to android.targetSdk.",
                            targetSdkVersion.apiLevel,
                            variantSdkLevel,
                            names.joinToString(separator = ", ")
                        )
                    )
            }
        }
    }

    companion object {

        internal fun File.isLintStdout() = this.path == File("stdout").path
        internal fun File.isLintStderr() = this.path == File("stderr").path

        internal fun needsCopyReportTask(lintOptions: Lint) : Boolean {
            val textOutput = lintOptions.textOutput
            return (lintOptions.textReport && textOutput != null && !textOutput.isLintStdout() && !textOutput.isLintStderr()) ||
                    (lintOptions.htmlReport && lintOptions.htmlOutput != null) ||
                    (lintOptions.xmlReport && lintOptions.xmlOutput != null) ||
                    (lintOptions.sarifReport && lintOptions.sarifOutput != null)
        }
    }
}
