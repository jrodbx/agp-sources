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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunParameters
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask.checkForNonApks
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.test.gatherTestLibraries
import com.android.build.gradle.internal.test.recordCrashedInstrumentedTestRun
import com.android.build.gradle.internal.test.recordOkInstrumentedTestRun
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.testing.TestRunData
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.model.TestOptions
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.TestLibraries
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
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
import org.gradle.internal.logging.ConsoleRenderer
import java.io.File
import javax.inject.Inject

@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
@CacheableTask
abstract class ManagedDeviceTestTask: NonIncrementalTask(), AndroidTestTask {

    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:Input
    abstract val testAction: Property<Class<out DeviceTestRunTaskAction<out DeviceTestRunInput>>>

    @get:Nested
    abstract val deviceInput: Property<DeviceTestRunInput>

    @get:Nested
    abstract val testData: Property<TestData>

    @get:Input
    abstract val deviceDslName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val setupResult: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buddyApks: ConfigurableFileCollection

    /** Whether failing tests should fail the build */
    private var shouldIgnore: Boolean = false

    // For analytics only
    @get:Internal
    @get:VisibleForTesting
    @set:VisibleForTesting
    lateinit var dependencies: ArtifactCollection
        protected set

    @get:Classpath
    @get:Optional
    abstract val classes: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val buildConfigClasses: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    abstract val rClasses: ConfigurableFileCollection

    override fun getIgnoreFailures(): Boolean = shouldIgnore

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

    @get:Optional
    @get:Input
    abstract val installOptions: ListProperty<String>

    @get:Input
    abstract val executionEnum: Property<TestOptions.Execution>

