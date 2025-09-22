/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.repository;

/**
 * Public constants used by the repository when saving {@code source.properties} files in local
 * packages.
 *
 * <p>These constants are public and part of the SDK Manager public API. Once published we can't
 * change them arbitrarily since various parts of our build process depend on them.
 */
public class PkgProps {

    // Base Package
    public static final String PKG_REVISION = "Pkg.Revision";
    public static final String PKG_LICENSE = "Pkg.License";
    public static final String PKG_LICENSE_REF = "Pkg.LicenseRef";
    public static final String PKG_DESC = "Pkg.Desc";
    public static final String PKG_DESC_URL = "Pkg.DescUrl";
    public static final String PKG_SOURCE_URL = "Pkg.SourceUrl";
    public static final String PKG_OBSOLETE = "Pkg.Obsolete";
    public static final String PKG_LIST_DISPLAY = "Pkg.ListDisplay";

    // AndroidVersion

    public static final String VERSION_API_LEVEL = "AndroidVersion.ApiLevel";
    /** Code name of the platform if the platform is not final */
    public static final String VERSION_CODENAME = "AndroidVersion.CodeName";

    public static final String VERSION_IS_BASE_EXTENSION = "AndroidVersion.IsBaseSdk";
    public static final String VERSION_EXTENSION_LEVEL = "AndroidVersion.ExtensionLevel";

    // AddonPackage

    public static final String ADDON_NAME = "Addon.Name";
    public static final String ADDON_NAME_ID = "Addon.NameId";
    public static final String ADDON_NAME_DISPLAY = "Addon.NameDisplay";
    public static final String ADDON_VENDOR = "Addon.Vendor";
    public static final String ADDON_VENDOR_ID = "Addon.VendorId";
    public static final String ADDON_VENDOR_DISPLAY = "Addon.VendorDisplay";

    // DocPackage

    // ExtraPackage

    public static final String EXTRA_PATH = "Extra.Path";
    public static final String EXTRA_OLD_PATHS = "Extra.OldPaths";
    public static final String EXTRA_MIN_API_LEVEL = "Extra.MinApiLevel";
    public static final String EXTRA_PROJECT_FILES = "Extra.ProjectFiles";
    public static final String EXTRA_VENDOR_ID = "Extra.VendorId";
    public static final String EXTRA_VENDOR_DISPLAY = "Extra.VendorDisplay";
    public static final String EXTRA_NAME_DISPLAY = "Extra.NameDisplay";

    // ILayoutlibVersion

    public static final String LAYOUTLIB_API = "Layoutlib.Api";
    public static final String LAYOUTLIB_REV = "Layoutlib.Revision";

    // MinToolsPackage

    public static final String MIN_TOOLS_REV = "Platform.MinToolsRev";

    // PlatformPackage

    public static final String PLATFORM_VERSION = "Platform.Version";

    // ToolPackage

    public static final String MIN_PLATFORM_TOOLS_REV = "Platform.MinPlatformToolsRev";

    // SamplePackage

    public static final String SAMPLE_MIN_API_LEVEL = "Sample.MinApiLevel";

    // SystemImagePackage

    public static final String SYS_IMG_ABI = "SystemImage.Abi";
    public static final String SYS_IMG_TAG_ID = "SystemImage.TagId";
    public static final String SYS_IMG_TAG_DISPLAY = "SystemImage.TagDisplay";
}
