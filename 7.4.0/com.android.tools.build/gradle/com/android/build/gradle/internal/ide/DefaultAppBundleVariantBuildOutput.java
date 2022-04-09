/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.builder.model.AppBundleVariantBuildOutput;
import java.io.File;
import java.io.Serializable;

/** Default implementation of the {@link AppBundleVariantBuildOutput}. */
public class DefaultAppBundleVariantBuildOutput
        implements AppBundleVariantBuildOutput, Serializable {

    @NonNull private final String name;
    @NonNull private final File bundleFile;
    @NonNull private final File apkFolder;

    public DefaultAppBundleVariantBuildOutput(
            @NonNull String name, @NonNull File bundleFile, @NonNull File apkFolder) {
        this.name = name;
        this.bundleFile = bundleFile;
        this.apkFolder = apkFolder;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public File getBundleFile() {
        return bundleFile;
    }

    @NonNull
    @Override
    public File getApkFolder() {
        return apkFolder;
    }
}
