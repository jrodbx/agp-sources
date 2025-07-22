/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdklib.repository.installer;

import static com.android.repository.api.PackageOperation.InstallStatus.COMPLETE;
import static com.android.repository.api.PackageOperation.StatusChangeListener;
import static com.android.repository.api.PackageOperation.StatusChangeListenerException;

import com.android.annotations.NonNull;
import com.android.repository.api.Installer;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.PlatformTarget;
import java.nio.file.Path;

/**
 * A {@link PackageOperation.StatusChangeListener} that knows how to install and uninstall Sources
 * packages.
 */
public class SourceInstallListener implements StatusChangeListener {

    private final AndroidSdkHandler mSdkHandler;

    public SourceInstallListener(@NonNull AndroidSdkHandler sdkHandler) {
        mSdkHandler = sdkHandler;
    }

    @Override
    public void statusChanged(@NonNull PackageOperation op, @NonNull ProgressIndicator progress)
            throws StatusChangeListenerException {
        if (op.getInstallStatus() == COMPLETE) {
            // Update source path in PlatformTarget
            String targetHash = AndroidTargetHash.getPlatformHashString(
                    ((DetailsTypes.ApiDetailsType) op.getPackage().getTypeDetails())
                            .getAndroidVersion());

            IAndroidTarget target = mSdkHandler.getAndroidTargetManager(progress)
                    .getTargetFromHashString(targetHash, progress);
            if (target instanceof PlatformTarget) {
                Path sourcePath = null;
                if (op instanceof Installer) {
                    sourcePath = op.getLocation(progress);
                }
                ((PlatformTarget) target).setSources(sourcePath);
            }
        }
    }
}
