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
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kotlin.io.FilesKt;

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
     * destination file exists, it gets overwritten. If the [from] file is read-only on windows, the
     * [to] file will be left read-write so that it can be overwritten the next time this function
     * is called.
     */
    public static void copyFile(@NonNull File from, @NonNull File to) throws IOException {
        copyFile(from.toPath(), to.toPath());
    }

    /**
     * Copies a regular file from one path to another. This function uses two standard copy options:
     * - COPY_ATTRIBUTES copies platform-dependent attributes like file timestamp (only used for
     * non-Windows OSes because performance is worse for Windows and better for non-Windows OSes).
     * - REPLACE_EXISTING will try to delete the target file before the copy. If you want other
     * options, there is an overload which lets you set a custom set.
     *
     * <p>Lastly, if the [from] file is read-only on windows, the [to] file will be left read-write
     * so that it can be overwritten the next time this function is called.
     */
    public static void copyFile(@NonNull Path from, @NonNull Path to) throws IOException {
        if (System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            copyFile(from, to, StandardCopyOption.REPLACE_EXISTING);
        } else {
            copyFile(
                    from,
                    to,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies a regular file from one path to another. If the [from] file is read-only on windows,
     * the [to] file will be left read-write so that it can be overwritten the next time this
     * function is called.
     */
    // Suppress because this is the sanctioned use of Files.copy.
    // See https://issuetracker.google.com/182063560
    @SuppressWarnings("NoNioFilesCopy")
    public static void copyFile(@NonNull Path from, @NonNull Path to, CopyOption... options)
            throws IOException {
        java.nio.file.Files.copy(from, to, options);
        /*
         * Some source-control systems on Windows use the read-only bit to signify that the
         * file has not been checked out. If we're copying one of these files then we don't
         * want to propagate that bit because a followup call to [copyFile] would not be
         * able to overwrite the file a second time.
         */
        setWritable(to);
    }

    /**
     * Set the destination file to writeable.
     *
     * <p>Special note: JimFS doesn't support converting to File from Path and Path doesn't support
     * checking or setting the Windows readonly bit. We have to catch this case and skip the setting
     * writable=true.
     */
    private static void setWritable(Path path) {

        File fileOrNull;
        try {
            fileOrNull = path.toFile();
        } catch (UnsupportedOperationException e) {
            fileOrNull = null;
        }
        if (fileOrNull != null && !fileOrNull.canWrite()) {
            fileOrNull.setWritable(true);
        }
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
                    File destination = new File(to, FilesKt.toRelativeString(f, from));
                    Files.createParentDirs(destination);
                    mkdirs(destination);

                    copyDirectoryContentToDirectory(f, destination);
                } else if (f.isFile()) {
                    File destination =
                            new File(to, FilesKt.toRelativeString(f.getParentFile(), from));
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
     * Returns {@code true} if a file/directory is located directly or indirectly inside a base
     * directory.
     *
     * <p>The given file/directories may or may not exist.
     *
     * <p>Note on corner cases:
     *
     * <ul>
     *   <li>Case sensitivity: Internally, this method uses the {@link Path} API, so it is able to
     *       take the case sensitivity of the filesystem into account (e.g., file `/a/b` is inside
     *       directory `/A` in case-insensitive filesystem).
     *   <li>For other corner cases (e.g., hard/symbolic links), please refer to the javadoc of
     *       {@link Path#relativize(Path)} which this method uses.
     * </ul>
     */
    public static boolean isFileInDirectory(@NonNull File fileOrDir, @NonNull File baseDir) {
        String relativePath;
        try {
            relativePath = baseDir.toPath().relativize(fileOrDir.toPath()).toString();
        } catch (IllegalArgumentException e) {
            // This exception is thrown from Path.relativize() (e.g., when one path is absolute and
            // the other is relative), so we return `false` in that case.
            return false;
        }
        return !relativePath.isEmpty() && !relativePath.startsWith("..");
    }

    /**
     * Returns {@code true} if the two files refer to the same physical file (or potentially the
     * same physically file when created in the case that the given files do not exist yet).
     *
     * <p>This is the correct way to compare physical files, instead of using {@link
     * File#equals(Object)} or similar variants.
     */
    public static boolean isSameFile(@NonNull File file1, @NonNull File file2) {
        // The best ways to compare physical files are:
        //   1. java.nio.file.Files.isSameFile(file1.toPath(), file2.toPath())
        //   2. file1.getCanonicalFile().equals(file2.getCanonicalFile())
        // If the files exist, method 1 should be preferred (e.g., it compares hard links better).
        // If the files don't exist, method 1 may throw an exception, so method 2 should be used
        // instead. Caveat: In the rare cases that the non-existent files are later created as
        // hard/symbolic links to somewhere else, it may invalidate the result returned here.
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
