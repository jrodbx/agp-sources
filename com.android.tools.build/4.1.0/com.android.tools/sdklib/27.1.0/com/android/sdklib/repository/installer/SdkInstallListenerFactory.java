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
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * {@link InstallerFactory.StatusChangeListenerFactory} that returns the appropriate set of
 * listeners for the given package.
 */
public class SdkInstallListenerFactory implements InstallerFactory.StatusChangeListenerFactory {

    private AndroidSdkHandler mSdkHandler;

    public SdkInstallListenerFactory(@NonNull AndroidSdkHandler handler) {
        mSdkHandler = handler;
    }

    @NonNull
    @Override
    public List<PackageOperation.StatusChangeListener> createListeners(@NonNull RepoPackage p) {
        List<PackageOperation.StatusChangeListener> result = Lists.newArrayList();
        if (p.getTypeDetails() instanceof DetailsTypes.MavenType) {
            result.add(new MavenInstallListener(mSdkHandler));
        }
        if (p.getTypeDetails() instanceof DetailsTypes.SourceDetailsType) {
            result.add(new SourceInstallListener(mSdkHandler));
        }
        return result;
    }

    @NonNull
    protected AndroidSdkHandler getSdkHandler() {
        return mSdkHandler;
    }
}
