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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.ide.common.process.BaseProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutput;
import com.android.utils.LineCollector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Abstraction to the split-select tool.
 */
public class SplitSelectTool {

    /**
     * Runs the native split-select tool given the main APK, the list of pure split APKs and the
     * targeted device characteristics. The tool should select the minimum set of the split APKs
     * that should be installed in that device.
     *
     * @param processExecutor a reusable process executor instance.
     * @param splitSelectExec the pointer to the split-select tool in this machine.
     * @param deviceConfig the targed device
     * @param mainApkPath the path the main application APK.
     * @param splitApksPath the path to all the pure split APKs.
     * @return the set of APK to successfully install the application on the targeted device.
     * @throws ProcessException
     */
    @NonNull
    public static List<String> splitSelect(
            @NonNull ProcessExecutor processExecutor,
            @NonNull File splitSelectExec,
            @NonNull String deviceConfig,
            @NonNull String mainApkPath,
            @NonNull Collection<String> splitApksPath) throws ProcessException {

        ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
        processBuilder.setExecutable(splitSelectExec);

        processBuilder.addArgs("--target", deviceConfig);

        // specify the main APK parameter
        processBuilder.addArgs("--base", mainApkPath);

        // and the splits...
        for (String apkPath : splitApksPath) {
            processBuilder.addArgs("--split", apkPath);
        }
        SplitSelectOutputHandler outputHandler =
                new SplitSelectOutputHandler();

        processExecutor.execute(processBuilder.createProcess(), outputHandler)
                .rethrowFailure()
                .assertNormalExitValue();

        return outputHandler.getResultApks();
    }


    private static class SplitSelectOutputHandler extends BaseProcessOutputHandler {

        private final List<String> resultApks = new ArrayList<String>();

        @NonNull
        public List<String> getResultApks() {
            return resultApks;
        }

        @Override
        public void handleOutput(@NonNull ProcessOutput processOutput) throws ProcessException {
            if (processOutput instanceof BaseProcessOutput) {
                BaseProcessOutput impl = (BaseProcessOutput) processOutput;
                LineCollector lineCollector = new LineCollector();
                impl.processStandardOutputLines(lineCollector);
                resultApks.addAll(lineCollector.getResult());
                String stderr = impl.getErrorOutputAsString();
                if (!stderr.isEmpty()) {
                    throw new RuntimeException("split-select:" + stderr);
                }
            } else {
                throw new IllegalArgumentException(
                        "processOutput was not created by this handler.");
            }
        }
    }
}
