/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.testing;

import static com.android.build.gradle.internal.testing.utp.AdditionalTestOutputUtilsKt.ADDITIONAL_TEST_OUTPUT_MIN_API_LEVEL;
import static com.android.build.gradle.internal.testing.utp.AdditionalTestOutputUtilsKt.findAdditionalTestOutputDirectoryOnDevice;
import static com.android.ddmlib.DdmPreferences.getTimeOut;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner.CoverageOutput;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;

/**
 * Basic Callable to run tests on a given {@link DeviceConnector} using {@link
 * RemoteAndroidTestRunner}.
 *
 * <p>The boolean return value is true if success.
 */
public class SimpleTestRunnable implements WorkerExecutorFacade.WorkAction {

    public static final String FILE_COVERAGE_EC = "coverage.ec";
    private static final String TMP = "/data/local/tmp/";

    // This constant value is a copy from
    // androidx.test.services.storage.TestStorageConstants.ON_DEVICE_PATH_INTERNAL_USE.
    // Android Test orchestrator outputs test coverage files under this directory when
    // useTestStorageService option is enabled.
    private static final String TEST_STORAGE_SERVICE_OUTPUT_DIR = "/sdcard/googletest/internal_use/";

    @NonNull private final RemoteAndroidTestRunner runner;
    @NonNull private final String projectName;
    @NonNull private final DeviceConnector device;
    @NonNull private final String flavorName;
    @NonNull private final StaticTestData testData;
    @NonNull private final File resultsDir;
    @Nullable private final File additionalTestOutputDir;
    @NonNull private final File coverageDir;
    @NonNull private final List<File> testedApks;
    @NonNull private final Collection<String> installOptions;
    @NonNull private final ILogger logger;
    @NonNull private final Set<File> helperApks;
    @NonNull private final BaseTestRunner.TestResult testResult;

    private final int timeoutInMs;
    private final boolean additionalTestOutputEnabled;

    @Inject
    public SimpleTestRunnable(SimpleTestParams params) {
        this.projectName = params.projectName;
        this.device = params.device;
        this.runner = params.runner;
        this.flavorName = params.flavorName;
        this.helperApks = params.helperApks;
        this.resultsDir = params.resultsDir;
        this.additionalTestOutputDir = params.additionalTestOutputDir;
        this.coverageDir = params.coverageDir;
        this.testedApks = params.testedApks;
        this.testData = params.testData;
        this.timeoutInMs = params.timeoutInMs;
        this.installOptions = params.installOptions;
        this.logger = params.logger;
        this.testResult = params.testResult;
        this.additionalTestOutputEnabled = params.additionalTestOutputEnabled;
    }

