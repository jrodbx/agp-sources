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

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;
import com.android.sdklib.repository.legacy.local.LocalAddonPkgInfo;
import com.android.sdklib.repository.legacy.remote.RemotePkgInfo;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkAddonConstants;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkRepoConstants;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.w3c.dom.Node;

/**
 * Represents an add-on XML node in an SDK repository.
 *
 * @deprecated This is part of the old SDK manager framework. Use {@link AndroidSdkHandler}/{@link
 * RepoManager} and associated classes instead.
 */
@Deprecated
public class RemoteAddonPkgInfo extends RemotePkgInfo {

    /**
     * The helper handling the layoutlib version.
     */
    private final LayoutlibVersionMixin mLayoutlibVersion;

    /**
     * An add-on library.
     */
    public static class Lib {

        private final String mName;
        private final String mDescription;

        public Lib(String name, String description) {
            mName = name;
            mDescription = description;
        }

        public String getName() {
            return mName;
        }

        public String getDescription() {
            return mDescription;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mDescription == null) ? 0 : mDescription.hashCode());
            result = prime * result + ((mName == null) ? 0 : mName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Lib)) {
                return false;
            }
            Lib other = (Lib) obj;
            if (mDescription == null) {
                if (other.mDescription != null) {
                    return false;
                }
            } else if (!mDescription.equals(other.mDescription)) {
                return false;
            }
            if (mName == null) {
                if (other.mName != null) {
                    return false;
                }
            } else if (!mName.equals(other.mName)) {
                return false;
            }
            return true;
        }
    }

    private final Lib[] mLibs;

    /**
     * Creates a new add-on package from the attributes and elements of the given XML node. This
     * constructor should throw an exception if the package cannot be created.
     *
     * @param source      The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
     *                    parameters that vary according to the originating XML schema.
     * @param licenses    The licenses loaded from the XML originating document.
     */
    public RemoteAddonPkgInfo(SdkSource source, Node packageNode, String nsUri,
            Map<String, String> licenses) {
        super(source, packageNode, nsUri, licenses);

        // --- name id/display ---
        // addon-4.xsd introduces the name-id, name-display, vendor-id and vendor-display.
        // These are not optional but we still need to support a fallback for older addons
        // that only provide name and vendor. If the addon provides neither set of fields,
        // it will simply not work as expected.

        String nameId = RemotePackageParserUtils
                .getXmlString(packageNode, SdkRepoConstants.NODE_NAME_ID).trim();
        String nameDisp = RemotePackageParserUtils
                .getXmlString(packageNode, SdkRepoConstants.NODE_NAME_DISPLAY).trim();
        String name = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_NAME)
                .trim();

        // The old <name> is equivalent to the new <name-display>
        if (nameDisp.isEmpty()) {
            nameDisp = name;
        }

        // For a missing id, we simply use a sanitized version of the display name
        if (nameId.isEmpty()) {
            nameId = LocalAddonPkgInfo.sanitizeDisplayToNameId(!name.isEmpty() ? name : nameDisp);
        }

        assert !nameId.isEmpty();
        assert !nameDisp.isEmpty();

        // --- vendor id/display ---
        // Same processing for vendor id vs display

        String vendorId = RemotePackageParserUtils
                .getXmlString(packageNode, SdkAddonConstants.NODE_VENDOR_ID).trim();
        String vendorDisp = RemotePackageParserUtils
                .getXmlString(packageNode, SdkAddonConstants.NODE_VENDOR_DISPLAY).trim();
        String vendor = RemotePackageParserUtils
                .getXmlString(packageNode, SdkAddonConstants.NODE_VENDOR).trim();

        // The old <vendor> is equivalent to the new <vendor-display>
        if (vendorDisp.isEmpty()) {
            vendorDisp = vendor;
        }

        // For a missing id, we simply use a sanitized version of the display vendor
        if (vendorId.isEmpty()) {
            boolean hasVendor = !vendor.isEmpty();
            vendorId = LocalAddonPkgInfo.sanitizeDisplayToNameId(hasVendor ? vendor : vendorDisp);
        }

        assert !vendorId.isEmpty();
        assert !vendorDisp.isEmpty();

        // --- other attributes

        int apiLevel = RemotePackageParserUtils
                .getXmlInt(packageNode, SdkAddonConstants.NODE_API_LEVEL, 0);
        AndroidVersion androidVersion = new AndroidVersion(apiLevel, null /*codeName*/);

        mLibs = parseLibs(RemotePackageParserUtils
                .findChildElement(packageNode, SdkAddonConstants.NODE_LIBS));

        mLayoutlibVersion = new LayoutlibVersionMixin(packageNode);

        PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder
                .newAddon(androidVersion, getRevision(), IdDisplay.create(vendorId, vendorDisp),
                        IdDisplay.create(nameId, nameDisp));
        pkgDescBuilder.setDescriptionShort(
                createShortDescription(mListDisplay, getRevision(), nameDisp, androidVersion,
                        isObsolete()));
        pkgDescBuilder.setDescriptionUrl(getDescUrl());
        pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, nameDisp, isObsolete()));
        pkgDescBuilder.setIsObsolete(isObsolete());
        pkgDescBuilder.setLicense(getLicense());
        mPkgDesc = pkgDescBuilder.create();

    }

    /**
     * Parses a <libs> element.
     */
    private Lib[] parseLibs(Node libsNode) {
        ArrayList<Lib> libs = new ArrayList<Lib>();

        if (libsNode != null) {
            String nsUri = libsNode.getNamespaceURI();
            for (Node child = libsNode.getFirstChild(); child != null;
                    child = child.getNextSibling()) {

                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        nsUri.equals(child.getNamespaceURI()) &&
                        SdkRepoConstants.NODE_LIB.equals(child.getLocalName())) {
                    libs.add(parseLib(child));
                }
            }
        }

        return libs.toArray(new Lib[0]);
    }

    /**
     * Parses a <lib> element from a <libs> container.
     */
    private Lib parseLib(Node libNode) {
        return new Lib(RemotePackageParserUtils.getXmlString(libNode, SdkRepoConstants.NODE_NAME),
                RemotePackageParserUtils.getXmlString(libNode, SdkRepoConstants.NODE_DESCRIPTION));
    }

    /**
     * Returns the libs defined in this add-on. Can be an empty array but not null.
     */
    @NonNull
    public Lib[] getLibs() {
        return mLibs;
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * <p/>
     */
    private static String createListDescription(String listDisplay, String displayName,
            boolean obsolete) {
        if (!listDisplay.isEmpty()) {
            return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
        }

        return String.format("%1$s%2$s", displayName, obsolete ? " (Obsolete)" : "");
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    private static String createShortDescription(String listDisplay,
            Revision revision,
            String displayName,
            AndroidVersion version,
            boolean obsolete) {
        if (!listDisplay.isEmpty()) {
            return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(),
                    obsolete ? " (Obsolete)" : "");
        }

        return String.format("%1$s, Android API %2$s, revision %3$s%4$s", displayName,
                version.getApiString(), revision.toShortString(),
                obsolete ? " (Obsolete)" : "");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mLayoutlibVersion == null) ? 0 : mLayoutlibVersion.hashCode());
        result = prime * result + Arrays.hashCode(mLibs);
        String name = getPkgDesc().getName().getDisplay();
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + (getPkgDesc().hasVendor() ? 0
                : getPkgDesc().getVendor().hashCode());
        result = prime * result + getPkgDesc().getAndroidVersion().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof RemoteAddonPkgInfo)) {
            return false;
        }
        RemoteAddonPkgInfo other = (RemoteAddonPkgInfo) obj;
        if (mLayoutlibVersion == null) {
            if (other.mLayoutlibVersion != null) {
                return false;
            }
        } else if (!mLayoutlibVersion.equals(other.mLayoutlibVersion)) {
            return false;
        }
        if (!Arrays.equals(mLibs, other.mLibs)) {
            return false;
        }
        return true;
    }
}
