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

package com.android.sdklib.repository.installer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.Revision;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

/**
 * A {@link PackageOperation.StatusChangeListener} that knows how to install and uninstall Maven
 * packages.
 */
public class MavenInstallListener implements PackageOperation.StatusChangeListener {

    public static final String MAVEN_DIR_NAME = "m2repository";

    public static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";

    @Override
    public void statusChanged(@NonNull PackageOperation installer,
            @NonNull ProgressIndicator progress)
            throws PackageOperation.StatusChangeListenerException {
        if (installer.getInstallStatus() == PackageOperation.InstallStatus.COMPLETE) {
            Path dir = installer.getLocation(progress);
            if (!updateMetadata(dir.getParent(), progress)) {
                throw new PackageOperation.StatusChangeListenerException(
                        "Failed to update maven metadata for " +
                                installer.getPackage().getDisplayName());
            }
        }
    }

    private boolean updateMetadata(Path root, ProgressIndicator progress) {
        MavenMetadata metadata = null;
        for (Path version : FileOpUtils.listFiles(root)) {
            if (CancellableFileIo.isDirectory(version)) {
                metadata = createOrUpdateMetadata(version, metadata, progress);
            }
        }
        Path metadataFile = root.resolve(MAVEN_METADATA_FILE_NAME);
        if (metadata != null) {
            progress.logVerbose("Writing Maven metadata to " + metadataFile.toAbsolutePath());
            return writeMetadata(metadata, metadataFile, progress);
        }
        // We didn't find anything. Delete the metadata file as well.
        progress.logVerbose("Deleting Maven metadata " + metadataFile.toAbsolutePath());
        return deleteMetadataFiles(metadataFile, progress);
    }

    private static boolean deleteMetadataFiles(
            @NonNull Path metadataFile, @NonNull ProgressIndicator progress) {
        try {
            Files.deleteIfExists(metadataFile);
        } catch (IOException unused) {
            progress.logError("Failed to delete " + metadataFile.toAbsolutePath());
            return false;
        }

        Path md5File = getMetadataHashFile(metadataFile, "MD5");
        try {
            Files.deleteIfExists(md5File);
        } catch (IOException unused) {
            progress.logError("Failed to delete " + md5File.toAbsolutePath());
            return false;
        }

        Path sha1File = getMetadataHashFile(metadataFile, "SHA1");
        try {
            Files.deleteIfExists(sha1File);
        } catch (IOException unused) {
            progress.logError("Failed to delete " + sha1File.toAbsolutePath());
            return false;
        }

        return true;
    }

    /** Writes out a {@link MavenMetadata} to the specified location. */
    private static boolean writeMetadata(
            @NonNull MavenMetadata metadata,
            @NonNull Path file,
            @NonNull ProgressIndicator progress) {
        Revision max = null;
        for (String s : metadata.versioning.versions.version) {
            Revision rev = Revision.parseRevision(s);
            if (max == null || (!rev.isPreview() && rev.compareTo(max) > 0)) {
                max = rev;
            }
        }
        if (max != null) {
            metadata.versioning.release = max.toString();
        }
        metadata.versioning.lastUpdated = System.currentTimeMillis();
        Marshaller marshaller;
        try {
            JAXBContext context;
            try {
                context = JAXBContext.newInstance(MavenMetadata.class);
            } catch (JAXBException e) {
                // Shouldn't happen
                progress.logError("Failed to create JAXBContext", e);
                return false;
            }
            marshaller = context.createMarshaller();
        } catch (JAXBException e) {
            // Shouldn't happen
            progress.logError("Failed to create Marshaller", e);
            return false;
        }
        ByteArrayOutputStream metadataOutBytes = new ByteArrayOutputStream();
        try {
            marshaller.marshal(
                    new JAXBElement<>(new QName("metadata"), MavenMetadata.class,
                            metadata), metadataOutBytes);
        } catch (JAXBException e) {
            progress.logWarning("Failed to write maven metadata: ", e);
            return false;
        }
        try (OutputStream metadataOutFile = Files.newOutputStream(file)) {
            metadataOutFile.write(metadataOutBytes.toByteArray());
        } catch (IOException e) {
            progress.logWarning("Failed to write metadata file.", e);
            return false;
        }
        // ignore

        if (!writeHashFile(file, "MD5", progress, metadataOutBytes)) {
            return false;
        }
        if (!writeHashFile(file, "SHA1", progress, metadataOutBytes)) {
            return false;
        }
        return true;
    }

