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
package com.android.sdklib.repository.legacy;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.GenericFactory;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgType;
import com.android.sdklib.repository.meta.AddonFactory;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.Library;
import com.android.sdklib.repository.meta.RepoFactory;
import com.android.sdklib.repository.meta.SdkCommonFactory;
import com.android.sdklib.repository.meta.SysImgFactory;
import com.android.sdklib.repository.targets.SystemImage;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities used by the {@link FallbackLocalRepoLoader} and {@link FallbackRemoteRepoLoader}s to
 * convert {@link IPkgDesc}s into forms useful to {@link RepoManager}.
 */
public class LegacyRepoUtils {

    private static final Pattern PATTERN_LIB_DATA = Pattern.compile(
            "^([a-zA-Z0-9._-]+\\.jar);(.*)$", Pattern.CASE_INSENSITIVE);    //$NON-NLS-1$

    /**
     * The list of libraries of the add-on. <br/>
     * This is a string in the format "java.package1;java.package2;...java.packageN".
     * For each library's java package name, the manifest.ini contains a key with
     * value "library.jar;Jar Description String". Example:
     * <pre>
     * libraries=com.example.foo;com.example.bar
     * com.example.foo=foo.jar;Foo Library
     * com.example.bar=bar.jar;Bar Library
     * </pre>
     * Not saved in source.properties.
     */
    private static final String ADDON_LIBRARIES    = "libraries";            //$NON-NLS-1$

    /**
     * Convert a {@link IPkgDesc} and other old-style information into a {@link TypeDetails}.
     */
    @NonNull
    static TypeDetails createTypeDetails(@NonNull IPkgDesc desc,
            int layoutLibVersion, @NonNull Collection<OptionalLibrary> addonLibraries,
            @Nullable File packageDir, @NonNull ProgressIndicator progress, @NonNull FileOp fop) {

        SdkCommonFactory sdkFactory = AndroidSdkHandler.getCommonModule().createLatestFactory();
        RepoFactory repoFactory = AndroidSdkHandler.getRepositoryModule().createLatestFactory();
        AddonFactory addonFactory = AndroidSdkHandler.getAddonModule().createLatestFactory();
        SysImgFactory sysImgFactory = AndroidSdkHandler.getSysImgModule().createLatestFactory();
        GenericFactory genericFactory = RepoManager.getGenericModule().createLatestFactory();

        AndroidVersion androidVersion = desc.getAndroidVersion();

        if (desc.getType() == PkgType.PKG_PLATFORM) {
            DetailsTypes.PlatformDetailsType details = repoFactory.createPlatformDetailsType();

            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            details.setCodename(androidVersion.getCodename());
            DetailsTypes.PlatformDetailsType.LayoutlibType layoutLib = repoFactory
              .createLayoutlibType();
            layoutLib.setApi(layoutLibVersion);
            details.setLayoutlib(layoutLib);
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_SYS_IMAGE ||
                desc.getType() == PkgType.PKG_ADDON_SYS_IMAGE) {
            DetailsTypes.SysImgDetailsType details = sysImgFactory.createSysImgDetailsType();
            //noinspection ConstantConditions
            details.setAbi(desc.getPath());
            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            details.setCodename(androidVersion.getCodename());
            IdDisplay tagIdDisplay = desc.getTag();
            if (tagIdDisplay != null) {
                IdDisplay tag = sdkFactory.createIdDisplayType();
                tag.setId(tagIdDisplay.getId());
                tag.setDisplay(tagIdDisplay.getDisplay());
                details.setTag(tag);
            } else {
                details.setTag(SystemImage.DEFAULT_TAG);
            }
            IdDisplay vendorIdDisplay = desc.getVendor();
            if (vendorIdDisplay != null) {
                IdDisplay vendor = sdkFactory.createIdDisplayType();
                vendor.setId(vendorIdDisplay.getId());
                vendor.setDisplay(vendorIdDisplay.getDisplay());
                details.setVendor(vendor);
            }
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_ADDON) {
            DetailsTypes.AddonDetailsType details = addonFactory.createAddonDetailsType();
            IdDisplay vendorIdDisplay = desc.getVendor();
            if (vendorIdDisplay != null) {
                IdDisplay vendor = sdkFactory.createIdDisplayType();
                vendor.setId(vendorIdDisplay.getId());
                vendor.setDisplay(vendorIdDisplay.getDisplay());
                details.setVendor(vendor);
            }
            IdDisplay nameIdDisplay = desc.getName();
            if (nameIdDisplay != null) {
                IdDisplay tag = sdkFactory.createIdDisplayType();
                tag.setId(nameIdDisplay.getId());
                tag.setDisplay(nameIdDisplay.getDisplay());
                details.setTag(tag);
            }
            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            details.setCodename(androidVersion.getCodename());
            if (!addonLibraries.isEmpty()) {
                DetailsTypes.AddonDetailsType.Libraries librariesType = addonFactory.createLibrariesType();
                List<Library> libraries = librariesType.getLibrary();
                for (OptionalLibrary addonLib : addonLibraries) {
                    Library lib = sdkFactory.createLibraryType();
                    lib.setDescription(addonLib.getDescription());
                    lib.setName(addonLib.getName());
                    String jarPath = addonLib.getJar().getPath();
                    if (packageDir != null) {
                        lib.setPackagePath(packageDir);
                        try {
                            jarPath = FileOpUtils
                                    .makeRelative(new File(packageDir, SdkConstants.FD_ADDON_LIBS),
                                            new File(jarPath), fop);
                        } catch (IOException e) {
                            progress.logWarning("Error finding library", e);
                        }
                    }
                    if (!jarPath.isEmpty()) {
                        lib.setLocalJarPath(jarPath);
                    }
                    libraries.add(lib);
                }
                details.setLibraries(librariesType);
            }
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_SOURCE) {
            DetailsTypes.SourceDetailsType details = repoFactory.createSourceDetailsType();
            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            details.setCodename(androidVersion.getCodename());
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_EXTRA) {
            DetailsTypes.ExtraDetailsType details = addonFactory.createExtraDetailsType();
            IdDisplay vendorIdDisplay = desc.getVendor();
            if (vendorIdDisplay != null) {
                IdDisplay vendor = sdkFactory.createIdDisplayType();
                vendor.setId(vendorIdDisplay.getId());
                vendor.setDisplay(vendorIdDisplay.getDisplay());
                details.setVendor(vendor);
            }
            return (TypeDetails) details;
        } else {
            return (TypeDetails) genericFactory.createGenericDetailsType();
        }
    }

