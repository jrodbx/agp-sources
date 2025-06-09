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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;

/**
 * A factory for {@link PackageOperation}s. Will also add appropriate {@link
 * PackageOperation.StatusChangeListener}s via a {@link StatusChangeListenerFactory}.
 */
public interface InstallerFactory {

    /** Canonical way to create instances of {@link Installer}. */
    @NonNull
    Installer createInstaller(
            @NonNull RemotePackage p, @NonNull RepoManager mgr, @NonNull Downloader downloader);

    /** Canonical way to create instances of {@link Uninstaller}. */
    @NonNull
    Uninstaller createUninstaller(@NonNull LocalPackage p, @NonNull RepoManager mgr);

    /**
     * Sets the factory used to generate the list of listeners added to the generated installers/
     * uninstallers.
     */
    void setListenerFactory(@NonNull StatusChangeListenerFactory listenerFactory);

    /**
     * If a factory can't itself create an installer for a given package, it will try again using
     * the given {@code fallback}, if provided.
     * If an operation fails, the operation can contain a reference to an operation created by the
     * fallback factory to facilitate retrying the install.
     */
    void setFallbackFactory(@Nullable InstallerFactory fallback);

    /**
     * A class that can create a list of appropriate listeners for an installer/uninstaller.
     */
    interface StatusChangeListenerFactory {
        @NonNull
        List<PackageOperation.StatusChangeListener> createListeners(@NonNull RepoPackage p);
    }
}