    /**
     * Adds the artifact version at the given {@code versionPath} to the given {@link MavenMetadata}
     * (creating a new one if necessary).
     */
    @Nullable
    private static MavenMetadata createOrUpdateMetadata(
            @NonNull Path versionPath,
            @Nullable MavenMetadata metadata,
            @NonNull ProgressIndicator progress) {
        Path pomFile =
                versionPath.resolve(
                        String.format(
                                "%1$s-%2$s.pom",
                                versionPath.getParent().getFileName(), versionPath.getFileName()));
        if (CancellableFileIo.exists(pomFile)) {
            PackageInfo info = unmarshal(pomFile, PackageInfo.class, progress);
            if (info != null) {
                if (metadata == null) {
                    metadata = new MavenMetadata();
                    metadata.artifactId = info.artifactId;
                    metadata.groupId = info.groupId;
                    metadata.versioning = new MavenMetadata.Versioning();
                    metadata.versioning.versions = new MavenMetadata.Versioning.Versions();
                    metadata.versioning.versions.version = Lists.newArrayList();
                }
                metadata.versioning.versions.version.add(info.version);
            }
        }
        return metadata;
    }

    /** Attempts to unmarshal an object of the given type from the specified file. */
    @VisibleForTesting
    static <T> T unmarshal(
            @NonNull Path f, @NonNull Class<T> clazz, @NonNull ProgressIndicator progress) {
        JAXBContext context;
        try {
            context = JAXBContext.newInstance(clazz);
        } catch (JAXBException e) {
            // Shouldn't happen
            progress.logError("Failed to create JAXBContext", e);
            return null;
        }
        Unmarshaller unmarshaller;
        T result;
        try {
            unmarshaller = context.createUnmarshaller();
            unmarshaller.setEventHandler(event -> true);
        } catch (JAXBException e) {
            // Shouldn't happen
            progress.logError("Failed to create unmarshaller", e);
            return null;
        }
        InputStream metadataInputStream;
        try {
            metadataInputStream = CancellableFileIo.newInputStream(f);
        } catch (IOException e) {
            return null;
        }
        try {
            result = (T) unmarshaller.unmarshal(metadataInputStream);
        } catch (JAXBException e) {
            progress.logWarning("Couldn't parse maven metadata file: " + f, e);
            return null;
        }
        return result;
    }

    /** Writes a file containing a hash of the given file using the specified algorithm. */
    private static boolean writeHashFile(
            @NonNull Path file,
            @NonNull String algorithm,
            @NonNull ProgressIndicator progress,
            @NonNull ByteArrayOutputStream metadataOutBytes) {
        Path hashFile = getMetadataHashFile(file, algorithm);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // Shouldn't happen
            progress.logError(algorithm + " algorithm not found", e);
            return false;
        }
        OutputStream hashOutputStream;
        try {
            hashOutputStream = Files.newOutputStream(hashFile);
        } catch (IOException e) {
            progress.logWarning("Failed to open " + algorithm + " file");
            return false;
        }
        try {
            hashOutputStream.write(
                    DatatypeConverter.printHexBinary(digest.digest(metadataOutBytes.toByteArray()))
                            .getBytes());
        } catch (IOException e) {
            progress.logWarning("Failed to write " + algorithm + " file");
            return false;
        } finally {
            try {
                hashOutputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return true;
    }

    @NonNull
    private static Path getMetadataHashFile(@NonNull Path file, @NonNull String algorithm) {
        return file.getParent().resolve(MAVEN_METADATA_FILE_NAME + "." + algorithm.toLowerCase());
    }

    /**
     * jaxb-usable class for marshalling/unmarshalling maven metadata files.
     */
    @VisibleForTesting
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "metadata")
    static class MavenMetadata {

        protected String groupId;

        protected String artifactId;

        protected MavenMetadata.Versioning versioning;

        @XmlAccessorType(XmlAccessType.FIELD)
        public static class Versioning {

            protected String release;

            protected MavenMetadata.Versioning.Versions versions;

            protected long lastUpdated;

            @XmlAccessorType(XmlAccessType.FIELD)
            public static class Versions {

                protected List<String> version;
            }
        }
    }

    /**
     * jaxb-usable class for unmarshalling maven artifact pom files.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "project", namespace = "http://maven.apache.org/POM/4.0.0")
    private static class PackageInfo {

        @XmlElement(namespace = "http://maven.apache.org/POM/4.0.0")
        public String artifactId;

        @XmlElement(namespace = "http://maven.apache.org/POM/4.0.0")
        public String groupId;

        @XmlElement(namespace = "http://maven.apache.org/POM/4.0.0")
        public String version;
    }
}
