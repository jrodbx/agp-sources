package com.android.build.gradle.internal.lint

import com.android.build.api.dsl.Lint
import com.android.build.api.variant.impl.HasTestFixtures
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.variant.VariantModel
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import java.io.File

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
        testComponentPropertiesList: Collection<TestComponentCreationConfig>
    ) {
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
                taskFactory.register(
                    LintModelWriterTask.LintVitalCreationAction(
                        mainVariant,
                        checkDependencies = componentType.isAar
                    )
                )
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
            //  with checkDependencies = true if that's necessary to properly run lint from an
            //  app or dynamic feature module with checkDependencies = true.
            taskFactory.register(
                LintModelWriterTask.LintCreationAction(
                    variantWithTests,
                    checkDependencies = componentType.isAar
                )
            )
            taskFactory.register(
                AndroidLintAnalysisTask.SingleVariantCreationAction(variantWithTests)
            )

            if (componentType.isDynamicFeature) {
                // Don't register any lint reporting tasks or lintFix task for dynamic features
                // because any reporting and/or fixing is done when lint runs from the base app.
                continue
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
                taskFactory.register(
                    AndroidLintAnalysisTask.LintVitalCreationAction(mainVariant)
                )
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

        val defaultVariant = variantModel.defaultVariant
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
                is UnitTestCreationConfig -> {
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
