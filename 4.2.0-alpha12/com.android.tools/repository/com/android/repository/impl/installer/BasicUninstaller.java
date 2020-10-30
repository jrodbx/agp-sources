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

package com.android.repository.impl.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.io.FileOp;
import java.io.File;

/**
 * A basic {@link Uninstaller} that just deletes the package.
 */
class BasicUninstaller extends AbstractUninstaller {

    public BasicUninstaller(@NonNull LocalPackage p, @NonNull RepoManager mgr,
            @NonNull FileOp fop) {
        super(p, mgr, fop);
    }

    @Override
    protected boolean doPrepare(@NonNull File tempPath, @NonNull ProgressIndicator progress) {
        return true;
    }

    /**
     * Just deletes the package.
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean doComplete(@Nullable File tempPath, @NonNull ProgressIndicator progress) {
        File location = getPackage().getLocation();

        mFop.deleteFileOrFolder(location);
        getRepoManager().markLocalCacheInvalid();;

        boolean successfullyDeleted = !mFop.exists(location);
        if (!successfullyDeleted) {
            progress.logWarning(String.format("Failed to delete package location: %1$s", location));
        }
        return successfullyDeleted;
    }

}
