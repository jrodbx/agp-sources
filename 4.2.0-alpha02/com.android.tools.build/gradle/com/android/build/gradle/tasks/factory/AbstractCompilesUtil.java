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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.JavaVersion;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * Common code for configuring {@link AbstractCompile} instances.
 */
public class AbstractCompilesUtil {

    public static final String ANDROID_APT_PLUGIN_NAME = "com.neenbedankt.android-apt";

    @NonNull
    @VisibleForTesting
    public static JavaVersion getDefaultJavaVersion(@NonNull String compileSdkVersion) {
        String currentJdkVersion = System.getProperty("java.specification.version");
        final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        Integer compileSdkLevel = (hash == null ? null : hash.getFeatureLevel());

        JavaVersion javaVersionToUse;
        if (compileSdkLevel == null) {
            javaVersionToUse = JavaVersion.VERSION_1_6;
        } else {
            if (0 < compileSdkLevel && compileSdkLevel <= 20) {
                javaVersionToUse = JavaVersion.VERSION_1_6;
            } else if (21 <= compileSdkLevel && compileSdkLevel < 24) {
                javaVersionToUse = JavaVersion.VERSION_1_7;
            } else {
                javaVersionToUse = JavaVersion.VERSION_1_7;
            }
        }

        JavaVersion jdkVersion = JavaVersion.toVersion(currentJdkVersion);

        if (jdkVersion.compareTo(javaVersionToUse) < 0) {
            Logging.getLogger(AbstractCompilesUtil.class).warn(
                    "Default language level for compileSdkVersion '{}' is " +
                            "{}, but the JDK used is {}, so the JDK language level will be used.",
                    compileSdkVersion,
                    javaVersionToUse,
                    jdkVersion);
            javaVersionToUse = jdkVersion;
        }
        return javaVersionToUse;
    }
}