    public override fun doTaskAction() {
        checkForNonApks(buddyApks.files) { message: String ->
            throw InvalidUserDataException(message)
        }

        FileUtils.cleanOutputDir(resultsDir.get().asFile)

        FileUtils.cleanOutputDir(getCoverageDirectory().get().asFile)

        val additionalTestOutputDir = if (getAdditionalTestOutputEnabled().get()) {
            getAdditionalTestOutputDir().get().also {
                FileUtils.cleanOutputDir(it.asFile)
            }
        } else {
            null
        }

        workerExecutor.noIsolation().submit(ManagedDeviceTestRunnable::class.java) { params ->
            val testRunTaskAction = objectFactory.newInstance(testAction.get())
                as DeviceTestRunTaskAction<DeviceTestRunInput>

            params.initializeWith(projectPath, path, analyticsService)
            params.testsFound.setDisallowChanges(testsFound())
            params.deviceInput.setDisallowChanges(deviceInput)
            params.setupResult.setDisallowChanges(setupResult)
            params.testData.setDisallowChanges(testData.get().getAsStaticData())
            params.testRunAction.setDisallowChanges(testRunTaskAction)
            params.path.setDisallowChanges(path)
            params.flavorName.setDisallowChanges(testData.get().flavorName)
            params.deviceDslName.setDisallowChanges(deviceDslName)
            params.resultsOutDir.setDisallowChanges(resultsDir)
            params.codeCoverageOutDir.setDisallowChanges(getCoverageDirectory())
            params.additionalTestOutputDir.setDisallowChanges(additionalTestOutputDir)
            params.reportsDir.setDisallowChanges(getReportsDir())
            params.installOptions.setDisallowChanges(installOptions)
            params.buddyApks.setFrom(buddyApks)
            params.testLibraries.setDisallowChanges(
                gatherTestLibraries(dependencies)
            )
            params.ignoreFailures.setDisallowChanges(ignoreFailures)
            params.executionEnum.setDisallowChanges(executionEnum)
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

    abstract class ManagedDeviceTestRunnable :
        ProfileAwareWorkAction<ManagedDeviceTestRunnableParameters>() {

        val logger: Logger = Logging.getLogger("ManagedDeviceTestRunnable")

        override fun run() {
            val success = if (!parameters.testsFound.get()) {
                logger.info("No tests found, nothing to do.")
                true
            } else {
                try {
                    val testRunParams = object: DeviceTestRunParameters<DeviceTestRunInput> {
                        override val deviceInput = parameters.deviceInput.get()
                        override val setupResult = parameters.setupResult
                        override val testRunData = TestRunData(
                            parameters.flavorName.get(),
                            parameters.path.get(),
                            parameters.deviceDslName.get(),
                            parameters.resultsOutDir.get(),
                            parameters.codeCoverageOutDir.get(),
                            parameters.additionalTestOutputDir.get(),
                            parameters.installOptions.getOrElse(listOf()),
                            parameters.buddyApks.files,
                            parameters.projectPath.get(),
                            parameters.testData.get()
                        )
                    }
                    parameters.testRunAction.get().runTests(testRunParams)
                } catch (e: Exception) {
                    recordCrashedInstrumentedTestRun(
                        parameters.testLibraries.get(),
                        parameters.executionEnum.get(),
                        false,
                        parameters.analyticsService.get())
                    throw e
                }
            }

            val reportOutDir = parameters.reportsDir.get().asFile
            FileUtils.cleanOutputDir(reportOutDir)

            val report = TestReport(
                ReportType.SINGLE_FLAVOR, parameters.resultsOutDir.get().asFile, reportOutDir)
            val results = report.generateReport()

            recordOkInstrumentedTestRun(
                parameters.testLibraries.get(),
                parameters.executionEnum.get(),
                false,
                results.testCount,
                parameters.analyticsService.get())

            if (!success) {
                val reportUrl = ConsoleRenderer().asClickableFileUrl(
                    File(reportOutDir, "index.html"))
                val message = """
                    There were failing tests for Device: ${parameters.deviceDslName.get()}.
                    See the report at: $reportUrl
                    """.trimIndent()
                if (parameters.ignoreFailures.get()) {
                    logger.warn(message)
                    return
                } else {
                    throw GradleException(message)
                }
            }
        }
    }
    abstract class ManagedDeviceTestRunnableParameters : ProfileAwareWorkAction.Parameters() {
        abstract val testsFound: Property<Boolean>
        abstract val deviceInput: Property<DeviceTestRunInput>
        abstract val setupResult: DirectoryProperty
        abstract val testData: Property<StaticTestData>
        abstract val testRunAction: Property<DeviceTestRunTaskAction<DeviceTestRunInput>>
        abstract val path: Property<String>
        abstract val flavorName: Property<String>
        abstract val deviceDslName: Property<String>
        abstract val resultsOutDir: DirectoryProperty
        abstract val codeCoverageOutDir: DirectoryProperty
        abstract val additionalTestOutputDir: DirectoryProperty
        abstract val reportsDir: DirectoryProperty
        abstract val installOptions: ListProperty<String>
        abstract val buddyApks: ConfigurableFileCollection
        abstract val testLibraries: Property<TestLibraries>
        abstract val ignoreFailures: Property<Boolean>
        abstract val executionEnum: Property<TestOptions.Execution>
    }

    class CreationAction<DeviceT: Device>(
        creationConfig: InstrumentedTestCreationConfig,
        private val device: DeviceT,
        private val testRunConfigAction : Class<out DeviceTestRunConfigureAction<DeviceT, *>>,
        private val testRunTaskAction: Class<out DeviceTestRunTaskAction<*>>,
        private val testData: AbstractTestDataImpl,
        private val testResultOutputDir: File,
        private val testReportOutputDir: File,
        private val additionalTestOutputDir: File,
        private val coverageOutputDir: File,
        private val setupResultDir: Provider<Directory>?,
        nameSuffix: String = ""
    ): VariantTaskCreationAction<
            ManagedDeviceTestTask,
            InstrumentedTestCreationConfig
    >(creationConfig) {

        override val name = computeTaskName(device.name, nameSuffix)

        override val type = ManagedDeviceTestTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ManagedDeviceTestTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    ManagedDeviceTestTask::getCoverageDirectory
                )
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
                        ManagedDeviceTestTask::getAdditionalTestOutputDir)
                    .atLocation(additionalTestOutputDir.absolutePath)
                    .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_ADDITIONAL_OUTPUT)
            }
        }

        override fun configure(task: ManagedDeviceTestTask) {
            super.configure(task)

            val projectOptions = creationConfig.services.projectOptions
            val testedConfig = (creationConfig as? AndroidTestCreationConfig)?.mainVariant
            val variantName = testedConfig?.name ?: creationConfig.name

            task.description = "Installs and runs the test for $variantName " +
                    " on the managed device ${device.name}"

            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.testAction.setDisallowChanges(testRunTaskAction)

            task.deviceInput.setDisallowChanges(
                task.objectFactory.newInstance(testRunConfigAction).configureTaskInput(device))

            task.testData.setDisallowChanges(testData)

            task.deviceDslName.setDisallowChanges(device.name)

            if (setupResultDir != null) {
                task.setupResult.setDisallowChanges(setupResultDir)
            }

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
            task.resultsDir.disallowChanges()

            task.getReportsDir().set(testReportOutputDir)
            task.getReportsDir().disallowChanges()

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
            task.executionEnum.setDisallowChanges(creationConfig.global.testOptionExecutionEnum)
        }
    }
}
