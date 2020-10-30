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
import java.util.Locale;
import java.util.Properties;

/**
 * Local system-image package, for a given platform's {@link AndroidVersion}
 * and given ABI.
 * The package itself has a major revision.
 * There should be only one for a given android platform version &amp; ABI.
 *
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
public class LocalSysImgPkgInfo extends LocalPkgInfo {


    @NonNull
    private final IPkgDesc mDesc;

    public LocalSysImgPkgInfo(@NonNull  LocalSdk localSdk,
            @NonNull  File localDir,
            @NonNull  Properties sourceProps,
            @NonNull  AndroidVersion version,
            @Nullable IdDisplay tag,
            @NonNull  String abi,
            @NonNull Revision revision) {
        super(localSdk, localDir, sourceProps);
        String listDisplay = sourceProps.getProperty(PkgProps.PKG_LIST_DISPLAY);
        if (listDisplay == null) {
            listDisplay = "";
        }
        mDesc = PkgDesc.Builder.newSysImg(version, tag, abi, revision)
                .setDescriptionShort(
                        createShortDescription(listDisplay,
                                abi, null, tag, version,
                                revision, sourceProps.containsKey(PkgProps.PKG_OBSOLETE)))
                .setListDisplay(
                        createListDescription(listDisplay,
                                tag, getAbiDisplayNameInternal(abi),
                                sourceProps.containsKey(PkgProps.PKG_OBSOLETE)))
                .create();
    }

    @NonNull
    @Override
    public IPkgDesc getDesc() {
        return mDesc;
    }

    /**
     * Extracts the tag id &amp; display from the properties.
     * If missing, uses the "default" tag id.
     */
    @NonNull
    public static IdDisplay extractTagFromProps(Properties props) {
        if (props != null) {
            String tagId   = props.getProperty(PkgProps.SYS_IMG_TAG_ID, SystemImage.DEFAULT_TAG.getId());
            String tagDisp = props.getProperty(PkgProps.SYS_IMG_TAG_DISPLAY, "");      //$NON-NLS-1$
            if (tagDisp == null || tagDisp.isEmpty()) {
                tagDisp = tagIdToDisplay(tagId);
            }
            assert tagId   != null;
            assert tagDisp != null;
            return IdDisplay.create(tagId, tagDisp);
        }
        return SystemImage.DEFAULT_TAG;
    }

    /**
     * Computes a display-friendly tag string based on the tag id.
     * This is typically used when there's no tag-display attribute.
     *
     * @param tagId A non-null tag id to sanitize for display.
     * @return The tag id with all non-alphanum symbols replaced by spaces and trimmed.
     */
    @NonNull
    public static String tagIdToDisplay(@NonNull String tagId) {
        String name;
        name = tagId.replaceAll("[^A-Za-z0-9]+", " ");      //$NON-NLS-1$ //$NON-NLS-2$
        name = name.replaceAll(" +", " ");                  //$NON-NLS-1$ //$NON-NLS-2$
        name = name.trim();

        if (!name.isEmpty()) {
            char c = name.charAt(0);
            if (!Character.isUpperCase(c)) {
                StringBuilder sb = new StringBuilder(name);
                sb.replace(0, 1, String.valueOf(c).toUpperCase(Locale.US));
                name = sb.toString();
            }
        }
        return name;
    }

    public static String createListDescription(String listDisplay, IdDisplay tag, String abiDisplayName, boolean obsolete) {
        if (!listDisplay.isEmpty()) {
            return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
        }

        boolean isDefaultTag = SystemImage.DEFAULT_TAG.equals(tag);
        return String.format("%1$s%2$s System Image%3$s", isDefaultTag ? "" : (tag.getDisplay() + " "), abiDisplayName,
                obsolete ? " (Obsolete)" : "");
    }

    public static String createShortDescription(String listDisplay,
            String abi,
            IdDisplay vendor,
            IdDisplay tag,
            AndroidVersion version,
            Revision revision,
            boolean obsolete) {
        if (!listDisplay.isEmpty()) {
            return String.format("%1$s, %2$s API %3$s, revision %4$s%5$s", listDisplay, vendor == null ? "Android" : vendor.getDisplay(),
                    version.getApiString(), revision.toShortString(), obsolete ? " (Obsolete)" : "");
        }

        boolean isDefaultTag = SystemImage.DEFAULT_TAG.equals(tag);
        return String.format("%1$s%2$s System Image, %3$s API %4$s, revision %5$s%6$s", isDefaultTag ? "" : (tag.getDisplay() + " "),
                getAbiDisplayNameInternal(abi), vendor == null ? "Android" : vendor.getDisplay(), version.getApiString(),
                revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    public static String getAbiDisplayNameInternal(String abi) {
        return abi.replace("armeabi", "ARM EABI")          //$NON-NLS-1$  //$NON-NLS-2$
                .replace("arm64", "ARM 64")            //$NON-NLS-1$  //$NON-NLS-2$
                .replace("x86", "Intel x86 Atom")    //$NON-NLS-1$  //$NON-NLS-2$
                .replace("x86_64", "Intel x86_64 Atom") //$NON-NLS-1$  //$NON-NLS-2$
                .replace("mips", "MIPS")              //$NON-NLS-1$  //$NON-NLS-2$
                .replace("-", " ");                      //$NON-NLS-1$  //$NON-NLS-2$
    }
}
