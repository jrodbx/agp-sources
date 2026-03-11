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

package com.android.sdklib.repository.legacy.remote.internal.archives;


import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;

/**
 * The legacy OS that this archive can be downloaded on.
 * <p/>
 * This attribute was used for the &lt;archive&gt; element in repo schema 1-9. add-on schema 1-6 and
 * sys-img schema 1-2. Starting with repo schema 10, add-on schema 7 and sys-img schema 3, this is
 * replaced by the &lt;host-os&gt; element and {@link com.android.sdklib.repository.legacy.remote.internal.archives.ArchFilter}.
 *
 * @see com.android.sdklib.repository.legacy.remote.internal.archives.HostOs
 * @deprecated This is part of the old SDK manager framework. Use {@link AndroidSdkHandler}/{@link
 * RepoManager} and associated classes instead.
 */
@Deprecated
public enum LegacyOs {
    ANY("Any"),
    LINUX("Linux"),
    MACOSX("MacOS X"),
    WINDOWS("Windows");

    private final String mUiName;

    LegacyOs(String uiName) {
        mUiName = uiName;
    }

    /**
     * Returns the UI name of the OS.
     */
    public String getUiName() {
        return mUiName;
    }
}
