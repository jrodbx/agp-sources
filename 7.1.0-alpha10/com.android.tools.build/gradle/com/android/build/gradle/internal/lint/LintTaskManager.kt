package com.android.build.gradle.internal.lint

import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.variant.VariantModel
import com.android.builder.core.VariantType
import com.android.utils.appendCapitalized
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** Factory for the LintModel based lint tasks */
class LintTaskManager constructor(private val globalScope: GlobalScope, private val taskFactory: TaskFactory, private val projectInfo: ProjectInfo) {

    fun createBeforeEvaluateLintTasks() {
        // LintFix task
        taskFactory.register(AndroidLintGlobalTask.LintFixCreationAction(globalScope))

        // LintGlobalTask
        val globalTask = taskFactory.register(AndroidLintGlobalTask.GlobalCreationAction(globalScope))
        taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME) { it.dependsOn(globalTask) }

    }

    fun createLintTasks(
            variantType: VariantType,
            variantModel: VariantModel,
            variantPropertiesList: List<VariantImpl>,
            testComponentPropertiesList: List<TestComponentImpl>
            ) {
        if (variantType.isForTesting) {
            return // Don't  create lint tasks in test-only projects
        }

        val variantsWithTests = attachTestsToVariants(
            variantPropertiesList = variantPropertiesList,
            testComponentPropertiesList = if (globalScope.extension.lintOptions.isIgnoreTestSources) {
                listOf()
            } else {
                testComponentPropertiesList
            }
        )

        // Map of task path to the providers for tasks that that task subsumes,
        // and therefore should be disabled if both are in the task graph.
        // e.g. Running `lintRelease` should cause `lintVitalRelease` to be skipped,
        val variantLintTaskToLintVitalTask =
            mutableMapOf<String, TaskProvider<AndroidLintTextOutputTask>>()

        val needsCopyReportTask = needsCopyReportTask(globalScope.extension.lintOptions)

        for (variantWithTests in variantsWithTests.values) {
            if (variantType.isAar) {
                // We need the library lint models if checkDependencies is true
                taskFactory.register(LintModelWriterTask.LintCreationAction(variantWithTests.main))
                // We need the library lint model metadata if checkDependencies is false
                taskFactory.register(LintModelMetadataTask.CreationAction(variantWithTests.main))
            } else {
                // We need app and dynamic feature models if there are dynamic features.
                // TODO (b/180672373) consider also publishing dynamic feature and app lint models
                //  with checkDependencies = true if that's necessary to properly run lint from an
                //  app or dynamic feature module with checkDependencies = true.
                taskFactory.register(
                    LintModelWriterTask.LintCreationAction(
                        variantWithTests.main,
                        checkDependencies = false
                    )
                )
            }
            taskFactory.register(
                AndroidLintAnalysisTask.SingleVariantCreationAction(variantWithTests)
            )

            if (variantType.isDynamicFeature) {
                taskFactory.register(
                    AndroidLintAnalysisTask.LintVitalCreationAction(variantWithTests)
                )
                taskFactory.register(
                    LintModelWriterTask.LintVitalCreationAction(variantWithTests.main)
                )
                // Don't register any lint reporting tasks or lintFix task for dynamic features
                // because any reporting and/or fixing is done when lint runs from the base app.
                continue
            }

            taskFactory.register(AndroidLintTask.SingleVariantCreationAction(variantWithTests))
            val variantLintTask =
                taskFactory.register(
                    AndroidLintTextOutputTask.SingleVariantCreationAction(variantWithTests.main)
                )

            if (needsCopyReportTask) {
                val copyLintReportTask =
                    taskFactory.register(AndroidLintCopyReportTask.CreationAction(variantWithTests.main))
                variantLintTask.configure {
                    it.finalizedBy(copyLintReportTask)
                }
            }

            val mainVariant = variantWithTests.main
            if (mainVariant.variantType.isBaseModule &&
                !mainVariant.variantDslInfo.isDebuggable &&
                globalScope.extension.lintOptions.isCheckReleaseBuilds
            ) {
                taskFactory.register(
                    AndroidLintAnalysisTask.LintVitalCreationAction(variantWithTests)
                )
                taskFactory.register(AndroidLintTask.LintVitalCreationAction(mainVariant))
                val lintVitalTask =
                    taskFactory.register(
                        AndroidLintTextOutputTask.LintVitalCreationAction(mainVariant)
                    )

                // If lint is being run, we do not need to run lint vital.
                variantLintTaskToLintVitalTask[getTaskPath(variantLintTask)] = lintVitalTask
            }
            taskFactory.register(AndroidLintTask.FixSingleVariantCreationAction(variantWithTests))
        }

        // Nothing left to do for dynamic features because they don't have global lint or lintFix
        // tasks, and they don't have any lint reporting tasks.
        if (variantType.isDynamicFeature) {
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
        }

        val lintTaskPath = getTaskPath("lint")

        projectInfo.getProject().gradle.taskGraph.whenReady {
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
            variantPropertiesList: List<VariantImpl>,
            testComponentPropertiesList: List<TestComponentImpl>): LinkedHashMap<String, VariantWithTests> {
        val variantsWithTests = LinkedHashMap<String, VariantWithTests>()
        for (variant in variantPropertiesList) {
            variantsWithTests[variant.name] = VariantWithTests(variant)
        }
        for (testComponent in testComponentPropertiesList) {
            val key = testComponent.testedConfig.name
            val current = variantsWithTests[key]!!
            when (testComponent) {
                is AndroidTestCreationConfig -> {
                    check(current.androidTest == null) {
                        "Component ${current.main} appears to have two conflicting android test components ${current.androidTest} and $testComponent"
                    }
                    variantsWithTests[key] =
                        VariantWithTests(current.main, testComponent, current.unitTest)
                }
                is UnitTestCreationConfig -> {
                    check(current.unitTest == null) {
                        "Component ${current.main} appears to have two conflicting unit test components ${current.unitTest} and $testComponent"
                    }
                    variantsWithTests[key] =
                        VariantWithTests(current.main, current.androidTest, testComponent)
                }
                else -> throw IllegalStateException("Unexpected test component type")
            }
        }
        return variantsWithTests
    }

    private fun getTaskPath(task: TaskProvider<AndroidLintTextOutputTask>): String {
        return (getTaskPath(task.name))
    }

    private fun getTaskPath(taskName: String): String {
        return if (projectInfo.getProject().rootProject === projectInfo.getProject()) ":$taskName" else projectInfo.getProject().path + ':' + taskName
    }

    companion object {

        internal fun File.isLintStdout() = this.path == File("stdout").path
        internal fun File.isLintStderr() = this.path == File("stderr").path

        internal fun needsCopyReportTask(lintOptions: LintOptions) : Boolean {
            val textOutput = lintOptions.textOutput
            return (lintOptions.textReport && textOutput != null && !textOutput.isLintStdout() && !textOutput.isLintStderr()) ||
                    (lintOptions.htmlReport && lintOptions.htmlOutput != null) ||
                    (lintOptions.xmlReport && lintOptions.xmlOutput != null) ||
                    (lintOptions.sarifReport && lintOptions.sarifOutput != null)
        }
    }
}
