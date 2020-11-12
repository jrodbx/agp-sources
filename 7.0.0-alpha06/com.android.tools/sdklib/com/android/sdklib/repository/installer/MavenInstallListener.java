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
import com.android.repository.Revision;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private final AndroidSdkHandler mSdkHandler;

    public MavenInstallListener(@NonNull AndroidSdkHandler sdkHandler) {
        mSdkHandler = sdkHandler;
    }

    @Override
    public void statusChanged(@NonNull PackageOperation installer,
            @NonNull ProgressIndicator progress)
            throws PackageOperation.StatusChangeListenerException {
        if (installer.getInstallStatus() == PackageOperation.InstallStatus.COMPLETE) {
            File dir = mSdkHandler.getFileOp().toFile(installer.getLocation(progress));
            if (!updateMetadata(dir.getParentFile(), progress)) {
                throw new PackageOperation.StatusChangeListenerException(
                        "Failed to update maven metadata for " +
                                installer.getPackage().getDisplayName());
            }
        }
    }

    private boolean updateMetadata(File root, ProgressIndicator progress) {
        FileOp fileOp = mSdkHandler.getFileOp();
        MavenMetadata metadata = null;
        for (File version : fileOp.listFiles(root)) {
            if (fileOp.isDirectory(version)) {
                metadata = createOrUpdateMetadata(version, metadata, progress, fileOp);
            }
        }
        File metadataFile = new File(root, MAVEN_METADATA_FILE_NAME);
        if (metadata != null) {
            progress.logVerbose("Writing Maven metadata to " + metadataFile.getAbsolutePath());
            return writeMetadata(metadata, metadataFile, progress, fileOp);
        }
        // We didn't find anything. Delete the metadata file as well.
        progress.logVerbose("Deleting Maven metadata " + metadataFile.getAbsolutePath());
        return deleteMetadataFiles(metadataFile, progress, fileOp);
    }

    private static boolean deleteMetadataFiles(
            @NonNull File metadataFile,
            @NonNull ProgressIndicator progress,
            @NonNull FileOp fop) {
        if (!FileOpUtils.deleteIfExists(metadataFile, fop)) {
            progress.logError("Failed to delete " + metadataFile.getAbsolutePath());
            return false;
        }

        File md5File = getMetadataHashFile(metadataFile, "MD5");
        if (!FileOpUtils.deleteIfExists(md5File, fop)) {
            progress.logError("Failed to delete " + md5File.getAbsolutePath());
            return false;
        }

        File sha1File = getMetadataHashFile(metadataFile, "SHA1");
        if (!FileOpUtils.deleteIfExists(sha1File, fop)) {
            progress.logError("Failed to delete " + sha1File.getAbsolutePath());
            return false;
        }

        return true;
    }

    /**
     * Writes out a {@link MavenMetadata} to the specified location.
     */
    private static boolean writeMetadata(@NonNull MavenMetadata metadata, @NonNull File file,
            @NonNull ProgressIndicator progress, @NonNull FileOp fop) {
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
        OutputStream metadataOutFile = null;
        try {
            metadataOutFile = fop.newFileOutputStream(file);
            metadataOutFile.write(metadataOutBytes.toByteArray());
        } catch (IOException e) {
            progress.logWarning("Failed to write metadata file.", e);
            return false;
        } finally {
            if (metadataOutFile != null) {
                try {
                    metadataOutFile.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        if (!writeHashFile(file, "MD5", progress, metadataOutBytes, fop)) {
            return false;
        }
        if (!writeHashFile(file, "SHA1", progress, metadataOutBytes, fop)) {
            return false;
        }
        return true;
    }

    /**
     * Adds the artifact version at the given {@code versionPath} to the given
     * {@link MavenMetadata} (creating a new one if necessary).
     */
    @Nullable
    private static MavenMetadata createOrUpdateMetadata(@NonNull File versionPath,
            @Nullable MavenMetadata metadata, @NonNull ProgressIndicator progress,
            @NonNull FileOp fop) {
        File pomFile = new File(versionPath,
                String.format("%1$s-%2$s.pom", versionPath.getParentFile().getName(),
                        versionPath.getName()));
        if (fop.exists(pomFile)) {
            PackageInfo info = unmarshal(pomFile, PackageInfo.class, progress, fop);
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

    /**
     * Attempts to unmarshal an object of the given type from the specified file.
     */
    @VisibleForTesting
    static <T> T unmarshal(@NonNull File f, @NonNull Class<T> clazz,
            @NonNull ProgressIndicator progress, @NonNull FileOp fop) {
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
            metadataInputStream = fop.newFileInputStream(f);
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

    /**
     * Writes a file containing a hash of the given file using the specified algorithm.
     */
    private static boolean writeHashFile(@NonNull File file, @NonNull String algorithm,
            @NonNull ProgressIndicator progress, @NonNull ByteArrayOutputStream metadataOutBytes,
            @NonNull FileOp fop) {
        File hashFile = getMetadataHashFile(file, algorithm);
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
            hashOutputStream = fop.newFileOutputStream(hashFile);
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
    private static File getMetadataHashFile(@NonNull File file, @NonNull String algorithm) {
        return new File(file.getParent(),
                MAVEN_METADATA_FILE_NAME + "." + algorithm.toLowerCase());
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
