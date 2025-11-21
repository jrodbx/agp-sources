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

package com.android.build.gradle.internal.process;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import org.gradle.api.Action;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;

/**
 * Implementation of JavaProcessExecutor that uses Gradle's mechanism to execute external java
 * processes.
 */
public class GradleJavaProcessExecutor implements JavaProcessExecutor {

    @NonNull private final Function<Action<? super JavaExecSpec>, ExecResult> execOperations;

    public GradleJavaProcessExecutor(
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> execOperations) {
        this.execOperations = execOperations;
    }

    @NonNull
    @Override
    public ProcessResult execute(
            @NonNull JavaProcessInfo javaProcessInfo,
            @NonNull ProcessOutputHandler processOutputHandler) {
        LoggerWrapper.getLogger(GradleJavaProcessExecutor.class)
                .info("Executing java process: ", javaProcessInfo.toString());
        ProcessOutput output = processOutputHandler.createOutput();

        ExecResult result;
        try {
            result = execOperations.apply(new ExecAction(javaProcessInfo, output));
        } finally {
            try {
                output.close();
            } catch (IOException e) {
                LoggerWrapper.getLogger(GradleJavaProcessExecutor.class)
                        .warning("Exception while closing sub process streams", e);
            }
        }

        try {
            processOutputHandler.handleOutput(output);
        } catch (ProcessException e) {
            return new OutputHandlerFailedGradleProcessResult(e);
        }

        return new GradleProcessResult(result, javaProcessInfo);
    }

    private static class ExecAction implements Action<JavaExecSpec> {

        @NonNull private final JavaProcessInfo javaProcessInfo;

        @NonNull private final ProcessOutput processOutput;

        private ExecAction(
                @NonNull JavaProcessInfo javaProcessInfo, @NonNull ProcessOutput processOutput) {
            this.javaProcessInfo = javaProcessInfo;
            this.processOutput = processOutput;
        }

        @Override
        public void execute(JavaExecSpec javaExecSpec) {
            javaExecSpec.classpath(new File(javaProcessInfo.getClasspath()));
            javaExecSpec.getMainClass().set(javaProcessInfo.getMainClass());
            javaExecSpec.args(javaProcessInfo.getArgs());
            javaExecSpec.jvmArgs(javaProcessInfo.getJvmArgs());
            javaExecSpec.environment(javaProcessInfo.getEnvironment());
            javaExecSpec.setStandardOutput(processOutput.getStandardOutput());
            javaExecSpec.setErrorOutput(processOutput.getErrorOutput());

            // we run by default in headless mode, so the forked JVM doesn't steal focus.
            javaExecSpec.systemProperty("java.awt.headless", "true");

            // we want the caller to be able to do its own thing.
            javaExecSpec.setIgnoreExitValue(true);
        }
    }
}
