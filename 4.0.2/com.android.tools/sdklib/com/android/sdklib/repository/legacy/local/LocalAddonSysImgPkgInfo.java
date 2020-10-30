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
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;

import java.io.File;
import java.util.Properties;

/**
 * Local add-on system-image package, for a given addon's {@link AndroidVersion} and given ABI.
 * The system-image tag is the add-on name.
 * The package itself has a major revision.
 * There should be only one for a given android platform version & ABI.
 *
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
class LocalAddonSysImgPkgInfo extends LocalPkgInfo {


    @NonNull
    private final IPkgDesc mDesc;

    LocalAddonSysImgPkgInfo(@NonNull LocalSdk localSdk,
            @NonNull File localDir,
            @NonNull Properties sourceProps,
            @NonNull AndroidVersion version,
            @Nullable IdDisplay addonVendor,
            @Nullable IdDisplay addonName,
            @NonNull String abi,
            @NonNull Revision revision) {
        super(localSdk, localDir, sourceProps);
        String id = sourceProps.getProperty(PkgProps.SYS_IMG_TAG_ID);
        IdDisplay tag;
        if (id == null) {
            tag = SystemImage.DEFAULT_TAG;
        }
        else {
            String display = sourceProps.getProperty(PkgProps.SYS_IMG_TAG_DISPLAY);
            tag = IdDisplay.create(id, display == null ? id : display);
        }
        String listDisplay = sourceProps.getProperty(PkgProps.PKG_LIST_DISPLAY);
        if (listDisplay == null) {
            listDisplay = "";
        }
        mDesc = PkgDesc.Builder.newAddonSysImg(version, addonVendor, addonName, abi, revision)
                .setDescriptionShort(LocalSysImgPkgInfo
                        .createShortDescription(listDisplay, abi,
                                addonVendor,
                                tag, version, revision,
                                sourceProps.containsKey(PkgProps.PKG_OBSOLETE)))
                .setListDisplay(LocalSysImgPkgInfo
                        .createListDescription(listDisplay, tag,
                                LocalSysImgPkgInfo.getAbiDisplayNameInternal(abi),
                                sourceProps.containsKey(PkgProps.PKG_OBSOLETE))).create();
    }

    @NonNull
    @Override
    public IPkgDesc getDesc() {
        return mDesc;
    }
}
