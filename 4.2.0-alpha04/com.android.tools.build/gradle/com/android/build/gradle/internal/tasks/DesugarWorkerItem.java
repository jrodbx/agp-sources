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

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.core.DesugarProcessArgs;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;

public final class DesugarWorkerItem {

    private static final String DESUGAR_MAIN = "com.google.devtools.build.android.desugar.Desugar";
    private static final Logger LOGGER = Logging.getLogger(DesugarWorkerItem.class);

    @NonNull private final Path java8LangSupportJar;
    @NonNull private final DesugarProcessArgs args;
    @NonNull private final Path lambdaTmpDir;

    public DesugarWorkerItem(
            @NonNull Path java8LangSupportJar,
            @NonNull DesugarProcessArgs args,
            @NonNull Path lambdaTmpDir) {
        this.java8LangSupportJar = java8LangSupportJar;
        this.args = args;
        this.lambdaTmpDir = lambdaTmpDir;
    }

    public void configure(WorkerConfiguration workerConfiguration) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "desugar configuring in {}", ManagementFactory.getRuntimeMXBean().getName());
        }
        workerConfiguration.setIsolationMode(IsolationMode.PROCESS);
        workerConfiguration.classpath(ImmutableList.of(java8LangSupportJar.toFile()));
        workerConfiguration.forkOptions(
                javaForkOptions ->
                        javaForkOptions.setJvmArgs(
                                ImmutableList.of(
                                        "-Xmx64m",
                                        "-Djdk.internal.lambda.dumpProxyClasses="
                                                + lambdaTmpDir.toString())));

        boolean isWindows = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;
        workerConfiguration.setParams(args.getArgs(isWindows));
    }

    public static class DesugarActionParams implements Serializable {
        private List<String> args;

        public DesugarActionParams(List<String> args) {
            this.args = args;
        }

        public List<String> getArgs() {
            return args;
        }
    }

    /**
     * Action running in a separate process to desugar java8 byte codes into java7 compliant byte
     * codes.
     */
    public static class DesugarAction implements Runnable {
        @NonNull private final List<String> args;

        @Inject
        public DesugarAction(@NonNull DesugarActionParams params) {
            this.args = params.getArgs();
        }

        @Override
        public void run() {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "New desugar in {}", ManagementFactory.getRuntimeMXBean().getName());
                }
                Class<?> clazz = Class.forName(DESUGAR_MAIN);
                Method mainMethod = clazz.getMethod("main", String[].class);
                mainMethod.setAccessible(true);

                mainMethod.invoke(null, (Object) args.toArray(new String[0]));
            } catch (Exception e) {
                LOGGER.error("Error while running desugar ", e);
            }
        }
    }
}
