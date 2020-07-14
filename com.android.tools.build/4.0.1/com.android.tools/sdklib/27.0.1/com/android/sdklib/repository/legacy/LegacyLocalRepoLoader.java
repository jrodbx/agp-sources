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
import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.RepoPackageImpl;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.legacy.descriptors.PkgType;
import com.android.sdklib.repository.legacy.local.LocalAddonPkgInfo;
import com.android.sdklib.repository.legacy.local.LocalPkgInfo;
import com.android.sdklib.repository.legacy.local.LocalPlatformPkgInfo;
import com.android.sdklib.repository.legacy.local.LocalSdk;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@link FallbackLocalRepoLoader} that uses a {@link LocalSdk} to parse {@link LocalPkgInfo} and
 * convert them into {@link LocalPackage}s.
 */
public class LegacyLocalRepoLoader implements FallbackLocalRepoLoader {

    /**
     * The old {@link LocalSdk} we'll use to parse the old packages.
     */
    private final LocalSdk mLocalSdk;

    /**
     * Cache of packages found by {@link #mLocalSdk}.
     */
    private Map<File, LocalPkgInfo> mPkgs = null;

    private final FileOp mFop;

    /**
     * Create a new LegacyLocalRepoLoader, based on {@link LocalSdk}.
     *
     * @param root    The root directory of the SDK.
     * @param fop     {@link FileOp} to use. For normal operation should be {@link
     *                FileOpUtils#create()}.
     */
    public LegacyLocalRepoLoader(@NonNull File root, @NonNull FileOp fop) {
        mLocalSdk = new LocalSdk(fop);
        mLocalSdk.setLocation(root);
        mFop = fop;
    }

    /**
     * Tries to parse a package rooted in the specified directory.
     * @return A {@link LocalPackage} if one was found, otherwise null.
     */
    @Override
    @Nullable
    public LocalPackage parseLegacyLocalPackage(@NonNull File dir,
            @NonNull ProgressIndicator progress) {
        if (!mFop.exists(new File(dir, SdkConstants.FN_SOURCE_PROP))) {
            return null;
        }
        progress.logVerbose(String.format("Parsing legacy package: %s", dir));
        LocalPkgInfo info;
        if (mPkgs == null) {
            Map<File, LocalPkgInfo> result = Maps.newHashMap();
            mLocalSdk.clearLocalPkg(PkgType.PKG_ALL);
            for (LocalPkgInfo local : mLocalSdk.getPkgsInfos(PkgType.PKG_ALL)) {
                result.put(local.getLocalDir(), local);
            }
            mPkgs = result;
        }

        info = mPkgs.get(dir);
        if (info == null) {
            // If we had a source.properties in a nonstandard place, don't include it.
            return null;
        }

        return new LegacyLocalPackage(info, progress);
    }

    @Override
    public void refresh() {
        mPkgs = null;
    }

    @Override
    public boolean shouldParse(@NonNull File root) {
        return mFop.exists(new File(root, SdkConstants.FN_SOURCE_PROP));
    }

    /**
     * {@link LocalPackage} wrapper around a {@link LocalPkgInfo}.
     */
    class LegacyLocalPackage implements LocalPackage {

        private final ProgressIndicator mProgress;

        private final LocalPkgInfo mWrapped;

        LegacyLocalPackage(@NonNull LocalPkgInfo wrapped, @NonNull ProgressIndicator progress) {
            mWrapped = wrapped;
            mProgress = progress;
        }

        @Override
        @NonNull
        public TypeDetails getTypeDetails() {
            int layoutVersion = 0;
            if (mWrapped instanceof LocalPlatformPkgInfo) {
                layoutVersion = ((LocalPlatformPkgInfo) mWrapped).getLayoutlibApi();
            }
            List<OptionalLibrary> addonLibraries = Lists.newArrayList();
            if (mWrapped instanceof LocalAddonPkgInfo) {
                addonLibraries = LegacyRepoUtils
                        .parseLegacyAdditionalLibraries(mWrapped.getLocalDir(), mProgress, mFop);
            }
            return LegacyRepoUtils
              .createTypeDetails(mWrapped.getDesc(), layoutVersion, addonLibraries, getLocation(),
                mProgress, mFop);
        }

        @NonNull
        @Override
        public Revision getVersion() {
            return mWrapped.getDesc().getRevision();
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return LegacyRepoUtils.getDisplayName(mWrapped.getDesc());
        }

        @Override
        @Nullable
        public License getLicense() {
            License res = mWrapped.getDesc().getLicense();
            CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
            if (res == null) {
                res = factory.createLicenseType();
                res.setValue(mWrapped.getSourceProperties().getProperty(PkgProps.PKG_LICENSE));
                res.setId(
                        String.format(
                                Locale.US,
                                "license-%X",
                                mWrapped.getSourceProperties().hashCode()));
                res.setType("text");
            }
            return res;
        }

        @Override
        @NonNull
        public Collection<Dependency> getAllDependencies() {
            List<Dependency> result = Lists.newArrayList();
            Revision rev = mWrapped.getDesc().getMinPlatformToolsRev();
            CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
            if (rev != null) {
                result.add(factory.createDependencyType(rev, SdkConstants.FD_PLATFORM_TOOLS));
            }
            rev = mWrapped.getDesc().getMinToolsRev();
            if (rev != null) {
                result.add(factory.createDependencyType(rev, SdkConstants.FD_TOOLS));
            }
            return result;
        }

        @Override
        @NonNull
        public String getPath() {
            String relativePath = null;
            try {
                relativePath = FileOpUtils.makeRelative(mWrapped.getLocalSdk().getLocation(),
                        mWrapped.getLocalDir(), mFop);
            } catch (IOException e) {
                // nothing, we'll just not have a default path.
            }
            return LegacyRepoUtils.getLegacyPath(mWrapped.getDesc(), relativePath);
        }

        @Override
        public boolean obsolete() {
            return mWrapped.getDesc().isObsolete() ||
                   mWrapped.getDesc().getType() == PkgType.PKG_SAMPLE;
        }

        @Override
        @NonNull
        public CommonFactory createFactory() {
            return RepoManager.getCommonModule().createLatestFactory();
        }

        @NonNull
        @Override
        public RepoPackageImpl asMarshallable() {
            return LocalPackageImpl.create(this);
        }

        @Override
        public int compareTo(@NonNull RepoPackage o) {
            int result = ComparisonChain.start()
              .compare(getPath(), o.getPath())
              .compare(getVersion(), o.getVersion())
              .result();
            if (result != 0) {
                return result;
            }
            if (!(o instanceof LocalPackage)) {
                return getClass().getName().compareTo(o.getClass().getName());
            }
            return 0;
        }

        @Override
        @NonNull
        public File getLocation() {
            return mWrapped.getLocalDir();
        }

        @Override
        public void setInstalledPath(File root) {
            // Ignore, we already know our whole path.
        }
    }
}
