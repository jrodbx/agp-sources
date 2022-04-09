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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

/**
 * Implementations provide a general mechanism for downloading files.
 */
public interface Downloader {

    /**
     * Gets a stream associated with the content at the given URL. This could be achieved by
     * downloading the file completely, streaming it directly, or some combination.
     *
     * @param url       The URL to fetch.
     * @param indicator Facility for showing download progress and logging.
     * @return An InputStream corresponding to the specified content, or {@code null} if the
     *         download is cancelled.
     */
    @Nullable
    InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException;

    /**
     * Downloads the content at the given URL to a temporary file and returns a handle to that file.
     *
     * @param url       The URL to fetch.
     * @param indicator Facility for showing download progress and logging.
     * @return The temporary file, or {@code null} if the download is cancelled.
     */
    @Nullable
    Path downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator) throws IOException;

    /**
     * Downloads the content at the given URL to the given file. Calling this method instructs the
     * downloader to fetch the content from the original server and bypass any caches.
     *
     * <p>For example, in case of HTTP connections, calling this method may be equivalent to adding
     * "Cache-Control: no-cache" and "Pragma: no-cache" headers to the request, thus instructing the
     * network facilities that no caching is expected to take place (e.g., on proxy servers).
     *
     * @param url The URL to fetch.
     * @param target The location to download to.
     * @param checksum If specified, first check {@code target} to see if the given checksum matches
     *     the existing file. If so, returns immediately.
     * @param indicator Facility for showing download progress and logging.
     */
    void downloadFully(
            @NonNull URL url,
            @NonNull Path target,
            @Nullable Checksum checksum,
            @NonNull ProgressIndicator indicator)
            throws IOException;

    /**
     * Downloads the content at the given URL to the given file, using caching techniques if
     * possible. Which specific caching techniques to use is defined by the implementation. As far
     * as the contract goes, calling this method instructs the downloader that the caller does not
     * require the content to be fetched from the original server, and a previously cached copy may
     * be re-used.
     *
     * <p>Note that calling this method does not guarantee caching or a better performance: it is an
     * implementation detail how exactly this hint is used. For example, when checking for software
     * updates availability, the caller will know that no caching must be used, so it should not
     * call this method, and call {@link #downloadFully} instead. On the other hand, for a large
     * download which is not expected to change on the original server, the caller typically would
     * not mind against intrinsic optimizations and caching, so in that case it should call this
     * method.
     *
     * @param url The URL to fetch.
     * @param target The location to download to.
     * @param checksum If specified, first check {@code target} to see if the given checksum matches
     *     the existing file. If so, returns immediately.
     * @param indicator Facility for showing download progress and logging.
     */
    default void downloadFullyWithCaching(
            @NonNull URL url,
            @NonNull Path target,
            @Nullable Checksum checksum,
            @NonNull ProgressIndicator indicator)
            throws IOException {
        downloadFully(url, target, checksum, indicator);
    }

    /**
     * For implementations that support resuming partial downloads, sets the directory location
     * where to download intermediates (i.e. partial) files. If the intermediate directory belongs
     * to a different file system and or partition, there will be an additional overhead of moving
     * the intermediate file to its final destination after a full download is complete.
     *
     * <p>If the directory is invalid (or inaccessible, or the file system is full), implementors
     * will ignore this value and use the default behavior.
     *
     * <p>By default, implementations use the same intermediate directory as the destination
     * directory of the download.
     *
     * @param intermediatesLocation the path to use for download intermediates
     */
    default void setDownloadIntermediatesLocation(@NonNull Path intermediatesLocation) {}

    /**
     * Hash the given input stream.
     *
     * @param in The stream to hash. It will be fully consumed but not closed.
     * @param fileSize The expected length of the stream, for progress display purposes.
     * @param progress The indicator will be updated with the expected completion fraction.
     * @return The sha1 hash of the input stream.
     * @throws IOException IF there's a problem reading from the stream.
     */
    @NonNull
    static String hash(
            @NonNull InputStream in,
            long fileSize,
            @NonNull String algorithm,
            @NonNull ProgressIndicator progress)
            throws IOException {
        progress.setText("Checking existing file...");
        Hasher hasher =
                (algorithm.equalsIgnoreCase("sha-256") ? Hashing.sha256() : Hashing.sha1())
                        .newHasher();
        byte[] buf = new byte[5120];
        long totalRead = 0;
        int bytesRead;
        while ((bytesRead = in.read(buf)) > 0) {
            hasher.putBytes(buf, 0, bytesRead);
            progress.setFraction((double) totalRead / (double) fileSize);
        }
        return hasher.hash().toString();
    }
}
