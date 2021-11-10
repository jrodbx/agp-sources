/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.utils;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Utility methods for {@link java.nio.file.Path}. */
public final class PathUtils {

    /**
     * The number of times we should try deleting an empty directory, due to a limitation of the
     * Windows filesystem. See {@link #deleteRecursivelyIfExists(Path)}.
     */
    private static final int EMPTY_DIRECTORY_DELETION_ATTEMPTS = 10;

    private PathUtils() {}

    /**
     * Deletes a file or a directory if it exists. If the directory is not empty, its contents will
     * be deleted recursively. Symbolic links to files or directories will be removed, but the files
     * or directories they link will not be altered. See https://issuetracker.google.com/71843178
     *
     * @param path the file or directory to delete. The file/directory may not exist; if the
     *     directory exists, it may be non-empty.
     */
    public static void deleteRecursivelyIfExists(@NonNull Path path) throws IOException {
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            try (Stream<Path> pathsInDir = Files.list(path)) {
                Iterator<Path> iterator = pathsInDir.iterator();
                while (iterator.hasNext()) {
                    deleteRecursivelyIfExists(iterator.next());
                }
            }
        }

        /*
         * HACK ALERT (http://issuetracker.google.com/77478235):
         *
         * We have confirmed through a number of experiments (see the bug above) that there is a
         * timing issue on the Windows filesystem, such that if we delete a directory immediately
         * after deleting its contents, the deletion might occasionally fail. If we wait for a
         * little after deleting its contents, then it has a higher chance to succeed.
         *
         * Note that in our experiments, we did not see any other thread/process accessing the same
         * directory at the time of deletion, so this is not a concurrency issue.
         *
         * We have also tried using Guava (com.google.common.io.MoreFiles.deleteRecursively) for
         * file deletion, but (surprisingly) Guava also faces this same problem.
         *
         * To work around the issue, we use a combination of waiting and retrying. Waiting alone is
         * not enough because it's unclear how long we should wait to be effective but not wasteful.
         * Retrying alone is not enough because we tried it before and still ran into this issue---
         * see bug 131623810.
         */
        try {
            try {
                Files.deleteIfExists(path);
            } catch (AccessDeniedException exception) {
                /*
                 * NOTE (http://issuetracker.google.com/173689862):
                 *
                 * On Windows filesystem, a file marked read-only can not be deleted
                 * by the Files.deleteIfExists(...) call above. On Linux and Darwin,
                 * the same file can be deleted.
                 *
                 * Set the file to writable to make it work the same on Windows as
                 * the others.
                 *
                 * See referenced bug for user scenario that triggered the issue.
                 */
                path.toFile().setWritable(true);
                Files.deleteIfExists(path);
            }
        } catch (DirectoryNotEmptyException exception) {
            // Keep deleting the directory until it succeeds or hits a reasonable limit.
            int failedAttempts = 1; // Count failed attempts, including the initial failure above
            while (failedAttempts < EMPTY_DIRECTORY_DELETION_ATTEMPTS) {
                try {
                    // making the thread sleep for a minimum amount of time appears to improve the
                    // odds of success. I suspect that JDK is forcing re-reading or flushing when
                    // the thread is swapped in/out.
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                try {

                    Files.deleteIfExists(path);
                } catch (DirectoryNotEmptyException e) {
                    failedAttempts++;
                    continue;
                }
                break;
            }
            if (failedAttempts >= EMPTY_DIRECTORY_DELETION_ATTEMPTS) {
                throw new RuntimeException(
                        String.format(
                                "Unable to delete directory '%s' after %d attempts",
                                path.toString(), failedAttempts),
                        exception);
            }
        }
    }

    /** Returns a system-independent path. */
    @NonNull
    public static String toSystemIndependentPath(@NonNull Path path) {
        String filePath = path.toString();
        if (!path.getFileSystem().getSeparator().equals("/")) {
            return filePath.replace(path.getFileSystem().getSeparator(), "/");
        }
        return filePath;
    }

    @NonNull
    public static Path createTmpToRemoveOnShutdown(@NonNull String prefix) throws IOException {
        Path tmpFile = Files.createTempFile(prefix, "");
        addRemovePathHook(tmpFile);
        return tmpFile;
    }

    @NonNull
    public static Path createTmpDirToRemoveOnShutdown(@NonNull String prefix) throws IOException {
        Path tmpDir = Files.createTempDirectory(prefix);
        addRemovePathHook(tmpDir);
        return tmpDir;
    }

    @NonNull
    public static List<Path> getClassPathItems(@NonNull String classPath) {
        Iterable<String> components = Splitter.on(File.pathSeparator).split(classPath);

        List<Path> classPathJars = Lists.newArrayList();
        PathMatcher zipOrJar =
                FileSystems.getDefault()
                        .getPathMatcher(
                                String.format(
                                        "glob:**{%s,%s}",
                                        SdkConstants.EXT_ZIP, SdkConstants.EXT_JAR));

        for (String component : components) {
            Path componentPath = Paths.get(component);
            if (Files.isRegularFile(componentPath)) {
                classPathJars.add(componentPath);
            } else {
                // this is a directory containing zips or jars, get them all
                try (Stream<Path> paths = Files.walk(componentPath)) {
                    paths.filter(zipOrJar::matches).forEach(classPathJars::add);
                } catch (IOException ignored) {
                    // just ignore, users can specify non-existing dirs as class path
                }
            }
        }

        return classPathJars;
    }

    /**
     * Adds a hook to the shutdown event of the JVM which will delete all files and directories at
     * the given path (inclusive) when the JVM exits.
     *
     * @param path the path to delete
     */
    public static void addRemovePathHook(@NonNull Path path) {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        PathUtils.deleteRecursivelyIfExists(path);
                                    } catch (IOException e) {
                                        Logger.getLogger(PathUtils.class.getName())
                                                .log(Level.WARNING, "Unable to delete " + path, e);
                                    }
                                }));
    }
}
