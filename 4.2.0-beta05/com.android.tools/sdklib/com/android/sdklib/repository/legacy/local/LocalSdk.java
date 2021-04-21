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

import static com.android.sdklib.repository.PkgProps.*;
import static com.android.sdklib.repository.legacy.remote.internal.sources.RepoConstants.*;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.sdklib.AndroidVersionHelper;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.PackageParserUtils;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDescExtra;
import com.android.sdklib.repository.legacy.descriptors.PkgType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/**
 * This class keeps information on the current locally installed SDK.
 * It tries to lazily load information as much as possible.
 * <p>
 * Packages are accessed by their type and a main query attribute, depending on the
 * package type. There are different versions of {@link #getPkgInfo} which depend on the
 * query attribute.
 *
 * <table border='1' cellpadding='3' summary="">
 * <tr>
 * <th>Type</th>
 * <th>Query parameter</th>
 * <th>Getter</th>
 * </tr>
 *
 * <tr>
 * <td>Tools</td>
 * <td>Unique instance</td>
 * <td>{@code getPkgInfo(PkgType.PKG_TOOLS)} ⇒ {@link LocalPkgInfo}</td>
 * </tr>
 *
 * <tr>
 * <td>Platform-Tools</td>
 * <td>Unique instance</td>
 * <td>{@code getPkgInfo(PkgType.PKG_PLATFORM_TOOLS)} ⇒ {@link LocalPkgInfo}</td>
 * </tr>
 *
 * <tr>
 * <td>Docs</td>
 * <td>Unique instance</td>
 * <td>{@code getPkgInfo(PkgType.PKG_DOCS)} ⇒ {@link LocalPkgInfo}</td>
 * </tr>
 *
 * <tr>
 * <td>Build-Tools</td>
 * <td>{@link Revision}</td>
 * <td>{@code getLatestBuildTool()} ⇒ {@link BuildToolInfo}, <br>
 *     or {@code getBuildTool(Revision)} ⇒ {@link BuildToolInfo}, <br>
 *     or {@code getPkgInfo(PkgType.PKG_BUILD_TOOLS, Revision)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgsInfos(PkgType.PKG_BUILD_TOOLS)} ⇒ {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Extras</td>
 * <td>String vendor/path</td>
 * <td>{@code getExtra(String)} ⇒ {@link LocalExtraPkgInfo}, <br>
 *     or {@code getPkgInfo(PkgType.PKG_EXTRAS, String)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgsInfos(PkgType.PKG_EXTRAS)} ⇒ {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Sources</td>
 * <td>{@link AndroidVersion}</td>
 * <td>{@code getPkgInfo(PkgType.PKG_SOURCES, AndroidVersion)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgsInfos(PkgType.PKG_SOURCES)} ⇒ {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Samples</td>
 * <td>{@link AndroidVersion}</td>
 * <td>{@code getPkgInfo(PkgType.PKG_SAMPLES, AndroidVersion)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgsInfos(PkgType.PKG_SAMPLES)} ⇒ {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Platforms</td>
 * <td>{@link AndroidVersion}</td>
 * <td>{@code getPkgInfo(PkgType.PKG_PLATFORMS, AndroidVersion)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgInfo(PkgType.PKG_ADDONS, String)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgsInfos(PkgType.PKG_PLATFORMS)} ⇒ {@link LocalPkgInfo}[], <br>
 *     or {@code getTargetFromHashString(String)} ⇒ {@link IAndroidTarget}</td>
 * </tr>
 *
 * <tr>
 * <td>Add-ons</td>
 * <td>{@link AndroidVersion} x String vendor/path</td>
 * <td>{@code getPkgInfo(PkgType.PKG_ADDONS, String)} ⇒ {@link LocalPkgInfo}, <br>
 *     or {@code getPkgsInfos(PkgType.PKG_ADDONS)}    ⇒ {@link LocalPkgInfo}[], <br>
 *     or {@code getTargetFromHashString(String)} ⇒ {@link IAndroidTarget}</td>
 * </tr>
 *
 * <tr>
 * <td>System images</td>
 * <td>{@link AndroidVersion} x {@link String} ABI</td>
 * <td>{@code getPkgsInfos(PkgType.PKG_SYS_IMAGES)} ⇒ {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * </table>
 *
 * Apps/libraries that use it are encouraged to keep an existing instance around
 * (using a singleton or similar mechanism).
 * <p>
 * Threading: All accessor methods are synchronized on the same internal lock so
 * it's safe to call them from any thread, even concurrently. <br>
 * A method like {@code getPkgsInfos} returns a copy of its data array, which objects are
 * not altered after creation, so its value is not influenced by the internal state after
 * it returns.
 * <p>
 *
 * Implementation Background:
 * <ul>
 * <li> The sdk manager has a set of "Package" classes that cover both local
 *      and remote SDK operations.
 * <li> Goal was to split it in 2 cleanly separated parts: {@link LocalSdk} parses sdk on disk,
 *      and a separate class wraps the downloaded manifest (this is now handled within Studio only)
 * <li> The local SDK should be a singleton accessible somewhere, so there will be one in ADT
 *      (via the Sdk instance), one in Studio, and one in the command line tool. <br>
 *      Right now there's a bit of mess with some classes creating a temp LocalSdkParser,
 *      some others using an SdkManager instance, and that needs to be sorted out.
 * <li> As a transition, the SdkManager instance wraps a LocalSdk and uses this. Eventually the
 *      SdkManager.java class will go away (its name is totally misleading, for starters.)
 * <li> The current LocalSdkParser stays as-is for compatibility purposes and the goal is also
 *      to totally remove it when the SdkManager class goes away.
 * </ul>
 * @version 2 of the {@code SdkManager} class, essentially.
 * @deprecated in favor of {@link AndroidSdkHandler}/{@link RepoManager}.
 */
