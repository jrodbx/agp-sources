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

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Dependency;
import com.android.repository.api.Downloader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.SchemaModule;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.RepoPackageImpl;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.LayoutlibVersion;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.descriptors.PkgType;
import com.android.sdklib.repository.legacy.remote.RemotePkgInfo;
import com.android.sdklib.repository.legacy.remote.internal.archives.ArchFilter;
import com.android.sdklib.repository.legacy.remote.internal.packages.RemoteAddonPkgInfo;
import com.android.sdklib.repository.legacy.remote.internal.packages.RemotePlatformPkgInfo;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkAddonSource;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkRepoSource;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkSource;
import com.android.sdklib.repository.legacy.remote.internal.sources.SdkSysImgSource;
import com.android.sdklib.repository.targets.OptionalLibraryImpl;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * {@link FallbackRemoteRepoLoader} implementation that uses the old {@link SdkSource} mechanism for
 * parsing packages.
 */
public class LegacyRemoteRepoLoader implements FallbackRemoteRepoLoader {

    /**
     * Parses xml files using the {@link SdkSource} mechanism into {@link LegacyRemotePackage}s.
     */
    @NonNull
    @Override
    public Collection<RemotePackage> parseLegacyXml(@NonNull RepositorySource source,
            @NonNull Downloader downloader, @NonNull SettingsController settings,
            @NonNull ProgressIndicator progress) {
        SdkSource legacySource;
        RemotePkgInfo[] packages = null;
        Collection<SchemaModule<?>> modules = source.getPermittedModules();
        double progressMax = 0;
        double progressIncrement = 1. / modules.size();
        for (SchemaModule module : modules) {
            legacySource = null;
            if (module.equals(AndroidSdkHandler.getRepositoryModule())) {
                legacySource = new SdkRepoSource(source.getUrl(), "Legacy Repo Source");
            } else if (module.equals(AndroidSdkHandler.getAddonModule())) {
                legacySource = new SdkAddonSource(source.getUrl(), "Legacy Addon Source");
            } else if (module.equals(AndroidSdkHandler.getSysImgModule())) {
                legacySource = new SdkSysImgSource(source.getUrl(), "Legacy System Image Source");
            }
            if (legacySource != null) {
                legacySource.load(
                        downloader,
                        settings,
                        progress.createSubProgress(progressMax + progressIncrement));
                if (legacySource.getFetchError() != null) {
                    progress.logInfo(legacySource.getFetchError());
                }
                packages = legacySource.getPackages();
                if (packages != null) {
                    break;
                }
            }
            progressMax += progressIncrement;
            progress.setFraction(progressMax);
        }
        List<RemotePackage> result = Lists.newArrayList();
        if (packages != null) {
            for (RemotePkgInfo pkgInfo : packages) {
                if (pkgInfo.getPkgDesc().getType() == PkgType.PKG_SAMPLE) {
                    continue;
                }
                RemotePackage pkg = new LegacyRemotePackage(pkgInfo, source);
                result.add(pkg);
            }
        }
        return result;
    }

    /**
     * A {@link RemotePackage} implementation that wraps a {@link RemotePkgInfo}.
     */
    private class LegacyRemotePackage implements RemotePackage {

        private final RemotePkgInfo mWrapped;

        private RepositorySource mSource;

        private TypeDetails mDetails;

        LegacyRemotePackage(RemotePkgInfo remote, RepositorySource source) {
            mWrapped = remote;
            mSource = source;
        }

        @Override
        @NonNull
        public TypeDetails getTypeDetails() {
            if (mDetails == null) {
                int layoutlibApi = 0;
                if (mWrapped instanceof RemotePlatformPkgInfo) {
                    LayoutlibVersion layoutlibVersion = ((RemotePlatformPkgInfo) mWrapped)
                            .getLayoutLibVersion();
                    if (layoutlibVersion != null) {
                        layoutlibApi = layoutlibVersion.getApi();
                    }
                }
                List<OptionalLibrary> libs = Lists.newArrayList();
                if (mWrapped instanceof RemoteAddonPkgInfo) {
                    for (RemoteAddonPkgInfo.Lib wrappedLib : ((RemoteAddonPkgInfo) mWrapped)
                            .getLibs()) {
                        libs.add(new OptionalLibraryImpl(wrappedLib.getName(), new File(""),
                                wrappedLib.getDescription(), false));
                    }
                }
                ProgressIndicator progress = new ConsoleProgressIndicator();
                mDetails = LegacyRepoUtils
                        .createTypeDetails(mWrapped.getPkgDesc(), layoutlibApi, libs, null,
                                progress, FileOpUtils.create());
            }
            return mDetails;
        }

