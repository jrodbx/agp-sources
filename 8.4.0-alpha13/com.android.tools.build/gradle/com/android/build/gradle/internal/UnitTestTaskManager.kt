/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.PackageForUnitTest
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.GenerateTestConfig
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.PathSensitivity
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

class UnitTestTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig
): HostTestTaskManager(project, globalConfig) {

    fun createTopLevelTasks() {
        // Create top level unit test tasks.
        taskFactory.register(
            globalConfig.taskNames.test
        ) { unitTestTask: Task ->
            unitTestTask.group = JavaBasePlugin.VERIFICATION_GROUP
            unitTestTask.description = "Run unit tests for all variants."
        }
        taskFactory.configure(
            JavaBasePlugin.CHECK_TASK_NAME
        ) { check: Task -> check.dependsOn(globalConfig.taskNames.test) }
    }

    /** Creates the tasks to build unit tests.  */
    fun createTasks(unitTestCreationConfig: HostTestCreationConfig) {
        val taskContainer = unitTestCreationConfig.taskContainer
        val testedVariant = unitTestCreationConfig.mainVariant
        val includeAndroidResources = globalConfig.unitTestOptions
            .isIncludeAndroidResources
        createAnchorTasks(unitTestCreationConfig)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(unitTestCreationConfig)

        // process java resources
        createProcessJavaResTask(unitTestCreationConfig)
        if (includeAndroidResources) {
            if (testedVariant.componentType.isAar) {
                // Add a task to process the manifest
                createProcessTestManifestTask(unitTestCreationConfig)

                // Add a task to create the res values
                createGenerateResValuesTask(unitTestCreationConfig)

                // Add a task to merge the assets folders
                createMergeAssetsTask(unitTestCreationConfig)
                createMergeResourcesTask(unitTestCreationConfig, true, ImmutableSet.of())
                // Add a task to process the Android Resources and generate source files
                createApkProcessResTask(unitTestCreationConfig,
                    InternalArtifactType.FEATURE_RESOURCE_PKG
                )
                taskFactory.register(PackageForUnitTest.CreationAction(unitTestCreationConfig))

                // Add data binding tasks if enabled
                createDataBindingTasksIfNecessary(unitTestCreationConfig)
            } else if (testedVariant.componentType.isApk) {
                // The IDs will have been inlined for an non-namespaced application
                // so just re-export the artifacts here.
                unitTestCreationConfig
                    .artifacts
                    .copy(InternalArtifactType.PROCESSED_RES, testedVariant.artifacts)
                unitTestCreationConfig
                    .artifacts
                    .copy(SingleArtifact.ASSETS, testedVariant.artifacts)
                taskFactory.register(PackageForUnitTest.CreationAction(unitTestCreationConfig))
            } else {
                throw IllegalStateException(
                    "Tested variant "
                            + testedVariant.name
                            + " in "
                            + project.path
                            + " must be a library or an application to have unit tests.")
            }
            val generateTestConfig = taskFactory.register(
                GenerateTestConfig.
                CreationAction(unitTestCreationConfig))
            val compileTask = if (unitTestCreationConfig is KmpComponentCreationConfig) {
                unitTestCreationConfig.androidKotlinCompilation.compileTaskProvider
            } else {
                taskContainer.compileTask
            }
            compileTask.dependsOn(generateTestConfig)
            // The GenerateTestConfig task has 2 types of inputs: direct inputs and indirect inputs.
            // Only the direct inputs are registered with Gradle, whereas the indirect inputs are
            // not (see that class for details).
            // Since the compile task also depends on the indirect inputs to the GenerateTestConfig
            // task, making the compile task depend on the GenerateTestConfig task is not enough, we
            // also need to register those inputs with Gradle explicitly here. (We can't register
            // @Nested objects programmatically, so it's important to keep these inputs consistent
            // with those defined in TestConfigInputs.)
            compileTask.configure { task: Task ->
                val testConfigInputs = GenerateTestConfig.TestConfigInputs(unitTestCreationConfig)
                val taskInputs = task.inputs
                taskInputs
                    .files(testConfigInputs.resourceApk)
                    .withPropertyName("resourceApk")
                    .optional()
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                taskInputs
                    .files(testConfigInputs.mergedAssets)
                    .withPropertyName("mergedAssets")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                taskInputs
                    .files(testConfigInputs.mergedManifest)
                    .withPropertyName("mergedManifest")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                taskInputs.property(
                    "packageNameOfFinalRClassProvider",
                    testConfigInputs.packageNameOfFinalRClass)
            }

            taskContainer.assembleTask.configure { task: Task ->
                task.dependsOn(
                    unitTestCreationConfig.artifacts.get(
                        InternalArtifactType.APK_FOR_LOCAL_TEST
                    )
                )
            }

        } else {
            if (testedVariant.componentType.isAar && testedVariant.buildFeatures.androidResources) {
                // With compile classpath R classes, we need to generate a dummy R class for unit
                // tests
                // See https://issuetracker.google.com/143762955 for more context.
                taskFactory.register(
                    GenerateLibraryRFileTask.TestRuntimeStubRClassCreationAction(
                        unitTestCreationConfig
                    )
                )
            }
        }

        // Whether we have android resources or not, we must always depend on the
        // CLASSES so we run compilation, etc...
        taskContainer.assembleTask.configure { task: Task ->
            task.dependsOn(
                unitTestCreationConfig
                    .artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.CLASSES),
            )
        }

        taskFactory.configure(ASSEMBLE_UNIT_TEST) { assembleTest: Task ->
            assembleTest.dependsOn(unitTestCreationConfig.taskContainer.assembleTask.name)
        }

        // TODO(b/276758294): Remove such checks
        if (unitTestCreationConfig !is KmpComponentCreationConfig) {
            // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
            // dependencies on tasks that prepare necessary data files.
            val compileTask = taskContainer.compileTask
            compileTask.dependsOn(taskContainer.processJavaResourcesTask,
                    testedVariant.taskContainer.processJavaResourcesTask)

            val javacTask = createJavacTask(unitTestCreationConfig)
            setJavaCompilerTask(javacTask, unitTestCreationConfig)
        }
        maybeCreateTransformClassesWithAsmTask(unitTestCreationConfig)

        if (globalConfig.avoidTaskRegistration.not()
            && unitTestCreationConfig.services.projectOptions.get(LINT_ANALYSIS_PER_COMPONENT)
            && globalConfig.lintOptions.ignoreTestSources.not()
        ) {
            taskFactory.register(
                AndroidLintAnalysisTask.PerComponentCreationAction(
                    unitTestCreationConfig,
                    fatalOnly = false
                )
            )
            taskFactory.register(
                LintModelWriterTask.PerComponentCreationAction(
                    unitTestCreationConfig,
                    useModuleDependencyLintModels = false,
                    fatalOnly = false,
                    isMainModelForLocalReportTask = false
                )
            )
        }

        // TODO: use merged java res for unit tests (bug 118690729)
        createRunUnitTestTask(unitTestCreationConfig)
    }

    private fun createRunUnitTestTask(unitTestCreationConfig: HostTestCreationConfig) {
        if (unitTestCreationConfig.isUnitTestCoverageEnabled) {
            project.pluginManager.apply(JacocoPlugin::class.java)
        }
        val runTestsTask = taskFactory.register(AndroidUnitTest.CreationAction(
            unitTestCreationConfig,
            getJacocoVersion(unitTestCreationConfig)
        ))

        unitTestCreationConfig.runTestTaskConfigurationActions(runTestsTask)
        taskFactory.configure(globalConfig.taskNames.test) { test: Task ->
            test.dependsOn(runTestsTask)
        }

        if (unitTestCreationConfig.isUnitTestCoverageEnabled) {
            val ant = JacocoConfigurations.getJacocoAntTaskConfiguration(
                project, getJacocoVersion(unitTestCreationConfig)
            )
            project.plugins.withType(JacocoPlugin::class.java) {
                // Jacoco plugin is applied and test coverage enabled, ∴ generate coverage report.
                taskFactory.register(
                    JacocoReportTask.CreateActionUnitTest(unitTestCreationConfig, ant)
                )
            }
        }
    }

    /**
     * This version will be set for the Jacoco plugin extension in AndroidUnitTest,
     * and the JacocoReportTask UnitTest Ant configuration.
     *
     * The priority of version that will be chosen is:
     *     1. Gradle Property: [StringOption.JACOCO_TOOL_VERSION]
     *     2. Android DSL: android.testCoverage.jacocoVersion
     *     3. Jacoco DSL: jacoco.toolVersion
     *     4. JacocoOptions.DEFAULT_VERSION
     */
    private fun getJacocoVersion(unitTestCreationConfig: HostTestCreationConfig): String {
        val jacocoVersionProjectOption =
            unitTestCreationConfig.services.projectOptions[StringOption.JACOCO_TOOL_VERSION]
        if (!jacocoVersionProjectOption.isNullOrEmpty()) {
            return jacocoVersionProjectOption
        }
        if ((unitTestCreationConfig.global.testCoverage as JacocoOptions).versionSetByUser) {
            return unitTestCreationConfig.global.testCoverage.jacocoVersion
        }
        val pluginExtension = project.extensions.findByType(JacocoPluginExtension::class.java)
        if (pluginExtension != null) {
            return pluginExtension.toolVersion
        }
        return JacocoOptions.DEFAULT_VERSION
    }

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.SUB_PROJECTS,
        InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS,
    )
}
