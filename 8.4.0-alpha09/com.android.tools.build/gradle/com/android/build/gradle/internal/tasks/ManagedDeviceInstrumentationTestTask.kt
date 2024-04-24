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
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.computeManagedDeviceEmulatorMode
import com.android.build.gradle.internal.dsl.EmulatorControl
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
import com.android.build.gradle.internal.testing.utp.EmulatorControlConfig
import com.android.build.gradle.internal.testing.utp.ManagedDeviceTestRunner
import com.android.build.gradle.internal.testing.utp.RetentionConfig
import com.android.build.gradle.internal.testing.utp.UtpDependencies
import com.android.build.gradle.internal.testing.utp.createEmulatorControlConfig
import com.android.build.gradle.internal.testing.utp.createRetentionConfig
import com.android.build.gradle.internal.testing.utp.maybeCreateUtpConfigurations
import com.android.build.gradle.internal.testing.utp.resolveDependencies
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.model.TestOptions
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.options.Option
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.logging.Level

/**
 * Runs instrumentation tests of a variant on a device defined in the DSL.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceInstrumentationTestTask: NonIncrementalTask(), AndroidTestTask {

    abstract class TestRunnerFactory {

        /** Java runtime environment to run UTP in */
        @get:Internal
        abstract val jvmExecutable: RegularFileProperty

        @get:Input
        abstract val javaVersion: Property<JavaVersion>

        @get: Input
        abstract val executionEnum: Property<TestOptions.Execution>

        @get: Input
        abstract val forceCompilation: Property<Boolean>

        @get: Input
        abstract val emulatorControlConfig: Property<EmulatorControlConfig>

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

        @get: Internal
        abstract val avdComponents: Property<AvdComponentsBuildService>

        @get: Nested
        abstract val utpDependencies: UtpDependencies

        @get: Internal
        abstract val utpLoggingLevel: Property<Level>

        @get: Input
        abstract val emulatorGpuFlag: Property<String>

        @get:Input
        abstract val showEmulatorKernelLoggingFlag: Property<Boolean>

        @get:Input
        @get: Optional
        abstract val installApkTimeout: Property<Int>

        @get: Input
        abstract val enableEmulatorDisplay: Property<Boolean>

        @get: Input
        @get: Optional
        abstract val getTargetIsSplitApk: Property<Boolean>

        @get: Input
        @get: Optional
        abstract val getKeepInstalledApks: Property<Boolean>

        fun createTestRunner(
            workerExecutor: WorkerExecutor, numShards: Int?): ManagedDeviceTestRunner {

            val useOrchestrator = when(executionEnum.get()) {
                TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR,
                TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> true
                else -> false
            }

            return ManagedDeviceTestRunner(
                workerExecutor,
                utpDependencies,
                jvmExecutable.get().asFile,
                sdkBuildService.get().sdkLoader(compileSdkVersion, buildToolsRevision),
                emulatorControlConfig.get(),
                retentionConfig.get(),
                useOrchestrator,
                forceCompilation.get(),
                numShards,
                emulatorGpuFlag.get(),
                showEmulatorKernelLoggingFlag.get(),
                avdComponents.get(),
                installApkTimeout.getOrNull(),
                enableEmulatorDisplay.get(),
                utpLoggingLevel.get(),
                getTargetIsSplitApk.getOrElse(false),
                !getKeepInstalledApks.get(),
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

    private var shouldIgnore: Boolean = false

    // For analytics only
    @get: Internal
    @get: VisibleForTesting
    lateinit var dependencies: ArtifactCollection
        private set

    @get: Nested
    abstract val device: Property<ManagedVirtualDevice>

    @get:Classpath
    @get:Optional
    abstract val classes: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val buildConfigClasses: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val rClasses: ConfigurableFileCollection

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

    @Input
    abstract fun getAdditionalTestOutputEnabled(): Property<Boolean>

    @Optional
    @OutputDirectory
    abstract fun getAdditionalTestOutputDir(): DirectoryProperty

    @get: Input
    abstract val enableEmulatorDisplay: Property<Boolean>

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    abstract fun getPrivacySandboxSdkApksFiles(): ConfigurableFileCollection

    @Option(
        option="enable-display",
        description = "Adding this option will display the emulator while testing, instead" +
                "of running the tests on a headless emulator.")
    fun setDisplayEmulatorOption(value: Boolean) = enableEmulatorDisplay.set(value)

    public override fun doTaskAction() {
        val device = device.get()
        DeviceProviderInstrumentTestTask.checkForNonApks(buddyApks.files)
            { message: String ->
                throw InvalidUserDataException(message)
            }

        val resultsOutDir = resultsDir.get().asFile
        FileUtils.cleanOutputDir(resultsOutDir)

        val codeCoverageOutDir = getCoverageDirectory().get().asFile
        FileUtils.cleanOutputDir(codeCoverageOutDir)

        val additionalTestOutputDir = if (getAdditionalTestOutputEnabled().get()) {
            getAdditionalTestOutputDir().get().getAsFile().also {
                FileUtils.cleanOutputDir(it)
            }
        } else {
            null
        }

        val success = if (!testsFound()) {
            logger.info("No tests found, nothing to do.")
            true
        } else {
            try {
                val runner = testRunnerFactory.createTestRunner(
                    workerExecutor,
                    testRunnerFactory.testShardsSize.getOrNull()
                )

                runner.runTests(
                    device,
                    path,
                    resultsOutDir,
                    codeCoverageOutDir,
                    additionalTestOutputDir,
                    projectPath.get(),
                    testData.get().flavorName.get(),
                    testData.get().getAsStaticData(),
                    installOptions.getOrElse(listOf()),
                    buddyApks.files,
                    logger,
                    getPrivacySandboxSdkApksFiles()?.files ?: setOf()
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
    }

    /**
     * Determines if there are any tests to run.
     *
     * @return true if there are some tests to run, false otherwise
     */
    private fun testsFound(): Boolean {
        return testData
            .get()
            .hasTests(classes, rClasses, buildConfigClasses)
            .get()
    }

    class CreationAction(
        creationConfig: InstrumentedTestCreationConfig,
        private val device: ManagedVirtualDevice,
        private val testData: AbstractTestDataImpl,
        private val testResultOutputDir: File,
        private val testReportOutputDir: File,
        private val additionalTestOutputDir: File,
        private val coverageOutputDir: File,
        nameSuffix: String = "",
    ) : VariantTaskCreationAction<
            ManagedDeviceInstrumentationTestTask, InstrumentedTestCreationConfig>(creationConfig)
    {

        override val name = computeTaskName(device.name, nameSuffix)

        override val type = ManagedDeviceInstrumentationTestTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ManagedDeviceInstrumentationTestTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    ManagedDeviceInstrumentationTestTask::getCoverageDirectory)
                .atLocation(coverageOutputDir.absolutePath)
                .on(InternalArtifactType.MANAGED_DEVICE_CODE_COVERAGE)

            val isAdditionalAndroidTestOutputEnabled = creationConfig
                .services
                .projectOptions[BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT]
            if (isAdditionalAndroidTestOutputEnabled) {
                creationConfig
                    .artifacts
                    .setInitialProvider(
                        taskProvider,
                        ManagedDeviceInstrumentationTestTask::getAdditionalTestOutputDir)
                    .atLocation(additionalTestOutputDir.absolutePath)
                    .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_ADDITIONAL_OUTPUT)
            }
        }

        override fun configure(task: ManagedDeviceInstrumentationTestTask) {
            super.configure(task)

            task.enableEmulatorDisplay.convention(false)

            task.testRunnerFactory.jvmExecutable.apply {
                set(File(System.getProperty("java.home"), "bin/java"))
                disallowChanges()
            }

            task.testRunnerFactory.javaVersion.setDisallowChanges(JavaVersion.current())

            task.testRunnerFactory.enableEmulatorDisplay.set(
                task.enableEmulatorDisplay
            )

            val globalConfig = creationConfig.global
            val projectOptions = creationConfig.services.projectOptions

            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant

            val variantName = testedConfig?.name ?: creationConfig.name

            task.description = "Installs and runs the test for $variantName " +
                    " on the managed device ${device.name}"

            task.device.setDisallowChanges(device)

            if (device.apiLevel <= 26 &&
                !projectOptions[BooleanOption.GRADLE_MANAGED_DEVICE_ALLOW_OLD_API_LEVEL_DEVICES]
            ) {
                throw GradleException(
                    """
                    API level 26 and lower is currently not supported for Gradle Managed devices.
                    Your current configuration requires API level ${device.apiLevel}.
                    While it's not recommended, you can use API levels 26 and lower by adding
                    android.experimental.testOptions.managedDevices.allowOldApiLevelDevices=true
                    to your gradle.properties file.
                    """.trimIndent()
                )
            }

            task.testRunnerFactory.avdComponents.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.testData.setDisallowChanges(testData)
            task.installOptions.set(globalConfig.installationOptions.installOptions)
            task.testRunnerFactory.compileSdkVersion.setDisallowChanges(
                globalConfig.compileSdkHashString
            )
            task.testRunnerFactory.buildToolsRevision.setDisallowChanges(
                globalConfig.buildToolsRevision
            )
            task.testRunnerFactory.sdkBuildService.setDisallowChanges(
                    getBuildService(
                            creationConfig.services.buildServiceRegistry,
                            SdkComponentsBuildService::class.java))
            task.testRunnerFactory.testShardsSize.setDisallowChanges(
                projectOptions.get(IntegerOption.MANAGED_DEVICE_SHARD_POOL_SIZE)
            )

            val executionEnum = globalConfig.testOptionExecutionEnum
            task.testRunnerFactory.executionEnum.setDisallowChanges(executionEnum)

            task.testRunnerFactory.forceCompilation.setDisallowChanges(
                creationConfig.isForceAotCompilation)

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
            task.testRunnerFactory.getTargetIsSplitApk.setDisallowChanges(
                    testedConfig?.componentType?.isDynamicFeature ?: false
            )

            task.testRunnerFactory.getKeepInstalledApks.setDisallowChanges(
                    projectOptions.get(BooleanOption.ANDROID_TEST_LEAVE_APKS_INSTALLED_AFTER_RUN)
            )

            task.testRunnerFactory.emulatorGpuFlag.setDisallowChanges(
                computeManagedDeviceEmulatorMode(creationConfig.services.projectOptions)
            )

            task.testRunnerFactory.showEmulatorKernelLoggingFlag.setDisallowChanges(
                creationConfig.services.projectOptions[
                        BooleanOption.GRADLE_MANAGED_DEVICE_EMULATOR_SHOW_KERNEL_LOGGING]
            )

            val infoLoggingEnabled =
                Logging.getLogger(ManagedDeviceInstrumentationTestTask::class.java).isInfoEnabled()
            task.testRunnerFactory.utpLoggingLevel.set(
                if (infoLoggingEnabled) Level.INFO else Level.OFF
            )

            task.testRunnerFactory
                .emulatorControlConfig
                .setDisallowChanges(
                    createEmulatorControlConfig(
                        projectOptions,
                        globalConfig.androidTestOptions.emulatorControl as EmulatorControl))

            task.testRunnerFactory
                .retentionConfig
                .setDisallowChanges(
                        createRetentionConfig(
                                projectOptions,
                                globalConfig.androidTestOptions.emulatorSnapshots as EmulatorSnapshots))

            task.testRunnerFactory
                .installApkTimeout
                .setDisallowChanges(projectOptions[IntegerOption.INSTALL_APK_TIMEOUT])

            task.dependencies =
                creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.EXTERNAL,
                            AndroidArtifacts.ArtifactType.CLASSES_JAR)

            task.getAdditionalTestOutputEnabled()
                .set(projectOptions[BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT])

            task.resultsDir.set(testResultOutputDir)
            task.getReportsDir().set(testReportOutputDir)

            task.project.configurations
                .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)?.let {
                    task.buddyApks.from(it)
                }

            task.classes.from(
                creationConfig.artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .getFinalArtifacts(ScopedArtifact.CLASSES)
            )
            task.classes.disallowChanges()
            creationConfig.buildConfigCreationConfig?.let {
                task.buildConfigClasses.from(it.compiledBuildConfig)
            }
            task.buildConfigClasses.disallowChanges()
            creationConfig.androidResourcesCreationConfig?.let {
                task.rClasses.from(
                    it.getCompiledRClasses(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH)
                )
            }
            task.rClasses.disallowChanges()

            if(creationConfig.privacySandboxCreationConfig != null && testedConfig != null) {
                task.getPrivacySandboxSdkApksFiles()
                    .setFrom(
                        testedConfig
                            .variantDependencies
                            .getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS))
            }
            task.getPrivacySandboxSdkApksFiles().disallowChanges()
        }
    }
}
