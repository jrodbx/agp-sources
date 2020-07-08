/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.profile;

import static com.google.common.base.Verify.verifyNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.analytics.Anonymizer;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configures and creates instances of {@link ProcessProfileWriter}.
 *
 * <p>There can be only one instance of {@link ProcessProfileWriter} per process (well class loader
 * to be exact). This instance can be configured initially before any calls to {@link
 * ThreadRecorder#get()} is made. An exception will be thrown if an attempt is made to configure the
 * instance of {@link ProcessProfileWriter} past this initialization window.
 */
public final class ProcessProfileWriterFactory {

    private static final Object LOCK = new Object();

    @GuardedBy("LOCK")
    @Nullable
    private static ProcessProfileWriterFactory sINSTANCE;

    @GuardedBy("this")
    @Nullable
    private ScheduledExecutorService mScheduledExecutorService = null;

    @GuardedBy("this")
    @Nullable
    private ILogger mLogger = null;

    @GuardedBy("this")
    @Nullable
    private ProcessProfileWriter processProfileWriter = null;

    @GuardedBy("this")
    private boolean enableChromeTracingOutput;

    private ProcessProfileWriterFactory() {}

    /**
     * Set up the the ProcessProfileWriter.
     *
     * <p>Idempotent for multi-project builds, where the arguments are ignored for subsequent calls.
     */
    public static void initialize(
            @NonNull File rootProjectDirectoryPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger,
            boolean enableChromeTracingOutput) {
        getFactory()
                .initializeInternal(
                        rootProjectDirectoryPath, gradleVersion, logger, enableChromeTracingOutput);
    }

    private synchronized void initializeInternal(
            @NonNull File rootProjectDirectoryPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger,
            boolean enableChromeTracingOutput) {
        if (isInitialized()) {
            return;
        }
        this.mLogger = logger;
        this.enableChromeTracingOutput = enableChromeTracingOutput;
        ProcessProfileWriter recorder = get();
        setGlobalProperties(recorder, rootProjectDirectoryPath, gradleVersion, logger);
    }

    private static void setGlobalProperties(
            @NonNull ProcessProfileWriter recorder,
            @NonNull File projectPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger) {
        recorder.getProperties()
                .setOsName(Strings.nullToEmpty(System.getProperty("os.name")))
                .setOsVersion(Strings.nullToEmpty(System.getProperty("os.version")))
                .setJavaVersion(Strings.nullToEmpty(System.getProperty("java.version")))
                .setJavaVmVersion(Strings.nullToEmpty(System.getProperty("java.vm.version")))
                .setMaxMemory(Runtime.getRuntime().maxMemory())
                .setGradleVersion(Strings.nullToEmpty(gradleVersion));

        String anonymizedProjectId;
        try {
            anonymizedProjectId = Anonymizer.anonymizeUtf8(logger, projectPath.getAbsolutePath());
        } catch (IOException e) {
            anonymizedProjectId = "*ANONYMIZATION_ERROR*";
        }
        recorder.getProperties().setProjectId(anonymizedProjectId);
    }

    public static ProcessProfileWriterFactory getFactory() {
        synchronized (LOCK) {
            if (sINSTANCE == null) {
                sINSTANCE = new ProcessProfileWriterFactory();
            }
            return sINSTANCE;
        }
    }

    synchronized boolean isInitialized() {
        return processProfileWriter != null;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public static Future<Void> shutdownAndMaybeWrite(@Nullable Path outputFile) {
        return getFactory().shutdownAndMaybeWriteInternal(outputFile);
    }

    @VisibleForTesting
    public static void initializeForTests() {
        AnalyticsSettings.setInstanceForTest(new AnalyticsSettingsData());
        shutdownAndMaybeWrite(null);
        initialize(
                new File("fake/path/to/test_project"),
                "2.10",
                new StdLogger(StdLogger.Level.VERBOSE),
                false);
    }


    private static void initializeAnalytics(@NonNull ILogger logger,
            @NonNull ScheduledExecutorService eventLoop) {
        AnalyticsSettings.initialize(logger);
        UsageTracker.initialize(eventLoop);
        UsageTracker.setMaxJournalTime(10, TimeUnit.MINUTES);
        UsageTracker.setMaxJournalSize(1000);
    }

    @NonNull
    private synchronized Future<Void> shutdownAndMaybeWriteInternal(@Nullable Path outputFile) {
        Future<Void> shutdownAction;
        if (isInitialized()) {
            ProcessProfileWriter processProfileWriter = verifyNotNull(this.processProfileWriter);
            if (outputFile == null) {
                // Write analytics files in another thread as it might involve parsing manifest files.
                shutdownAction =
                        getScheduledExecutorService()
                                .submit(
                                        () -> {
                                            processProfileWriter.finish();
                                            deinitializeAnalytics();
                                            return null;
                                        });
            } else {
                // If writing a GradleBuildProfile file for Benchmarking, go ahead and block
                processProfileWriter.finishAndWrite(outputFile);
                deinitializeAnalytics();
                shutdownAction = CompletableFuture.completedFuture(null);
            }
        } else {
            shutdownAction = CompletableFuture.completedFuture(null);
        }
        this.processProfileWriter = null;
        return shutdownAction;
    }

    synchronized ProcessProfileWriter get() {
        if (processProfileWriter == null) {
            if (mLogger == null) {
                mLogger = new StdLogger(StdLogger.Level.INFO);
            }
            initializeAnalytics(mLogger, getScheduledExecutorService());
            processProfileWriter = new ProcessProfileWriter(enableChromeTracingOutput);
        }
        return processProfileWriter;
    }


    private synchronized ScheduledExecutorService getScheduledExecutorService() {
        if (mScheduledExecutorService == null) {
            mScheduledExecutorService = Executors.newScheduledThreadPool(1);
        }
        return mScheduledExecutorService;
    }

    private synchronized void deinitializeAnalytics() {
        if (mScheduledExecutorService != null) {
            UsageTracker.deinitialize();
            mScheduledExecutorService.shutdown();
            mScheduledExecutorService = null;
        }
    }
}