@Deprecated
public class LocalSdk {

    /** Location of the SDK. Maybe null. Can be changed. */
    private File mSdkRoot;
    /** File operation object. (Used for overriding in mock testing.) */
    private final FileOp mFileOp;
    /** List of package information loaded so far. Lazily populated. */
    @GuardedBy(value="mLocalPackages")
    private final Multimap<PkgType, LocalPkgInfo> mLocalPackages = TreeMultimap.create();
    /** Directories already parsed into {@link #mLocalPackages}. */
    @GuardedBy(value="mLocalPackages")
    private final Multimap<PkgType, LocalDirInfo> mVisitedDirs = HashMultimap.create();
    /** A legacy build-tool for older platform-tools < 17. */
    private BuildToolInfo mLegacyBuildTools;

    /**
     * Creates an initial LocalSdk instance with an unknown location.
     */
    public LocalSdk() {
        mFileOp = FileOpUtils.create();
    }

    /**
     * Creates an initial LocalSdk instance for a known SDK location.
     *
     * @param sdkRoot The location of the SDK root folder.
     */
    public LocalSdk(@NonNull File sdkRoot) {
        this();
        setLocation(sdkRoot);
    }

    /**
     * Creates an initial LocalSdk instance with an unknown location. This is designed for unit
     * tests to override the {@link FileOp} being used.
     *
     * @param fileOp The alternate {@link FileOp} to use for all file-based interactions.
     */
    @VisibleForTesting
    public LocalSdk(@NonNull FileOp fileOp) {
        mFileOp = fileOp;
    }

    /*
     * Returns the current FileOp being used.
     */
    @NonNull
    public FileOp getFileOp() {
        return mFileOp;
    }

    /**
     * Sets or changes the SDK root location. This also clears any cached information.
     *
     * @param sdkRoot The location of the SDK root folder.
     */
    public void setLocation(@NonNull File sdkRoot) {
        assert sdkRoot != null;
        mSdkRoot = sdkRoot;
        clearLocalPkg(PkgType.PKG_ALL);
    }

    /**
     * Location of the SDK. Maybe null. Can be changed.
     *
     * @return The location of the SDK. Null if not initialized yet.
     */
    @Nullable
    public File getLocation() {
        return mSdkRoot;
    }

    /**
     * Location of the SDK. Maybe null. Can be changed.
     * The getLocation() API replaces this function.
     * @return The location of the SDK. Null if not initialized yet.
     */
    @Deprecated
    @Nullable
    public String getPath() {
        return mSdkRoot != null ? mSdkRoot.getPath() : null;
    }

    /**
     * Clear the tracked visited folders and the cached {@link LocalPkgInfo} for the
     * given filter types.
     *
     * @param filters A set of PkgType constants or {@link PkgType#PKG_ALL} to clear everything.
     */
    public void clearLocalPkg(@NonNull EnumSet<PkgType> filters) {
        mLegacyBuildTools = null;

        synchronized (mLocalPackages) {
            for (PkgType filter : filters) {
                mVisitedDirs.removeAll(filter);
                mLocalPackages.removeAll(filter);
            }
        }
    }

    //--------- Generic querying ---------


