/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.testing.utp.RetentionConfigKt.createRetentionConfig;
import static com.android.build.gradle.internal.testing.utp.UtpTestUtilsKt.shouldEnableUtp;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_RESULTS;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_TESTS;
import static com.android.builder.core.BuilderConstants.FD_FLAVORS;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.builder.model.TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR;
import static com.android.builder.model.TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.BuildToolsExecutableInput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.SdkComponentsKt;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.dsl.EmulatorSnapshots;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.TestsAnalytics;
import com.android.build.gradle.internal.test.report.CompositeTestResults;
import com.android.build.gradle.internal.test.report.ReportType;
import com.android.build.gradle.internal.test.report.TestReport;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.testing.OnDeviceOrchestratorTestRunner;
import com.android.build.gradle.internal.testing.ShardedTestRunner;
import com.android.build.gradle.internal.testing.SimpleTestRunnable;
import com.android.build.gradle.internal.testing.SimpleTestRunner;
import com.android.build.gradle.internal.testing.TestData;
import com.android.build.gradle.internal.testing.TestRunner;
import com.android.build.gradle.internal.testing.utp.RetentionConfig;
import com.android.build.gradle.internal.testing.utp.UtpDependencies;
import com.android.build.gradle.internal.testing.utp.UtpDependencyUtilsKt;
import com.android.build.gradle.internal.testing.utp.UtpTestResultListener;
import com.android.build.gradle.internal.testing.utp.UtpTestRunner;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.TestOptions;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.process.ExecOperations;
import org.gradle.workers.WorkerExecutor;

