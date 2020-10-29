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
package com.android.sdklib.repository.targets;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.PackageParserUtils;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a platform target in the SDK.
 */
public class PlatformTarget implements IAndroidTarget {
    /**
     * Default vendor for platform targets
     */
    public static final String PLATFORM_VENDOR = "Android Open Source Project";

    /**
     * "Android NN" is the default name for platform targets.
     */
    private static final String PLATFORM_NAME = "Android %s";

    /**
     * "Android NN (Preview)" is the default name for preview platform targets.
     */
    private static final String PLATFORM_NAME_PREVIEW = "Android %s (Preview)";

    /**
     * The {@link LocalPackage} from which this target was created.
     */
    private LocalPackage mPackage;

    /**
     * The {@link TypeDetails} of {@link #mPackage}.
     */
    private DetailsTypes.PlatformDetailsType mDetails;

    /** Additional {@link OptionalLibrary}s provided by this target. */
    private List<OptionalLibrary> mOptionalLibraries = ImmutableList.of();

    /**
     * The emulator skins for this target, including those included in the package as well as those
     * from associated system images.
     */
    private Set<File> mSkins;

    /**
     * Parsed version of the {@code build.prop} file in {@link #mPackage}.
     */
    private Map<String, String> mBuildProps;

    /**
     * Reference to the latest {@link BuildToolInfo}.
     */
    private BuildToolInfo mBuildToolInfo;

    /**
     * Location of the sources for this package. If {@code null} the legacy path will be used.
     */
    private File mSourcesPath = null;

    /**
     * Construct a new {@code PlatformTarget} based on the given package.
     */
    public PlatformTarget(@NonNull LocalPackage p, @NonNull AndroidSdkHandler sdkHandler,
            @NonNull FileOp fop, @NonNull ProgressIndicator progress) {
        mPackage = p;
        TypeDetails details = p.getTypeDetails();
        assert details instanceof DetailsTypes.PlatformDetailsType;
        mDetails = (DetailsTypes.PlatformDetailsType) details;

        File optionalDir = new File(p.getLocation(), "optional");
        if (optionalDir.isDirectory()) {
            File optionalJson = new File(optionalDir, "optional.json");
            if (optionalJson.isFile()) {
                mOptionalLibraries = getLibsFromJson(optionalJson);
            }
        }

        File buildProp = new File(getLocation(), SdkConstants.FN_BUILD_PROP);

        if (!fop.isFile(buildProp)) {
            String message = "Build properties not found for package " + p.getDisplayName();
            progress.logWarning(message);
            throw new IllegalArgumentException(message);
        }

        try {
            mBuildProps = ProjectProperties.parsePropertyStream(fop.newFileInputStream(buildProp),
                    buildProp.getPath(), null);
        } catch (IOException ignore) {
        }
        if (mBuildProps == null) {
            mBuildProps = Maps.newHashMap();
        }
        mBuildToolInfo = sdkHandler.getLatestBuildTool(progress, null, getVersion().isPreview());

        mSkins = Sets
          .newTreeSet(PackageParserUtils.parseSkinFolder(getFile(IAndroidTarget.SKINS), fop));
    }

    public void setSources(@Nullable File location) {
        mSourcesPath = location;
    }

    /**
     * Simple struct used by {@link Gson} when parsing the library file.
     */
    public static class Library {

        String name;

        String jar;

        boolean manifest;
    }

    /** Parses {@link OptionalLibrary}s from the given json file. */
    @NonNull
    public static List<OptionalLibrary> getLibsFromJson(@NonNull File jsonFile) {

        Gson gson = new Gson();

        try {
            Type collectionType = new TypeToken<Collection<Library>>() {
            }.getType();
            Collection<Library> libs;
            try (BufferedReader reader = Files.newReader(jsonFile, Charsets.UTF_8)) {
                libs = gson.fromJson(reader, collectionType);
            }

            // convert into the right format.
            List<OptionalLibrary> optionalLibraries = Lists.newArrayListWithCapacity(libs.size());

            File rootFolder = jsonFile.getParentFile();
            for (Library lib : libs) {
                optionalLibraries.add(new OptionalLibraryImpl(
                        lib.name,
                        new File(rootFolder, lib.jar),
                        lib.name,
                        lib.manifest));
            }

            return optionalLibraries;
        } catch (IOException | JsonIOException | JsonSyntaxException e) {
            // Shouldn't happen since we've checked the file is here, but can happen in
            // some cases (too many files open, corrupted file contents, etc).
            return Collections.emptyList();
        }
    }


    @Override
    @NonNull
    public String getLocation() {
        return mPackage.getLocation().getPath() + File.separator;
    }

    /**
     * {@inheritDoc}
     *
     * For platform, this is always {@link #PLATFORM_VENDOR}
     */
    @Override
    public String getVendor() {
        return PLATFORM_VENDOR;
    }

    @Override
    public String getName() {
        AndroidVersion version = getVersion();
        if (version.isPreview()) {
            return String.format(PLATFORM_NAME_PREVIEW, version);
        } else {
            return String.format(PLATFORM_NAME, version);
        }
    }

    @Override
    public String getFullName() {
        return getName();
    }

    @Override
    @NonNull
    public AndroidVersion getVersion() {
        return mDetails.getAndroidVersion();
    }

    @Override
    public String getVersionName() {
        return SdkVersionInfo.getVersionString(mDetails.getApiLevel());
    }

    @Override
    public int getRevision() {
        return mPackage.getVersion().getMajor();
    }