    /**
     * Gets the {@link RepoPackage#getDisplayName()} return value from an {@link IPkgDesc}.
     */
    public static String getDisplayName(IPkgDesc legacy) {
        // The legacy code inconsistently adds "Obsolete" to display names, and we want
        // to handle it separately in the new code. Remove it if it's there.
        return getDisplayNameInternal(legacy).replace(" (Obsolete)", "");
    }

    private static String getDisplayNameInternal(IPkgDesc legacy) {
        String result = legacy.getListDescription();
        if (result != null) {
            return result;
        }
        if (legacy.getType() == PkgType.PKG_PLATFORM) {
            AndroidVersion androidVersion = legacy.getAndroidVersion();
            assert androidVersion != null;
            return SdkVersionInfo.getAndroidName(androidVersion.getFeatureLevel());
        }
        result = legacy.getListDescription();
        if (!result.isEmpty()) {
            return result;
        }
        result = legacy.getName() != null ? legacy.getName().getDisplay() : "";
        if (!result.isEmpty()) {
            return result;
        }
        return legacy.getInstallId();
    }

    public static List<OptionalLibrary> parseLegacyAdditionalLibraries(
            @NonNull File packageLocation, @NonNull ProgressIndicator progress,
            @NonNull FileOp fop) {
        List<OptionalLibrary> result = Lists.newArrayList();
        File addOnManifest = new File(packageLocation, SdkConstants.FN_MANIFEST_INI);

        if (!fop.isFile(addOnManifest)) {
            return result;
        }
        Map<String, String> propertyMap;
        try {
            propertyMap = ProjectProperties.parsePropertyStream(
                    fop.newFileInputStream(addOnManifest),
                    addOnManifest.getPath(),
                    null);

        } catch (IOException e) {
            progress.logWarning("Failed to find " + addOnManifest, e);
            return result;
        }
        if (propertyMap == null) {
            return result;
        }
        // get the optional libraries
        String librariesValue = propertyMap.get(ADDON_LIBRARIES);

        SdkCommonFactory sdkFactory = AndroidSdkHandler.getCommonModule().createLatestFactory();

        Map<String, String[]> libMap = Maps.newHashMap();
        if (librariesValue != null) {
            librariesValue = librariesValue.trim();
            if (!librariesValue.isEmpty()) {
                // split in the string into the libraries name
                String[] libraryNames = librariesValue.split(";");     //$NON-NLS-1$
                if (libraryNames.length > 0) {
                    for (String libName : libraryNames) {
                        libName = libName.trim();

                        // get the library data from the properties
                        String libData = propertyMap.get(libName);

                        if (libData != null) {
                            // split the jar file from the description
                            Matcher m = PATTERN_LIB_DATA.matcher(libData);
                            if (m.matches()) {
                                libMap.put(libName, new String[]{
                                        m.group(1), m.group(2)});
                            } else {
                                progress.logWarning(String.format(
                                        "Ignoring library '%1$s', property value has wrong format\n\t%2$s",
                                        libName, libData));
                            }
                        } else {
                            progress.logWarning(
                                    String.format("Ignoring library '%1$s', missing property value",
                                            libName));
                        }
                    }
                }
            }
            for (Map.Entry<String, String[]> entry : libMap.entrySet()) {
                String jarFile = entry.getValue()[0];
                String desc = entry.getValue()[1];
                Library lib = sdkFactory.createLibraryType();
                lib.setName(entry.getKey());
                lib.setPackagePath(packageLocation);
                lib.setDescription(desc);
                lib.setLocalJarPath(jarFile);
                lib.setManifestEntryRequired(true);
                result.add(lib);
            }
        }
        return result;
    }

