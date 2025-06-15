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

package com.android.sdklib.repository.legacy.local;

import com.android.annotations.NonNull;
import com.android.repository.api.RepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.repository.Revision;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;

import java.io.File;
import java.util.Properties;

/**
 * Local sample package, for a given platform's {@link AndroidVersion}.
 * The package itself has a major revision.
 * There should be only one for a given android platform version.
 *
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
class LocalSamplePkgInfo extends LocalPkgInfo {

    @NonNull
    private final IPkgDesc mDesc;

    LocalSamplePkgInfo(@NonNull LocalSdk localSdk,
            @NonNull File localDir,
            @NonNull Properties sourceProps,
            @NonNull AndroidVersion version,
            @NonNull Revision revision,
            @NonNull Revision minToolsRev) {
        super(localSdk, localDir, sourceProps);
        mDesc = PkgDesc.Builder.newSample(version, revision, minToolsRev).create();
    }

    @NonNull
    @Override
    public IPkgDesc getDesc() {
        return mDesc;
    }
}
