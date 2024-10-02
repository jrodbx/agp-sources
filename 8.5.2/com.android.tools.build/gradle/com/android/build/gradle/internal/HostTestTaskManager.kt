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
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.tasks.PackageForHostTest
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.GenerateTestConfig
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.PathSensitivity
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

open class HostTestTaskManager(
    project: Project,
    globalConfig: GlobalTaskCreationConfig
): TaskManager(project, globalConfig) {

    override val javaResMergingScopes = setOf(
        InternalScopedArtifacts.InternalScope.SUB_PROJECTS,
        InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS,
        InternalScopedArtifacts.InternalScope.LOCAL_DEPS,
    )

    fun createTopLevelTasksCore(taskName: String, description: String) {
        // Create top level host test tasks.
        taskFactory.register(
            taskName
        ) { hostTestTask: Task ->
            hostTestTask.group = JavaBasePlugin.VERIFICATION_GROUP
            hostTestTask.description = description
        }
        taskFactory.configure(
            JavaBasePlugin.CHECK_TASK_NAME
        ) { check: Task -> check.dependsOn(taskName) }
    }

    /**
     * This version will be set for the Jacoco plugin extension in AndroidUnitTest,
     * and the JacocoReportTask Test Ant configuration.
     *
     * The priority of version that will be chosen is:
     *     1. Gradle Property: [StringOption.JACOCO_TOOL_VERSION]
     *     2. Android DSL: android.testCoverage.jacocoVersion
     *     3. Jacoco DSL: jacoco.toolVersion
     *     4. JacocoOptions.DEFAULT_VERSION
     */
    protected fun getJacocoVersion(hostTestCreationConfig: HostTestCreationConfig): String {
        val jacocoVersionProjectOption =
            hostTestCreationConfig.services.projectOptions[StringOption.JACOCO_TOOL_VERSION]
        if (!jacocoVersionProjectOption.isNullOrEmpty()) {
            return jacocoVersionProjectOption
        }
        if ((hostTestCreationConfig.global.testCoverage as JacocoOptions).versionSetByUser) {
            return hostTestCreationConfig.global.testCoverage.jacocoVersion
        }
        val pluginExtension = project.extensions.findByType(JacocoPluginExtension::class.java)
        if (pluginExtension != null) {
            return pluginExtension.toolVersion
        }
        return JacocoOptions.DEFAULT_VERSION
    }

    protected fun createRunHostTestTask(
        hostTestCreationConfig: HostTestCreationConfig,
        taskName: String,
        coverageTestTaskName: String,
        internalArtifactType: InternalArtifactType<RegularFile>
    ) {
        if (hostTestCreationConfig.codeCoverageEnabled) {
            project.pluginManager.apply(JacocoPlugin::class.java)
        }
        val runTestsTask = taskFactory.register(
            AndroidUnitTest.CreationAction(
                hostTestCreationConfig,
                getJacocoVersion(hostTestCreationConfig),
                internalArtifactType
            ))

        hostTestCreationConfig.runTestTaskConfigurationActions(runTestsTask)
        taskFactory.configure(taskName) { test: Task ->
            test.dependsOn(runTestsTask)
        }

        if (hostTestCreationConfig.codeCoverageEnabled) {
            val ant = JacocoConfigurations.getJacocoAntTaskConfiguration(
                project, getJacocoVersion(hostTestCreationConfig)
            )
            project.plugins.withType(JacocoPlugin::class.java) {
                // Jacoco plugin is applied and test coverage enabled, ∴ generate coverage report.
                taskFactory.register(
                    JacocoReportTask.CreateActionHostTest(
                        hostTestCreationConfig,
                        ant,
                        coverageTestTaskName,
                        internalArtifactType)
                )
            }
        }
    }

    protected fun setupAndroidRequiredTasks(
        testedVariant: VariantCreationConfig,
        hostTestCreationConfig: HostTestCreationConfig
        ) {
        if (testedVariant.componentType.isAar) {
            // Add a task to process the manifest
            createProcessTestManifestTask(hostTestCreationConfig)

            // Add a task to create the res values
            createGenerateResValuesTask(hostTestCreationConfig)

            // Add a task to merge the assets folders
            createMergeAssetsTask(hostTestCreationConfig)
            createMergeResourcesTask(hostTestCreationConfig, true, ImmutableSet.of())
            // Add a task to process the Android Resources and generate source files
            createApkProcessResTask(hostTestCreationConfig,
                InternalArtifactType.FEATURE_RESOURCE_PKG
            )
            taskFactory.register(PackageForHostTest.CreationAction(hostTestCreationConfig))

            // Add data binding tasks if enabled
            createDataBindingTasksIfNecessary(hostTestCreationConfig)
        } else if (testedVariant.componentType.isApk) {
            // The IDs will have been inlined for an non-namespaced application
            // so just re-export the artifacts here.
            hostTestCreationConfig
                .artifacts
                .copy(InternalArtifactType.PROCESSED_RES, testedVariant.artifacts)
            hostTestCreationConfig
                .artifacts
                .copy(SingleArtifact.ASSETS, testedVariant.artifacts)
            taskFactory.register(PackageForHostTest.CreationAction(hostTestCreationConfig))
        } else {
            throw IllegalStateException(
                "Tested variant "
                        + testedVariant.name
                        + " in "
                        + project.path
                        + " must be a library or an application to have" +
                        " ${if (testedVariant.componentType.isForScreenshotPreview) "screenshot" else "unit"} tests.")
        }
    }

    protected fun setupCompilationTaskDependencies(
        hostTestCreationConfig: HostTestCreationConfig,
        taskContainer: MutableTaskContainer
    ) {
        val generateTestConfig =
            taskFactory.register(GenerateTestConfig.CreationAction(hostTestCreationConfig))
        val compileTask = if (hostTestCreationConfig is KmpComponentCreationConfig) {
            hostTestCreationConfig.androidKotlinCompilation.compileTaskProvider
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
            val testConfigInputs = GenerateTestConfig.TestConfigInputs(hostTestCreationConfig)
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
                hostTestCreationConfig.artifacts.get(
                    InternalArtifactType.APK_FOR_LOCAL_TEST
                )
            )
        }
    }

    protected fun setupAssembleAndJavaCompilationTasks(
        hostTestCreationConfig: HostTestCreationConfig,
        taskContainer: MutableTaskContainer,
        testedVariant: VariantCreationConfig,
        assembleTasK: String) {
        // Whether we have android resources or not, we must always depend on the
        // CLASSES so we run compilation, etc...
        taskContainer.assembleTask.configure { task: Task ->
            task.dependsOn(
                hostTestCreationConfig
                    .artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.CLASSES),
            )
        }

        taskFactory.configure(assembleTasK) { assembleTest: Task ->
            assembleTest.dependsOn(hostTestCreationConfig.taskContainer.assembleTask.name)
        }

        // TODO(b/276758294): Remove such checks
        if (hostTestCreationConfig !is KmpComponentCreationConfig) {
            // compileDebugSources should be enough for running tests from AS, so add
            // dependencies on tasks that prepare necessary data files.
            val compileTask = taskContainer.compileTask
            compileTask.dependsOn(taskContainer.processJavaResourcesTask,
                testedVariant.taskContainer.processJavaResourcesTask)

            val javacTask = createJavacTask(hostTestCreationConfig)
            setJavaCompilerTask(javacTask, hostTestCreationConfig)
            initializeAllScope(hostTestCreationConfig.artifacts)
        }
        maybeCreateTransformClassesWithAsmTask(hostTestCreationConfig)

        if (globalConfig.avoidTaskRegistration.not()
            && hostTestCreationConfig.services.projectOptions.get(BooleanOption.LINT_ANALYSIS_PER_COMPONENT)
            && globalConfig.lintOptions.ignoreTestSources.not()
        ) {
            taskFactory.register(
                AndroidLintAnalysisTask.PerComponentCreationAction(
                    hostTestCreationConfig,
                    fatalOnly = false
                )
            )
            taskFactory.register(
                LintModelWriterTask.PerComponentCreationAction(
                    hostTestCreationConfig,
                    useModuleDependencyLintModels = false,
                    fatalOnly = false,
                    isMainModelForLocalReportTask = false
                )
            )
        }
    }
}
