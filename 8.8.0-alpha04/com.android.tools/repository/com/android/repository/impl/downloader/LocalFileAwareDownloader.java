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

package com.android.repository.impl.downloader;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Checksum;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Decorator around another {@link Downloader} that handles {@code file:///} URLs. Such URLs can
 * be used for testing.
 */
public class LocalFileAwareDownloader implements Downloader {

    private final Downloader mDelegate;

    public LocalFileAwareDownloader(@NonNull Downloader delegate) {
        mDelegate = delegate;
    }

    @Override
    @Nullable
    public InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        if ("file".equals(url.getProtocol())) {
            return url.openStream();
        }

        return mDelegate.downloadAndStream(url, indicator);
    }

    @Override
    @Nullable
    public Path downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        if ("file".equals(url.getProtocol())) {
            Path tempFile = Files.createTempFile(LocalFileAwareDownloader.class.getName(), null);
            Path source = Paths.get(url.getFile());
            Files.copy(source, tempFile);
        }

        return mDelegate.downloadFully(url, indicator);
    }

    @Override
    public void downloadFully(
            @NonNull URL url,
            @NonNull Path target,
            @Nullable Checksum checksum,
            @NonNull ProgressIndicator indicator)
            throws IOException {
        if ("file".equals(url.getProtocol())) {
            // Ignore the checksum when just copying locally.
            Path source = target.getFileSystem().getPath(url.getFile());
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
            return;
        }

        mDelegate.downloadFully(url, target, checksum, indicator);
    }
}
