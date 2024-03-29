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
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import java.nio.file.Path;

/**
 * Framework for a basic uninstaller that keeps track of its status and invalidates the list of
 * installed packages when complete.
 */
public abstract class AbstractUninstaller extends AbstractPackageOperation
  implements Uninstaller {

    private final LocalPackage mPackage;

    public AbstractUninstaller(@NonNull LocalPackage p, @NonNull RepoManager mgr) {
        super(mgr);
        mPackage = p;
    }

    @Override
    @NonNull
    public LocalPackage getPackage() {
        return mPackage;
    }

    @NonNull
    @Override
    public final Path getLocation(@NonNull ProgressIndicator progress) {
        return mPackage.getLocation();
    }

    @Override
    @NonNull
    public String getName() {
        return String.format("Uninstall %1$s", mPackage.getDetailedDisplayName());
    }
}