    /**
     * Retrieves information on a package identified by an {@link IPkgDesc}.
     *
     * @param descriptor {@link IPkgDesc} describing a package.
     * @return The first package found with the same descriptor or null.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull IPkgDesc descriptor) {

        for (LocalPkgInfo pkg : getPkgsInfos(EnumSet.of(descriptor.getType()))) {
            IPkgDesc d = pkg.getDesc();
            if (d.equals(descriptor)) {
                return pkg;
            }
        }

        return null;
    }

    /**
     * Retrieves information on a package identified by an {@link AndroidVersion}.
     *
     * Note: don't use this for {@link PkgType#PKG_SYS_IMAGE} since there can be more than
     * one ABI and this method only returns a single package per filter type.
     *
     * @param filter {@link PkgType#PKG_PLATFORM}, {@link PkgType#PKG_SAMPLE}
     *                or {@link PkgType#PKG_SOURCE}.
     * @param version The {@link AndroidVersion} specific for this package type.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter, @NonNull AndroidVersion version) {
        assert filter == PkgType.PKG_PLATFORM ||
                filter == PkgType.PKG_SAMPLE ||
                filter == PkgType.PKG_SOURCE;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            IPkgDesc d = pkg.getDesc();
            if (d.hasAndroidVersion() && d.getAndroidVersion().equals(version)) {
                return pkg;
            }
        }

        return null;
    }

    /**
     * Retrieves information on a package identified by its {@link Revision}.
     * <p>
     * Note that {@link PkgType#PKG_TOOLS} and {@link PkgType#PKG_PLATFORM_TOOLS}
     * are unique in a local SDK so you'll want to use {@link #getPkgInfo(PkgType)}
     * to retrieve them instead.
     *
     * @param filter {@link PkgType#PKG_BUILD_TOOLS}.
     * @param revision The {@link Revision} uniquely identifying this package.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter, @NonNull Revision revision) {

        assert filter == PkgType.PKG_BUILD_TOOLS;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            IPkgDesc d = pkg.getDesc();
            if (d.getRevision().equals(revision)) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Retrieves information on a package identified by its {@link String} path.
     * <p>
     * For add-ons and platforms, the path is the target hash string
     * (see {@link AndroidTargetHash} for helpers methods to generate this string.)
     *
     * @param filter {@link PkgType#PKG_ADDON}, {@link PkgType#PKG_PLATFORM}.
     * @param path The vendor/path uniquely identifying this package.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter, @NonNull String path) {

        assert filter == PkgType.PKG_ADDON ||
                filter == PkgType.PKG_PLATFORM;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            IPkgDesc d = pkg.getDesc();
            if (d.hasPath() && path.equals(d.getPath())) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Retrieves information on a package identified by both vendor and path strings.
     * <p>
     * For add-ons the path is target hash string
     * (see {@link AndroidTargetHash} for helpers methods to generate this string.)
     *
     * @param filter {@link PkgType#PKG_EXTRA}, {@link PkgType#PKG_ADDON}.
     * @param vendor The vendor id of the extra package.
     * @param path The path uniquely identifying this package for its vendor.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter,
            @NonNull String vendor,
            @NonNull String path) {

        assert filter == PkgType.PKG_EXTRA ||
                filter == PkgType.PKG_ADDON;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            IPkgDesc d = pkg.getDesc();
            if (d.hasVendor() && vendor.equals(d.getVendor().getId())) {
                if (d.hasPath() && path.equals(d.getPath())) {
                    return pkg;
                }
            }
        }
        return null;
    }

    /**
     * Retrieves information on an extra package identified by its {@link String} vendor/path.
     *
     * @param vendor The vendor id of the extra package.
     * @param path The path uniquely identifying this package for its vendor.
     * @return An existing extra package information or null if not found.
     */
    @Nullable
    public LocalExtraPkgInfo getExtra(@NonNull String vendor, @NonNull String path) {
        return (LocalExtraPkgInfo) getPkgInfo(PkgType.PKG_EXTRA, vendor, path);
    }

