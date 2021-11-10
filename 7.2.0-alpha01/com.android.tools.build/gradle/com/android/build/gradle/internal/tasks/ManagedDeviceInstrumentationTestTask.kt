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
import com.android.build.gradle.internal.dsl.EmulatorSnapshots
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.test.recordCrashedInstrumentedTestRun
import com.android.build.gradle.internal.test.recordOkInstrumentedTestRun
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
import com.android.build.gradle.internal.testing.utp.shouldEnableUtp
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.BuilderConstants.FD_FLAVORS
import com.android.builder.core.BuilderConstants.FD_REPORTS
import com.android.builder.core.BuilderConstants.MANAGED_DEVICE
import com.android.builder.model.TestOptions
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor

/**
 * Runs instrumentation tests of a variant on a device defined in the DSL.
 */
@DisableCachingByDefault
abstract class ManagedDeviceInstrumentationTestTask(): NonIncrementalTask(), AndroidTestTask {

    abstract class TestRunnerFactory {
        @get: Input
        abstract val unifiedTestPlatform: Property<Boolean>

        @get: Input
        abstract val executionEnum: Property<TestOptions.Execution>

        @get: Input
        abstract val retentionConfig: Property<RetentionConfig>

        @get: Input
        abstract val compileSdkVersion: Property<String>

        @get: Input
        abstract val buildToolsRevision: Property<Revision>

        @get: Input
        @get: Optional
        abstract val testShardsSize: Property<Int>

        @get: Internal
        abstract val sdkBuildService: Property<SdkComponentsBuildService>

        @get: Nested
        abstract val utpDependencies: UtpDependencies

        fun createTestRunner(workerExecutor: WorkerExecutor): ManagedDeviceTestRunner {

            Preconditions.checkArgument(
                    unifiedTestPlatform.get(),
                    "android.experimental.androidTest.useUnifiedTestPlatform must be enabled.")

            val useOrchestrator = when(executionEnum.get()) {
                TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR,
                TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> true
                else -> false
            }

            return ManagedDeviceTestRunner(
                workerExecutor,
                utpDependencies,
                sdkBuildService.get().sdkLoader(compileSdkVersion, buildToolsRevision),
                retentionConfig.get(),
                useOrchestrator,
                testShardsSize.getOrNull()
            )
        }
    }

    @get: Nested
    abstract val testRunnerFactory: TestRunnerFactory

    @get: Nested
    abstract val testData: Property<TestData>

    @get: Optional
    @get: Input
    abstract val installOptions: ListProperty<String>

    @get: InputFiles
    @get: PathSensitive(PathSensitivity.RELATIVE)
    abstract val buddyApks: ConfigurableFileCollection

    private var hasFailures: Boolean = false
    private var shouldIgnore: Boolean = false

    // For analytics only
    private lateinit var dependencies: ArtifactCollection

    @get: Input
    abstract val deviceName: Property<String>

    @get: Input
    abstract val avdName: Property<String>

    @get: Input
    abstract val apiLevel: Property<Int>

    @get: Input
    abstract val abi: Property<String>

    @get: Internal
    abstract val avdComponents: Property<AvdComponentsBuildService>

    @Internal
    override fun getTestFailed(): Boolean {
        return hasFailures
    }

    @get: Input
    abstract val enableEmulatorDisplay: Property<Boolean>

    @Option(
        option="enable-display",
        description = "Adding this option will display the emulator while testing, instead" +
                "of running the tests on a headless emulator.")
    fun setDisplayEmulatorOption(value: Boolean) = enableEmulatorDisplay.set(value)

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

    @OutputDirectory
    abstract fun getCoverageDirectory(): DirectoryProperty

    override fun doTaskAction() {
        val emulatorProvider = avdComponents.get().emulatorDirectory
        Preconditions.checkArgument(
                emulatorProvider.isPresent(),
                "The emulator is missing. Download the emulator in order to use managed devices.")
        val managedDevice = UtpManagedDevice(
                deviceName.get(),
                avdName.get(),
                apiLevel.get(),
                abi.get(),
                avdComponents.get().avdFolder.get().asFile.absolutePath,
                path,
                emulatorProvider.get().asFile.resolve(FN_EMULATOR).absolutePath,
                enableEmulatorDisplay.get())

        DeviceProviderInstrumentTestTask.checkForNonApks(buddyApks.files)
            { message: String ->
                throw InvalidUserDataException(message)
            }

        val resultsOutDir = resultsDir.get().asFile
        FileUtils.cleanOutputDir(resultsOutDir)

        val codeCoverageOutDir = getCoverageDirectory().get().asFile
        FileUtils.cleanOutputDir(codeCoverageOutDir)

        val success = if (!testsFound()) {
            logger.info("No tests found, nothing to do.")
            true
        } else {
            try {
                val runner = testRunnerFactory.createTestRunner(workerExecutor)
                runner.runTests(
                        managedDevice,
                        resultsOutDir,
                        codeCoverageOutDir,
                        projectPath.get(),
                        testData.get().flavorName.get(),
                        testData.get().getAsStaticData(),
                        installOptions.getOrElse(listOf()),
                        buddyApks.files,
                        LoggerWrapper(logger)
                )
            } catch (e: Exception) {
                recordCrashedInstrumentedTestRun(
                        dependencies,
                        testRunnerFactory.executionEnum.get(),
                        false,
                        analyticsService.get())
                throw e
            }
        }

        val reportOutDir = getReportsDir().get().asFile
        FileUtils.cleanOutputDir(reportOutDir)

        val report = TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir)
        val results = report.generateReport()

