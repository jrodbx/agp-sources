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
package com.android.sdklib.repository.legacy.remote.internal.packages;

import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;
import com.android.sdklib.repository.legacy.remote.RemotePkgInfo;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkSource;

import org.w3c.dom.Node;

import java.util.Map;

/**
 * Remote package representing the Android LLDB.
 *
 * @deprecated This is part of the old SDK manager framework. Use {@link AndroidSdkHandler}/{@link
 * RepoManager} and associated classes instead.
 */
@Deprecated
public class RemoteLLDBPkgInfo extends RemotePkgInfo {

    public RemoteLLDBPkgInfo(SdkSource source, Node packageNode, String nsUri,
            Map<String, String> licenses) {
        super(source, packageNode, nsUri, licenses);
        mPkgDesc = PkgDesc.Builder.newLLDB(getRevision())
                .setListDisplay("LLDB")
                .setDescriptionShort("LLDB")
                .setLicense(getLicense())
                .create();
    }
}