        @NonNull
        @Override
        public Revision getVersion() {
            return mWrapped.getRevision();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return LegacyRepoUtils.getDisplayName(mWrapped.getPkgDesc());
        }

        @Override
        public License getLicense() {
            return mWrapped.getLicense();
        }

        @NonNull
        @Override
        public Collection<Dependency> getAllDependencies() {
            // TODO: implement (this isn't implemented in the current version either)
            return ImmutableList.of();
        }

        @NonNull
        @Override
        public String getPath() {
            return LegacyRepoUtils.getLegacyPath(mWrapped.getPkgDesc(), null);
        }

        @NonNull
        @Override
        public CommonFactory createFactory() {
            return RepoManager.getCommonModule().createLatestFactory();
        }

        @NonNull
        @Override
        public RepoPackageImpl asMarshallable() {
            return RemotePackageImpl.create(this);
        }

        @Override
        public boolean obsolete() {
            return mWrapped.isObsolete();
        }

        @Override
        public int compareTo(RepoPackage o) {
            int res = ComparisonChain.start()
                    .compare(getPath(), o.getPath())
                    .compare(getVersion(), o.getVersion())
                    .result();
            if (res != 0) {
                return res;
            }
            if (!(o instanceof RemotePackage)) {
                return getClass().getName().compareTo(o.getClass().getName());
            }
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RepoPackage && compareTo((RepoPackage) obj) == 0;
        }

        @Override
        public int hashCode() {
            return getPath().hashCode() * 37 + getVersion().hashCode();
        }

        @NonNull
        @Override
        public RepositorySource getSource() {
            return mSource;
        }

        @Override
        public void setSource(@NonNull RepositorySource source) {
            mSource = source;
        }

        @Override
        public Archive getArchive() {
            for (com.android.sdklib.repository.legacy.remote.internal.archives.Archive archive : mWrapped
                    .getArchives()) {
                if (archive.isCompatible()) {
                    CommonFactory f = RepoManager.getCommonModule().createLatestFactory();
                    Archive arch = f.createArchiveType();
                    Archive.CompleteType complete = f.createCompleteType();
                    complete.setChecksum(archive.getChecksum());
                    complete.setSize(archive.getSize());
                    complete.setUrl(archive.getUrl());
                    arch.setComplete(complete);
                    ArchFilter filter = archive.getArchFilter();
                    if (filter.getHostBits() != null) {
                        arch.setHostBits(filter.getHostBits().getSize());
                    }
                    if (filter.getHostOS() != null) {
                        arch.setHostOs(filter.getHostOS().getXmlName());
                    }
                    if (filter.getJvmBits() != null) {
                        arch.setJvmBits(filter.getJvmBits().getSize());
                    }
                    if (filter.getMinJvmVersion() != null) {
                        arch.setMinJvmVersion(f.createRevisionType(filter.getMinJvmVersion()));
                    }
                    return arch;
                }
            }
            return null;
        }

        @NonNull
        @Override
        public Channel getChannel() {
            if (getVersion().isPreview()) {
                // We map the old concept of previews to the second-stablest channel.
                return Channel.create(1);
            }
            return Channel.create(0);
        }

        @NonNull
        @Override
        public File getInstallDir(@NonNull RepoManager manager,
                @NonNull ProgressIndicator progress) {
            File localPath = manager.getLocalPath();
            assert localPath != null;
            return mWrapped.getPkgDesc().getCanonicalInstallFolder(localPath);
        }

        @Override
        public String toString() {
            return "Legacy package: " + mWrapped.toString();
        }
    }
}
