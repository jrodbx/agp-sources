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
package com.android.utils;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess") // These are utility methods, meant to be public.
public final class FileUtils {
    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);
    private static final Joiner COMMA_SEPARATED_JOINER = Joiner.on(", ");
    private static final Joiner UNIX_NEW_LINE_JOINER = Joiner.on('\n');

    private FileUtils() {}

    /**
     * Recursively deletes a path.
     *
     * @param path the path delete, may exist or not
     * @throws IOException failed to delete the file / directory
     */
    public static void deletePath(@NonNull final File path) throws IOException {
        deleteRecursivelyIfExists(path);
    }

    /**
     * Recursively deletes a directory content (including the sub directories) but not itself.
     *
     * @param directory the directory, that must exist and be a valid directory
     * @throws IOException failed to delete the file / directory
     */
    public static void deleteDirectoryContents(@NonNull final File directory) throws IOException {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory");

        File[] files = directory.listFiles();
        Preconditions.checkNotNull(files);
        for (File file : files) {
            deletePath(file);
        }
    }

    /**
     * Makes sure {@code path} is an empty directory. If {@code path} is a directory, its contents
     * are removed recursively, leaving an empty directory. If {@code path} is not a directory,
     * it is removed and a directory created with the given path. If {@code path} does not
     * exist, a directory is created with the given path.
     *
     * @param path the path, that may exist or not and may be a file or directory
     * @throws IOException failed to delete directory contents, failed to delete {@code path} or
     * failed to create a directory at {@code path}
     */
    public static void cleanOutputDir(@NonNull File path) throws IOException {
        if (!path.isDirectory()) {
            if (path.exists()) {
                deletePath(path);
            }

            if (!path.mkdirs()) {
                throw new IOException(String.format("Could not create empty folder %s", path));
            }

            return;
        }

        deleteDirectoryContents(path);
    }

    /**
     * Copies a regular file from one path to another, preserving file attributes. If the
     * destination file exists, it gets overwritten.
     */
    public static void copyFile(@NonNull File from, @NonNull File to) throws IOException {
        java.nio.file.Files.copy(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.COPY_ATTRIBUTES,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Copies a directory from one path to another. If the destination directory exists, the file
     * contents are merged and files from the source directory overwrite files in the destination.
     */
    public static void copyDirectory(@NonNull File from, @NonNull File to) throws IOException {
        Preconditions.checkArgument(from.isDirectory(), "Source path is not a directory.");
        Preconditions.checkArgument(
                !to.exists() || to.isDirectory(),
                "Destination path exists and is not a directory.");

        mkdirs(to);
        File[] children = from.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile()) {
                    copyFileToDirectory(child, to);
                } else if (child.isDirectory()) {
                    copyDirectoryToDirectory(child, to);
                } else {
                    throw new IllegalArgumentException(
                            "Don't know how to copy file " + child.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Makes a copy of the given file in the specified directory, preserving the name and file
     * attributes.
     */
    public static void copyFileToDirectory(@NonNull final File from, @NonNull final File to)
            throws IOException {
        copyFile(from, new File(to, from.getName()));
    }

    /**
     * Makes a copy of the given directory in the specified destination directory.
     *
     * @see #copyDirectory(File, File)
     */
    public static void copyDirectoryToDirectory(@NonNull final File from, @NonNull final File to)
            throws IOException {
        copyDirectory(from, new File(to, from.getName()));
    }

    /**
     * Makes a copy of the directory's content, in the specified location, while maintaining the
     * directory structure. So the entire directory tree from the source will be copied.
     *
     * @param from directory from which the content is copied
     * @param to destination directory, will be created if does not exist
     */
    public static void copyDirectoryContentToDirectory(
            @NonNull final File from, @NonNull final File to) throws IOException {
        Preconditions.checkArgument(from.isDirectory(), "Source path is not a directory.");

        File[] children = from.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory()) {
                    File destination = new File(to, relativePath(f, from));
                    Files.createParentDirs(destination);
                    mkdirs(destination);

                    copyDirectoryContentToDirectory(f, destination);
                } else if (f.isFile()) {
                    File destination = new File(to, relativePath(f.getParentFile(), from));
                    Files.createParentDirs(destination);
                    mkdirs(destination);

                    copyFileToDirectory(f, destination);
                }
            }
        }
    }

    /**
     * Creates a directory, if it doesn't exist.
     *
     * @param folder the directory to create, may already exist
     * @return {@code folder}
     */
    @NonNull
    public static File mkdirs(@NonNull File folder) {
        // attempt to create first.
        // if failure only throw if folder does not exist.
        // This makes this method able to create the same folder(s) from different thread
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new RuntimeException("Cannot create directory " + folder);
        }

        return folder;
    }

    /**
     * Deletes an existing file or an existing empty directory.
     *
     * @param file the file or directory to delete. The file/directory must exist, if the directory
     *     exists, it must be empty.
     */
    public static void delete(@NonNull File file) throws IOException {
        java.nio.file.Files.delete(file.toPath());
    }

    /**
     * Deletes a file or an empty directory if it exists.
     *
     * @param file the file or directory to delete. The file/directory may not exist; if the
     *     directory exists, it must be empty.
     */
    public static void deleteIfExists(@NonNull File file) throws IOException {
        java.nio.file.Files.deleteIfExists(file.toPath());
    }

    /**
     * Deletes a file or a directory if it exists. If the directory is not empty, its contents will
     * be deleted recursively.
     *
     * @param file the file or directory to delete. The file/directory may not exist; if the
     *     directory exists, it may be non-empty.
     */
    public static void deleteRecursivelyIfExists(@NonNull File file) throws IOException {
        PathUtils.deleteRecursivelyIfExists(file.toPath());
    }

    public static void renameTo(@NonNull File file, @NonNull File to) throws IOException {
        boolean result = file.renameTo(to);
        if (!result) {
            throw new IOException("Failed to rename " + file.getAbsolutePath() + " to " + to);
        }
    }

    /**
     * Joins a list of path segments to a given File object.
     *
     * @param dir the file object.
     * @param paths the segments.
     * @return a new File object.
     */
    @NonNull
    public static File join(@NonNull File dir, @NonNull String... paths) {
        if (paths.length == 0) {
            return dir;
        }

        return new File(dir, PATH_JOINER.join(paths));
    }

    /**
     * Joins a list of path segments to a given File object.
     *
     * @param dir the file object.
     * @param paths the segments.
     * @return a new File object.
     */
    @NonNull
    public static File join(@NonNull File dir, @NonNull Iterable<String> paths) {
        return new File(dir, PATH_JOINER.join(removeEmpty(paths)));
    }

    /**
     * Joins a set of segment into a string, separating each segments with a host-specific
     * path separator.
     *
     * @param paths the segments.
     * @return a string with the segments.
     */
    @NonNull
    public static String join(@NonNull String... paths) {
        return PATH_JOINER.join(removeEmpty(Lists.newArrayList(paths)));
    }

    /**
     * Joins a set of segment into a string, separating each segments with a host-specific
     * path separator.
     *
     * @param paths the segments.
     * @return a string with the segments.
     */
    @NonNull
    public static String join(@NonNull Iterable<String> paths) {
        return PATH_JOINER.join(paths);
    }


    private static Iterable<String> removeEmpty(Iterable<String> input) {
        return Lists.newArrayList(input)
                .stream()
                .filter(it -> !it.isEmpty())
                .collect(Collectors.toList());
    }
    /**
     * Loads a text file forcing the line separator to be of Unix style '\n' rather than being
     * Windows style '\r\n'.
     */
    @NonNull
    public static String loadFileWithUnixLineSeparators(@NonNull File file) throws IOException {
        return UNIX_NEW_LINE_JOINER.join(Files.readLines(file, Charsets.UTF_8));
    }

    /**
     * Computes the relative of a file or directory with respect to a directory.
     *
     * @param file the file or directory, which must exist in the filesystem
     * @param dir the directory to compute the path relative to
     * @return the relative path from {@code dir} to {@code file}; if {@code file} is a directory
     * the path comes appended with the file separator (see documentation on {@code relativize}
     * on java's {@code URI} class)
     */
    @NonNull
    public static String relativePath(@NonNull File file, @NonNull File dir) {
        checkArgument(file.isFile() || file.isDirectory(), "%s is not a file nor a directory.",
                file.getPath());
        checkArgument(dir.isDirectory(), "%s is not a directory.", dir.getPath());
        return relativePossiblyNonExistingPath(file, dir);
    }

    /**
     * Computes the relative of a file or directory with respect to a directory.
     * For example, if the file's absolute path is {@code /a/b/c} and the directory
     * is {@code /a}, this method returns {@code b/c}.
     *
     * @param file the path that may not correspond to any existing path in the filesystem
     * @param dir the directory to compute the path relative to
     * @return the relative path from {@code dir} to {@code file}; if {@code file} is a directory
     * the path comes appended with the file separator (see documentation on {@code relativize}
     * on java's {@code URI} class)
     */
    @NonNull
    public static String relativePossiblyNonExistingPath(@NonNull File file, @NonNull File dir) {
        String path = dir.toURI().relativize(file.toURI()).getPath();
        return toSystemDependentPath(path);
    }

    /**
     * Converts a /-based path into a path using the system dependent separator.
     *
     * @param path the system independent path to convert
     * @return the system dependent path
     */
    @NonNull
    public static String toSystemDependentPath(@NonNull String path) {
        if (File.separatorChar != '/') {
            path = path.replace('/', File.separatorChar);
        }
        return path;
    }

    /**
     * Escapes all OS dependent chars if necessary.
     *
     * @param path the file path to escape.
     * @return the escaped file path or itself if it is not necessary.
     */
    @NonNull
    public static String escapeSystemDependentCharsIfNecessary(@NonNull String path) {
        if (File.separatorChar == '\\') {
            return path.replace("\\", "\\\\");
        }
        return path;
    }

    /**
     * Converts a system-dependent path into a /-based path.
     *
     * @param path the system dependent path
     * @return the system independent path
     */
    @NonNull
    public static String toSystemIndependentPath(@NonNull String path) {
        if (File.separatorChar != '/') {
            path = path.replace(File.separatorChar, '/');
        }
        return path;
    }

    /**
     * Returns an absolute path that can be open by system APIs for all platforms.
     *
     * @param file The file whose path needs to be converted.
     * @return On non-Windows platforms, the absolute path of the file. On Windows, the absolute
     *     path preceded by "\\?\". This ensures that Windows API calls can open the path even if it
     *     is more than 260 characters long.
     */
    @NonNull
    public static String toExportableSystemDependentPath(@NonNull File file) {
        if (File.separatorChar != '/' && !file.getAbsolutePath().startsWith("\\\\?\\")) {
            return "\\\\?\\" + file.getAbsolutePath();
        }
        return file.getAbsolutePath();
    }

    @NonNull
    public static String sha1(@NonNull File file) throws IOException {
        return Hashing.sha1().hashBytes(Files.toByteArray(file)).toString();
    }

    @NonNull
    public static FluentIterable<File> getAllFiles(@NonNull File dir) {
        return FluentIterable.from(Files.fileTraverser().depthFirstPreOrder(dir))
                .filter(Files.isFile());
    }

    @NonNull
    public static String getNamesAsCommaSeparatedList(@NonNull Iterable<File> files) {
        return COMMA_SEPARATED_JOINER.join(Iterables.transform(files, File::getName));
    }

    /**
     * Replace all unsafe characters for a file name (OS independent) with an underscore
     * @param input an potentially unsafe file name
     * @return a safe file name
     */
    @NonNull
    public static String sanitizeFileName(String input) {
        return input.replaceAll("[:\\\\/*\"?|<>']", "_");
    }

    /**
     * Chooses a directory name, based on a JAR file name, considering exploded-aar and classes.jar.
     */
    @NonNull
    public static String getDirectoryNameForJar(@NonNull File inputFile) {
        // add a hash of the original file path.
        HashFunction hashFunction = Hashing.sha1();
        HashCode hashCode = hashFunction.hashString(inputFile.getAbsolutePath(), Charsets.UTF_16LE);

        String name = Files.getNameWithoutExtension(inputFile.getName());
        if (name.equals("classes") && inputFile.getAbsolutePath().contains("exploded-aar")) {
            // This naming scheme is coming from DependencyManager#computeArtifactPath.
            File versionDir = inputFile.getParentFile().getParentFile();
            File artifactDir = versionDir.getParentFile();
            File groupDir = artifactDir.getParentFile();

            name = Joiner.on('-').join(
                    groupDir.getName(), artifactDir.getName(), versionDir.getName());
        }
        name = name + "_" + hashCode.toString();
        return name;
    }

    /**
     * Creates a new text file with the given content. The file should not exist when this method
     * is called.
     *
     * @param file the file to write to
     * @param content the new content of the file
     */
    public static void createFile(@NonNull File file, @NonNull String content) throws IOException {
        checkArgument(!file.exists(), "%s exists already.", file);

        writeToFile(file, content);
    }

    /**
     * Creates a new text file or replaces content of an existing file.
     *
     * @param file the file to write to
     * @param content the new content of the file
     */
    public static void writeToFile(@NonNull File file, @NonNull String content) throws IOException {
        Files.createParentDirs(file);
        Files.asCharSink(file, StandardCharsets.UTF_8).write(content);
    }

    /**
     * Find a list of files in a directory, using a specified path pattern.
     */
    public static List<File> find(@NonNull File base, @NonNull final Pattern pattern) {
        checkArgument(base.isDirectory(), "'%s' must be a directory.", base.getAbsolutePath());
        return FluentIterable.from(Files.fileTraverser().depthFirstPreOrder(base))
                .filter(
                        file ->
                                pattern.matcher(FileUtils.toSystemIndependentPath(file.getPath()))
                                        .find())
                .toList();
    }

    /**
     * Find a file with the specified name in a given directory .
     */
    public static Optional<File> find(@NonNull File base, @NonNull final String name) {
        checkArgument(base.isDirectory(), "'%s' must be a directory.", base.getAbsolutePath());
        return FluentIterable.from(Files.fileTraverser().depthFirstPreOrder(base))
                .filter(file -> name.equals(file.getName()))
                .last();
    }

    /**
     * Join multiple file paths as String.
     */
    @NonNull
    public static String joinFilePaths(@NonNull Iterable<File> files) {
        return Joiner.on(File.pathSeparatorChar)
                .join(Iterables.transform(files, File::getAbsolutePath));
    }

    /**
     * Returns {@code true} if the parent directory of the given file/directory exists, and {@code
     * false} otherwise. Note that this method resolves the real path of the given file/directory
     * first via {@link File#getCanonicalFile()}.
     */
    public static boolean parentDirExists(@NonNull File file) {
        File canonicalFile;
        try {
            canonicalFile = file.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return canonicalFile.getParentFile() != null && canonicalFile.getParentFile().exists();
    }

    /**
     * Returns {@code true} if a file/directory is in a given directory or in a subdirectory of the
     * given directory, and {@code false} otherwise. Note that this method resolves the real paths
     * of the given file/directory first via {@link File#getCanonicalFile()}.
     */
    public static boolean isFileInDirectory(@NonNull File file, @NonNull File directory) {
        File parentFile;
        try {
            parentFile = file.getCanonicalFile().getParentFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        while (parentFile != null) {
            if (isSameFile(parentFile, directory)) {
                return true;
            }
            parentFile = parentFile.getParentFile();
        }
        return false;
    }

    /**
     * Returns {@code true} if the two files refer to the same physical file, and {@code false}
     * otherwise. This is the correct way to compare physical files, instead of comparing using
     * {@link File#equals(Object)} directly.
     *
     * <p>Unlike {@link java.nio.file.Files#isSameFile(Path, Path)}, this method does not require
     * the files to exist.
     *
     * <p>Internally, this method delegates to {@link java.nio.file.Files#isSameFile(Path, Path)} if
     * the files exist.
     *
     * <p>If either of the files does not exist, this method instead compares the canonical files of
     * the two files, since {@link java.nio.file.Files#isSameFile(Path, Path)} in some cases require
     * that the files exist and therefore cannot be used. The downside of using {@link
     * File#getCanonicalFile()} is that it may not handle hard links and symbolic links correctly as
     * with {@link java.nio.file.Files#isSameFile(Path, Path)}.
     */
    public static boolean isSameFile(@NonNull File file1, @NonNull File file2) {
        try {
            if (file1.exists() && file2.exists()) {
                return java.nio.file.Files.isSameFile(file1.toPath(), file2.toPath());
            } else {
                return file1.getCanonicalFile().equals(file2.getCanonicalFile());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a new {@link FileSystem} for a given ZIP file.
     *
     * <p>Note that NIO filesystems are unique per URI, so the returned {@link FileSystem} should be
     * closed as soon as possible.
     */
    @NonNull
    public static FileSystem createZipFilesystem(@NonNull Path archive) throws IOException {
        URI uri = URI.create("jar:" + archive.toUri().toString());
        return FileSystems.newFileSystem(uri, Collections.emptyMap());
    }
}
