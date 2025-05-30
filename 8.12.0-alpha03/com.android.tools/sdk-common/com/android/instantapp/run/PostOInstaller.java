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
package com.android.instantapp.run;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Installer for Instant Apps in Post O devices. It receives a list of apks to install and installs
 * them with "ephemeral" flag.
 */
class PostOInstaller implements InstantAppSideLoader.Installer {
    @NonNull private final List<File> myApks;
    @NonNull private final RunListener myListener;

    PostOInstaller(@NonNull List<File> apks, @NonNull RunListener listener) {
        myApks = apks;
        myListener = listener;
    }

    @Override
    public void install(@NonNull IDevice device) throws InstantAppRunException {
        List<String> myInstallOptions = Lists.newArrayList("-t", "--ephemeral");
        myListener.logMessage(
                "Running adb command: \"" + getAdbInstallCommand(myApks, myInstallOptions) + "\"",
                null);

        try {
            device.installPackages(myApks, true, myInstallOptions, 5, TimeUnit.MINUTES);
        } catch (InstallException e) {
            throw new InstantAppRunException(InstantAppRunException.ErrorType.INSTALL_FAILED, e);
        }
    }

    @NonNull
    @VisibleForTesting
    static String getAdbInstallCommand(
            @NonNull List<File> apks, @NonNull List<String> installOptions) {
        StringBuilder sb = new StringBuilder();
        sb.append("$ adb install-multiple -r ");
        if (!installOptions.isEmpty()) {
            sb.append(Joiner.on(' ').join(installOptions));
            sb.append(' ');
        }

        for (File f : apks) {
            sb.append(f.getPath());
            sb.append(' ');
        }

        return sb.toString();
    }
}
