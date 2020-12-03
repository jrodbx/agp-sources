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
package com.android.sdklib.tool;


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.google.common.collect.Lists;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.DatatypeConverter;

/**
 * Tool to create a mini remote repository from a selection of available packages. Most easily
 * invoked as "gradlew updateOfflineRepo", to update the offline-sdk instances under prebuilts.
 */
public class OfflineRepoCreator {

    private final OfflineRepoConfig mConfig;

    public OfflineRepoCreator(@NonNull OfflineRepoConfig config) {
        mConfig = config;
    }

    public void run() throws IOException {
        File tempDir = FileOpUtils.getNewTempDir("OfflineRepoCreator", mConfig.mFop);
        AndroidSdkHandler handler = AndroidSdkHandler.getInstance(tempDir);
        ProgressIndicator progress = new ConsoleProgressIndicator();
        RepoManager mgr = handler.getSdkManager(progress);
        SettingsController settings = new SettingsController() {
            @Override
            public boolean getForceHttp() {
                return false;
            }

            @Override
            public void setForceHttp(boolean force) {
            }

            @Override
            public boolean getDisableSdkPatches() {
                return true;
            }

            @Override
            public void setDisableSdkPatches(boolean disable) {
            }

            @Nullable
            @Override
            public Channel getChannel() {
                return null;
            }
        };
        mgr.loadSynchronously(0, progress, new LegacyDownloader(mConfig.mFop, settings), settings);

        Map<String, RemotePackage> remotes = mgr.getPackages().getRemotePackages();
        List<RemotePackageImpl> toWrite = new ArrayList<>();
        for (String path : mConfig.mPackages) {
            RemotePackageImpl remote = (RemotePackageImpl) remotes.get(path);
            if (remote == null) {
                continue;
            }
            toWrite.add(remote);
            URL url = InstallerUtil.resolveCompleteArchiveUrl(remote, progress);
            Path dest = mConfig.mDest.resolve(remote.getArchive().getComplete().getUrl());
            if (checkExisting(remote, dest)) {
                continue;
            }
            System.out.println("downloading " + url + " to " + dest);
            Files.copy(url.openStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!toWrite.isEmpty()) {
            writeRepoXml(toWrite, mgr, progress);
        }
    }

    private static boolean checkExisting(@NonNull RemotePackageImpl remote, @NonNull Path dest)
            throws IOException {
        if (Files.exists(dest)) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                try (InputStream in = new BufferedInputStream(Files.newInputStream(dest))) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        digest.update(buf, 0, n);
                    }
                }
                if (DatatypeConverter.printHexBinary(digest.digest())
                        .equals(remote.getArchive().getComplete().getChecksum()
                                .toUpperCase())) {
                    System.out.println(dest + " is up to date");
                    return true;
                }

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        return false;
    }

    private void writeRepoXml(@NonNull List<RemotePackageImpl> toWrite, @NonNull RepoManager mgr,
            @NonNull ProgressIndicator progress) throws IOException {
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        Repository repo = factory.createRepositoryType();
        Set<String> seenLicenses = new HashSet<>();
        for (RemotePackageImpl remote : toWrite) {
            License l = remote.getLicense();
            if (l != null && !seenLicenses.contains(l.getId())) {
                repo.addLicense(l);
                seenLicenses.add(l.getId());
            }
            remote.setChannel(null);
            repo.getRemotePackage().add(remote);

        }
        Path outFile = mConfig.mDest.resolve("offline-repo.xml");
        System.out.println("Writing repo xml to " + outFile);
        InstallerUtil.writeRepoXml(mgr, repo, outFile.toFile(), mConfig.mFop, progress);
    }

    public static void main(String[] args) throws IOException {
        OfflineRepoConfig config = OfflineRepoConfig.parse(args);
        if (config == null) {
            System.exit(1);
        }
        OfflineRepoCreator creator = new OfflineRepoCreator(config);
        creator.run();
    }

    private static class OfflineRepoConfig {

        private Path mDest;
        private List<String> mPackages = Lists.newArrayList();
        private FileOp mFop = FileOpUtils.create();

        private static final String DEST = "--dest";
        private static final String PKG_LIST = "--package_file";

        @Nullable
        public static OfflineRepoConfig parse(@NonNull String[] args) {
            OfflineRepoConfig result = new OfflineRepoConfig();
            try {
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals(DEST)) {
                        result.mDest = Paths.get(args[++i]);
                    } else if (args[i].equals(PKG_LIST)) {
                        result.mPackages.addAll(Files.readAllLines(Paths.get(args[++i])));
                    } else {
                        result.mPackages.add(args[i]);
                    }
                }
                if (result.mDest == null || result.mPackages.isEmpty()) {
                    printUsage();
                    return null;
                }
                return result;
            } catch (Exception e) {
                printUsage();
                System.err.println();
                e.printStackTrace();
                return null;
            }
        }

        private static void printUsage() {
            System.err.println("Usage: java com.android.sdklib.tool.OfflineRepoCreator \\");
            System.err.println("  --dest <path> [--package_file <filename>] <packages>...");
            System.err.println();
            System.err.println("<package> is a sdk-style path (e.g. \"build-tools;23.0.0\" or "
                    + "\"platforms;android-23\")");
            System.err.println("<filename> is a file that contains one <package> per line");
            System.err.println();
            System.err.println("* If the env var REPO_OS_OVERRIDE is set to \"windows\",\n"
                    + "  \"macosx\", or \"linux\", packages will be created for that OS");
        }
    }
}