    /**
     * Gets the {@code path} (see {@link RepoPackage#getPath()}) for a legacy package.
     * @param desc The {@link IPkgDesc} of the legacy package.
     * @param relativeInstallPath The path of the package relative to the sdk root. Used to generate
     *                            the path if normal methods fail.
     * @return The path.
     */
    public static String getLegacyPath(@NonNull IPkgDesc desc,
            @Nullable String relativeInstallPath) {
        switch (desc.getType()) {
            case PKG_TOOLS:
                return SdkConstants.FD_TOOLS;
            case PKG_PLATFORM_TOOLS:
                return SdkConstants.FD_PLATFORM_TOOLS;
            case PKG_BUILD_TOOLS:
                return DetailsTypes.getBuildToolsPath(desc.getRevision());
            case PKG_DOC:
                return SdkConstants.FD_DOCS;
            case PKG_PLATFORM:
                return DetailsTypes.getPlatformPath(desc.getAndroidVersion());
            case PKG_ADDON:
                return DetailsTypes.getAddonPath(desc.getVendor(),
                        desc.getAndroidVersion(), desc.getName());
            case PKG_SYS_IMAGE:
            case PKG_ADDON_SYS_IMAGE:
                return DetailsTypes.getSysImgPath(
                        desc.getVendor(),
                        desc.getAndroidVersion(), desc.getTag(),
                        desc.getPath());
            case PKG_SOURCE:
                return DetailsTypes.getSourcesPath(desc.getAndroidVersion());
            case PKG_EXTRA:
                String path = SdkConstants.FD_EXTRAS;

                String vendor = desc.getVendor().getId();
                if (vendor != null && !vendor.isEmpty()) {
                    path += RepoPackage.PATH_SEPARATOR + vendor;
                }

                String name = desc.getPath();
                if (name != null && !name.isEmpty()) {
                    path += RepoPackage.PATH_SEPARATOR + name;
                }

                return path;
            case PKG_NDK:
                return SdkConstants.FD_NDK;
            case PKG_LLDB:
                return DetailsTypes.getLldbPath(desc.getRevision());
            default:
                // This shouldn't happen, but has been observed.
                if (relativeInstallPath != null) {
                    return relativeInstallPath
                            .replace(File.separatorChar, RepoPackage.PATH_SEPARATOR);
                }
                return "unknown";
        }
    }

}
