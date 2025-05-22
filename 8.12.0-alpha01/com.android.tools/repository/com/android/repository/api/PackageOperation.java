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
import java.nio.file.Path;

/**
 * An install or uninstall operation that affects the current SDK state.
 */
public interface PackageOperation {

    /**
     * Statuses of in-progress operations.
     */
    enum InstallStatus {
        /**
         * This operation hasn't started yet
         */
        NOT_STARTED,
        /**
         * This operation is in the process of preparing the component. No changes are
         * made to the SDK during this phase.
         */
        PREPARING,
        /**
         * The steps that can be taken without affecting the installed SDK have completed.
         */
        PREPARED,
        /**
         * The SDK is being modified.
         */
        RUNNING,
        /**
         * The operation has ended unsuccessfully.
         */
        FAILED,
        /** The operation has ended unsuccessfully. */
        COMPLETE
    }

    /**
     * The package (local or remote) that's being affected.
     */
    @NonNull
    RepoPackage getPackage();

    /** The filesystem path affected by this operation. */
    @NonNull
    Path getLocation(@NonNull ProgressIndicator progress);

    /**
     * Perform any preparatory work that can be done in the background prior to (un)installing a
     * package.
     *
     * @return {@code true} if the preparation was successful, {@code false} otherwise.
     */
    boolean prepare(@NonNull ProgressIndicator progress);

    /**
     * Install/Uninstall the package.
     *
     * @param progress A {@link ProgressIndicator}.
     * @return {@code true} if the install/uninstall was successful, {@code false} otherwise.
     */
    boolean complete(@NonNull ProgressIndicator progress);

    /**
     * Gets the {@link RepoManager} for which we're installing/uninstalling a package.
     */
    @NonNull
    RepoManager getRepoManager();

    /**
     * Registers a listener that will be called when the {@link InstallStatus} of this installer
     * changes.
     */
    void registerStateChangeListener(@NonNull StatusChangeListener listener);

    /**
     * Gets the current {@link InstallStatus} of this installer.
     */
    @NonNull
    InstallStatus getInstallStatus();

    /**
     * A listener that will be called when the {@link #getInstallStatus() status} of this installer
     * changes.
     */
    interface StatusChangeListener {

        void statusChanged(@NonNull PackageOperation op, @NonNull ProgressIndicator progress)
                throws PackageOperation.StatusChangeListenerException;
    }

    /**
     * Exception thrown by a {@link StatusChangeListener}.
     */
    class StatusChangeListenerException extends Exception {

        public StatusChangeListenerException(@Nullable Exception e) {
            super(e);
        }

        public StatusChangeListenerException(@Nullable String reason) {
            super(reason);
        }
    }

    /**
     * Gets the name of this operation, suitable for using in user-facing messages.
     * e.g. "Install Foo" or "Uninstall Bar".
     */
    @NonNull
    String getName();

    /**
     * If this operation fails, it might be possible to try the install a different way.
     * Specifically, if we have a
     * {@link InstallerFactory#setFallbackFactory(InstallerFactory) fallback factory}, this could be
     * an operation created by that factory.
     */
    @Nullable
    PackageOperation getFallbackOperation();

    /**
     * Sets the operation used to retry if this operation fails.
     * @see #getFallbackOperation()
     */
    void setFallbackOperation(@Nullable PackageOperation fallback);
}
