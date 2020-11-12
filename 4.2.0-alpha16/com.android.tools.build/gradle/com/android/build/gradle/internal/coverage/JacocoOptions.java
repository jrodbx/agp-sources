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
import javax.inject.Inject;

public class JacocoOptions implements com.android.build.api.dsl.JacocoOptions {

    /** Default JaCoCo version. */
    public static final String DEFAULT_VERSION = "0.7.9";

    @Inject
    public JacocoOptions() {}

    @NonNull private String version = DEFAULT_VERSION;

    @Override
    @NonNull
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(@NonNull String version) {
        this.version = version;
    }
}
