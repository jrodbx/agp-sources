/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ddmlib.testrunner;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IShellEnabledDevice;
import com.android.support.AndroidxName;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.util.List;

/** Runs an instrumented Android test using the adb command and AndroidTestOrchestrator. */
public class AndroidTestOrchestratorRemoteAndroidTestRunner extends RemoteAndroidTestRunner {
    private static final AndroidxName SERVICES_APK_PACKAGE =
            AndroidxName.of("android.support.test.services.");
    private static final AndroidxName SHELL_MAIN_CLASS =
            AndroidxName.of("android.support.test.services.shellexecutor.", "ShellMain");
    private static final AndroidxName ORCHESTRATOR_PACKAGE =
            AndroidxName.of("android.support.test.orchestrator.");
    private static final AndroidxName ORCHESTRATOR_CLASS =
            AndroidxName.of("android.support.test.orchestrator.", "AndroidTestOrchestrator");

    /** Whether to use new ATSL names. */
    private final boolean useAndroidx;

    public AndroidTestOrchestratorRemoteAndroidTestRunner(
            @NonNull String applicationId,
            @Nullable String instrumentationRunner,
            @NonNull IShellEnabledDevice device,
            boolean useAndroidx) {
        super(applicationId, instrumentationRunner, device);
        this.useAndroidx = useAndroidx;
    }

    @NonNull
    @Override
    public String getAmInstrumentCommand() {
        List<String> adbArgs = Lists.newArrayList();

        adbArgs.add("CLASSPATH=$(pm path " + getPackageName(SERVICES_APK_PACKAGE) + ")");
        adbArgs.add("app_process / " + getClassName(SHELL_MAIN_CLASS));

        adbArgs.add("am");
        adbArgs.add("instrument");
        adbArgs.add("-r");
        adbArgs.add("-w");

        adbArgs.add("-e");
        adbArgs.add("targetInstrumentation");
        adbArgs.add(getRunnerPath());

        adbArgs.add(getRunOptions());
        adbArgs.add(getArgsCommand());

        adbArgs.add(getPackageName(ORCHESTRATOR_PACKAGE) + "/" + getClassName(ORCHESTRATOR_CLASS));

        return Joiner.on(' ').join(adbArgs);
    }

    @NonNull
    private String getPackageName(AndroidxName aPackage) {
        String result = useAndroidx ? aPackage.newName() : aPackage.oldName();
        result = result.substring(0, result.length() - 1); // Remove the trailing dot.
        return result;
    }

    @NonNull
    private String getClassName(@NonNull AndroidxName name) {
        return useAndroidx ? name.newName() : name.oldName();
    }

    @Override
    public void setCoverageReportLocation(String reportPath) {
        addInstrumentationArg("coverageFilePath", reportPath);
    }

    @Override
    public CoverageOutput getCoverageOutputType() {
        return CoverageOutput.DIR;
    }
}