    @NonNull
    protected String getUserId()
            throws ShellCommandUnresponsiveException, AdbCommandRejectedException, IOException,
                    TimeoutException {
        final List<String> output = new ArrayList<>();
        executeShellCommand(
                "am get-current-user",
                new MultiLineReceiver() {
                    @Override
                    public void processNewLines(@NotNull String[] lines) {
                        output.addAll(
                                Arrays.stream(lines)
                                        .filter(it -> !it.isEmpty())
                                        .collect(Collectors.toList()));
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });

        if (output.size() != 1) {
            throw new IOException(
                    "Unexpected output of command get-current-user, "
                            + "expected a user id found the following output:\n> "
                            + String.join("\n> ", output));
        }
        return output.get(0);
    }

    private Collection<String> getInstallOptions(@Nullable String userId) {
        ImmutableList.Builder<String> builder =
                new ImmutableList.Builder<String>().addAll(this.installOptions);
        if (userId != null) {
            builder.add("--user " + userId);
        }
        return builder.build();
    }

    @Override
    public void run() {
        String deviceName = device.getName();
        boolean isInstalled = false;

        CustomTestRunListener runListener =
                new CustomTestRunListener(deviceName, projectName, flavorName, logger);
        runListener.setReportDir(resultsDir);

        long time = System.currentTimeMillis();
        boolean success = false;

        boolean useTestStorageService = Boolean.parseBoolean(
                testData.getInstrumentationRunnerArguments()
                        .getOrDefault("useTestStorageService", "false"));
        String prefixedCoverageFilePath = null;
        String userId = null;

        String additionalTestOutputDir = null;

        try {
            device.connect(timeoutInMs, logger);
            if (device.getApiLevel() >= 24) {
                userId = getUserId();
            }
            final Collection<String> installOptions = getInstallOptions(userId);
            String coverageFile = getCoverageFile(userId);
            prefixedCoverageFilePath =
                    (useTestStorageService ? TEST_STORAGE_SERVICE_OUTPUT_DIR : "") + coverageFile;

            if (!testedApks.isEmpty()) {
                logger.verbose(
                        "DeviceConnector '%s': installing %s",
                        deviceName, Joiner.on(", ").join(testedApks));
                if (testedApks.size() > 1 && device.getApiLevel() < 21) {
                    throw new InstallException(
                            "Internal error, file a bug, multi-apk applications require a device with API level 21+");
                }
                if (testedApks.size() > 1) {
                    device.installPackages(testedApks, installOptions, timeoutInMs, logger);
                } else {
                    device.installPackage(testedApks.get(0), installOptions, timeoutInMs, logger);
                }
            }

            if (!helperApks.isEmpty()) {
                ArrayList<String> helperApkInstallOptions = new ArrayList<>(installOptions);
                int apiLevel = device.getApiLevel();
                if (apiLevel >= 23) {
                    // Grant all permissions listed in the app manifest (Introduced at Android 6.0)
                    // for test helper APKs.
                    helperApkInstallOptions.add("-g");
                }
                if (apiLevel >= 30) {
                    // AndroidX Test services and orchestrator APK set android:forceQueryable="true"
                    // in their manifest file however this attribute seems to be ignored on some
                    // physical devices. See https://github.com/android/android-test/issues/743
                    // for details.
                    helperApkInstallOptions.add("--force-queryable");
                }
                for (File helperApk : helperApks) {
                    logger.verbose(
                            "DeviceConnector '%s': installing helper APK %s",
                            deviceName, helperApk);
                    device.installPackage(helperApk, helperApkInstallOptions, timeoutInMs, logger);
                }
            }

            if (runner instanceof AndroidTestOrchestratorRemoteAndroidTestRunner) {
                // Grant MANAGE_EXTERNAL_STORAGE permission to androidx.test.services so that it
                // can write test artifacts in external storage.
                if (device.getApiLevel() >= 30) {
                    executeShellCommand(
                            "appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow",
                            getOutputReceiver());
                }
            }

            logger.verbose(
                    "DeviceConnector '%s': installing %s", deviceName, testData.getTestApk());
            device.installPackage(testData.getTestApk(), installOptions, timeoutInMs, logger);
            isInstalled = true;

            for (Map.Entry<String, String> argument :
                    testData.getInstrumentationRunnerArguments().entrySet()) {
                runner.addInstrumentationArg(argument.getKey(), argument.getValue());
            }

            if (additionalTestOutputEnabled
                    && device.getApiLevel() >= ADDITIONAL_TEST_OUTPUT_MIN_API_LEVEL) {
                additionalTestOutputDir =
                        findAdditionalTestOutputDirectoryOnDevice(device, testData);

                if (additionalTestOutputDir != null) {
                    MultiLineReceiver receiver = getOutputReceiver();
                    String mkdirp = "mkdir -p " + additionalTestOutputDir;
                    executeShellCommand(mkdirp, receiver);

                    setUpDirectories(additionalTestOutputDir, userId);
                    runner.setAdditionalTestOutputLocation(additionalTestOutputDir);
                }
            }

            if (testData.isTestCoverageEnabled()) {
                runner.addInstrumentationArg("coverage", "true");
                if (runner.getCoverageOutputType() == CoverageOutput.DIR) {
                    setUpDirectories(prefixedCoverageFilePath, userId);
                }

                // Don't use prefixedCoverageFilePath here because the runner will
                // take care of it.
                runner.setCoverageReportLocation(coverageFile);
            }

            if (testData.getAnimationsDisabled() || userId != null) {
                runner.setRunOptions(
                        (testData.getAnimationsDisabled() ? "--no_window_animation " : "")
                                + (userId != null ? ("--user " + userId) : ""));
            }

            runner.setRunName(deviceName);
            runner.setMaxtimeToOutputResponse(timeoutInMs);

            runner.run(runListener);

            TestRunResult testRunResult = runListener.getRunResult();

            success = true;

            // for now throw an exception if no tests.
            // TODO return a status instead of allow merging of multi-variants/multi-device reports.
            if (testRunResult.getNumTests() == 0) {
                CustomTestRunListener fakeRunListener =
                        new CustomTestRunListener(deviceName, projectName, flavorName, logger);
                fakeRunListener.setReportDir(resultsDir);

                // create a fake test output
                Map<String, String> emptyMetrics = Collections.emptyMap();
                TestIdentifier fakeTest =
                        new TestIdentifier(device.getClass().getName(), "No tests found.");
                fakeRunListener.testStarted(fakeTest);
                fakeRunListener.testFailed(
                        fakeTest,
                        "No tests found. This usually means that your test classes are"
                                + " not in the form that your test runner expects (e.g. don't inherit from"
                                + " TestCase or lack @Test annotations).");
                fakeRunListener.testEnded(fakeTest, emptyMetrics);

                // end the run to generate the XML file.
                fakeRunListener.testRunEnded(System.currentTimeMillis() - time, emptyMetrics);
                testResult.setTestResult(BaseTestRunner.TestResult.Result.FAILED);
                return;
            }
            testResult.setTestResult(
                    (testRunResult.hasFailedTests() || testRunResult.isRunFailure())
                            ? BaseTestRunner.TestResult.Result.FAILED
                            : BaseTestRunner.TestResult.Result.SUCCEEDED);
        } catch (Exception e) {
            Map<String, String> emptyMetrics = Collections.emptyMap();

            // create a fake test output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(baos, true);
            e.printStackTrace(pw);
            TestIdentifier fakeTest = new TestIdentifier(device.getClass().getName(), "runTests");
            runListener.testStarted(fakeTest);
            runListener.testFailed(fakeTest, baos.toString());
            runListener.testEnded(fakeTest, emptyMetrics);

            // end the run to generate the XML file.
            runListener.testRunEnded(System.currentTimeMillis() - time, emptyMetrics);

            // and throw
            throw new RuntimeException(e);
        } finally {
            if (isInstalled) {
                // Pull test data output to host.
                if (success && additionalTestOutputEnabled && additionalTestOutputDir != null) {
                    try {
                        pullTestData(deviceName, additionalTestOutputDir);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                // Get the coverage if needed.
                if (success && testData.isTestCoverageEnabled()) {
                    try {
                        pullCoverageData(
                                deviceName,
                                prefixedCoverageFilePath,
                                runner.getCoverageOutputType(),
                                useTestStorageService,
                                userId);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    uninstall(testData.getTestApk(), testData.getApplicationId(), deviceName);
                } catch (DeviceException e) {
                    throw new RuntimeException(e);
                }

                for (File testedApk : testedApks) {
                    try {
                        uninstall(testedApk, testData.getTestedApplicationId(), deviceName);
                    } catch (DeviceException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            try {
                device.disconnect(timeoutInMs, logger);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NonNull
    private MultiLineReceiver getOutputReceiver() {
        return new MultiLineReceiver() {
            @Override
            public void processNewLines(@NonNull String[] lines) {
                for (String line : lines) {
                    logger.verbose(line);
                }
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    @NonNull
    private static String cleanUpDir(@NonNull String path) {
        return String.format("if [ -d %s ]; then rm -rf %s; fi && mkdir -p %s", path, path, path);
    }

    private void setUpDirectories(@NonNull String userCoverageDir, @Nullable String userId)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {

        MultiLineReceiver receiver = getOutputReceiver();
        String cleanUpTmp = cleanUpDir(getCoverageTmp());
        executeShellCommand(cleanUpTmp, receiver);

        String cleanUpUser = cleanUpDir(userCoverageDir);
        execAsScript(cleanUpUser, "tmpScript.sh", userId, true);
    }

    private void execAsScript(
            @NonNull String command,
            @NonNull String scriptName,
            @Nullable String userId,
            boolean withRunAs)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        String scriptPath = getCoverageTmp() + "/" + scriptName;
        MultiLineReceiver receiver = getOutputReceiver();
        executeShellCommand("echo '" + command + "' > " + scriptPath, receiver);

        String finalCommand = "sh " + scriptPath;
        if (withRunAs) {
            finalCommand = asTestedApplication(userId, finalCommand);
        }
        executeShellCommand(finalCommand, receiver);
    }

    @VisibleForTesting
    String getCoverageFile(@Nullable String userId) {
        final Supplier<String> defaultDir =
                () -> {
                    if (userId == null) {
                        switch (runner.getCoverageOutputType()) {
                            case DIR:
                                return "/data/data/"
                                        + testData.getTestedApplicationId()
                                        + "/coverage_data/";
                            case FILE:
                                return String.format(
                                        "/data/data/%s/%s",
                                        testData.getTestedApplicationId(), FILE_COVERAGE_EC);
                            default:
                                throw new AssertionError("Unknown coverage type.");
                        }
                    } else {
                        switch (runner.getCoverageOutputType()) {
                            case DIR:
                                return String.format(
                                        "/data/user/%s/%s/coverage_data/",
                                        userId, testData.getTestedApplicationId());
                            case FILE:
                                return String.format(
                                        "/data/user/%s/%s/%s",
                                        userId,
                                        testData.getTestedApplicationId(),
                                        FILE_COVERAGE_EC);
                            default:
                                throw new AssertionError("Unknown coverage type.");
                        }
                    }
                };

        return testData.getInstrumentationRunnerArguments()
                .getOrDefault("coverageFilePath", defaultDir.get());
    }

    @NonNull
    private String asTestedApplication(@Nullable String userId, @NonNull String... command) {
        Iterator<String> iterator =
                Arrays.stream(command)
                        .map(
                                s ->
                                        String.format(
                                                "run-as %s %s %s",
                                                testData.getTestedApplicationId(),
                                                (userId != null ? ("--user " + userId) : ""),
                                                s))
                        .iterator();
        return Joiner.on(" && ").join(iterator);
    }

    private void pullTestData(String deviceName, String additionalTestOutputDir)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        if (this.additionalTestOutputDir == null) {
            throw new RuntimeException(
                    "Attempt to pull additional test output without an output directory set");
        }

        final File hostDir = new File(this.additionalTestOutputDir, deviceName);
        FileUtils.cleanOutputDir(hostDir);
        MultiLineReceiver reportPathReceiver =
                new MultiLineReceiver() {
                    @Override
                    public void processNewLines(@NonNull String[] lines) {
                        for (String report : lines) {
                            if (report.isEmpty()) break;
                            if (report.startsWith("ls")) break; // Ignore first line of stdout.

                            logger.verbose(
                                    "DeviceConnector '%s': fetching test data %s",
                                    deviceName, report);
                            File hostSingleReport = new File(hostDir, report);
                            try {
                                device.pullFile(
                                        Paths.get(additionalTestOutputDir, report).toString(),
                                        hostSingleReport.getPath());
                            } catch (IOException e) {
                                logger.error(e, "Error while pulling test data from device.");
                            }
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                };

        logger.verbose(
                "DeviceConnector '%s': fetching additional test data from %s",
                deviceName, additionalTestOutputDir);

        // List all files in additionalTestOutputDir with one file per line.
        executeShellCommand("ls -1 " + additionalTestOutputDir, reportPathReceiver);
        reportPathReceiver.flush();
    }

    private void pullCoverageData(
            String deviceName,
            String coverageFile,
            CoverageOutput coverageOutput,
            boolean useTestStorageService,
            @Nullable String userId)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        switch (coverageOutput) {
            case DIR:
                pullCoverageFromDir(deviceName, coverageFile, useTestStorageService, userId);
                break;
            case FILE:
                pullSingleCoverageFile(deviceName, coverageFile, userId);
                break;
            default:
                throw new AssertionError("Unknown coverage type.");
        }
    }

    private void pullSingleCoverageFile(
            String deviceName, String coverageFile, @Nullable String userId)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        String temporaryCoverageCopy =
                TMP + testData.getTestedApplicationId() + "." + FILE_COVERAGE_EC;

        MultiLineReceiver outputReceiver = getOutputReceiver();

        logger.verbose(
                "DeviceConnector '%s': fetching coverage data from %s", deviceName, coverageFile);
        executeShellCommand(
                asTestedApplication(userId, " cat " + coverageFile)
                        + " | cat > "
                        + temporaryCoverageCopy,
                outputReceiver);
        device.pullFile(
                temporaryCoverageCopy,
                new File(coverageDir, deviceName + "-" + FILE_COVERAGE_EC).getPath());
        executeShellCommand("rm " + temporaryCoverageCopy, outputReceiver);
    }

    private void pullCoverageFromDir(
            String deviceName,
            String coverageDir,
            Boolean useTestStorageService,
            @Nullable String userId)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        String coverageTmp = getCoverageTmp();
        MultiLineReceiver outputReceiver = getOutputReceiver();

        logger.verbose(
                "DeviceConnector '%s': fetching coverage dir from %s", deviceName, coverageDir);

        // 1. create a script to copy all coverage reports to coverageTmp dir; execute script
        // 2. write down all file names to a coveragePaths, and copy that file to host
        // 3. for every .ec file in the paths list, copy it from device to host
        String listFiles;
        if (useTestStorageService) {
            listFiles = "ls " + coverageDir;
        } else {
            listFiles = asTestedApplication(userId, "ls " + coverageDir);
        }
        String copyScript =
                String.format(
                        "for i in $(%s); do run-as %s cat %s$i > %s/$i; done",
                        listFiles, testData.getTestedApplicationId(), coverageDir, coverageTmp);
        execAsScript(copyScript, "copyFromUser.sh", userId, false);

        String coveragePaths = coverageTmp + "/paths.txt";
        executeShellCommand("ls " + coverageTmp + " > " + coveragePaths, outputReceiver);

        File hostCoverage = new File(this.coverageDir, deviceName);
        FileUtils.cleanOutputDir(hostCoverage);

        File hostCoveragePaths = new File(hostCoverage, "coverage_file_paths.txt");
        device.pullFile(coveragePaths, hostCoveragePaths.getPath());
        List<String> reportPaths = Files.readAllLines(hostCoveragePaths.toPath());
        for (String reportPath : reportPaths) {
            if (reportPath.endsWith(".ec")) {
                File hostSingleReport = new File(hostCoverage, reportPath);
                device.pullFile(coverageTmp + "/" + reportPath, hostSingleReport.getPath());
            }
        }

        FileUtils.delete(hostCoveragePaths);

        executeShellCommand("rm -r " + coverageTmp, outputReceiver);
    }

    private void executeShellCommand(@NonNull String command, @NonNull MultiLineReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
                    IOException {
        device.executeShellCommand(command, receiver, getTimeOut(), TimeUnit.MILLISECONDS);
    }

    @NonNull
    private String getCoverageTmp() {
        return TMP + testData.getTestedApplicationId() + "-coverage_data";
    }

    private void uninstall(
            @NonNull File apkFile, @Nullable String packageName, @NonNull String deviceName)
            throws DeviceException {
        if (packageName != null) {
            logger.verbose("DeviceConnector '%s': uninstalling %s", deviceName, packageName);
            device.uninstallPackage(packageName, timeoutInMs, logger);
        } else {
            logger.verbose(
                    "DeviceConnector '%s': unable to uninstall %s: unable to get package name",
                    deviceName, apkFile);
        }
    }

    public static class SimpleTestParams implements Serializable {
        @NonNull private final RemoteAndroidTestRunner runner;
        @NonNull private final String projectName;
        @NonNull private final DeviceConnector device;
        @NonNull private final String flavorName;
        @NonNull private final StaticTestData testData;
        @NonNull private final File resultsDir;
        private final boolean additionalTestOutputEnabled;
        @Nullable private final File additionalTestOutputDir;
        @NonNull private final File coverageDir;
        @NonNull private final List<File> testedApks;
        @NonNull private final Collection<String> installOptions;
        @NonNull private final ILogger logger;
        @NonNull private final Set<File> helperApks;
        @NonNull private final BaseTestRunner.TestResult testResult;

        private final int timeoutInMs;

        public SimpleTestParams(
                @NonNull DeviceConnector device,
                @NonNull String projectName,
                @NonNull RemoteAndroidTestRunner runner,
                @NonNull String flavorName,
                @NonNull List<File> testedApks,
                @NonNull StaticTestData testData,
                @NonNull Set<File> helperApks,
                @NonNull File resultsDir,
                boolean additionalTestOutputEnabled,
                @Nullable File additionalTestOutputDir,
                @NonNull File coverageDir,
                int timeoutInMs,
                @NonNull Collection<String> installOptions,
                @NonNull ILogger logger,
                @NonNull BaseTestRunner.TestResult testResult) {
            this.projectName = projectName;
            this.device = device;
            this.runner = runner;
            this.flavorName = flavorName;
            this.helperApks = helperApks;
            this.resultsDir = resultsDir;
            this.additionalTestOutputDir = additionalTestOutputDir;
            this.coverageDir = coverageDir;
            this.testedApks = testedApks;
            this.testData = testData;
            this.timeoutInMs = timeoutInMs;
            this.installOptions = installOptions;
            this.logger = logger;
            this.testResult = testResult;
            this.additionalTestOutputEnabled = additionalTestOutputEnabled;
        }
    }
}
