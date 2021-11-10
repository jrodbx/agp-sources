/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ide.common.process;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple implementation of ProcessExecutor, using the standard Java Process(Builder) API.
 */
public class DefaultProcessExecutor implements ProcessExecutor {

    private final ILogger mLogger;

    public DefaultProcessExecutor(ILogger logger) {
        mLogger = logger;
    }

    /**
     * Asynchronously submits a process for execution.
     *
     * @param processInfo process execution information
     * @param processOutputHandler handler for process output
     * @return a future that will be complete when the process finishes
     */
    @Override
    @NonNull
    public ListenableFuture<ProcessResult> submit(@NonNull ProcessInfo processInfo,
            @NonNull final ProcessOutputHandler processOutputHandler) {
        final List<String> command = buildCommand(processInfo);
        mLogger.info("command: " + Joiner.on(' ').join(command));

        final SettableFuture<ProcessResult> result = SettableFuture.create();

        try {
            // Launch the command line process.
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(processInfo));

            Map<String, Object> envVariableMap = processInfo.getEnvironment();

            if (!envVariableMap.isEmpty()) {
                Map<String, String> env = processBuilder.environment();
                for (Map.Entry<String, Object> entry : envVariableMap.entrySet()) {
                    env.put(entry.getKey(), entry.getValue().toString());
                }
            }

            // Start the process.
            Process process = processBuilder.start();

            // Grab the output, and the exit code.
            final ProcessOutput output = processOutputHandler.createOutput();
            ListenableFuture<Integer> outputFuture = grabProcessOutput(process, output);

            Futures.addCallback(
                    outputFuture,
                    new FutureCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer exit) {
                            try {
                                output.close();
                                processOutputHandler.handleOutput(output);
                                result.set(new ProcessResultImpl(command, exit));
                            } catch (Exception e) {
                                result.set(new ProcessResultImpl(command, e));
                            }
                        }

                        @Override
                        public void onFailure(@Nullable Throwable t) {
                            result.set(new ProcessResultImpl(command, t));
                        }
                    },
                    MoreExecutors.directExecutor());
        } catch (Exception e) {
            result.set(new ProcessResultImpl(command, e));
        }

        return result;
    }

    @NonNull
    @Override
    public ProcessResult execute(@NonNull ProcessInfo processInfo,
            @NonNull ProcessOutputHandler processOutputHandler) {
        try {
            return submit(processInfo, processOutputHandler).get();
        } catch (Exception e) {
            return new ProcessResultImpl(buildCommand(processInfo), e);
        }
    }

    /**
     * Get the stderr/stdout outputs of a process and return when the process is done.
     * Both <b>must</b> be read or the process will block on windows.
     *
     * @param process the process to get the output from
     * @param output the {@link ProcessOutput} where to send the output; note that on Windows
     * capturing the output is not optional
     * @return a future with the the process return code
     */
    private static ListenableFuture<Integer> grabProcessOutput(@NonNull final Process process,
            @NonNull final ProcessOutput output) {
        final SettableFuture<Integer> result = SettableFuture.create();
        final AtomicReference<Throwable> exceptionHolder = new AtomicReference<>();

        /*
         * It looks like on windows process#waitFor() can return before the thread have filled the
         * arrays, so we wait for both threads and the process itself.
         *
         * To make sure everything is complete before setting the future, the thread handling
         * "out" will wait for all its input to be read, will wait for the "err" thread to finish
         * and will wait for the process to finish. Only after all three are done will it set
         * the future and terminate.
         *
         * This means that the future will be set while the "out" thread is still running, but
         * no output is pending and the process has already finished.
         */
        final Thread threadErr = new Thread("stderr") {
            @Override
            public void run() {
                InputStream stderr = process.getErrorStream();
                OutputStream stream = output.getErrorOutput();

                try {
                    ByteStreams.copy(stderr, stream);
                    stream.flush();
                } catch (IOException e) {
                    exceptionHolder.compareAndSet(null, e);
                }
            }
        };

        Thread threadOut = new Thread("stdout") {
            @Override
            public void run() {
                InputStream stdout = process.getInputStream();
                OutputStream stream = output.getStandardOutput();

                try {
                    ByteStreams.copy(stdout, stream);
                    stream.flush();
                } catch (Throwable e) {
                    exceptionHolder.compareAndSet(null, e);
                }

                try {
                    threadErr.join();
                    int processResult = process.waitFor();
                    if (exceptionHolder.get() != null) {
                        result.setException(exceptionHolder.get());
                    }

                    result.set(processResult);
                    output.close();
                } catch (Throwable e) {
                    result.setException(e);
                }
            }
        };

        threadErr.start();
        threadOut.start();

        return result;
    }

    /**
     * Constructs the command to execute the given process info.
     *
     * @param processInfo the process info to execute
     * @return a list with the executable and its arguments
     */
    @NonNull
    private static List<String> buildCommand(@NonNull ProcessInfo processInfo) {
        List<String> command = Lists.newArrayList();
        command.add(processInfo.getExecutable());
        command.addAll(processInfo.getArgs());
        return command;
    }
}
