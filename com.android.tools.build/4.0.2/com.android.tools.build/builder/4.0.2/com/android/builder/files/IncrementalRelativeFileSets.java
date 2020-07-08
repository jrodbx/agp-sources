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

package com.android.builder.files;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for incremental relative file sets, immutable maps of relative files to status.
 */
public final class IncrementalRelativeFileSets {

    /**
     * Utility class: no visible constructor.
     */
    private IncrementalRelativeFileSets() {
    }

    /**
     * Reads a directory and adds all files in the directory in a new incremental relative set. The
     * status of each file is set to {@link FileStatus#NEW}. This method is used to construct an
     * initial set of files and is, therefore, an incremental update from zero.
     *
     * @param directory the directory, must be an existing directory
     * @return the file set
     * @deprecated Prefer the new Gradle InputChanges API.
     */
    @Deprecated
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromDirectory(@NonNull File directory) {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory()");
        return ImmutableMap.copyOf(
                Maps.asMap(
                        RelativeFiles.fromDirectory(directory),
                        Functions.constant(FileStatus.NEW)));
    }

    /**
     * Reads a zip file and adds all files in the file in a new incremental relative set. The
     * status of each file is set to {@link FileStatus#NEW}. This method is used to construct an
     * initial set of files and is, therefore, an incremental update from zero.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(@NonNull File zip)
            throws IOException {
        return fromZip(zip, FileStatus.NEW);
    }

    /**
     * Reads a zip file and adds all files in the file in a new incremental relative set. The
     * status of each file is set to {@code status}.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @param status the status to set the files to
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(
            @NonNull File zip,
            FileStatus status)
            throws IOException {
        Preconditions.checkArgument(zip.isFile(), "!zip.isFile(): %s", zip);

        return ImmutableMap.<RelativeFile, FileStatus>builder()
                .putAll(
                        Maps.asMap(
                                RelativeFiles.fromZip(new ZipCentralDirectory(zip)), f -> status))
                .build();
    }

    /**
     * Reads a zip file and adds all files in the file in a new incremental relative set. The status
     * of each file is set to {@link FileStatus#NEW}. This method is used to construct an initial
     * set of files and is, therefore, an incremental update from zero.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(@NonNull ZipCentralDirectory zip)
            throws IOException {
        return fromZip(zip, FileStatus.NEW);
    }

    /**
     * Reads a zip file and adds all files in the file in a new incremental relative set. The status
     * of each file is set to {@code status}.
     *
     * @param zip the zip file to read, must be a valid, existing zip file
     * @param status the status to set the files to
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZip(
            @NonNull ZipCentralDirectory zip, FileStatus status) throws IOException {
        return ImmutableMap.<RelativeFile, FileStatus>builder()
                .putAll(Maps.asMap(RelativeFiles.fromZip(zip), f -> status))
                .build();
    }

    /**
     * Computes the incremental file set that results from comparing a zip file with a possibly
     * existing cached file. If the cached file does not exist, then the whole zip is reported as
     * {@link FileStatus#NEW}. If {@code zip} does not exist and a cached file exists, then the
     * whole zip is reported as {@link FileStatus#REMOVED}. Otherwise, both zips are compared and
     * the difference returned.
     *
     * @param zipCentralDirectory the zip file to read, must be a valid, existing zip file
     * @param cache the cache where to find the old version of the zip
     * @param cacheUpdates receives all runnables that will update the cache; running all runnables
     *     placed in this set will ensure that a second invocation of this method reports no changes
     * @return the file set
     * @throws IOException failed to read the zip file
     */
    @NonNull
    public static Map<RelativeFile, FileStatus> fromZip(
            @NonNull ZipCentralDirectory zipCentralDirectory,
            @NonNull KeyedFileCache cache,
            @NonNull Set<Runnable> cacheUpdates)
            throws IOException {
        File zipFile = zipCentralDirectory.getFile();
        File oldFile = cache.get(zipFile);
        if (oldFile == null) {
            /*
             * No old zip in cache. If the zip also doesn't exist, report all empty.
             */
            if (!zipFile.isFile()) {
                return ImmutableMap.of();
            }

            cacheUpdates.add(IOExceptionRunnable.asRunnable(() -> cache.add(zipCentralDirectory)));
            return fromZip(zipCentralDirectory, FileStatus.NEW);
        }

        if (!zipFile.isFile()) {
            /*
             * Zip does not exist, but a cached version does. This means the zip was deleted
             * and all entries are removed.
             */
            ZipCentralDirectory oldCdr = new ZipCentralDirectory(oldFile);
            Collection<DirectoryEntry> entries = oldCdr.getEntries().values();

            Map<RelativeFile, FileStatus> map = Maps.newHashMapWithExpectedSize(entries.size());

            for (DirectoryEntry entry : entries) {
                map.put(new RelativeFile(zipFile, entry.getName()), FileStatus.REMOVED);
            }

            cacheUpdates.add(IOExceptionRunnable.asRunnable(() -> cache.remove(zipFile)));
            return Collections.unmodifiableMap(map);
        }

        /*
         * We have both a new and old zip. Compare both.
         */
        Map<RelativeFile, FileStatus> result = Maps.newHashMap();

        Closer closer = Closer.create();
        try {
            Map<String, DirectoryEntry> newEntries = zipCentralDirectory.getEntries();
            Map<String, DirectoryEntry> oldEntries = new ZipCentralDirectory(oldFile).getEntries();

            /*
             * Search for new and modified files.
             */
            for (DirectoryEntry entry : newEntries.values()) {
                String path = entry.getName();
                RelativeFile newRelative = new RelativeFile(zipFile, path);

                DirectoryEntry oldEntry = oldEntries.get(path);
                if (oldEntry == null) {
                    result.put(newRelative, FileStatus.NEW);
                    continue;
                }

                if (oldEntry.getCrc32() != entry.getCrc32()
                        || oldEntry.getSize() != entry.getSize()) {
                    result.put(newRelative, FileStatus.CHANGED);
                }

                /*
                 * If we get here, then the file exists in both unmodified.
                 */
            }

            for (DirectoryEntry entry : oldEntries.values()) {
                String path = entry.getName();
                RelativeFile oldRelative = new RelativeFile(zipFile, path);

                DirectoryEntry newEntry = newEntries.get(path);
                if (newEntry == null) {
                    /*
                     * File does not exist in new. It has been deleted.
                     */
                    result.put(oldRelative, FileStatus.REMOVED);
                }
            }
        } catch (Throwable t) {
            throw closer.rethrow(t, IOException.class);
        } finally {
            closer.close();
        }

        cacheUpdates.add(IOExceptionRunnable.asRunnable(() -> cache.add(zipCentralDirectory)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Computes the incremental relative file set that is the union of all provided sets. If a
     * relative file exists in more than one set, one of the files will exist in the union set,
     * but which one is not defined.
     *
     * @param sets the sets to union
     * @return the result of the union
     */
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> union(
            @NonNull Iterable<ImmutableMap<RelativeFile, FileStatus>> sets) {
        Map<RelativeFile, FileStatus> map = Maps.newHashMap();
        for (ImmutableMap<RelativeFile, FileStatus> set : sets) {
            map.putAll(set);
        }

        return ImmutableMap.copyOf(map);
    }

    /**
     * Reads files and builds an incremental relative file set. Each individual file in {@code
     * files} may be a file or directory. If it is a directory, then all files in the directory are
     * added as if {@link #fromDirectory(File)} had been invoked; if it is a file, then it is
     * assumed to be a zip file and all files in the zip are added as if {@link #fromZip(File)} had
     * been invoked.
     *
     * <p>The status of each file is set to {@link FileStatus#NEW}. This method is used to construct
     * an initial set of files and is, therefore, an incremental update from zero.
     *
     * @param files the files and directories
     * @return the file set
     * @throws IOException failed to read the files
     * @deprecated Prefer the new Gradle InputChanges API.
     */
    @Deprecated
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> fromZipsAndDirectories(
            @NonNull Iterable<File> files) throws IOException {

        Set<ImmutableMap<RelativeFile, FileStatus>> sets = Sets.newHashSet();
        for (File f : files) {
            if (f.isFile()) {
                sets.add(fromZip(f));
            } else {
                sets.add(fromDirectory(f));
            }
        }

        return union(sets);
    }

    /**
     * Builds an incremental relative file set from a set of modified files. If the modified file is
     * in a base directory, then it is placed as a relative file in the resulting data set. If the
     * modified file is itself a base file, then it is treated as a zip file.
     *
     * <p>If there are new zip files, then all files in the zip are added to the result data set. If
     * there are deleted or updated zips, then the relative incremental changes are added to the
     * data set. To allow detecting incremental changes in zips, the provided cache is used.
     *
     * @param baseFiles the files; all entries must exist and be either directories or zip files
     * @param updates the files updated in the directories or base zip files updated
     * @param cache the file cache where to find old versions of zip files
     * @param cacheUpdates receives all runnables that will update the cache; running all runnables
     *     placed in this set will ensure that a second invocation of this method reports no changes
     *     for zip files; the updates are reported as deferrable runnables instead of immediately
     *     run in this method to allow not changing the cache contents if something else fails and
     *     we want to restore the previous state
     * @param fileDeletionPolicy the policy for file deletions
     * @return the data
     * @throws IOException failed to read a zip file
     * @deprecated Prefer the new Gradle InputChanges API.
     */
    @Deprecated
    @NonNull
    public static ImmutableMap<RelativeFile, FileStatus> makeFromBaseFiles(
            @NonNull Collection<File> baseFiles,
            @NonNull Map<File, FileStatus> updates,
            @NonNull KeyedFileCache cache,
            @NonNull Set<Runnable> cacheUpdates,
            @NonNull FileDeletionPolicy fileDeletionPolicy)
            throws IOException {
        for (File f : baseFiles) {
            Preconditions.checkArgument(f.exists(), "!f.exists(): %s", f);
        }

        Map<RelativeFile, FileStatus> relativeUpdates = Maps.newHashMap();
        for (Map.Entry<File, FileStatus> fileUpdate : updates.entrySet()) {

            File file = fileUpdate.getKey();
            FileStatus status = fileUpdate.getValue();

            if (fileDeletionPolicy == FileDeletionPolicy.DISALLOW_FILE_DELETIONS) {
                Preconditions.checkState(
                        status != FileStatus.REMOVED,
                        String.format(
                                "Changes include a deleted file ('%s'), which is not allowed.",
                                file.getAbsolutePath()));
            }

            if (baseFiles.contains(file)) {
                relativeUpdates.putAll(fromZip(new ZipCentralDirectory(file), cache, cacheUpdates));
            } else {
                /*
                 * We ignore directories because there are no relative files for directories.
                 */
                if (file.isDirectory()) {
                    continue;
                }

                /*
                 * If the file does not exist in the set of base files, assume it is a file in a
                 * directory. If we don't find the base directory, ignore it.
                 */
                File possibleBaseDirectory = file.getParentFile();
                while (possibleBaseDirectory != null) {
                    if (baseFiles.contains(possibleBaseDirectory)) {
                        relativeUpdates.put(new RelativeFile(possibleBaseDirectory, file), status);
                        break;
                    }

                    possibleBaseDirectory = possibleBaseDirectory.getParentFile();
                }
            }
        }

        return ImmutableMap.copyOf(relativeUpdates);
    }

    /**
     * Policy for file deletions.
     *
     * <p>For incremental tasks, the previous gradle API does not provide information about whether
     * a deletion is done on a normal file or a directory. Therefore, we use this class to either
     * assume deletions to be done on normal files, or not allow deletions at all.
     *
     * <p>TODO: Remove this class and its usages in preference for the new InputChanges Gradle API.
     *
     * @deprecated Prefer the new Gradle InputChanges API.
     */
    @Deprecated
    public enum FileDeletionPolicy {
        /** Deletions are assumed to be done on normal files, not directories. */
        ASSUME_NO_DELETED_DIRECTORIES,

        /** Deletions of files or directories are not allowed. */
        DISALLOW_FILE_DELETIONS
    }
}