/** Run instrumentation tests for a given variant */
public abstract class DeviceProviderInstrumentTestTask extends NonIncrementalTask
        implements AndroidTestTask {

    private static final Predicate<File> IS_APK =
            file -> SdkConstants.EXT_ANDROID_PACKAGE.equals(Files.getFileExtension(file.getName()));

    public abstract static class TestRunnerFactory {
        @Input
        public abstract Property<Boolean> getUnifiedTestPlatform();

        @Input
        public abstract Property<Boolean> getShardBetweenDevices();

        @Input
        @Optional
        public abstract Property<Integer> getNumShards();

        @Input
        public abstract Property<TestOptions.Execution> getExecutionEnum();

        @Input
        public abstract Property<RetentionConfig> getRetentionConfig();

        @Internal
        public abstract Property<SdkComponentsBuildService> getSdkBuildService();

        @Nested
        public abstract UtpDependencies getUtpDependencies();

        /**
         * Property for the serials passed into the connectedCheck task. This is used to filter the
         * device serials if and only if the --serial command line argument is not set on this task.
         */
        @Input
        public abstract ListProperty<String> getConnectedCheckDeviceSerials();

        /**
         * Property for the serials passed into this task directly. This is used to filter which
         * devices to test against. The priority of how to filter devices are as follows.
         *
         * <p>1. serials passed directly through the --serial command line to this task stored in
         * the property getSerialValues().
         *
         * <p>2. serials passed directly through the --serial command line on the connectedCheck
         * task. Stored in the property getConnectedCheckTargetSerials().
         *
         * <p>3. serials stored in the ANDROID_SERIAL environment variable.
         *
         * <p>4. no filtering is done and all devices are tested.
         */
        @Input
        public abstract ListProperty<String> getDeviceSerialValues();

        @Inject
        public ExecOperations getExecOperations() {
            throw new UnsupportedOperationException("Injected by Gradle.");
        }

        @Nested
        public abstract BuildToolsExecutableInput getBuildTools();

        TestRunner createTestRunner(
                WorkerExecutor workerExecutor,
                ExecutorServiceAdapter executorServiceAdapter,
                @Nullable UtpTestResultListener utpTestResultListener) {
            GradleProcessExecutor gradleProcessExecutor =
                    new GradleProcessExecutor(getExecOperations()::exec);

            if (getUnifiedTestPlatform().get()) {
                boolean useOrchestrator =
                        (getExecutionEnum().get() == ANDROID_TEST_ORCHESTRATOR
                                || getExecutionEnum().get() == ANDROIDX_TEST_ORCHESTRATOR);
                return new UtpTestRunner(
                        getBuildTools().splitSelectExecutable().getOrNull(),
                        gradleProcessExecutor,
                        workerExecutor,
                        executorServiceAdapter,
                        getUtpDependencies(),
                        getSdkBuildService()
                                .get()
                                .sdkLoader(
                                        getBuildTools().getCompileSdkVersion(),
                                        getBuildTools().getBuildToolsRevision()),
                        getRetentionConfig().get(),
                        useOrchestrator,
                        utpTestResultListener);
            } else {
                switch (getExecutionEnum().get()) {
                    case ANDROID_TEST_ORCHESTRATOR:
                    case ANDROIDX_TEST_ORCHESTRATOR:
                        Preconditions.checkArgument(
                                !getShardBetweenDevices().get(),
                                "Sharding is not supported with Android Test Orchestrator.");

                        return new OnDeviceOrchestratorTestRunner(
                                getBuildTools().splitSelectExecutable().getOrNull(),
                                gradleProcessExecutor,
                                getExecutionEnum().get(),
                                executorServiceAdapter);
                    case HOST:
                        if (getShardBetweenDevices().get()) {

                            return new ShardedTestRunner(
                                    getBuildTools().splitSelectExecutable().getOrNull(),
                                    gradleProcessExecutor,
                                    getNumShards().getOrNull(),
                                    executorServiceAdapter);
                        } else {

                            return new SimpleTestRunner(
                                    getBuildTools().splitSelectExecutable().getOrNull(),
                                    gradleProcessExecutor,
                                    executorServiceAdapter);
                        }
                    default:
                        throw new AssertionError("Unknown value " + getExecutionEnum().get());
                }
            }
        }
    }

    public abstract static class DeviceProviderFactory {
        @Nullable private DeviceProvider deviceProvider;

        @Input
        public abstract Property<Integer> getTimeOutInMs();

        public DeviceProvider getDeviceProvider(
                @NonNull Provider<RegularFile> adbExecutableProvider,
                @Nullable String environmentSerials) {
            if (deviceProvider != null) {
                return deviceProvider;
            }
            // Don't store it in the field, as it breaks configuration caching.
            return new ConnectedDeviceProvider(
                    adbExecutableProvider,
                    getTimeOutInMs().get(),
                    LoggerWrapper.getLogger(DeviceProviderInstrumentTestTask.class),
                    environmentSerials);
        }
    }

    private boolean ignoreFailures;
    private boolean testFailed;

    // For analytics only
    private ArtifactCollection dependencies;

    @Nullable private UtpTestResultListener utpTestResultListener;

    public void setUtpTestResultListener(@Nullable UtpTestResultListener utpTestResultListener) {
        this.utpTestResultListener = utpTestResultListener;
    }

    /**
     * The workers object is of type ExecutorServiceAdapter instead of WorkerExecutorFacade to
     * assert that the object returned is of type ExecutorServiceAdapter as Gradle workers can not
     * be used here because the device tests doesn't run within gradle.
     */
    @NonNull
    @Internal
    public ExecutorServiceAdapter getExecutorServiceAdapter() {
        return Workers.INSTANCE.withThreads(getPath(), getAnalyticsService().get());
    }

    @Override
    protected void doTaskAction() throws DeviceException, IOException, ExecutionException {
        String environmentSerials =
                getTargetSerials().isEmpty() ? System.getenv("ANDROID_SERIAL") : null;
        DeviceProvider deviceProvider =
                getDeviceProviderFactory()
                        .getDeviceProvider(
                                getTestRunnerFactory().getBuildTools().adbExecutable(),
                                environmentSerials);
        if (!deviceProvider.isConfigured()) {
            setDidWork(false);
            return;
        }
        checkForNonApks(
                getBuddyApks().getFiles(),
                message -> {
                    throw new InvalidUserDataException(message);
                });

        File resultsOutDir = getResultsDir().get().getAsFile();
        FileUtils.cleanOutputDir(resultsOutDir);

        final File additionalTestOutputDir;
        if (getAdditionalTestOutputEnabled().get()) {
            additionalTestOutputDir = getAdditionalTestOutputDir().get().getAsFile();
            FileUtils.cleanOutputDir(additionalTestOutputDir);
        } else {
            additionalTestOutputDir = null;
        }

        File coverageOutDir = getCoverageDirectory().get().getAsFile();
        FileUtils.cleanOutputDir(coverageOutDir);

        boolean success;
        // If there are tests to run, and the test runner returns with no results, we fail (since
        // this is most likely a problem with the device setup). If no, the task will succeed.
        if (!testsFound()) {
            getLogger().info("No tests found, nothing to do.");
            // If we don't create the coverage file, createXxxCoverageReport task will fail.
            File emptyCoverageFile = new File(coverageOutDir, SimpleTestRunnable.FILE_COVERAGE_EC);
            emptyCoverageFile.createNewFile();
            success = true;
        } else {
            success =
                    deviceProvider.use(
                            () -> {
                                TestRunner testRunner =
                                        getTestRunnerFactory()
                                                .createTestRunner(
                                                        getWorkerExecutor(),
                                                        getExecutorServiceAdapter(),
                                                        utpTestResultListener);
                                Collection<String> extraArgs =
                                        getInstallOptions().getOrElse(ImmutableList.of());

                                try {
                                    return testRunner.runTests(
                                            getProjectName(),
                                            getTestData().get().getFlavorName().get(),
                                            getTestData().get().getAsStaticData(),
                                            getBuddyApks().getFiles(),
                                            getFilteredDevices(deviceProvider),
                                            deviceProvider.getTimeoutInMs(),
                                            extraArgs,
                                            resultsOutDir,
                                            getAdditionalTestOutputEnabled().get(),
                                            additionalTestOutputDir,
                                            coverageOutDir,
                                            new LoggerWrapper(getLogger()));
                                } catch (Exception e) {
                                    TestsAnalytics.recordCrashedInstrumentedTestRun(
                                            dependencies,
                                            getTestRunnerFactory().getExecutionEnum().get(),
                                            getCodeCoverageEnabled().get(),
                                            getAnalyticsService().get());
                                    throw e;
                                }
                            });
        }

        // run the report from the results.
        File reportOutDir = getReportsDir().getAsFile().get();
        FileUtils.cleanOutputDir(reportOutDir);

        TestReport report = new TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir);
        CompositeTestResults results = report.generateReport();

        TestsAnalytics.recordOkInstrumentedTestRun(
                dependencies,
                getTestRunnerFactory().getExecutionEnum().get(),
                getCodeCoverageEnabled().get(),
                results.getTestCount(),
                getAnalyticsService().get());

        if (!success) {
            testFailed = true;
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(
                    new File(reportOutDir, "index.html"));
            String message = "There were failing tests. See the report at: " + reportUrl;
            if (getIgnoreFailures()) {
                getLogger().warn(message);
                return;

            } else {
                throw new GradleException(message);
            }
        }

        testFailed = false;
    }

    public static void checkForNonApks(
            @NonNull Collection<File> buddyApksFiles, @NonNull Consumer<String> errorHandler) {
        List<File> nonApks =
                buddyApksFiles.stream().filter(IS_APK.negate()).collect(Collectors.toList());
        if (!nonApks.isEmpty()) {
            Collections.sort(nonApks);
            String message =
                    String.format(
                            "Not all files in %s configuration are APKs: %s",
                            SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION,
                            Joiner.on(' ').join(nonApks));
            errorHandler.accept(message);
        }
    }

    private List<? extends DeviceConnector> getFilteredDevices(DeviceProvider deviceProvider) {
        if (!(deviceProvider instanceof ConnectedDeviceProvider)) {
            // Custom providers may filter by the ANDROID_SERIAL environment variable. We have no
            // way to tell those providers to not filter by the environment variable if serials are
            // provided via command-line. In this case, the task should fail and alert the user to
            // either unset the environment variable or remove the command line arguments.
            String environmentSerials = System.getenv("ANDROID_SERIAL");
            boolean validEnvironment = environmentSerials != null && !environmentSerials.isEmpty();
            if (validEnvironment && !getTargetSerials().isEmpty()) {
                throw new GradleException(
                        "Cannot determine devices to target. For custom device providers either "
                                + "unset the ANDROID_SERIAL environment variable or do remove the "
                                + "--serial command line argumetnts when running this task");
            }
        }
        List<? extends DeviceConnector> allDevices = deviceProvider.getDevices();
        List<String> targetSerials = Lists.newArrayList(getTargetSerials());
        if (targetSerials.isEmpty()) {
            return allDevices;
        }
        List<DeviceConnector> targetDevices = Lists.newArrayList();
        for (DeviceConnector device : allDevices) {
            if (targetSerials.contains(device.getSerialNumber())) {
                targetDevices.add(device);
                targetSerials.remove(device.getSerialNumber());
            }
        }
        if (!targetSerials.isEmpty()) {
            throw new GradleException(
                    "Serials specified via command line are not present. Devices have no match for "
                            + "the following serials: "
                            + targetSerials.toString());
        }
        return targetDevices;
    }

    /**
     * Get the serials by which the test task should filter the devices before being passed to the
     * test runner. If the an empty list is returned then no filtering should take place.
     *
     * @return Returns the serials to filter by.
     */
    private List<String> getTargetSerials() {
        if (getTestRunnerFactory().getDeviceSerialValues().isPresent()) {
            return getTestRunnerFactory().getDeviceSerialValues().get();
        }
        return getTestRunnerFactory()
                .getConnectedCheckDeviceSerials()
                .getOrElse(Collections.emptyList());
    }

    /**
     * Determines if there are any tests to run.
     *
     * @return true if there are some tests to run, false otherwise
     */
    private boolean testsFound() {
        return getTestData()
                .get()
                .hasTests(getClasses(), getRClasses(), getBuildConfigClasses())
                .get();
    }

    @OutputDirectory
    public abstract DirectoryProperty getReportsDir();

    @Override
    @OutputDirectory
    public abstract DirectoryProperty getResultsDir();

    @Optional
    @OutputDirectory
    public abstract DirectoryProperty getAdditionalTestOutputDir();

    @OutputDirectory
    public abstract DirectoryProperty getCoverageDirectory();

    @Input
    public abstract Property<Boolean> getCodeCoverageEnabled();

    @Input
    public abstract Property<Boolean> getAdditionalTestOutputEnabled();

    @Optional
    @Input
    public abstract ListProperty<String> getInstallOptions();

    @Nested
    public abstract Property<TestData> getTestData();

    @Nested
    public abstract TestRunnerFactory getTestRunnerFactory();

    @Nested
    public abstract DeviceProviderFactory getDeviceProviderFactory();

    @Classpath
    @Optional
    public abstract ConfigurableFileCollection getClasses();

    @Classpath
    @Optional
    public abstract ConfigurableFileCollection getBuildConfigClasses();

    @Classpath
    @Optional
    public abstract ConfigurableFileCollection getRClasses();

    @Option(
            option = "serial",
            description =
                    "The serial of the device to test against. This will take "
                            + "precedence over the serials specified in the ANDROID_SERIAL environment "
                            + "variable. In addition, when this argument is specified the test task "
                            + "will fail if it cannot connect to the device. \n\n"
                            + "Multiple devices can be specified by specifying the command multiple "
                            + "times. i.e. myAndroidTestTask --serial deviceSerial1 --serial "
                            + "deviceSerial2")
    public void setSerialOption(List<String> serials) {
        getTestRunnerFactory().getDeviceSerialValues().addAll(serials);
    }

    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    @Override
    @Internal // This is the result after running this task
    public boolean getTestFailed() {
        return testFailed;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBuddyApks();

    public static class CreationAction
            extends VariantTaskCreationAction<
                    DeviceProviderInstrumentTestTask, VariantCreationConfig> {

        private static final String CONNECTED_DEVICE_PROVIDER = "connected";

        @NonNull private final String deviceProviderName;
        @Nullable private final DeviceProvider deviceProvider;
        @NonNull private final Type type;
        @NonNull private final AbstractTestDataImpl testData;
        @Nullable private final Provider<List<String>> connectedCheckTargetSerials;

        public enum Type {
            INTERNAL_CONNECTED_DEVICE_PROVIDER,
            CUSTOM_DEVICE_PROVIDER,
        }

        public CreationAction(
                @NonNull VariantCreationConfig creationConfig,
                @NonNull AbstractTestDataImpl testData) {
            this(creationConfig, testData, null);
        }

        /** Creation action for AGP {@link ConnectedDeviceProvider} device providers. */
        public CreationAction(
                @NonNull VariantCreationConfig creationConfig,
                @NonNull AbstractTestDataImpl testData,
                @Nullable Provider<List<String>> connectedCheckTargetSerials) {
            this(
                    creationConfig,
                    null,
                    CONNECTED_DEVICE_PROVIDER,
                    Type.INTERNAL_CONNECTED_DEVICE_PROVIDER,
                    testData,
                    connectedCheckTargetSerials);
        }

        /** Creation action for custom (non-AGP) device providers. */
        public CreationAction(
                @NonNull VariantCreationConfig creationConfig,
                @NonNull DeviceProvider deviceProvider,
                @NonNull AbstractTestDataImpl testData,
                @Nullable Provider<List<String>> connectedCheckTargetSerials) {
            this(
                    creationConfig,
                    deviceProvider,
                    deviceProvider.getName(),
                    Type.CUSTOM_DEVICE_PROVIDER,
                    testData,
                    connectedCheckTargetSerials);
        }

        private CreationAction(
                @NonNull VariantCreationConfig creationConfig,
                @Nullable DeviceProvider deviceProvider,
                @NonNull String deviceProviderName,
                @NonNull Type type,
                @NonNull AbstractTestDataImpl testData,
                @Nullable Provider<List<String>> connectedCheckTargetSerials) {
            super(creationConfig);
            this.deviceProvider = deviceProvider;
            this.deviceProviderName = deviceProviderName;
            this.type = type;
            this.testData = testData;
            this.connectedCheckTargetSerials = connectedCheckTargetSerials;
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName(deviceProviderName);
        }

        @NonNull
        @Override
        public Class<DeviceProviderInstrumentTestTask> getType() {
            return DeviceProviderInstrumentTestTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<DeviceProviderInstrumentTestTask> taskProvider) {
            super.handleProvider(taskProvider);

            boolean isAdditionalAndroidTestOutputEnabled =
                    creationConfig
                            .getServices()
                            .getProjectOptions()
                            .get(BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT);

            if (type == Type.INTERNAL_CONNECTED_DEVICE_PROVIDER) {
                if (isAdditionalAndroidTestOutputEnabled) {
                    creationConfig
                            .getArtifacts()
                            .setInitialProvider(
                                    taskProvider,
                                    DeviceProviderInstrumentTestTask::getAdditionalTestOutputDir)
                            .withName(deviceProviderName)
                            .on(
                                    InternalArtifactType.CONNECTED_ANDROID_TEST_ADDITIONAL_OUTPUT
                                            .INSTANCE);
                }
                creationConfig
                        .getArtifacts()
                        .setInitialProvider(
                                taskProvider,
                                DeviceProviderInstrumentTestTask::getCoverageDirectory)
                        .withName(deviceProviderName)
                        .on(InternalArtifactType.CODE_COVERAGE.INSTANCE);
            } else {
                // NOTE : This task will be created per device provider, assume several tasks instances
                // will exist in the variant scope.
                if (isAdditionalAndroidTestOutputEnabled) {
                    creationConfig
                            .getArtifacts()
                            .setInitialProvider(
                                    taskProvider,
                                    DeviceProviderInstrumentTestTask::getAdditionalTestOutputDir)
                            .withName(deviceProviderName)
                            .on(
                                    InternalArtifactType
                                            .DEVICE_PROVIDER_ANDROID_TEST_ADDITIONAL_OUTPUT
                                            .INSTANCE);
                }
                creationConfig
                        .getArtifacts()
                        .setInitialProvider(
                                taskProvider,
                                DeviceProviderInstrumentTestTask::getCoverageDirectory)
                        .withName(deviceProviderName)
                        .on(InternalArtifactType.DEVICE_PROVIDER_CODE_COVERAGE.INSTANCE);
            }

            if (creationConfig instanceof TestComponentImpl) {
                if (type == Type.INTERNAL_CONNECTED_DEVICE_PROVIDER) {
                    creationConfig.getTaskContainer().setConnectedTestTask(taskProvider);
                    // possible redundant with setConnectedTestTask?
                    creationConfig.getTaskContainer().setConnectedTask(taskProvider);
                } else {
                    creationConfig.getTaskContainer().getProviderTestTaskList().add(taskProvider);
                }
            }
        }

        @Override
        public void configure(@NonNull DeviceProviderInstrumentTestTask task) {
            super.configure(task);

            BaseExtension extension = creationConfig.getGlobalScope().getExtension();
            Project project = task.getProject();
            ProjectOptions projectOptions = creationConfig.getServices().getProjectOptions();

            // this can be null for test plugin
            VariantCreationConfig testedConfig = creationConfig.getTestedConfig();

            String variantName =
                    testedConfig != null ? testedConfig.getName() : creationConfig.getName();
            if (type == Type.INTERNAL_CONNECTED_DEVICE_PROVIDER) {
                task.setDescription("Installs and runs the tests for " + variantName +
                        " on connected devices.");
            } else {
                task.setDescription(
                        StringHelper.appendCapitalized(
                                "Installs and runs the tests for "
                                        + variantName
                                        + " using provider: ",
                                deviceProviderName));
            }

            task.getAdditionalTestOutputEnabled()
                    .set(projectOptions.get(BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT));

            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

            task.getTestData().set(testData);
            task.getDeviceProviderFactory()
                    .getTimeOutInMs()
                    .set(extension.getAdbOptions().getTimeOutInMs());
            if (deviceProvider != null) {
                Preconditions.checkState(
                        type != Type.INTERNAL_CONNECTED_DEVICE_PROVIDER,
                        "If using AGP device provider, no device provider should be "
                                + "specified in order to make task compatible with configuration "
                                + "caching (DeviceProvider is not serializable currently).");
                task.getDeviceProviderFactory().deviceProvider = deviceProvider;
            }
            task.getInstallOptions().set(extension.getAdbOptions().getInstallOptions());
            task.getTestRunnerFactory()
                    .getShardBetweenDevices()
                    .set(projectOptions.getProvider(BooleanOption.ENABLE_TEST_SHARDING));
            task.getTestRunnerFactory()
                    .getNumShards()
                    .set(projectOptions.getProvider(IntegerOption.ANDROID_TEST_SHARD_COUNT));

            task.getTestRunnerFactory()
                    .getSdkBuildService()
                    .set(
                            BuildServicesKt.getBuildService(
                                    creationConfig.getServices().getBuildServiceRegistry(),
                                    SdkComponentsBuildService.class));

            SdkComponentsKt.initialize(task.getTestRunnerFactory().getBuildTools(), creationConfig);

            TestOptions.Execution executionEnum = extension.getTestOptions().getExecutionEnum();
            task.getTestRunnerFactory().getExecutionEnum().set(executionEnum);
            if (connectedCheckTargetSerials != null) {
                task.getTestRunnerFactory()
                        .getConnectedCheckDeviceSerials()
                        .set(connectedCheckTargetSerials);
            }
            boolean useUtp = shouldEnableUtp(projectOptions, extension.getTestOptions());
            task.getTestRunnerFactory().getUnifiedTestPlatform().set(useUtp);
            if (useUtp) {
                if (!projectOptions.get(BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM)) {
                    LoggerWrapper.getLogger(DeviceProviderInstrumentTestTask.class)
                            .warning(
                                    "Implicitly enabling Unified Test Platform because related "
                                            + "features are specified in gradle test options. "
                                            + "Please add "
                                            + "-Pandroid.experimental.androidTest."
                                            + "useUnifiedTestPlatform=true "
                                            + "to your gradle command to suppress this warning.");
                }
                UtpDependencyUtilsKt.maybeCreateUtpConfigurations(project);
                UtpDependencyUtilsKt.resolveDependencies(
                        task.getTestRunnerFactory().getUtpDependencies(),
                        task.getProject().getConfigurations());
            }

            task.getTestRunnerFactory()
                    .getRetentionConfig()
                    .set(
                            createRetentionConfig(
                                    projectOptions,
                                    (EmulatorSnapshots)
                                            extension.getTestOptions().getEmulatorSnapshots()));

            task.getCodeCoverageEnabled()
                    .set(creationConfig.getVariantDslInfo().isTestCoverageEnabled());
            boolean useJacocoTransformOutputs =
                    creationConfig
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION)
                            && creationConfig.getVariantDslInfo().isTestCoverageEnabled();
            task.dependencies =
                    creationConfig
                            .getVariantDependencies()
                            .getArtifactCollection(
                                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                                    useJacocoTransformOutputs
                                            ? AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR
                                            : AndroidArtifacts.ArtifactType.CLASSES_JAR);

            String flavorFolder = testData.getFlavorName().get();
            if (!flavorFolder.isEmpty()) {
                flavorFolder = FD_FLAVORS + "/" + flavorFolder;
            }
            String providerFolder =
                    type == Type.INTERNAL_CONNECTED_DEVICE_PROVIDER
                            ? CONNECTED
                            : DEVICE + "/" + deviceProviderName;
            final String subFolder = "/" + providerFolder + "/" + flavorFolder;

            String rootLocation = extension.getTestOptions().getResultsDir();
            if (rootLocation == null) {
                rootLocation = project.getBuildDir() + "/" + FD_OUTPUTS + "/" + FD_ANDROID_RESULTS;
            }
            task.getResultsDir().set(new File(rootLocation + subFolder));

            rootLocation = extension.getTestOptions().getReportDir();
            if (rootLocation == null) {
                rootLocation = project.getBuildDir() + "/" + FD_REPORTS + "/" + FD_ANDROID_TESTS;
            }
            task.getReportsDir().set(project.file(rootLocation + subFolder));

            // The configuration is not created by the experimental plugin, so just create an empty
            // FileCollection in this case.
            Configuration androidTestUtil =
                    project.getConfigurations()
                            .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION);
            if (androidTestUtil != null) {
                task.getBuddyApks().from(androidTestUtil);
            }

            // This task should never be UP-TO-DATE as we don't model the device state as input yet.
            task.getOutputs().upToDateWhen(it -> false);

            task.getClasses().from(creationConfig.getArtifacts().getAllClasses());
            task.getClasses().disallowChanges();
            task.getBuildConfigClasses()
                    .from(((ComponentImpl) creationConfig).getCompiledBuildConfig());
            task.getBuildConfigClasses().disallowChanges();
            task.getRClasses()
                    .from(
                            ((ComponentImpl) creationConfig)
                                    .getCompiledRClasses(
                                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH));
            task.getRClasses().disallowChanges();
        }
    }
}
