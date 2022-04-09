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

package com.android.repository.util;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.android.repository.api.RepositorySource;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.RevisionType;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.io.FileOpUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * Utility methods for {@link PackageOperation} implementations.
 */
public class InstallerUtil {

    /**
     * The name of the package metadata file for a package in the process of being installed.
     */
    public static final String PENDING_PACKAGE_XML_FN = "package.xml.pending";
    public static final String INSTALLER_DIR_FN = ".installer";

    /**
     * Unzips the given zipped input stream into the given directory.
     *
     * @param in The (zipped) input stream.
     * @param out The directory into which to expand the files. Must exist.
     * @param expectedSize Compressed size of the stream.
     * @param progress Currently only used for logging.
     * @throws IOException If we're unable to read or write.
     */
    public static void unzip(
            @NonNull Path in,
            @NonNull Path out,
            long expectedSize,
            @NonNull ProgressIndicator progress)
            throws IOException {
        if (!CancellableFileIo.exists(out) || !CancellableFileIo.isDirectory(out)) {
            throw new IllegalArgumentException("out must exist and be a directory.");
        }

        progress.setText("Unzipping...");
        ZipFile zipFile = new ZipFile(Files.newByteChannel(in));
        boolean indeterminate = false;
        if (expectedSize == 0) {
            progress.setIndeterminate(true);
            indeterminate = true;
        }
        try {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            progress.setFraction(0);
            double progressMax = 0;
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = entry.getName();
                Path entryFile = out.resolve(name);
                progress.setSecondaryText(name);
                if (entry.isUnixSymlink()) {
                    ByteArrayOutputStream targetByteStream = new ByteArrayOutputStream();
                    progressMax += (double) entry.getCompressedSize() / expectedSize;
                    readZipEntry(
                            zipFile,
                            entry,
                            targetByteStream,
                            indeterminate ? progress : progress.createSubProgress(progressMax));
                    if (!indeterminate) {
                        progress.setFraction(progressMax);
                    }
                    Path linkTarget = out.getFileSystem().getPath(targetByteStream.toString());
                    Files.createSymbolicLink(entryFile, linkTarget);
                } else if (entry.isDirectory()) {
                    Files.createDirectories(entryFile);
                } else {
                    if (Files.isSymbolicLink(entryFile)) {
                        Files.delete(entryFile);
                    }
                    if (!CancellableFileIo.exists(entryFile)) {
                        Path parent = entryFile.getParent();
                        if (parent != null && !CancellableFileIo.exists(parent)) {
                            Files.createDirectories(parent);
                        }
                        Files.createFile(entryFile);
                    }

                    OutputStream unzippedOutput = Files.newOutputStream(entryFile);
                    progressMax += (double) entry.getCompressedSize() / expectedSize;
                    if (readZipEntry(
                            zipFile,
                            entry,
                            unzippedOutput,
                            indeterminate ? progress : progress.createSubProgress(progressMax))) {
                        return;
                    }
                    if (!indeterminate) {
                        progress.setFraction(progressMax);
                    }
                    if (!FileOpUtils.isWindows()) {
                        // get the mode and test if it contains the executable bit
                        int mode = entry.getUnixMode();
                        //noinspection OctalInteger
                        if ((mode & 0111) != 0) {
                            try {
                                FileOpUtils.setExecutablePermission(entryFile);
                            } catch (IOException ignore) {
                            }
                        }
                    }
                }
            }
        }
        finally {
            progress.setIndeterminate(false);
            progress.setFraction(1);
            ZipFile.closeQuietly(zipFile);
        }
    }

    private static boolean readZipEntry(
            ZipFile zipFile,
            ZipArchiveEntry entry,
            OutputStream dest,
            @NonNull ProgressIndicator progress)
            throws IOException {
        int size;
        byte[] buf = new byte[8192];
        double fraction = 0;
        int prevPercent = 0;
        try (BufferedOutputStream bufferedDest = new BufferedOutputStream(dest);
             InputStream s = new BufferedInputStream(zipFile.getInputStream(entry))) {
            while ((size = s.read(buf)) > -1) {
                bufferedDest.write(buf, 0, size);
                fraction += (double) size / entry.getSize();
                int percent = (int) (fraction * 100);
                if (percent != prevPercent) {
                    // Don't update too often
                    progress.setFraction(fraction);
                    prevPercent = percent;
                }
                if (progress.isCanceled()) {
                    return true;
                }
            }
        }
        progress.setFraction(1);
        return false;
    }

    public static void writePendingPackageXml(
            @NonNull RepoPackage p,
            @NonNull Path packageRoot,
            @NonNull RepoManager manager,
            @NonNull ProgressIndicator progress)
            throws IOException {
        if (!CancellableFileIo.isDirectory(packageRoot)) {
            throw new IllegalArgumentException("packageRoot must exist and be a directory.");
        }
        CommonFactory factory = p.createFactory();
        // Create the package.xml
        Repository repo = factory.createRepositoryType();
        License license = p.getLicense();
        if (license != null) {
            repo.addLicense(license);
        }

        p.asMarshallable().addTo(repo);

        Path packageXml = packageRoot.resolve(PENDING_PACKAGE_XML_FN);
        writeRepoXml(manager, repo, packageXml, factory, progress);
    }

    @Nullable
    public static Repository readPendingPackageXml(
            @NonNull Path containingDir,
            @NonNull RepoManager manager,
            @NonNull ProgressIndicator progress)
            throws IOException {
        Repository repo;
        try {
            Path xmlFile = containingDir.resolve(PENDING_PACKAGE_XML_FN);
            if (CancellableFileIo.notExists(xmlFile)) {
                return null;
            }
            repo =
                    (Repository)
                            SchemaModuleUtil.unmarshal(
                                    CancellableFileIo.newInputStream(xmlFile),
                                    manager.getSchemaModules(),
                                    false,
                                    progress);
        } catch (JAXBException e) {
            throw new IOException("Failed to parse pending package xml", e);
        }
        return repo;
    }

    /**
     * Writes out the XML for a {@link LocalPackageImpl} corresponding to the given {@link
     * RemotePackage} to a {@code package.xml} file in {@code packageRoot}.
     *
     * @param p The package to convert to a local package and write out.
     * @param packageRoot The location to write to. Must exist and be a directory.
     * @param manager A {@link RepoManager} instance.
     * @param progress Currently only used for logging.
     * @throws IOException If we fail to write the output file.
     */
    public static void writePackageXml(
            @NonNull RemotePackage p,
            @NonNull Path packageRoot,
            @NonNull RepoManager manager,
            @NonNull ProgressIndicator progress)
            throws IOException {
        if (!CancellableFileIo.isDirectory(packageRoot)) {
            throw new IllegalArgumentException("packageRoot must exist and be a directory.");
        }
        CommonFactory factory = p.createFactory();
        // Create the package.xml
        Repository repo = factory.createRepositoryType();
        License l = p.getLicense();
        if (l != null) {
            repo.addLicense(l);
        }
        LocalPackageImpl impl = LocalPackageImpl.create(p);
        repo.setLocalPackage(impl);
        Path packageXml = packageRoot.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN);
        writeRepoXml(manager, repo, packageXml, factory, progress);
    }

    public static void writeRepoXml(
            @NonNull RepoManager manager,
            @NonNull Repository repo,
            @NonNull Path packageXml,
            @NonNull CommonFactory factory,
            @NonNull ProgressIndicator progress)
            throws IOException {
        JAXBElement<Repository> element = factory.generateRepository(repo);
        try (OutputStream fos = Files.newOutputStream(packageXml)) {
            SchemaModuleUtil.marshal(element, manager.getSchemaModules(), fos,
                    manager.getResourceResolver(progress), progress);
        }
    }

    /**
     * Returns a URL corresponding to {@link Archive#getComplete()} of the given {@link
     * RemotePackage}. If the url in the package is a relative url, resolves it by using the prefix
     * of the url in the {@link RepositorySource} of the package.
     *
     * @return The resolved {@link URL}, or {@code null} if the given archive location is not
     * parsable in its original or resolved form.
     */
    @Nullable
    public static URL resolveCompleteArchiveUrl(@NonNull RemotePackage p,
            @NonNull ProgressIndicator progress) {
        Archive arch = p.getArchive();
        if (arch == null) {
            return null;
        }
        String urlStr = arch.getComplete().getUrl();
        return resolveUrl(urlStr, p, progress);
    }

    @Nullable
    public static URL resolveUrl(@NonNull String urlStr, @NonNull RemotePackage p,
            @NonNull ProgressIndicator progress) {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            // If we don't have a real URL, it could be a relative URL. Pick up the URL prefix
            // from the source.
            try {
                String sourceUrl = p.getSource().getUrl();
                if (!sourceUrl.endsWith("/")) {
                    sourceUrl = sourceUrl.substring(0, sourceUrl.lastIndexOf('/') + 1);
                }
                urlStr = sourceUrl + urlStr;
                url = new URL(urlStr);
            } catch (MalformedURLException e2) {
                progress.logWarning("Failed to parse url: " + urlStr);
                return null;
            }
        }
        return url;
    }

    /**
     * Compute the complete list of packages that need to be installed to meet the dependencies of
     * the given list (including the requested packages themselves, if they are not already
     * installed). Returns {@code null} if we were unable to compute a complete list of dependencies
     * due to not being able to find required packages of the specified version.
     *
     * Packages are returned in install order (that is, if we request A which depends on B, the
     * result will be [B, A]). If a dependency cycle is encountered the order of the returned
     * results at or below the cycle is undefined. For example if we have A -> [B, C], B -> D, and D
     * -> B then the returned list will be either [B, D, C, A] or [D, B, C, A].
     *
     * Note that we assume installed packages already have their dependencies met.
     */
    @Nullable
    public static List<RemotePackage> computeRequiredPackages(
            @NonNull Collection<RemotePackage> requests, @NonNull RepositoryPackages packages,
            @NonNull ProgressIndicator logger) {
        Set<RemotePackage> requiredPackages = Sets.newHashSet();
        Map<String, UpdatablePackage> consolidatedPackages = packages.getConsolidatedPkgs();

        Set<String> seen = Sets.newHashSet();
        Multimap<String, Dependency> allDependencies = HashMultimap.create();
        Set<RemotePackage> roots = Sets.newHashSet();
        Queue<RemotePackage> current = Lists.newLinkedList();
        for (RemotePackage request : requests) {
            UpdatablePackage updatable = consolidatedPackages.get(request.getPath());
            if (updatable == null) {
                logger.logWarning(String.format("No package with key %s found!", request.getPath()));
                return null;
            }
            if (!updatable.hasLocal() || updatable.isUpdate()) {
                current.add(request);
                roots.add(request);
                requiredPackages.add(request);
                seen.add(request.getPath());
            }
        }

        while (!current.isEmpty()) {
            RemotePackage currentPackage = current.remove();

            Collection<Dependency> currentDependencies = currentPackage.getAllDependencies();
            for (Dependency d : currentDependencies) {
                String dependencyPath = d.getPath();
                UpdatablePackage updatableDependency = consolidatedPackages.get(dependencyPath);
                if (updatableDependency == null) {
                    logger.logWarning(
                            String.format("Dependant package with key %s not found!",
                                    dependencyPath));
                    return null;
                }
                LocalPackage localDependency = updatableDependency.getLocal();
                if (localDependency == null && d.isSoft() != null && d.isSoft()) {
                    // Soft dependency and package isn't already installed -> skip
                    continue;
                }
                Revision requiredMinRevision = null;
                RevisionType r = d.getMinRevision();
                if (r != null) {
                    requiredMinRevision = r.toRevision();
                }
                if (localDependency != null && (requiredMinRevision == null ||
                        requiredMinRevision.compareTo(localDependency.getVersion()) <= 0)) {
                    continue;
                }
                if (seen.contains(dependencyPath)) {
                    allDependencies.put(dependencyPath, d);
                    continue;
                }
                seen.add(dependencyPath);
                RemotePackage remoteDependency = updatableDependency.getRemote();
                if (remoteDependency == null || (requiredMinRevision != null
                        && requiredMinRevision.compareTo(remoteDependency.getVersion()) > 0)) {
                    logger.logWarning(String
                            .format("Package \"%1$s\" with revision at least %2$s not available.",
                                    updatableDependency.getRepresentative().getDisplayName(),
                                    requiredMinRevision));
                    return null;
                }

                requiredPackages.add(remoteDependency);
                allDependencies.put(dependencyPath, d);
                current.add(remoteDependency);
                // We had a dependency on it, so it can't be a root.
                roots.remove(remoteDependency);
            }
        }

        List<RemotePackage> result = Lists.newArrayList();

        while (!roots.isEmpty()) {
            RemotePackage root = roots.iterator().next();
            roots.remove(root);
            result.add(root);
            for (Dependency d : root.getAllDependencies()) {
                Collection<Dependency> nodeDeps = allDependencies.get(d.getPath());
                if (nodeDeps.size() == 1) {
                    UpdatablePackage newRoot = consolidatedPackages.get(d.getPath());
                    if (newRoot == null) {
                        logger.logWarning(
                                String.format("Package with key %s not found!", d.getPath()));
                        return null;
                    }

                    roots.add(newRoot.getRemote());
                }
                nodeDeps.remove(d);
            }
        }

        if (result.size() != requiredPackages.size()) {
            logger.logInfo("Failed to sort dependencies, returning partially-sorted list.");
            for (RemotePackage p : result) {
                requiredPackages.remove(p);
            }
            result.addAll(requiredPackages);
        }

        return Lists.reverse(result);
    }

    /**
     * Checks to see whether {@code path} is a valid install path. Specifically, checks whether
     * there are any existing packages installed in parents or children of {@code path}. Returns
     * {@code true} if the path is valid. Otherwise returns {@code false} and logs a warning.
     */
    public static boolean checkValidPath(
            @NonNull Path path, @NonNull RepoManager manager, @NonNull ProgressIndicator progress) {
        String check = path.normalize().toString() + File.separator;

        for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
            String existing = p.getLocation().normalize() + File.separator;
            if (!existing.equals(check)) {
                boolean childExists = existing.startsWith(check);
                boolean parentExists = check.startsWith(existing);
                if (childExists || parentExists) {
                    String message =
                            "Trying to install into "
                                    + check
                                    + " but package \""
                                    + p.getDisplayName()
                                    + "\" already exists at "
                                    + existing
                                    + ". It must be deleted or moved away before installing into a "
                                    + (childExists ? "parent" : "child")
                                    + " directory.";
                    progress.logWarning(message);
                    return false;
                }
            }
        }
        return true;
    }
}
