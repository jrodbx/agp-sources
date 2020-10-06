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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Basic Callable to distribute and run tests on a given {@link DeviceConnector} using {@link
 * RemoteAndroidTestRunner}. The boolean return value is true if success.
 */
public class ShardedTestRunnable implements WorkerExecutorFacade.WorkAction {

    public static final String FILE_COVERAGE_EC_SUFFIX = "coverage.ec";

    @NonNull private final String projectName;
    @NonNull private final DeviceConnector device;
    @NonNull private final String flavorName;
    @NonNull private final StaticTestData testData;
    @NonNull private final File resultsDir;
    @NonNull private final File coverageDir;
    @NonNull private final List<File> testedApks;
    @NonNull private final ILogger logger;
    @NonNull private final ShardProvider shardProvider;
    @NonNull private final BaseTestRunner.TestResult testResult;

    private final int timeoutInMs;
    private final ProgressListener progressListener;

    @Inject
    public ShardedTestRunnable(ShardedTestParams params) {
        this.projectName = params.projectName;
        this.device = params.device;
        this.flavorName = params.flavorName;
        this.resultsDir = params.resultsDir;
        this.coverageDir = params.coverageDir;
        this.testedApks = params.testedApks;
        this.testData = params.testData;
        this.timeoutInMs = params.timeoutInMs;
        this.logger = params.logger;
        this.shardProvider = params.shardProvider;
        this.progressListener = params.progressListener;
        this.testResult = params.testResult;
    }

    private String createCoverageFileName(int shard) {
        return "shard_" + shard + FILE_COVERAGE_EC_SUFFIX;
    }

