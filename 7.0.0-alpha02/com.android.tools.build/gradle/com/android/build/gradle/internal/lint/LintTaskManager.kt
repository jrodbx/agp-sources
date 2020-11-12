package com.android.build.gradle.internal.lint

import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.isConfigurationCache
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.LintFixTask
import com.android.build.gradle.tasks.LintGlobalTask
import com.android.builder.core.VariantType
import com.android.utils.appendCapitalized
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider

/** Factory for the LintModel based lint tasks */
class LintTaskManager constructor(private val globalScope: GlobalScope, private val taskFactory: TaskFactory) {

    val useNewLintModel: Boolean = computeUseNewLintModel(globalScope.project, globalScope.projectOptions)

    fun createBeforeEvaluateLintTasks() {
        // LintFix task
        if (useNewLintModel) {
            taskFactory.register(AndroidLintGlobalTask.LintFixCreationAction(globalScope))
        } else {
            taskFactory.register(AndroidLintGlobalTask.LintFixCreationAction.name, LintFixTask::class.java) { }
        }

        // LintGlobalTask
        val globalTask = if (useNewLintModel) {
            taskFactory.register(AndroidLintGlobalTask.GlobalCreationAction(globalScope))
        } else {
            taskFactory.register(AndroidLintGlobalTask.GlobalCreationAction.name, LintGlobalTask::class.java) { }
        }
        taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME) { it.dependsOn(globalTask) }

    }

    fun createLintTasks(
            variantType: VariantType,
            variantModel: VariantModel,
            variantPropertiesList: List<VariantImpl>,
            testComponentPropertiesList: List<TestComponentImpl>
            ) {
        if (!useNewLintModel) {
            return // This is only for the new lint tasks.
        }
        if (variantType.isForTesting) {
            return // Don't  create lint tasks in test-only projects
        }

        val variantsWithTests = attachTestsToVariants(variantPropertiesList, testComponentPropertiesList)

        // Map of task path to the providers for tasks that that task subsumes,
        // and therefore should be disabled if both are in the task graph.
        // e.g. Running `lintRelease` should cause `lintVitalRelease` to be skipped,
        val variantLintTaskToLintVitalTask = mutableMapOf<String, TaskProvider<AndroidLintTask>>()

        for (variantWithTests in variantsWithTests.values) {
            if (variantType.isAar) { // Export lint models to support checkDependencies.
                taskFactory.register(LintModelWriterTask.CreationAction(variantWithTests.main))
            }
            val variantLintTask =
                taskFactory.register(AndroidLintTask.SingleVariantCreationAction(variantWithTests))

            val mainVariant = variantWithTests.main
            if (mainVariant.variantType.isBaseModule &&
                !mainVariant.variantDslInfo.isDebuggable &&
                globalScope.extension.lintOptions.isCheckReleaseBuilds
            ) {
                val lintVitalTask =
                    taskFactory.register(AndroidLintTask.LintVitalCreationAction(mainVariant))

                // If lint is being run, we do not need to run lint vital.
                variantLintTaskToLintVitalTask[getTaskPath(variantLintTask)] = lintVitalTask
            }
            taskFactory.register(AndroidLintTask.FixSingleVariantCreationAction(variantWithTests))
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

        globalScope.project.gradle.taskGraph.whenReady {
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

    private fun getTaskPath(task: TaskProvider<AndroidLintTask>): String {
        return (getTaskPath(task.name))
    }

    private fun getTaskPath(taskName: String): String {
        return if (globalScope.project.rootProject === globalScope.project) ":$taskName" else globalScope.project.path + ':' + taskName
    }

    companion object {

        @JvmStatic
        fun computeUseNewLintModel(project: Project, projectOptions: ProjectOptions): Boolean {
            return projectOptions[BooleanOption.USE_NEW_LINT_MODEL] ||
                    (project.gradle.startParameter.isConfigurationCache ?: false)
        }
    }
}
