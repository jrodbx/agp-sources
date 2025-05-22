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

import com.android.annotations.NonNull;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.sdklib.repository.AndroidSdkHandler;

/**
 * Utilities for interacting with {@link Installer}s etc.
 */
public class SdkInstallerUtil {

    /**
     * Gets an {@link InstallerFactory} for installing or uninstalling the given package.
     */
    @NonNull
    public static InstallerFactory findBestInstallerFactory(
            @NonNull RepoPackage p, @NonNull AndroidSdkHandler handler) {
        InstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(new SdkInstallListenerFactory(handler));
        return factory;
    }

    private SdkInstallerUtil() {}
}