    /**
     * For unique local packages. Returns the cached LocalPkgInfo for the requested type. Loads it
     * from disk if not cached.
     *
     * @param filter {@link PkgType#PKG_TOOLS} or {@link PkgType#PKG_PLATFORM_TOOLS} or {@link
     *     PkgType#PKG_DOC}.
     * @return null if the package is not installed.
     */
    @Nullable
    @SuppressWarnings("IncompatibleArgumentType") // LocalDirInfo has an ugly equals method.
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter) {
        if (filter != PkgType.PKG_TOOLS &&
                filter != PkgType.PKG_PLATFORM_TOOLS &&
                filter != PkgType.PKG_DOC &&
                filter != PkgType.PKG_NDK &&
                filter != PkgType.PKG_LLDB) {
            assert false;
            return null;
        }

        LocalPkgInfo info = null;
        synchronized (mLocalPackages) {
            Collection<LocalPkgInfo> existing = mLocalPackages.get(filter);
            assert existing.size() <= 1;
            if (!existing.isEmpty()) {
                return existing.iterator().next();
            }

            File uniqueDir = new File(mSdkRoot, filter.getFolderName());

            if (!mVisitedDirs.containsEntry(filter, new LocalDirInfo.MapComparator(uniqueDir))) {
                switch(filter) {
                    case PKG_TOOLS:
                        info = scanTools(uniqueDir);
                        break;
                    case PKG_PLATFORM_TOOLS:
                        info = scanPlatformTools(uniqueDir);
                        break;
                    case PKG_DOC:
                        info = scanDoc(uniqueDir);
                        break;
                    case PKG_NDK:
                        info = scanNdk(uniqueDir);
                        break;
                    case PKG_LLDB:
                        info = scanLLDB(uniqueDir);
                        break;
                    default:
                        break;
                }
            }

            // Whether we have found a valid pkg or not, this directory has been visited.
            mVisitedDirs.put(filter, new LocalDirInfo(mFileOp, uniqueDir));

            if (info != null) {
                mLocalPackages.put(filter, info);
            }
        }

        return info;
    }

    /**
     * Retrieve all the info about the requested package type.
     * This is used for the package types that have one or more instances, each with different
     * versions.
     * The resulting array is sorted according to the PkgInfo's sort order.
     * <p>
     * Note: you can use this with {@link PkgType#PKG_TOOLS}, {@link PkgType#PKG_PLATFORM_TOOLS} and
     * {@link PkgType#PKG_DOC} but since there can only be one package of these types, it is
     * more efficient to use {@link #getPkgInfo(PkgType)} to query them.
     *
     * @param filter One of {@link PkgType} constants.
     * @return A list (possibly empty) of matching installed packages. Never returns null.
     */
    @NonNull
    public LocalPkgInfo[] getPkgsInfos(@NonNull PkgType filter) {
        return getPkgsInfos(EnumSet.of(filter));
    }

    /**
     * Retrieve all the info about the requested package types. This is used for the package types
     * that have one or more instances, each with different versions. The resulting array is sorted
     * according to the PkgInfo's sort order.
     *
     * <p>To force the LocalSdk parser to load <b>everything</b>, simply call this method with the
     * {@link PkgType#PKG_ALL} argument to load all the known package types.
     *
     * <p>Note: you can use this with {@link PkgType#PKG_TOOLS}, {@link PkgType#PKG_PLATFORM_TOOLS}
     * and {@link PkgType#PKG_DOC} but since there can only be one package of these types, it is
     * more efficient to use {@link #getPkgInfo(PkgType)} to query them.
     *
     * @param filters One or more of {@link PkgType#PKG_ADDON}, {@link PkgType#PKG_PLATFORM}, {@link
     *     PkgType#PKG_BUILD_TOOLS}, {@link PkgType#PKG_EXTRA}, {@link PkgType#PKG_SOURCE}, {@link
     *     PkgType#PKG_SYS_IMAGE}
     * @return A list (possibly empty) of matching installed packages. Never returns null.
     */
    @NonNull
    @SuppressWarnings("IncompatibleArgumentType") // LocalDirInfo has an ugly equals method.
    public LocalPkgInfo[] getPkgsInfos(@NonNull EnumSet<PkgType> filters) {
        List<LocalPkgInfo> list = Lists.newArrayList();

        for (PkgType filter : filters) {
            if (filter == PkgType.PKG_TOOLS ||
                    filter == PkgType.PKG_PLATFORM_TOOLS ||
                    filter == PkgType.PKG_DOC ||
                    filter == PkgType.PKG_NDK ||
                    filter == PkgType.PKG_LLDB) {
                LocalPkgInfo info = getPkgInfo(filter);
                if (info != null) {
                    list.add(info);
                }
            } else {
                synchronized (mLocalPackages) {
                    Collection<LocalPkgInfo> existing = mLocalPackages.get(filter);
                    assert existing != null; // Multimap returns an empty set if not found

                    if (!existing.isEmpty()) {
                        list.addAll(existing);
                        continue;
                    }

                    File subDir = new File(mSdkRoot, filter.getFolderName());

                    if (!mVisitedDirs.containsEntry(filter, new LocalDirInfo.MapComparator(subDir))) {
                        switch(filter) {
                            case PKG_BUILD_TOOLS:
                                scanBuildTools(subDir, existing);
                                break;

                            case PKG_PLATFORM:
                                scanPlatforms(subDir, existing);
                                break;

                            case PKG_SYS_IMAGE:
                                scanSysImages(subDir, existing, false);
                                break;

                            case PKG_ADDON_SYS_IMAGE:
                                scanSysImages(subDir, existing, true);
                                break;

                            case PKG_ADDON:
                                scanAddons(subDir, existing);
                                break;

                            case PKG_SAMPLE:
                                scanSamples(subDir, existing);
                                break;

                            case PKG_SOURCE:
                                scanSources(subDir, existing);
                                break;

                            case PKG_EXTRA:
                                scanExtras(subDir, existing);
                                break;

                            case PKG_TOOLS:
                            case PKG_PLATFORM_TOOLS:
                            case PKG_DOC:
                            case PKG_NDK:
                            case PKG_LLDB:
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "Unsupported pkg type " + filter.toString());
                        }
                        mVisitedDirs.put(filter, new LocalDirInfo(mFileOp, subDir));
                        list.addAll(existing);
                    }
                }
            }
        }

        Collections.sort(list);
        return list.toArray(new LocalPkgInfo[0]);
    }

    //---------- Package-specific querying --------

    /**
     * Returns the {@link BuildToolInfo} for the given revision.
     *
     * @param revision The requested revision.
     * @return A {@link BuildToolInfo}. Can be null if {@code revision} is null or is
     *  not part of the known set returned by {@code getPkgsInfos(PkgType.PKG_BUILD_TOOLS)}.
     */
    @Nullable
    public BuildToolInfo getBuildTool(@Nullable Revision revision) {
        LocalPkgInfo pkg = getPkgInfo(PkgType.PKG_BUILD_TOOLS, revision);
        if (pkg instanceof LocalBuildToolPkgInfo) {
            return ((LocalBuildToolPkgInfo) pkg).getBuildToolInfo();
        }
        return null;
    }

    /**
     * Returns the highest build-tool revision known, or null if there are are no build-tools.
     * <p>
     * If no specific build-tool package is installed but the platform-tools is lower than 17,
     * then this creates and returns a "legacy" built-tool package using platform-tools.
     * (We only split build-tools out of platform-tools starting with revision 17,
     *  before they were both the same thing.)
     *
     * @return The highest build-tool revision known, or null.
     */
    @Nullable
    public BuildToolInfo getLatestBuildTool() {
        if (mLegacyBuildTools != null) {
            return mLegacyBuildTools;
        }

        LocalPkgInfo[] pkgs = getPkgsInfos(PkgType.PKG_BUILD_TOOLS);

        if (pkgs.length == 0) {
            LocalPkgInfo ptPkg = getPkgInfo(PkgType.PKG_PLATFORM_TOOLS);
            if (ptPkg instanceof LocalPlatformToolPkgInfo &&
                    ptPkg.getDesc().getRevision().compareTo(new Revision(17)) < 0) {
                // older SDK, create a compatible build-tools
                mLegacyBuildTools = createLegacyBuildTools((LocalPlatformToolPkgInfo) ptPkg);
                return mLegacyBuildTools;
            }
            return null;
        }

        assert pkgs.length > 0;

        // Note: the pkgs come from a TreeMultimap so they should already be sorted.
        // Just in case, sort them again.
        Arrays.sort(pkgs);

        // LocalBuildToolPkgInfo's comparator sorts on its Revision so we just
        // need to take the latest element.

        LocalBuildToolPkgInfo preview = null;
        for (int i = pkgs.length - 1; i >= 0; i--) {
            LocalPkgInfo pkg = pkgs[i];
            // Don't want to include preview build tools
            if (pkg instanceof LocalBuildToolPkgInfo) {
                if (!pkg.getDesc().isPreview()) {
                    return ((LocalBuildToolPkgInfo)pkg).getBuildToolInfo();
                }
                else if (preview == null) {
                    preview = (LocalBuildToolPkgInfo)pkg;
                }
            }
        }

        // fall back to preview if we didn't find a non-preview
        return preview != null ? preview.getBuildToolInfo() : null;
    }

    @NonNull
    private BuildToolInfo createLegacyBuildTools(@NonNull LocalPlatformToolPkgInfo ptInfo) {
        File platformTools = new File(getLocation(), SdkConstants.FD_PLATFORM_TOOLS);
        File platformToolsLib = ptInfo.getLocalDir();
        File platformToolsRs = new File(platformTools, SdkConstants.FN_FRAMEWORK_RENDERSCRIPT);

        return BuildToolInfo.modifiedLayout(
                ptInfo.getDesc().getRevision(),
                platformTools,
                new File(platformTools, SdkConstants.FN_AAPT),
                new File(platformTools, SdkConstants.FN_AIDL),
                new File(platformTools, SdkConstants.FN_DX),
                new File(platformToolsLib, SdkConstants.FN_DX_JAR),
                new File(platformTools, SdkConstants.FN_RENDERSCRIPT),
                new File(platformToolsRs, SdkConstants.FN_FRAMEWORK_INCLUDE),
                new File(platformToolsRs, SdkConstants.FN_FRAMEWORK_INCLUDE_CLANG),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new File(platformTools, SdkConstants.FN_ZIPALIGN),
                null);
    }

    // -------------

    /**
     * Try to find a tools package at the given location.
     * Returns null if not found.
     */
    private LocalToolPkgInfo scanTools(File toolFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(toolFolder, SdkConstants.FN_SOURCE_PROP));
        Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }
        rev = fullySpecifyRevision(rev);


        Revision minPlatToolsRev =
                PackageParserUtils.getRevisionProperty(props, PkgProps.MIN_PLATFORM_TOOLS_REV);
        if (minPlatToolsRev == null) {
            minPlatToolsRev = Revision.NOT_SPECIFIED;
        }

        LocalToolPkgInfo info = new LocalToolPkgInfo(this, toolFolder, props, rev, minPlatToolsRev);

        // We're not going to check that all tools are present. At the very least
        // we should expect to find android and an emulator adapted to the current OS.
        // Note that since we're looking for old-style SDK components here we expect only the
        // old-style "tools" that contains "android".
        boolean hasEmulator = false;
        boolean hasAndroid = false;
        String android1 = androidCmdName().replace(".bat", ".exe");
        String android2 = android1.indexOf('.') == -1 ? null : android1.replace(".exe", ".bat");
        File[] files = mFileOp.listFiles(toolFolder);
        for (File file : files) {
            String name = file.getName();
            if (SdkConstants.FN_EMULATOR.equals(name)) {
                hasEmulator = true;
            }
            if (android1.equals(name) || (android2 != null && android2.equals(name))) {
                hasAndroid = true;
            }
        }
        if (!hasAndroid) {
            info.appendLoadError("Missing %1$s", androidCmdName());
        }
        if (!hasEmulator) {
            info.appendLoadError("Missing %1$s", SdkConstants.FN_EMULATOR);
        }

        return info;
    }

    /**
     * Returns the appropriate name for the 'android' command, which is 'android.bat' for
     * Windows and 'android' for all other platforms.
     */
    @VisibleForTesting
    public static String androidCmdName() {
        String os = System.getProperty("os.name");
        String cmd = "android";
        if (os.startsWith("Windows")) {
            cmd += ".bat";
        }
        return cmd;
    }

    /**
     * Creates a new revision with at least three components (major, minor, micro)
     * based on the given revision. This is important since in the past we would
     * sometimes write out revisions with only one component when internally we
     * required that they have more, and would convert using the specific Revision
     * subclass.
     * @param rev
     * @return
     */
    private static Revision fullySpecifyRevision(Revision rev) {
        // Since we used to require a complete revision
        if (!rev.isPreview()) {
            rev = new Revision(rev.getMajor(), rev.getMinor(), rev.getMicro());
        }
        return rev;
    }

    /**
     * Try to find a platform-tools package at the given location.
     * Returns null if not found.
     */
    private LocalPlatformToolPkgInfo scanPlatformTools(File ptFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(ptFolder, SdkConstants.FN_SOURCE_PROP));
        Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }
        // Since we used to require a complete revision
        rev = fullySpecifyRevision(rev);

        LocalPlatformToolPkgInfo info = new LocalPlatformToolPkgInfo(this, ptFolder, props, rev);
        return info;
    }

    /**
     * Try to find a docs package at the given location.
     * Returns null if not found.
     */
    private LocalDocPkgInfo scanDoc(File docFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(docFolder, SdkConstants.FN_SOURCE_PROP));
        Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }

        try {
            AndroidVersion vers = AndroidVersionHelper.create(props);
            LocalDocPkgInfo info = new LocalDocPkgInfo(this, docFolder, props, vers, rev);

            // To start with, a doc folder should have an "index.html" to be acceptable.
            // We don't actually check the content of the file.
            if (!mFileOp.isFile(new File(docFolder, "index.html"))) {
                info.appendLoadError("Missing index.html");
            }
            return info;

        } catch (AndroidVersionException e) {
            return null; // skip invalid or missing android version.
        }
    }

    /**
     * Try to find an NDK package at the given location.
     * Returns null if not found.
     */
    @Nullable
    private LocalNdkPkgInfo scanNdk(@NonNull File ndkFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(ndkFolder, SdkConstants.FN_SOURCE_PROP));
        Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }

        return new LocalNdkPkgInfo(this, ndkFolder, props, rev);
    }

    /**
     * Try to find an LLDB package at the given location.
     * Returns null if not found.
     */
    @Nullable
    private LocalLLDBPkgInfo scanLLDB(@NonNull File lldbFolder) {
        File pinnedLLDBFolder = new File(lldbFolder, LocalLLDBPkgInfo.PINNED_REVISION.toString());
        // Can we find some properties?
        Properties props = parseProperties(new File(pinnedLLDBFolder, SdkConstants.FN_SOURCE_PROP));
        Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }

        return new LocalLLDBPkgInfo(this, pinnedLLDBFolder, props, rev);
    }

    /**
     * Helper used by scanXyz methods below to check whether a directory should be visited. It can
     * be skipped if it's not a directory or if it's already marked as visited in mVisitedDirs for
     * the given package type -- in which case the directory is added to the visited map.
     *
     * @param pkgType The package type being scanned.
     * @param directory The file or directory to check.
     * @return False if directory can/should be skipped. True if directory should be visited, in
     *     which case it's registered in mVisitedDirs.
     */
    @SuppressWarnings("IncompatibleArgumentType") // LocalDirInfo has an ugly equals method.
    private boolean shouldVisitDir(@NonNull PkgType pkgType, @NonNull File directory) {
        if (!mFileOp.isDirectory(directory)) {
            return false;
        }
        synchronized (mLocalPackages) {
            if (mVisitedDirs.containsEntry(pkgType, new LocalDirInfo.MapComparator(directory))) {
                return false;
            }
            mVisitedDirs.put(pkgType, new LocalDirInfo(mFileOp, directory));
        }
        return true;
    }

    private void scanBuildTools(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        // The build-tool root folder contains a list of per-revision folders.
        for (File buildToolDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(PkgType.PKG_BUILD_TOOLS, buildToolDir)) {
                continue;
            }

            Properties props = parseProperties(new File(buildToolDir, SdkConstants.FN_SOURCE_PROP));
            Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            // Since we used to require a complete revision
            rev = fullySpecifyRevision(rev);

            BuildToolInfo btInfo = BuildToolInfo.fromStandardDirectoryLayout(rev, buildToolDir);
            LocalBuildToolPkgInfo pkgInfo =
                    new LocalBuildToolPkgInfo(this, buildToolDir, props, rev, btInfo);
            outCollection.add(pkgInfo);
        }
    }

    private void scanPlatforms(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(PkgType.PKG_PLATFORM, platformDir)) {
                continue;
            }

            Properties props = parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));
            Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            Revision minToolsRev =
                    PackageParserUtils.getRevisionProperty(props, PkgProps.MIN_TOOLS_REV);
            if (minToolsRev == null) {
                minToolsRev = Revision.NOT_SPECIFIED;
            }

            try {
                AndroidVersion vers = AndroidVersionHelper.create(props);

                LocalPlatformPkgInfo pkgInfo =
                        new LocalPlatformPkgInfo(this, platformDir, props, vers, rev, minToolsRev);
                outCollection.add(pkgInfo);

            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanAddons(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File addonDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(PkgType.PKG_ADDON, addonDir)) {
                continue;
            }

            Properties props = parseProperties(new File(addonDir, SdkConstants.FN_SOURCE_PROP));
            Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }
            // Since we used to require a complete revision
            rev = fullySpecifyRevision(rev);

            try {
                AndroidVersion vers = AndroidVersionHelper.create(props);

                // Starting with addon-4.xsd, we have vendor-id and name-id available
                // in the add-on source properties so we'll use that directly. Otherwise, try manifest.ini
                Properties allProps = new Properties();
                Properties manifestProps = parseProperties(new File(addonDir, SdkConstants.FN_MANIFEST_INI));
                if (manifestProps != null) {
                    allProps.putAll(manifestProps);
                }
                allProps.putAll(props);

                String nameId     = findProperty(allProps, ADDON_NAME_ID, NODE_NAME_ID);
                String nameDisp   = findProperty(allProps, ADDON_NAME_DISPLAY, NODE_NAME_DISPLAY, ADDON_NAME, NODE_NAME);
                String vendorId   = findProperty(allProps, ADDON_VENDOR_ID, NODE_VENDOR_ID);
                String vendorDisp = findProperty(allProps, ADDON_VENDOR_DISPLAY, NODE_VENDOR_DISPLAY, ADDON_VENDOR, NODE_VENDOR);

                if (nameId == null) {
                    // Support earlier add-ons that only had a name display attribute
                    if (nameDisp == null) {
                        nameDisp = "Unknown";
                    }
                    nameId = LocalAddonPkgInfo.sanitizeDisplayToNameId(nameDisp);
                }

                if (nameId != null && nameDisp == null) {
                    nameDisp = LocalExtraPkgInfo.getPrettyName(null, nameId);
                }

                if (vendorId != null && vendorDisp == null) {
                    vendorDisp = LocalExtraPkgInfo.getPrettyName(null, nameId);
                }

                if (vendorId == null) {
                    // Support earlier add-ons that only had a vendor display attribute
                    if (vendorDisp == null) {
                        vendorDisp = "Unknown";
                    }
                    vendorId = LocalAddonPkgInfo.sanitizeDisplayToNameId(vendorDisp);
                }

                LocalAddonPkgInfo pkgInfo = new LocalAddonPkgInfo(
                        this, addonDir, props, vers, rev,
                        IdDisplay.create(vendorId, vendorDisp),
                        IdDisplay.create(nameId, nameDisp));
                outCollection.add(pkgInfo);

            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanSysImages(
            File collectionDir,
            Collection<LocalPkgInfo> outCollection,
            boolean scanAddons) {
        List<File> propFiles = Lists.newArrayList();
        PkgType type = scanAddons ? PkgType.PKG_ADDON_SYS_IMAGE : PkgType.PKG_SYS_IMAGE;

        // Create a list of folders that contains a source.properties file matching these patterns:
        // sys-img/target/tag/abi
        // sys-img/target/abis
        // sys-img/add-on-target/abi
        // sys-img/target/add-on/abi
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(type, platformDir)) {
                continue;
            }

            for (File dir1 : mFileOp.listFiles(platformDir)) {
                // dir1 might be either a tag or an abi folder.
                if (!shouldVisitDir(type, dir1)) {
                    continue;
                }

                File prop1 = new File(dir1, SdkConstants.FN_SOURCE_PROP);
                if (mFileOp.isFile(prop1)) {
                    // dir1 was a legacy abi folder.
                    if (!propFiles.contains(prop1)) {
                        propFiles.add(prop1);
                    }
                } else {
                    File[] dir1Files = mFileOp.listFiles(dir1);
                    for (File dir2 : dir1Files) {
                        // dir2 should be an abi folder in a tag folder.
                        if (!shouldVisitDir(type, dir2)) {
                            continue;
                        }

                        File prop2 = new File(dir2, SdkConstants.FN_SOURCE_PROP);
                        if (mFileOp.isFile(prop2)) {
                            if (!propFiles.contains(prop2)) {
                                propFiles.add(prop2);
                            }
                        }
                    }
                }
            }
        }

        for (File propFile : propFiles) {
            Properties props = parseProperties(propFile);
            Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            try {
                AndroidVersion vers = AndroidVersionHelper.create(props);

                IdDisplay tag = LocalSysImgPkgInfo.extractTagFromProps(props);
                String vendorId = props.getProperty(PkgProps.ADDON_VENDOR_ID, null);
                File abiDir = propFile.getParentFile();

                if (vendorId == null && !scanAddons) {
                    LocalSysImgPkgInfo pkgInfo =
                            new LocalSysImgPkgInfo(this, abiDir, props, vers, tag, abiDir.getName(), rev);
                    outCollection.add(pkgInfo);

                } else if (vendorId != null && scanAddons) {
                    String vendorDisp = props.getProperty(PkgProps.ADDON_VENDOR_DISPLAY, vendorId);
                    IdDisplay vendor = IdDisplay.create(vendorId, vendorDisp);

                    LocalAddonSysImgPkgInfo pkgInfo =
                            new LocalAddonSysImgPkgInfo(
                                    this, abiDir, props, vers, vendor, tag, abiDir.getName(), rev);
                    outCollection.add(pkgInfo);
                }

            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanSamples(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(PkgType.PKG_SAMPLE, platformDir)) {
                continue;
            }

            Properties props = parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));
            Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            Revision minToolsRev =
                    PackageParserUtils.getRevisionProperty(props, PkgProps.MIN_TOOLS_REV);
            if (minToolsRev == null) {
                minToolsRev = Revision.NOT_SPECIFIED;
            }

            try {
                AndroidVersion vers = AndroidVersionHelper.create(props);

                LocalSamplePkgInfo pkgInfo =
                        new LocalSamplePkgInfo(this, platformDir, props, vers, rev, minToolsRev);
                outCollection.add(pkgInfo);
            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanSources(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        // The build-tool root folder contains a list of per-revision folders.
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(PkgType.PKG_SOURCE, platformDir)) {
                continue;
            }

            Properties props = parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));
            Revision rev = PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            try {
                AndroidVersion vers = AndroidVersionHelper.create(props);

                LocalSourcePkgInfo pkgInfo =
                        new LocalSourcePkgInfo(this, platformDir, props, vers, rev);
                outCollection.add(pkgInfo);
            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanExtras(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File vendorDir : mFileOp.listFiles(collectionDir)) {
            if (!shouldVisitDir(PkgType.PKG_EXTRA, vendorDir)) {
                continue;
            }

            for (File extraDir : mFileOp.listFiles(vendorDir)) {
                if (!shouldVisitDir(PkgType.PKG_EXTRA, extraDir)) {
                    continue;
                }

                Properties props = parseProperties(new File(extraDir, SdkConstants.FN_SOURCE_PROP));
                Revision rev =
                        PackageParserUtils.getRevisionProperty(props, PkgProps.PKG_REVISION);
                if (rev == null) {
                    continue; // skip, no revision
                }
                // Since we used to require a three-part revision
                rev = fullySpecifyRevision(rev);

                String oldPaths =
                        PackageParserUtils.getProperty(props, PkgProps.EXTRA_OLD_PATHS, null);

                String vendorId   = vendorDir.getName();
                String vendorDisp = props.getProperty(PkgProps.EXTRA_VENDOR_DISPLAY);
                if (vendorDisp == null || vendorDisp.isEmpty()) {
                    vendorDisp = vendorId;
                }

                String displayName = props.getProperty(PkgProps.EXTRA_NAME_DISPLAY, null);

                LocalExtraPkgInfo pkgInfo = new LocalExtraPkgInfo(
                        this,
                        extraDir,
                        props,
                        IdDisplay.create(vendorId, vendorDisp),
                        extraDir.getName(),
                        displayName,
                        PkgDescExtra.convertOldPaths(oldPaths),
                        rev);
                outCollection.add(pkgInfo);
            }
        }
    }

    /**
     * Parses the given file as properties file if it exists.
     * Returns null if the file does not exist, cannot be parsed or has no properties.
     */
    private Properties parseProperties(File propsFile) {
        InputStream fis = null;
        try {
            if (mFileOp.exists(propsFile)) {
                fis = mFileOp.newFileInputStream(propsFile);

                Properties props = new Properties();
                props.load(fis);

                // To be valid, there must be at least one property in it.
                if (!props.isEmpty()) {
                    return props;
                }
            }
        } catch (IOException e) {
            // Ignore
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    private String findProperty(Properties properties, String... keys) {
        for (String key : keys) {
            String value = properties.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

}
