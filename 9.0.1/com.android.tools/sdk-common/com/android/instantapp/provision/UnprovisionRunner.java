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
package com.android.instantapp.provision;

import static com.android.instantapp.provision.ProvisionRunner.executeShellCommand;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.google.common.collect.Lists;
import java.util.List;

public class UnprovisionRunner {
    @NonNull
    private static final List<String> INSTANT_APP_PACKAGES =
            Lists.newArrayList(
                    "com.google.android.instantapps.supervisor",
                    "com.google.android.instantapps.devman");

    @NonNull private final ProvisionListener myListener;
    private long myShellTimeout = 500;

    public UnprovisionRunner() {
        this(new ProvisionListener.NullListener());
    }

    public UnprovisionRunner(@NonNull ProvisionListener listener) {
        myListener = listener;
    }

    public void runUnprovision(@NonNull IDevice device) throws ProvisionException {
        myListener.setProgress(0);
        myListener.printMessage("Starting unprovisioning.");
        int i = 0;
        for (String pkgName : INSTANT_APP_PACKAGES) {
            if (!myListener.isCancelled()) {
                myListener.printMessage("Uninstalling package " + pkgName);
                myListener.logMessage(
                        "Uninstalling package " + pkgName + " of device " + device, null);
                removePackage(device, pkgName);
                i++;
                myListener.setProgress(1.0 * i / INSTANT_APP_PACKAGES.size());
            }
        }
        myListener.setProgress(1);
    }

    private void removePackage(@NonNull IDevice device, @NonNull String pkgName)
            throws ProvisionException {
        try {
            if (!isEmpty(
                    executeShellCommand(device, "pm path " + pkgName, false, myShellTimeout))) {
                String result = device.uninstallPackage(pkgName);
                if (result != null) {
                    throw new ProvisionException(ProvisionException.ErrorType.UNKNOWN, result);
                }
            }
        } catch (InstallException e) {
            throw new ProvisionException(
                    ProvisionException.ErrorType.UNINSTALL_FAILED, "Package " + pkgName, e);
        }
    }

    private static boolean isEmpty(@Nullable String str) {
        return str == null || str.isEmpty();
    }
}