    @Override
    public void run() {
        final String deviceName = device.getName();
        boolean isInstalled = false;

        long time = System.currentTimeMillis();
        boolean failed = false;
        List<String> coverageFiles = new ArrayList<>();
        String coverageFileLocation = "/data/data/" + testData.getTestedApplicationId() + "/";
        CustomTestRunListener runListener = null;
        try {
            device.connect(timeoutInMs, logger);
            logger.verbose("Connected to %s to run tests", deviceName);
            synchronized (ShardedTestRunnable.class) {
                if (!testedApks.isEmpty()) {
                    logger.verbose("DeviceConnector '%s': installing %s", deviceName,
                            Joiner.on(',').join(testedApks));
                    if (testedApks.size() > 1 && device.getApiLevel() < 21) {
                        throw new InstallException(
                                "Internal error, file a bug, multi-apk applications"
                                        + " require a device with API level 21+");
                    }
                    if (testedApks.size() > 1) {
                        device.installPackages(
                                testedApks,
                                ImmutableList.of() /* installOptions */,
                                timeoutInMs,
                                logger);
                    } else {
                        device.installPackage(
                                testedApks.get(0),
                                ImmutableList.of() /* installOptions */,
                                timeoutInMs,
                                logger);
                    }
                }

                logger.verbose(
                        "DeviceConnector '%s': installing %s", deviceName, testData.getTestApk());
                if (device.getApiLevel() >= 21) {
                    device.installPackages(
                            ImmutableList.of(testData.getTestApk()),
                            ImmutableList.of() /* installOptions */,
                            timeoutInMs,
                            logger);
                } else {
                    device.installPackage(
                            testData.getTestApk(),
                            ImmutableList.of() /* installOptions */,
                            timeoutInMs,
                            logger);
                }
                logger.verbose("Installed test apk on %s", deviceName);
            }
            isInstalled = true;
            Integer shard;

            while ((shard = shardProvider.getNextShard()) != null) {
                logger.verbose("Running shard %d on %s", shard, deviceName);
                RemoteAndroidTestRunner runner =
                        new RemoteAndroidTestRunner(
                                testData.getApplicationId(),
                                testData.getInstrumentationRunner(),
                                device);

                for (Map.Entry<String, String> argument :
                        testData.getInstrumentationRunnerArguments().entrySet()) {
                    runner.addInstrumentationArg(argument.getKey(), argument.getValue());
                }

                if (testData.isTestCoverageEnabled()) {
                    runner.addInstrumentationArg("coverage", "true");
                    String coverageFileName = createCoverageFileName(shard);
                    coverageFiles.add(coverageFileName);
                    runner.addInstrumentationArg("coverageFile",
                            coverageFileLocation + coverageFileName);
                }
                runner.addInstrumentationArg("shardIndex", String.valueOf(shard));
                runner.addInstrumentationArg("numShards",
                        String.valueOf(shardProvider.getTotalShards()));

                runner.setRunName(deviceName);
                runner.setMaxtimeToOutputResponse(timeoutInMs);
                runListener = new ShardedTestListener(
                        shard, deviceName, projectName, flavorName, logger);
                runListener.setReportDir(resultsDir);
                ((ShardedTestListener) runListener).setProgressListener(progressListener);
                runner.run(runListener);

                TestRunResult testRunResult = runListener.getRunResult();
                if (testRunResult.getNumTests() == 0) {
                    // just show a warning since it is OK to have 0 tests in sharded runner.
                    logger.warning("shard %d  has 0 tests. This might be OK", shard);
                }
                failed |= testRunResult.hasFailedTests() || testRunResult.isRunFailure();
                logger.verbose("done running shard %d on %s", shard, deviceName);
            }

            testResult.setTestResult(
                    failed
                            ? BaseTestRunner.TestResult.Result.FAILED
                            : BaseTestRunner.TestResult.Result.SUCCEEDED);
        } catch (Exception e) {
            Map<String, String> emptyMetrics = Collections.emptyMap();

            // create a fake test output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(baos, true);
            e.printStackTrace(pw);
            TestIdentifier fakeTest = new TestIdentifier(device.getClass().getName(), "runTests");
            if (runListener != null) {
                runListener.testStarted(fakeTest);
                runListener.testFailed(fakeTest, baos.toString());
                runListener.testEnded(fakeTest, emptyMetrics);

                // end the run to generate the XML file.
                runListener.testRunEnded(System.currentTimeMillis() - time, emptyMetrics);
            }

            // and throw
            throw new RuntimeException(e);
        } finally {
            if (isInstalled) {
                // Get the coverage if needed.
                if (testData.isTestCoverageEnabled()) {
                    MultiLineReceiver outputReceiver =
                            new MultiLineReceiver() {
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
                    logger.verbose("Have %d coverage files to fetch", coverageFiles.size());
                    for (String name : coverageFiles) {
                        String temporaryCoverageCopy =
                                "/data/local/tmp/" + testData.getTestedApplicationId() + "." + name;
                        String coverageFile = coverageFileLocation + name;
                        logger.verbose("DeviceConnector '%s': fetching coverage data from %s",
                                deviceName, coverageFile);
                        try {
                            device.executeShellCommand(
                                    "run-as "
                                            + testData.getTestedApplicationId()
                                            + " cat "
                                            + coverageFile
                                            + " | cat > "
                                            + temporaryCoverageCopy,
                                    outputReceiver,
                                    30,
                                    TimeUnit.SECONDS);
                            device.pullFile(
                                    temporaryCoverageCopy, new File(coverageDir, name).getPath());
                            device.executeShellCommand(
                                    "rm " + temporaryCoverageCopy,
                                    outputReceiver,
                                    30,
                                    TimeUnit.SECONDS);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                // uninstall the apps
                // This should really not be null, because if it was the build
                // would have broken before.
                try {
                    uninstall(testData.getTestApk(), testData.getApplicationId(), deviceName);
                } catch (DeviceException e) {
                    throw new RuntimeException(e);
                }

                if (!testedApks.isEmpty()) {
                    for (File testedApk : testedApks) {
                        try {
                            uninstall(testedApk, testData.getTestedApplicationId(), deviceName);
                        } catch (DeviceException e) {
                            throw new RuntimeException(e);
                        }
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

    private void uninstall(@NonNull File apkFile, @Nullable String packageName,
            @NonNull String deviceName)
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

    public interface ShardProvider {

        @Nullable
        Integer getNextShard();

        int getTotalShards();
    }

    private static final class ShardedTestListener extends CustomTestRunListener {

        private final String name;

        private ProgressListener mProgressListener;

        public ShardedTestListener(int shard, @NonNull String deviceName,
                @NonNull String projectName, @NonNull String flavorName, @Nullable ILogger logger) {
            super(deviceName, projectName, flavorName, logger);
            name = "TEST-" + deviceName + "-" + projectName + "-" + flavorName
                    + "-shard-" + shard + ".xml";
        }

        public void setProgressListener(
                ProgressListener progressListener) {
            mProgressListener = progressListener;
        }

        @Override
        protected File getResultFile(File reportDir) throws IOException {
            return new File(reportDir, name);
        }

        @Override
        public void testRunStarted(String runName, int testCount) {
            super.testRunStarted(runName, testCount);
            if (mProgressListener != null) {
                mProgressListener.setTestCountForOneShard(testCount);
            }
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            super.testEnded(test, testMetrics);
            if (mProgressListener != null) {
                mProgressListener.onTestFinished();
            }
        }
    }

    public static class ProgressListener {

        int knownShardCounts;

        int estimatedTestCount;

        int finishedTestCount;

        int shardCount;

        int knownTestCount;

        ILogger logger;

        public ProgressListener(int shardCount, ILogger logger) {
            this.shardCount = shardCount;
            this.logger = logger;
        }

        public synchronized void setTestCountForOneShard(int testCount) {
            knownShardCounts++;
            knownTestCount += testCount;
            estimatedTestCount = shardCount * (int) Math
                    .ceil((float) knownTestCount / knownShardCounts);
        }

        public synchronized void onTestFinished() {
            finishedTestCount++;
            logger.verbose("finished %d of estimated %d tests. %.2f%%", finishedTestCount,
                    estimatedTestCount,
                    estimatedTestCount == 0 ? 0 : 100f * finishedTestCount / estimatedTestCount);
        }
    }

    public static class ShardedTestParams implements Serializable {
        @NonNull private final String projectName;
        @NonNull private final DeviceConnector device;
        @NonNull private final String flavorName;
        @NonNull private final StaticTestData testData;
        @NonNull private final File resultsDir;
        @NonNull private final File coverageDir;
        @NonNull private final List<File> testedApks;
        @NonNull private final ILogger logger;
        @NonNull private final ShardProvider shardProvider;
        @NonNull private final BaseTestRunner.TestResult testResult;

        private final int timeoutInMs;
        private final ProgressListener progressListener;

        public ShardedTestParams(
                @NonNull DeviceConnector device,
                @NonNull String projectName,
                @NonNull String flavorName,
                @NonNull List<File> testedApks,
                @NonNull StaticTestData testData,
                @NonNull File resultsDir,
                @NonNull File coverageDir,
                int timeoutInMs,
                @NonNull ILogger logger,
                @NonNull ShardProvider shardProvider,
                ProgressListener progressListener,
                @NonNull BaseTestRunner.TestResult testResult) {
            this.projectName = projectName;
            this.device = device;
            this.flavorName = flavorName;
            this.resultsDir = resultsDir;
            this.coverageDir = coverageDir;
            this.testedApks = testedApks;
            this.testData = testData;
            this.timeoutInMs = timeoutInMs;
            this.logger = logger;
            this.shardProvider = shardProvider;
            this.progressListener = progressListener;
            this.testResult = testResult;
        }
    }
}
