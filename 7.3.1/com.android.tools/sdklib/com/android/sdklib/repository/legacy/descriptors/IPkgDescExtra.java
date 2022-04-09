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

package com.android.sdklib.repository.legacy.descriptors;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;

/**
 * {@link IPkgDescExtra} keeps information on individual extra SDK packages
 * (both local or remote packages definitions.) The base {@link IPkgDesc} tries
 * to present a unified interface to package attributes and this interface
 * adds methods specific to extras.
 * <p>
 * To create a new {@link IPkgDescExtra},
 * use {@link PkgDesc.Builder#newExtra(IdDisplay, String, String, String[], Revision)} )}.
 * <p>
 * The extra's revision is a {@link Revision}; the attribute is however
 * accessed via {@link IPkgDesc#getRevision()} instead of introducing a new
 * custom method.
 * <p>
 * To query generic packages capabilities, rely on {@link #getType()} and the
 * {@code IPkgDesc.hasXxx()} methods provided by {@link IPkgDesc}.
 *
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
public interface IPkgDescExtra extends IPkgDesc {
    /**
     * Returns an optional list of older paths for this extra package.
     * @return A non-null, possibly empty, for old paths previously used for the same extra.
     */
    @NonNull String[] getOldPaths();

    /**
     * Returns the display name of the Extra.
     * @return A non-null name for the Extra, used for display purposes.
     */
    @NonNull String getNameDisplay();

}
