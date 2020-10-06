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

package com.android.builder.core;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.BaseProcessOutputHandler;
import com.android.ide.common.process.CachedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.LineCollector;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse an APK with aapt to gather information
 */
public class ApkInfoParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^package: name='([^']+)' versionCode='([0-9]*)' versionName='([^']*)'.*$");

    @NonNull private final File aapt2File;
    @NonNull
    private final ProcessExecutor mProcessExecutor;

    /**
     * Information about an APK
     */
    public static final class ApkInfo {
        @NonNull
        private final String mPackageName;
        @Nullable
        private final Integer mVersionCode;
        @Nullable
        private final String mVersionName;

        private ApkInfo(
                @NonNull String packageName,
                @Nullable Integer versionCode,
                @Nullable String versionName) {
            mPackageName = packageName;
            mVersionCode = versionCode;
            mVersionName = versionName;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @Nullable
        public Integer getVersionCode() {
            return mVersionCode;
        }

        @Nullable
        public String getVersionName() {
            return mVersionName;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("packageName", mPackageName)
                    .add("versionCode", mVersionCode)
                    .add("versionName", mVersionName)
                    .toString();
        }
    }

    /**
     * Constructs a new parser
     *
     * @param aapt2File the AAPT2 executable file or the directory containing it.
     * @param processExecutor a process executor to call AAPT2
     */
    public ApkInfoParser(@NonNull File aapt2File, @NonNull ProcessExecutor processExecutor) {
        if (aapt2File.isDirectory()) {
            this.aapt2File = new File(aapt2File, SdkConstants.FN_AAPT2);
        } else {
            this.aapt2File = aapt2File;
        }

        if (!this.aapt2File.getName().toLowerCase(Locale.ENGLISH).startsWith("aapt2")) {
            throw new IllegalStateException("AAPT is deprecated now, AAPT2 should be used instead");
        }

        mProcessExecutor = processExecutor;
    }

    /**
     * Computes and returns the info for an APK
     *
     * @param apkFile the APK to parse
     * @return a non-null ApkInfo object.
     * @throws ProcessException when aapt failed to execute
     */
    @NonNull
    public ApkInfo parseApk(@NonNull File apkFile) throws ProcessException {

        if (!aapt2File.isFile()) {
            throw new IllegalStateException(
                    "aapt is missing from location: " + aapt2File.getAbsolutePath());
        }

        return getApkInfo(getAaptOutput(apkFile));
    }

    /**
     * Parses the aapt output and returns an ApkInfo object.
     * @param aaptOutput the aapt output as a list of lines.
     * @return an ApkInfo object.
     */
    @VisibleForTesting
    @NonNull
    static ApkInfo getApkInfo(@NonNull List<String> aaptOutput) {

        String pkgName = null, versionCode = null, versionName = null;

        for (String line : aaptOutput) {
            Matcher m = PATTERN.matcher(line);
            if (m.matches()) {
                pkgName = m.group(1);
                versionCode = m.group(2);
                versionName = m.group(3);
                break;
            }
        }

        if (pkgName == null) {
            throw new RuntimeException("Failed to find apk information with aapt");
        }

        Integer intVersionCode = null;
        try {
            intVersionCode = Integer.parseInt(versionCode);
        } catch(NumberFormatException ignore) {
            // leave the version code as null.
        }

        return new ApkInfo(pkgName, intVersionCode, versionName);
    }

    /** Returns the full 'aapt2 dump badging' output for the given APK. */
    @NonNull
    public List<String> getAaptOutput(@NonNull File apkFile) throws ProcessException {
        return invokeAaptWithParameters(apkFile, "dump", "badging");
    }

    @NonNull
    public List<String> getManifestContent(@NonNull File apkFile) throws ProcessException {
        return invokeAaptWithParameters(
                apkFile, "dump", "xmltree", "--file", "AndroidManifest.xml");
    }

    /** Returns the configurations (e.g. languages) in the APK. */
    @NonNull
    public List<String> getConfigurations(@NonNull File apkFile) throws ProcessException {
        return invokeAaptWithParameters(apkFile, "dump", "configurations");
    }

    private List<String> invokeAaptWithParameters(@NonNull File apkFile, String... parameters)
            throws ProcessException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        builder.setExecutable(aapt2File);
        builder.addArgs(parameters);
        builder.addArgs(apkFile.getPath());

        CachedProcessOutputHandler processOutputHandler = new CachedProcessOutputHandler();

        // b/129476626 AAPT2 dump will result 1 instead of 0. Once fixed add back the
        // '.assertNormalExitValue()'
        mProcessExecutor.execute(builder.createProcess(), processOutputHandler).rethrowFailure();

        BaseProcessOutputHandler.BaseProcessOutput output = processOutputHandler.getProcessOutput();

        LineCollector lineCollector = new LineCollector();
        output.processStandardOutputLines(lineCollector);

        return lineCollector.getResult();
    }
}
