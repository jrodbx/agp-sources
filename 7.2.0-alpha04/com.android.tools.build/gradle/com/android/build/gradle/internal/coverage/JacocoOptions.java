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

package com.android.build.gradle.internal.coverage;

import com.android.annotations.NonNull;
import com.android.build.api.dsl.TestCoverage;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class JacocoOptions implements com.android.build.api.dsl.JacocoOptions, TestCoverage {

    /** Default JaCoCo version. */
    public static final String DEFAULT_VERSION = "0.8.7";

    @Inject
    public JacocoOptions() {}

    @NonNull private String jacocoVersion = DEFAULT_VERSION;

    @Override
    @NonNull
    public String getVersion() {
        return jacocoVersion;
    }

    @Override
    public void setVersion(@NonNull String version) {
        this.jacocoVersion = version;
    }

    @NotNull
    @Override
    public String getJacocoVersion() {
        return jacocoVersion;
    }

    @Override
    public void setJacocoVersion(@NotNull String jacocoVersion) {
        this.jacocoVersion = jacocoVersion;
    }
}
