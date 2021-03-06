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
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.License;
import com.android.repository.api.RepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import java.nio.file.Path;

/**
 * {@link IPkgDesc} keeps information on individual SDK packages
 * (both local or remote packages definitions.)
 * <br>
 * Packages have different attributes depending on their type.
 * <p>
 * To create a new {@link IPkgDesc}, use one of the package-specific constructors
 * provided by {@code PkgDesc.Builder.newXxx()}.
 * <p>
 * To query packages capabilities, rely on {@link #getType()} and the {@code IPkgDesc.hasXxx()}
 * methods provided by {@link IPkgDesc}.
 *
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
public interface IPkgDesc extends Comparable<IPkgDesc> {

    /**
     * Returns the type of the package.
     * @return Returns one of the {@link PkgType} constants.
     */
    @NonNull
    PkgType getType();

    /**
     * Returns the list-display meta data of this package.
     * @return The list-display data, if available, or null.
     */
    @Nullable
    String getListDisplay();

    @Nullable
    IdDisplay getName();

    @Nullable
    String getDescriptionShort();

    @Nullable
    String getDescriptionUrl();

    @Nullable
    License getLicense();

    boolean isObsolete();

    /**
     * Returns the package's {@link Revision}.
     */
    @NonNull
    Revision getRevision();

  /**
     * Returns the package's {@link AndroidVersion} or null.
     * @return A non-null value if {@link #hasAndroidVersion()} is true; otherwise a null value.
     */
    @Nullable
    AndroidVersion getAndroidVersion();

    /**
     * Returns the package's path string or null.
     * <p>
     * For {@link PkgType#PKG_SYS_IMAGE}, the path is the system-image ABI. <br>
     * For {@link PkgType#PKG_PLATFORM}, the path is the platform hash string. <br>
     * For {@link PkgType#PKG_ADDON}, the path is the platform hash string. <br>
     * For {@link PkgType#PKG_EXTRA}, the path is the extra-path string. <br>
     *
     * @return A non-null value if {@link #hasPath()} is true; otherwise a null value.
     */
    @Nullable
    String getPath();

    /**
     * Returns the package's tag id-display tuple or null.
     *
     * @return A non-null tag if {@link #hasTag()} is true; otherwise a null value.
     */
    @Nullable
    IdDisplay getTag();

    /**
     * Returns the package's vendor-id string or null.
     * @return A non-null value if {@link #hasVendor()} is true; otherwise a null value.
     */
    @Nullable
    IdDisplay getVendor();

    /**
     * Returns the package's {@code min-tools-rev} or null.
     * @return A non-null value if {@link #hasMinToolsRev()} is true; otherwise a null value.
     */
    @Nullable
    Revision getMinToolsRev();

    /**
     * Returns the package's {@code min-platform-tools-rev} or null.
     * @return A non-null value if {@link #hasMinPlatformToolsRev()} is true; otherwise null.
     */
    @Nullable
    Revision getMinPlatformToolsRev();

    /**
     * Indicates whether <em>this</em> package descriptor is an update for the given
     * existing descriptor. Preview versions are never considered updates for non-
     * previews, and vice versa.
     *
     * @param existingDesc A non-null existing descriptor.
     * @return True if this package is an update for the given one.
     */
    boolean isUpdateFor(@NonNull IPkgDesc existingDesc);

  /**
   * Indicates whether <em>this</em> package descriptor is an update for the given
   * existing descriptor, using the given comparison method.
   *
   * @param existingDesc A non-null existing descriptor.
   * @param previewComparison The {@link Revision.PreviewComparison} method to use
   *                          when comparing the packages.
   * @return True if this package is an update for the given one.
   */
    boolean isUpdateFor(@NonNull IPkgDesc existingDesc,
                        @NonNull Revision.PreviewComparison previewComparison);

    /**
     * Returns a stable string id that can be used to reference this package, including
     * a suffix indicating that this package is a preview if it is.
     */
    @NonNull
    String getInstallId();

    /**
     * Returns a stable string id that can be used to reference this package, which
     * excludes the preview suffix.
     */
    String getBaseInstallId();

    /**
     * Returns the canonical location where such a package would be installed.
     *
     * @param sdkLocation The root of the SDK.
     * @return the canonical location where such a package would be installed.
     */
    @NonNull
    Path getCanonicalInstallFolder(@NonNull Path sdkLocation);

    /**
     * @return True if the revision of this package is a preview.
     */
    boolean isPreview();

    String getListDescription();

    boolean hasVendor();

    boolean hasAndroidVersion();

    boolean hasPath();

    boolean hasTag();

    boolean hasMinToolsRev();

    boolean hasMinPlatformToolsRev();
}

