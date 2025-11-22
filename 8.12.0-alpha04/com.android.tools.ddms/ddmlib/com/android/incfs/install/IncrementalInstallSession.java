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

package com.android.incfs.install;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Used to create a n incremental package installation session in which the blocks of the installed
 * APKs are streamed to the device when they need to be read.
 *
 * <p>
 *
 * <p>Incremental installation uses the Incremental Filesystem (IncFs) to allow installation to
 * complete before the entire APK has been streamed to the device. This class can be used to start
 * an incremental installation, control which blocks of the APK are allowed to be streamed to the
 * device, and wait until APK installation has completed and/or until all blocks have been streamed
 * to the device.
 *
 * <p>
 *
 * <p>The connection to the device stays opened until the session is closed, the install fails, the
 * device connection times-out, or an exception occurs while processing block requests from the
 * device.
 *
 * <p>
 *
 * <p>An example use of this class is:
 *
 * <pre>{@code try (IncrementalInstallSession session = new IncrementalInstallSession.Builder()
 *               .addApk(mTestApk0, mTestSignature0)
 *               .build()) {
 *         // Start the session on a separate thread.
 *         session.start(Executors.newCachedThreadPool(), mockDevice.getFactory());
 *
 *         // Wait a maximum of 45 seconds for the install to finish.
 *         session.waitForInstallCompleted(45, TimeUnit.SECONDS);
 * }</pre>
 */
public class IncrementalInstallSession implements AutoCloseable {
    public static class Builder {
        private final List<Path> mApks = new ArrayList<>();
        private final List<Path> mSignatures = new ArrayList<>();
        private final List<String> mArgs = new ArrayList<>();
        private IBlockFilter mFilter = (PendingBlock) -> true;
        private IBlockTransformer mTransformer = (PendingBlock block) -> block;
        private ILogger mLogger = new NullLogger();
        private long mResponseTimeoutNs = 0L;
        private boolean mReinstall;

        public Builder() {}

        /**
         * Adds an APK to set of APKs to be installed. The app must be signed with APK Signature
         * Scheme v4.
         *
         * @param apk the apk to install
         * @param signature the v4 signature of the apk
         */
        public Builder addApk(@NonNull Path apk, @NonNull Path signature) {
            mApks.add(apk);
            mSignatures.add(signature);
            return this;
        }

        /**
         * Adds extra arguments to pass to the installation. See 'adb shell pm install --help' for
         * available options.
         *
         * @param extraArgs the extra arguments to pass to the installation
         */
        public Builder addExtraArgs(@NonNull String... extraArgs) {
            mArgs.addAll(Arrays.asList(extraArgs));
            return this;
        }

        /**
         * Sets whether re-install of an app should be performed.
         *
         * @param reinstall whether re-install of an app should be performed
         */
        public Builder setAllowReinstall(boolean reinstall) {
            mReinstall = reinstall;
            return this;
        }

        /**
         * Sets the callback used to determine whether a block of data that must be delivered to the
         * device should be sent to the device.
         *
         * @param filter the callback
         */
        public Builder setBlockFilter(@NonNull IBlockFilter filter) {
            mFilter = filter;
            return this;
        }

        /**
         * Sets the callback used to transform the block of data before sending.
         *
         * @param transformer the callback
         */
        public Builder setBlockTransformer(@NonNull IBlockTransformer transformer) {
            mTransformer = transformer;
            return this;
        }

        /**
         * Sets the logger interface used to log errors, warnings, and information regarding the
         * incremental install session.
         *
         * @param logger the callback
         */
        public Builder setLogger(@NonNull ILogger logger) {
            mLogger = logger;
            return this;
        }

        /**
         * Sets the maximum amount of time during which no response from the device is allowed when
         * invoking {@link #waitForInstallCompleted(long, TimeUnit)} and {@link
         * #waitForServingCompleted(long, TimeUnit)}.
         *
         * @param timeout the maximum amount of time during which no response from the device is
         *     allowed. A value of 0 allows the specified methods to wait indefinitely for the next
         *     response from the device
         * @param maxTimeUnits units for non-zero {@code timeout}
         */
        public Builder setResponseTimeout(long timeout, @NonNull TimeUnit maxTimeUnits) {
            mResponseTimeoutNs = maxTimeUnits.toNanos(timeout);
            return this;
        }

        /**
         * Builds and starts the streaming install session.
         *
         * @throws IOException if the apk or signature file are not able to be read or are invalid.
         */
        public IncrementalInstallSession build() throws IOException {
            final ArrayList<String> commandBuilder = new ArrayList<>();
            commandBuilder.add("install-incremental");
            if (mReinstall) {
                commandBuilder.add("-r");
            }
            commandBuilder.addAll(mArgs);

            final ArrayList<StreamingApk> apkArguments = new ArrayList<>();
            for (int i = 0; i < mApks.size(); i++) {
                final Path apk = mApks.get(i);
                final Path signature = mSignatures.get(i);

                // Argument format [remoteApkName]:[apkSize]:[apkId]:[signatureBase64]:[mode]
                // remoteApkName   - name that will represent the apk while its being installed
                // apkSize         - the number of bytes in the apk file
                // apkId           - unique identifier for the apk during the install (index of the
                //                   apk in the mApkArguments)
                // signatureBase64 - bas64 representation of the signing info
                // mode            - Block streaming mode (1 for tree and data streaming).
                final StreamingApk apkArgument =
                        StreamingApk.generate(apk, signature, mFilter, mLogger);
                apkArguments.add(apkArgument);
                commandBuilder.add(
                        String.format(
                                Locale.US,
                                "arg%1$d.apk:%2$d:%1$d:%3$s:1",
                                i,
                                Files.size(apk),
                                apkArgument.getSignatureBase64()));
            }

            return new IncrementalInstallSession(
                    commandBuilder.toArray(new String[0]),
                    apkArguments,
                    mResponseTimeoutNs,
                    mTransformer,
                    mLogger);
        }
    }

    @NonNull private final String[] mCommandArgs;
    @NonNull private final ArrayList<StreamingApk> mApks;
    private final long mResponseTimeoutNs;

    @NonNull private final IBlockTransformer mTransformer;
    @NonNull private final ILogger mLogger;

    private IncrementalInstallSessionImpl mImpl;

    private IncrementalInstallSession(
            @NonNull String[] commandArgs,
            @NonNull ArrayList<StreamingApk> apks,
            long responseTimeoutNs,
            @NonNull IBlockTransformer transformer,
            @NonNull ILogger logger) {
        mCommandArgs = commandArgs;
        mApks = apks;
        mResponseTimeoutNs = responseTimeoutNs;
        mTransformer = transformer;
        mLogger = logger;
    }

    /**
     * Starts the streaming install session.
     *
     * @param executor the executor on which to start handling block requests from the device
     * @param conFactory the device connection factory
     * @throws IOException if an error occurs while communicating with the device
     */
    public synchronized IncrementalInstallSession start(
            @NonNull Executor executor, @NonNull IDeviceConnection.Factory conFactory)
            throws IOException {
        if (mImpl != null) {
            throw new IllegalStateException("Session cannot be started multiple time.");
        }

        final IDeviceConnection con = conFactory.connectToService("package", mCommandArgs);
        mImpl =
                new IncrementalInstallSessionImpl(
                        con, mApks, mResponseTimeoutNs, mTransformer, mLogger);
        mImpl.execute(executor);
        return this;
    }

    /**
     * Blocks the current thread until all APKs have been successfully installed. Data serving may
     * finish before or after the install succeeds.
     *
     * @param timeout the maximum amount of time to wait for the installs to finish. A value of 0
     *     will cause this method to wait indefinitely.
     * @param units units for non-zero {@code timeout}
     * @throws IOException if wait times out, an APK fails to be installed, or an exception occurs
     *     while handling block requests.
     */
    public void waitForInstallCompleted(long timeout, @NonNull TimeUnit units)
            throws IOException, InterruptedException {
        mImpl.waitForInstallCompleted(timeout, units);
    }

    /**
     * Blocks the current thread until all APK data has been streamed to the device. Installation
     * may finishes before or after serving is completed.
     *
     * @param timeout the maximum amount of time to wait for serving to finish. A value of 0 will
     *     cause this method to wait indefinitely.
     * @param units units for non-zero {@code timeout}
     * @throws IOException if wait times out, an APK fails to be streamed, or an exception occurs
     *     while handling block requests.
     */
    public void waitForServingCompleted(long timeout, @NonNull TimeUnit units)
            throws IOException, InterruptedException {
        mImpl.waitForServingCompleted(timeout, units);
    }

    /**
     * Blocks the current thread until either APK data has been streamed to the device or
     * the installation is finished.
     *
     * @param timeout the maximum amount of time to wait. A value of 0 will
     *     cause this method to wait indefinitely.
     * @param units units for non-zero {@code timeout}
     * @throws IOException if wait times out, an APK fails to be installed, or an exception occurs
     *     while handling block requests.
     */
    public void waitForAnyCompletion(long timeout, @NonNull TimeUnit units)
            throws IOException, InterruptedException {
        mImpl.waitForAnyCompletion(timeout, units);
    }

    /** Cancels communication with the device. */
    @Override
    public void close() {
        try (IncrementalInstallSessionImpl impl = mImpl) {}
    }

    // Default implementation of a logger.
    private static class NullLogger implements ILogger {

        @Override
        public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
            // ignored
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            // ignored
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            // ignored
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            // ignored
        }
    }
}
