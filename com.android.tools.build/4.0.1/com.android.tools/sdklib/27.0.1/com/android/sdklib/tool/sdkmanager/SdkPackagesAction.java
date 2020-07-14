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
package com.android.sdklib.tool.sdkmanager;

import com.android.annotations.NonNull;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public abstract class SdkPackagesAction extends SdkAction {
    private static final String PKG_FILE_ARG = "--package_file=";

    private List<String> mPackages = new ArrayList<>();

    SdkPackagesAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    protected boolean applyPackageOperation(
            @NonNull PackageOperation operation, @NonNull ProgressIndicator progress) {
        return operation.prepare(progress.createSubProgress(0.5))
                && operation.complete(progress.createSubProgress(1));
    }

    @Override
    boolean consumeArgument(@NonNull String arg, @NonNull ProgressIndicator progress) {
        if (arg.startsWith(PKG_FILE_ARG)) {
            String packageFile = arg.substring(PKG_FILE_ARG.length());
            try {
                mPackages.addAll(
                        Files.readAllLines(mSettings.getFileSystem().getPath(packageFile)));
            } catch (IOException e) {
                progress.logWarning(
                        String.format(
                                "Invalid package file \"%s\" threw exception:%n%s%n",
                                packageFile, e));
            }
        } else if (!arg.startsWith("--")) {
            mPackages.add(arg);
        } else {
            return false;
        }
        return true;
    }

    @NonNull
    public List<String> getPaths(@NonNull RepoManager mgr) {
        return mPackages;
    }
}
