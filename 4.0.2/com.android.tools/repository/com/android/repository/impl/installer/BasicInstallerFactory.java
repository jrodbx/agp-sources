/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Uninstaller;
import com.android.repository.io.FileOp;

/**
 * Factory for {@link BasicInstaller}s and {@link BasicUninstaller}s.
 */
public class BasicInstallerFactory extends AbstractInstallerFactory {

    @NonNull
    @Override
    protected Installer doCreateInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr,
            @NonNull Downloader downloader, @NonNull FileOp fop) {
        return new BasicInstaller(p, mgr, downloader, fop);
    }

    @NonNull
    @Override
    protected Uninstaller doCreateUninstaller(@NonNull LocalPackage p,
      @NonNull RepoManager mgr,
      @NonNull FileOp fop) {
        return new BasicUninstaller(p, mgr, fop);
    }
}
