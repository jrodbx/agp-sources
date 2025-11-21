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
package com.android.io;

import com.android.ProgressManagerAdapter;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Slow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Wrapper methods around {@link Files} that check for cancellation before read I/O operations.
 * Write operations (such as delete, etc) are not exposed by this class because cancelling them is
 * potentially unsafe. Please invoke write operations through the {@link Files} class directly.
 *
 * <p>Please don't add convenience methods to this class. All methods are intended to closely match
 * {@link Files}.
 *
 * @see Files
 * @see ProgressManagerAdapter
 */
public class CancellableFileIo {
    /** @see Files#exists(Path, LinkOption... options) */
    @Slow
    public static boolean exists(@NonNull Path file, @NonNull LinkOption... options) {
        ProgressManagerAdapter.checkCanceled();
        return Files.exists(file, options);
    }

    /** @see Files#notExists(Path, LinkOption...) */
    @Slow
    public static boolean notExists(@NonNull Path file, @NonNull LinkOption... options) {
        ProgressManagerAdapter.checkCanceled();
        return Files.notExists(file, options);
    }

    /** @see Files#isDirectory(Path, LinkOption... options) */
    @Slow
    public static boolean isDirectory(@NonNull Path file, @NonNull LinkOption... options) {
        ProgressManagerAdapter.checkCanceled();
        return Files.isDirectory(file, options);
    }

    /** @see Files#isRegularFile(Path, LinkOption... options) */
    @Slow
    public static boolean isRegularFile(@NonNull Path file, @NonNull LinkOption... options) {
        ProgressManagerAdapter.checkCanceled();
        return Files.isRegularFile(file, options);
    }

    /** @see Files#isSymbolicLink(Path) */
    @Slow
    public static boolean isSymbolicLink(@NonNull Path file) {
        ProgressManagerAdapter.checkCanceled();
        return Files.isSymbolicLink(file);
    }

    /** @see Files#isReadable(Path) */
    @Slow
    public static boolean isReadable(@NonNull Path file) {
        ProgressManagerAdapter.checkCanceled();
        return Files.isReadable(file);
    }

    /** @see Files#isWritable(Path) */
    @Slow
    public static boolean isWritable(@NonNull Path file) {
        ProgressManagerAdapter.checkCanceled();
        return Files.isWritable(file);
    }

    /** @see Files#isExecutable(Path) */
    @Slow
    public static boolean isExecutable(@NonNull Path file) {
        ProgressManagerAdapter.checkCanceled();
        return Files.isExecutable(file);
    }