    @Override
    public boolean isPlatform() {
        return true;
    }

    @Override
    @Nullable
    public IAndroidTarget getParent() {
        return null;
    }

    @Override
    @NonNull
    public String getPath(int pathId) {
        switch (pathId) {
            case ANDROID_JAR:
                return getLocation() + SdkConstants.FN_FRAMEWORK_LIBRARY;
            case UI_AUTOMATOR_JAR:
                return getLocation() + SdkConstants.FN_UI_AUTOMATOR_LIBRARY;
            case SOURCES:
                if (mSourcesPath != null) {
                    return mSourcesPath.getPath();
                }
                // It seems that such a path doesn't usually exist, but this is left here to
                // preserve the old behavior.
                return getLocation() + SdkConstants.FD_ANDROID_SOURCES;
            case ANDROID_AIDL:
                return getLocation() + SdkConstants.FN_FRAMEWORK_AIDL;
            case SAMPLES:
                return getLocation() + SdkConstants.OS_PLATFORM_SAMPLES_FOLDER;
            case SKINS:
                return getLocation() + SdkConstants.OS_SKINS_FOLDER;
            case TEMPLATES:
                return getLocation() + SdkConstants.OS_PLATFORM_TEMPLATES_FOLDER;
            case DATA:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER;
            case ATTRIBUTES:
                return getLocation() + SdkConstants.OS_PLATFORM_ATTRS_XML;
            case MANIFEST_ATTRIBUTES:
                return getLocation() + SdkConstants.OS_PLATFORM_ATTRS_MANIFEST_XML;
            case RESOURCES:
                return getLocation() + SdkConstants.OS_PLATFORM_RESOURCES_FOLDER;
            case FONTS:
                return getLocation() + SdkConstants.OS_PLATFORM_FONTS_FOLDER;
            case LAYOUT_LIB:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_LAYOUTLIB_JAR;
            case WIDGETS:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_WIDGETS;
            case ACTIONS_ACTIVITY:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_ACTIONS_ACTIVITY;
            case ACTIONS_BROADCAST:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_ACTIONS_BROADCAST;
            case ACTIONS_SERVICE:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_ACTIONS_SERVICE;
            case CATEGORIES:
                return getLocation() + SdkConstants.OS_PLATFORM_DATA_FOLDER +
                        SdkConstants.FN_INTENT_CATEGORIES;
            case ANT:
                return getLocation() + SdkConstants.OS_PLATFORM_ANT_FOLDER;
            default:
                return getLocation();
        }
    }

    @Override
    @Nullable
    public BuildToolInfo getBuildToolInfo() {
        return mBuildToolInfo;
    }

    @Override
    @NonNull
    public List<String> getBootClasspath() {
        return ImmutableList.of(getPath(IAndroidTarget.ANDROID_JAR));
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getOptionalLibraries() {
        return mOptionalLibraries;
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getAdditionalLibraries() {
        return ImmutableList.of();
    }

    @Override
    public boolean hasRenderingLibrary() {
        return true;
    }

    @Override
    @NonNull
    public File[] getSkins() {
        return mSkins.toArray(new File[0]);
    }

    public int getLayoutlibApi() {
        return mDetails.getLayoutlib().getApi();
    }

    @Override
    @Nullable
    public File getDefaultSkin() {
        // TODO: validate choice to ignore property in sdk.properties

        // only one skin? easy.
        if (mSkins.size() == 1) {
            return mSkins.iterator().next();
        }
        String skinName;
        // otherwise try to find a good default.
        if (getVersion().getApiLevel() >= 11 && getVersion().getApiLevel() <= 13) {
            skinName = "WXGA";
        } else if (getVersion().getApiLevel() >= 4) {
            // at this time, this is the default skin for all older platforms that had 2+ skins.
            skinName = "WVGA800";
        } else {
            skinName = "HVGA"; // this is for 1.5 and earlier.
        }

        return new File(getFile(IAndroidTarget.SKINS), skinName);
    }

    /**
     * {@inheritDoc}
     *
     * For platforms this is always {@link SdkConstants#ANDROID_TEST_RUNNER_LIB}.
     */
    @Override
    @NonNull
    public String[] getPlatformLibraries() {
        return new String[]{SdkConstants.ANDROID_TEST_RUNNER_LIB};
    }

    @Override
    @Nullable
    public String getProperty(@NonNull String name) {
        return mBuildProps.get(name);
    }

    @Override
    @Nullable
    public Map<String, String> getProperties() {
        return mBuildProps;
    }

    @Override
    @NonNull
    public String getShortClasspathName() {
        return getName();
    }

    @Override
    @NonNull
    public String getClasspathName() {
        return getName();
    }

    @Override
    public boolean canRunOn(@NonNull IAndroidTarget target) {
        if (getVersion().isPreview()) {
            return target.getVersion().equals(getVersion());
        }
        return target.getVersion().getApiLevel() > getVersion().getApiLevel();
    }

    @Override
    @NonNull
    public String hashString() {
        return AndroidTargetHash.getPlatformHashString(getVersion());
    }

    @Override
    public int compareTo(@NonNull IAndroidTarget o) {
        int res = getVersion().compareTo(o.getVersion());
        if (res != 0) {
            return res;
        }
        return o.isPlatform() ? 0 : -1;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PlatformTarget && compareTo((PlatformTarget)obj) == 0;
    }

    @Override
    public int hashCode() {
        return hashString().hashCode();
    }
}
