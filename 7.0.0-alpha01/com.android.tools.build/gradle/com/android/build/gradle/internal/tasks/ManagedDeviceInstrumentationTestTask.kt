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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.SdkConstants.FN_EMULATOR
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.FailureRetention
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.test.recordCrashedTestRun
import com.android.build.gradle.internal.test.recordOkTestRun
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.testing.utp.ManagedDeviceTestRunner
import com.android.build.gradle.internal.testing.utp.RetentionConfig
import com.android.build.gradle.internal.testing.utp.UtpDependencies
import com.android.build.gradle.internal.testing.utp.UtpManagedDevice
import com.android.build.gradle.internal.testing.utp.createRetentionConfig
import com.android.build.gradle.internal.testing.utp.maybeCreateUtpConfigurations
import com.android.build.gradle.internal.testing.utp.resolveDependencies
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.BuilderConstants.FD_FLAVORS
import com.android.builder.core.BuilderConstants.FD_REPORTS
import com.android.builder.core.BuilderConstants.MANAGED_DEVICE
import com.android.builder.model.AndroidProject
import com.android.builder.model.TestOptions
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.process.ExecOperations
import org.gradle.process.JavaExecSpec
import java.io.File
import java.util.function.Function
import javax.inject.Inject

/**
 * Runs instrumentation tests of a variant on a device defined in the DSL.
 */
abstract class ManagedDeviceInstrumentationTestTask(): NonIncrementalTask(), AndroidTestTask {

    abstract class TestRunnerFactory {
        @Input
        abstract fun getUnifiedTestPlatform(): Property<Boolean>

        @Input
        abstract fun getExecutionEnum(): Property<TestOptions.Execution>

        @Input
        abstract fun getRetentionConfig(): Property<RetentionConfig>

        @Internal
        abstract fun getSdkBuildService(): Property<SdkComponentsBuildService>

        @Nested
        abstract fun getUtpDependencies(): UtpDependencies

        @Inject
        open fun getExecOperations(): ExecOperations {
            throw UnsupportedOperationException("Injected by Gradle.")
        }

        fun createTestRunner(): ManagedDeviceTestRunner {
            val javaProcessExecutor =
                GradleJavaProcessExecutor(
                    Function { action: Action<in JavaExecSpec?>? ->
                        getExecOperations().javaexec(action)
                    }
                )

            Preconditions.checkArgument(
                getUnifiedTestPlatform().get(),
                "android.experimental.androidTest.useUnifiedTestPlatform must be enabled.")

            return ManagedDeviceTestRunner(
                javaProcessExecutor,
                getUtpDependencies(),
                getSdkBuildService().get(),
                getRetentionConfig().get())
        }
    }

    @Nested
    abstract fun getTestRunnerFactory(): TestRunnerFactory

    @Nested
    abstract fun getTestData(): Property<TestData>

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract fun getBuddyApks(): ConfigurableFileCollection

    private var hasFailures: Boolean = false
    private var shouldIgnore: Boolean = false

    // For analytics only
    private lateinit var dependencies: ArtifactCollection

    @Input
    abstract fun getDeviceName(): Property<String>

    @Input
    abstract fun getAvdName(): Property<String>

    @Input
    abstract fun getApiLevel(): Property<Int>

    @Input
    abstract fun getAbi(): Property<String>

    @Internal
    abstract fun getAvdComponents(): Property<AvdComponentsBuildService>

    @Internal
    override fun getTestFailed(): Boolean {
        return hasFailures
    }

    override fun getIgnoreFailures(): Boolean {
        return shouldIgnore
    }

    override fun setIgnoreFailures(ignore: Boolean) {
        shouldIgnore = ignore
    }

    @OutputDirectory
    abstract override fun getResultsDir(): DirectoryProperty

    @OutputDirectory
    abstract fun getReportsDir(): DirectoryProperty

    override fun doTaskAction() {
        val managedDevice = UtpManagedDevice(
            getDeviceName().get(),
            getAvdName().get(),
            getApiLevel().get(),
            getAbi().get(),
            getAvdComponents().get().avdFolder.get().asFile.absolutePath,
            path,
            getAvdComponents().get()
                .emulatorDirectory.get().asFile.resolve(FN_EMULATOR).absolutePath)

        DeviceProviderInstrumentTestTask.checkForNonApks(getBuddyApks().files)
            { message: String ->
                throw InvalidUserDataException(message)
            }

        val resultsOutDir = resultsDir.get().asFile
        FileUtils.cleanOutputDir(resultsOutDir)

        val success = if (!testsFound()) {
            logger.info("No tests found, nothing to do.")
            true
        } else {
            try {
                val runner = getTestRunnerFactory()
                    .createTestRunner()
                runner.runTests(
                    managedDevice,
                    resultsOutDir,
                    projectName,
                    getTestData().get().flavorName.get(),
                    getTestData().get().getAsStaticData(),
                    getBuddyApks().files,
                    LoggerWrapper(logger)
                )
            } catch (e: Exception) {
                recordCrashedTestRun(
                    dependencies,
                    getTestRunnerFactory().getExecutionEnum().get(),
                    false,
                    analyticsService.get())
                throw e
            }
        }

        val reportOutDir = getReportsDir().get().asFile
        FileUtils.cleanOutputDir(reportOutDir)

        val report = TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir)
        val results = report.generateReport()