    /** @see Files#isHidden(Path) */
    @Slow
    public static boolean isHidden(@NonNull Path file) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.isHidden(file);
    }

    /** @see Files#isSameFile(Path, Path) */
    @Slow
    public static boolean isSameFile(@NonNull Path path1, @NonNull Path path2) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.isSameFile(path1, path2);
    }

    /** @see Files#size(Path) */
    @Slow
    public static long size(@NonNull Path file) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.size(file);
    }

    /** @see Files#getLastModifiedTime(Path, LinkOption...) */
    @Slow
    @NonNull
    public static FileTime getLastModifiedTime(@NonNull Path file, @NonNull LinkOption... options)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.getLastModifiedTime(file, options);
    }

    /** @see Files#getAttribute(Path, String, LinkOption...) */
    @Slow
    @NonNull
    public static Object getAttribute(
            @NonNull Path file, @NonNull String attribute, @NonNull LinkOption... options)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.getAttribute(file, attribute, options);
    }

    /** @see Files#readAttributes(Path, Class, LinkOption...) */
    @Slow
    @NonNull
    public static <A extends BasicFileAttributes> A readAttributes(
            @NonNull Path file, @NonNull Class<A> type, @NonNull LinkOption... options)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.readAttributes(file, type, options);
    }

    /**
     * @see Files#list(Path)
     *
     * <p>The {@code try}-with-resources construct should be used to ensure that the stream is
     * closed after the stream operations are completed.
     */
    @Slow
    @NonNull
    public static Stream<Path> list(@NonNull Path dir) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.list(dir);
    }

    /**
     * @see Files#walk(Path, FileVisitOption...)
     *
     * <p>The {@code try}-with-resources construct should be used to ensure that the stream is
     * closed after the stream operations are completed.
     */
    @Slow
    @NonNull
    public static Stream<Path> walk(@NonNull Path start, @NonNull FileVisitOption... options)
            throws IOException {
        return walk(start, Integer.MAX_VALUE, options);
    }

    /**
     * @see Files#walk(Path, int, FileVisitOption...)
     *
     * <p>The {@code try}-with-resources construct should be used to ensure that the stream is
     * closed after the stream operations are completed.
     */
    @Slow
    @NonNull
    public static Stream<Path> walk(
            @NonNull Path start, int maxDepth, @NonNull FileVisitOption... options)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.walk(start, maxDepth, options).filter(path -> {
            ProgressManagerAdapter.checkCanceled();
            return true;
        });
    }

    /** @see Files#walkFileTree(Path, Set, int, FileVisitor) */
    @Slow
    @NonNull
    public static Path walkFileTree(
            @NonNull Path start,
            @NonNull Set<FileVisitOption> options,
            int maxDepth,
            @NonNull FileVisitor<? super Path> visitor)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.walkFileTree(start, options, maxDepth, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    @NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                ProgressManagerAdapter.checkCanceled();
                return visitor.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs)
                    throws IOException {
                return visitor.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException exc)
                    throws IOException {
                return visitor.visitFileFailed(file, exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(@NonNull Path dir, @Nullable IOException exc)
                    throws IOException {
                return visitor.postVisitDirectory(dir, exc);
            }
        });
    }

    /** @see Files#walkFileTree(Path, FileVisitor) */
    @Slow
    @NonNull
    public static Path walkFileTree(@NonNull Path start, @NonNull FileVisitor<? super Path> visitor)
            throws IOException {
        return walkFileTree(
                start, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor);
    }

    /** @see Files#newInputStream(Path, OpenOption...) */
    @Slow
    @NonNull
    public static InputStream newInputStream(@NonNull Path file, OpenOption... options)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.newInputStream(file, options);
    }

    /** @see Files#newBufferedReader(Path) */
    @Slow
    @NonNull
    public static BufferedReader newBufferedReader(@NonNull Path file) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.newBufferedReader(file);
    }

    /** @see Files#newByteChannel(Path, Set, FileAttribute...) */
    @Slow
    @NonNull
    public static SeekableByteChannel newByteChannel(
            @NonNull Path file,
            @NonNull Set<? extends OpenOption> options,
            @NonNull FileAttribute<?>... attrs)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.newByteChannel(file, options, attrs);
    }

    /** @see Files#newByteChannel(Path, OpenOption...) */
    @Slow
    @NonNull
    public static SeekableByteChannel newByteChannel(
            @NonNull Path file, @NonNull OpenOption... options)
            throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.newByteChannel(file, options);
    }

    /** @see Files#readAllBytes(Path) */
    @Slow
    @NonNull
    public static byte[] readAllBytes(@NonNull Path file) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.readAllBytes(file);
    }

    /** @see Files#readAllLines(Path) */
    @Slow
    @NonNull
    public static List<String> readAllLines(@NonNull Path file) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /** @see Files#readAllLines(Path, Charset) */
    @Slow
    @NonNull
    public static List<String> readAllLines(@NonNull Path file, @NonNull Charset cs) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.readAllLines(file, cs);
    }

    /** Reads a text file in UTF-8 encoding. */
    @Slow
    @NonNull
    public static String readString(@NonNull Path file) throws IOException {
        return new String(readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** @see Files#readSymbolicLink(Path) */
    @Slow
    @NonNull
    public static Path readSymbolicLink(@NonNull Path link) throws IOException {
        ProgressManagerAdapter.checkCanceled();
        return Files.readSymbolicLink(link);
    }
}
