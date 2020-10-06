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
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.Uninstaller;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class UninstallAction extends SdkPackagesAction {
    private static final String ACTION_ARG = "--uninstall";

    private UninstallAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    @Override
    public void execute(@NonNull ProgressIndicator progress)
            throws SdkManagerCli.CommandFailedException {
        getRepoManager().loadSynchronously(0, progress.createSubProgress(0.1), null, mSettings);

        List<String> paths = getPaths(getRepoManager());
        double progressMax = 0.1;
        double progressIncrement = 0.9 / paths.size();
        for (String path : paths) {
            LocalPackage p = getRepoManager().getPackages().getLocalPackages().get(path);
            if (p == null) {
                progress.logWarning("Unable to find package " + path);
            } else {
                Uninstaller uninstaller =
                        SdkInstallerUtil.findBestInstallerFactory(p, getSdkHandler())
                                .createUninstaller(
                                        p, getRepoManager(), getSdkHandler().getFileOp());
                progressMax += progressIncrement;
                if (!applyPackageOperation(uninstaller, progress.createSubProgress(progressMax))) {
                    // there was an error, abort.
                    throw new SdkManagerCli.CommandFailedException();
                }
            }
            progress.setFraction(progressMax);
        }
        progress.setFraction(1);
    }

    public static void register(
            @NonNull Map<String, Function<SdkManagerCliSettings, SdkAction>> argToFactory) {
        argToFactory.put(ACTION_ARG, UninstallAction::new);
    }
}