        recordOkTestRun(
            dependencies,
            getTestRunnerFactory().getExecutionEnum().get(),
            false,
            results.testCount,
            analyticsService.get())

        if (!success) {
            hasFailures = true
            val reportUrl = ConsoleRenderer().asClickableFileUrl(
                File(reportOutDir, "index.html"))
            val message = "There were failing tests. See the report at: $reportUrl"
            if (ignoreFailures) {
                logger.warn(message)
                return
            } else {
                throw GradleException(message)
            }
        }

        hasFailures = false
    }

    private fun testsFound() = !getTestData().get().testDirectories.asFileTree.isEmpty

    class CreationAction (
        creationConfig: VariantCreationConfig,
        private val avdComponents: Provider<AvdComponentsBuildService>,
        private val device: ManagedVirtualDevice,
        private val testData: AbstractTestDataImpl
    ): VariantTaskCreationAction<
            ManagedDeviceInstrumentationTestTask, VariantCreationConfig>(creationConfig) {

        override val name = computeTaskName(device.name)

        override val type = ManagedDeviceInstrumentationTestTask::class.java

        override fun configure(task: ManagedDeviceInstrumentationTestTask) {
            super.configure(task)

            val extension = creationConfig.globalScope.extension
            val projectOptions = creationConfig.services.projectOptions

            val testedConfig = creationConfig.testedConfig

            val variantName = testedConfig?.name ?: creationConfig.name

            task.description = "Installs and runs the test for $variantName " +
                    " on the managed device ${device.name}"

            task.getDeviceName().setDisallowChanges(device.name)
            task.getAvdName().setDisallowChanges(computeAvdName(device))

            task.getApiLevel().setDisallowChanges(device.apiLevel)
            task.getAbi().setDisallowChanges(device.abi)

            task.getAvdComponents().setDisallowChanges(avdComponents)

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.getTestData().setDisallowChanges(testData)
            task.getTestRunnerFactory().getSdkBuildService().setDisallowChanges(
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    SdkComponentsBuildService::class.java))

            val executionEnum = extension.testOptions.getExecutionEnum()
            task.getTestRunnerFactory().getExecutionEnum().setDisallowChanges(executionEnum)
            val useUtp = projectOptions.get(BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM)
            task.getTestRunnerFactory().getUnifiedTestPlatform().setDisallowChanges(useUtp)

            if (useUtp) {
                Preconditions.checkArgument(
                    executionEnum == TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR,
                    "Unified Test Platform only supports Android Test " +
                            "Orchestrator. Please either add android.testOptions.execution " +
                            "\"ANDROIDX_TEST_ORCHESTRATOR\"\n to your build file or unset " +
                            BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM.propertyName)

                maybeCreateUtpConfigurations(task.project)
                task.getTestRunnerFactory().getUtpDependencies()
                        .resolveDependencies(task.project.configurations)
            }

            task.getTestRunnerFactory()
                .getRetentionConfig()
                .setDisallowChanges(
                    createRetentionConfig(
                        projectOptions,
                        extension.testOptions.failureRetention as FailureRetention))

            task.dependencies =
                creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR)

            val flavor = testData.flavorName.get()
            val flavorFolder = if (flavor.isNotEmpty()) {
                "$FD_FLAVORS/$flavor"
            } else {
                ""
            }
            val deviceFolder = "$MANAGED_DEVICE/${device.name}"
            val subFolder = "/$deviceFolder/$flavorFolder"

            val resultsLocation = extension.testOptions.resultsDir ?:
                    "${task.project.buildDir}/${AndroidProject.FD_OUTPUTS}/" +
                    BuilderConstants.FD_ANDROID_RESULTS
            task.resultsDir.set(File(resultsLocation + subFolder))

            val reportsLocation = extension.testOptions.reportDir ?:
                    "${task.project.buildDir}/$FD_REPORTS/" +
                    BuilderConstants.FD_ANDROID_TESTS
            task.getReportsDir().set(File(reportsLocation + subFolder))

            task.project.configurations
                .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)?.let {
                    task.getBuddyApks().from(it)
                }
        }
    }
}
