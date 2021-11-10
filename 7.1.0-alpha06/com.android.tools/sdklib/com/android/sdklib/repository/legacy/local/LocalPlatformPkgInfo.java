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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
@SuppressWarnings("ConstantConditions")
public class LocalPlatformPkgInfo extends LocalPkgInfo {

    public static final String PROP_VERSION_SDK = "ro.build.version.sdk";      //$NON-NLS-1$

    public static final String PROP_VERSION_CODENAME = "ro.build.version.codename"; //$NON-NLS-1$

    public static final String PROP_VERSION_RELEASE = "ro.build.version.release";  //$NON-NLS-1$

    @NonNull
    private final IPkgDesc mDesc;


    private static final int LAYOUTLIB_VERSION_NOT_SPECIFIED = 0;
    private Map<String, String> myPlatformProp;

    LocalPlatformPkgInfo(@NonNull LocalSdk localSdk,
            @NonNull File localDir,
            @NonNull Properties sourceProps,
            @NonNull AndroidVersion version,
            @NonNull Revision revision,
            @NonNull Revision minToolsRev) {
        super(localSdk, localDir, sourceProps);
        mDesc = PkgDesc.Builder.newPlatform(version, revision, minToolsRev).create();
    }

    @NonNull
    @Override
    public IPkgDesc getDesc() {
        return mDesc;
    }

    /**
     * The "path" of a Platform is its Target Hash.
     */
    @NonNull
    public String getTargetHash() {
        return getDesc().getPath();
    }

    //-----

    public int getLayoutlibApi() {
        Map<String, String> platformProp = getPlatformProps();
        int layoutlibApi = 1;

        if (platformProp == null) {
            return layoutlibApi;
        }
        try {
            String propApi = platformProp.get(PkgProps.LAYOUTLIB_API);
            if (propApi == null) {
                // In old packages it was sometimes specified differently
                propApi = platformProp.get("sdk." + PkgProps.LAYOUTLIB_API.toLowerCase());
            }
            String propRev = platformProp.get(PkgProps.LAYOUTLIB_REV);
            if (propRev == null) {
                // In old packages it was sometimes specified differently
                propRev = platformProp.get("sdk." + PkgProps.LAYOUTLIB_REV.toLowerCase());
            }
            int llApi = propApi == null ? LAYOUTLIB_VERSION_NOT_SPECIFIED :
                    Integer.parseInt(propApi);
            int llRev = propRev == null ? LAYOUTLIB_VERSION_NOT_SPECIFIED :
                    Integer.parseInt(propRev);
            if (llApi > LAYOUTLIB_VERSION_NOT_SPECIFIED ||
                    llRev >= LAYOUTLIB_VERSION_NOT_SPECIFIED) {
                layoutlibApi = llApi;
            }
        } catch (NumberFormatException e) {
            // do nothing, we'll ignore the layoutlib version if it's invalid
        }
        return layoutlibApi;
    }

    private Map<String, String> getPlatformProps() {
        if (myPlatformProp == null) {
            LocalSdk sdk = getLocalSdk();
            FileOp fileOp = sdk.getFileOp();
            File platformFolder = getLocalDir();
            File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
            File sourcePropFile = new File(platformFolder, SdkConstants.FN_SOURCE_PROP);

            if (!fileOp.isFile(buildProp) || !fileOp.isFile(sourcePropFile)) {
                appendLoadError("Ignoring platform '%1$s': %2$s is missing.",   //$NON-NLS-1$
                        platformFolder.getName(), SdkConstants.FN_BUILD_PROP);
                return null;
            }

            Map<String, String> result = new HashMap<String, String>();

            // add all the property files
            Map<String, String> map = null;

            try {
                map = ProjectProperties.parsePropertyStream(fileOp.newFileInputStream(buildProp), buildProp.getPath(), null);
                if (map != null) {
                    result.putAll(map);
                }
            }
            catch (IOException ignore) {
            }

            try {
                map = ProjectProperties.parsePropertyStream(fileOp.newFileInputStream(sourcePropFile), sourcePropFile.getPath(), null);
                if (map != null) {
                    result.putAll(map);
                }
            }
            catch (IOException ignore) {
            }

            File sdkPropFile = new File(platformFolder, SdkConstants.FN_SDK_PROP);
            if (fileOp.isFile(sdkPropFile)) { // obsolete platforms don't have this.
                try {
                    map = ProjectProperties.parsePropertyStream(fileOp.newFileInputStream(sdkPropFile), sdkPropFile.getPath(), null);
                    if (map != null) {
                        result.putAll(map);
                    }
                }
                catch (IOException ignore) {
                }
            }
            myPlatformProp = result;
        }
        return myPlatformProp;
    }
}