        recordOkInstrumentedTestRun(
                dependencies,
                testRunnerFactory.executionEnum.get(),
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

    private fun testsFound() = !testData.get().testDirectories.asFileTree.isEmpty

    class CreationAction(
            creationConfig: VariantCreationConfig,
            private val avdComponents: Provider<AvdComponentsBuildService>,
            private val device: ManagedVirtualDevice,
            private val testData: AbstractTestDataImpl
    ): VariantTaskCreationAction<
            ManagedDeviceInstrumentationTestTask, VariantCreationConfig>(creationConfig) {

        override val name = computeTaskName(device.name)

        override val type = ManagedDeviceInstrumentationTestTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ManagedDeviceInstrumentationTestTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    ManagedDeviceInstrumentationTestTask::getCoverageDirectory)
                .withName(BuilderConstants.MANAGED_DEVICE)
                .on(InternalArtifactType.MANAGED_DEVICE_CODE_COVERAGE)
        }

        override fun configure(task: ManagedDeviceInstrumentationTestTask) {
            super.configure(task)

            task.enableEmulatorDisplay.convention(false)

            val extension = creationConfig.globalScope.extension
            val projectOptions = creationConfig.services.projectOptions

            val testedConfig = creationConfig.testedConfig

            val variantName = testedConfig?.name ?: creationConfig.name

            task.description = "Installs and runs the test for $variantName " +
                    " on the managed device ${device.name}"

            task.deviceName.setDisallowChanges(device.name)
            task.avdName.setDisallowChanges(computeAvdName(device))

            task.apiLevel.setDisallowChanges(device.apiLevel)
            task.abi.setDisallowChanges(device.abi)

            task.avdComponents.setDisallowChanges(avdComponents)

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.testData.setDisallowChanges(testData)
            task.installOptions.set(extension.adbOptions.installOptions)
            task.testRunnerFactory.compileSdkVersion.setDisallowChanges(
                extension.compileSdkVersion
            )
            task.testRunnerFactory.buildToolsRevision.setDisallowChanges(
                extension.buildToolsRevision
            )
            task.testRunnerFactory.sdkBuildService.setDisallowChanges(
                    getBuildService(
                            creationConfig.services.buildServiceRegistry,
                            SdkComponentsBuildService::class.java))
            task.testRunnerFactory.testShardsSize.setDisallowChanges(
                projectOptions.get(IntegerOption.MANAGED_DEVICE_SHARD_POOL_SIZE)
            )

            val executionEnum = extension.testOptions.getExecutionEnum()
            task.testRunnerFactory.executionEnum.setDisallowChanges(executionEnum)
            val useUtp = shouldEnableUtp(projectOptions,extension.testOptions,
                                         testedConfig?.variantType)
            task.testRunnerFactory.unifiedTestPlatform.setDisallowChanges(useUtp)

            if (useUtp) {
                if (!projectOptions.get(BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM)) {
                    LoggerWrapper.getLogger(CreationAction::class.java).warning(
                        "Implicitly enabling Unified Test Platform because related features " +
                                "are specified in gradle test options. Please add " +
                                "-Pandroid.experimental.androidTest.useUnifiedTestPlatform=true " +
                                "to your gradle command to suppress this warning."
                    )
                }
                maybeCreateUtpConfigurations(task.project)
                task.testRunnerFactory.utpDependencies
                        .resolveDependencies(task.project.configurations)
            }

            task.testRunnerFactory
                .retentionConfig
                .setDisallowChanges(
                        createRetentionConfig(
                                projectOptions,
                                extension.testOptions.emulatorSnapshots as EmulatorSnapshots))

            task.dependencies =
                creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.EXTERNAL,
                            AndroidArtifacts.ArtifactType.CLASSES_JAR)

            val flavor: String? = testData.flavorName.get()
            val flavorFolder = if (flavor.isNullOrEmpty()) "" else "$FD_FLAVORS/$flavor"
            val deviceFolder = "$MANAGED_DEVICE/${device.name}"
            val subFolder = "/$deviceFolder/$flavorFolder"

            val resultsLocation =
                extension.testOptions.resultsDir
                    ?: "${task.project.buildDir}/${SdkConstants.FD_OUTPUTS}/${BuilderConstants.FD_ANDROID_RESULTS}"
            task.resultsDir.set(File(resultsLocation + subFolder))

            val reportsLocation =
                extension.testOptions.reportDir
                    ?: "${task.project.buildDir}/$FD_REPORTS/${BuilderConstants.FD_ANDROID_TESTS}"
            task.getReportsDir().set(File(reportsLocation + subFolder))

            task.project.configurations
                .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)?.let {
                    task.buddyApks.from(it)
                }
        }
    }
}
