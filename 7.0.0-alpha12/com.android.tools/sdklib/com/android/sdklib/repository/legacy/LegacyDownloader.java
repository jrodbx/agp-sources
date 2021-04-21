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
package com.android.sdklib.repository.legacy;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.api.Checksum;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.SettingsController;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.legacy.remote.internal.DownloadCache;
import com.android.utils.Pair;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link Downloader} implementation that uses the old {@link DownloadCache}.
 *
 * TODO: Implement a new, fully-featured downloader, then mark this as deprecated.
 */
public class LegacyDownloader implements Downloader {

    private final DownloadCache mDownloadCache;

    private final FileOp mFileOp;

    private final SettingsController mSettingsController;
    private static final int BUF_SIZE = 8192;

    public LegacyDownloader(@NonNull FileOp fop, @NonNull SettingsController settings) {
        mDownloadCache =
                DownloadCache.inUserHome(fop, DownloadCache.Strategy.FRESH_CACHE, settings);
        mFileOp = fop;
        mSettingsController = settings;
    }

    @Override
    @Nullable
    public InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        return mDownloadCache.openCachedUrl(getUrl(url));
    }

    @Nullable
    @Override
    public Path downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        Path target =
                FileOpUtils.getNewTempDir("LegacyDownloader", mFileOp.getFileSystem())
                        .resolve("download");
        downloadFully(url, target, null, indicator);
        return target;
    }

    @Override
    public void downloadFully(
            @NonNull URL url,
            @NonNull Path target,
            @Nullable Checksum checksum,
            @NonNull ProgressIndicator indicator)
            throws IOException {
        if (CancellableFileIo.exists(target) && checksum != null) {
            indicator.setText("Verifying previous download...");
            try (InputStream in = new BufferedInputStream(mFileOp.newFileInputStream(target))) {
                if (checksum.getValue()
                        .equals(
                                Downloader.hash(
                                        in,
                                        CancellableFileIo.size(target),
                                        checksum.getType(),
                                        indicator.createSubProgress(0.3)))) {
                    return;
                }
            }
            indicator = indicator.createSubProgress(1);
        }
        Files.createDirectories(target.getParent());
        OutputStream out = Files.newOutputStream(target);
        Pair<InputStream, URLConnection> downloadedResult =
                mDownloadCache.openDirectUrl(getUrl(url));
        URLConnection connection = downloadedResult.getSecond();
        if (!(connection instanceof HttpURLConnection)
                || ((HttpURLConnection) connection).getResponseCode() == 200) {
            indicator.setText(
                    String.format("Downloading %s...", new File(url.getFile()).getName()));
            long total = connection.getContentLengthLong();
            long done = 0;
            InputStream from = downloadedResult.getFirst();
            byte[] buf = new byte[BUF_SIZE];
            int prevPercent = 0;
            while (true) {
                int r = from.read(buf);
                if (r == -1) {
                    break;
                }
                done += r;
                out.write(buf, 0, r);
                int percent = (int) (done * 100. / total);
                if (percent != prevPercent) {
                    // Don't update too often
                    indicator.setFraction((double) done / total);
                    prevPercent = percent;
                }
            }
            indicator.setFraction(1);
            out.close();
        }
    }

    private String getUrl(@NonNull URL url) {
        String urlStr = url.toString();
        if (mSettingsController.getForceHttp()) {
            urlStr = urlStr.replaceAll("^https://", "http://");
        }
        return urlStr;
    }
}
