/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;

/** A {@link MergeWriter} for assets, using {@link AssetItem}. */
public class MergedAssetWriter
        extends MergeWriter<AssetItem, MergedAssetWriter.AssetWorkParameters> {
    private static final int MIN_JOBS = 3;
    private static final int MAX_BUCKETS = 5;

    private final Map<String, AssetItem> allJobs = new HashMap<String, AssetItem>();

    public MergedAssetWriter(@NonNull File rootFolder, @NonNull WorkerExecutorFacade facade) {
        super(rootFolder, facade);
    }

    @Override
    public void addItem(@NonNull final AssetItem item) throws ConsumerException {
        if (item.isTouched()) {
            allJobs.put(item.getName(), item);
        }
    }

    public static class AssetWorkParameters implements Serializable {
        public final List<AssetItem> assetItemBucket;
        public final File rootFolder;

        private AssetWorkParameters(
                @NonNull List<AssetItem> assetItemBucket, @NonNull File rootFolder) {
            this.assetItemBucket = assetItemBucket;
            this.rootFolder = rootFolder;
        }
    }

    public static class AssetWorkAction implements WorkerExecutorFacade.WorkAction {

        private final List<AssetItem> assetItemBucket;
        private final File rootFolder;

        @Inject
        public AssetWorkAction(AssetWorkParameters parameters) {
            this.assetItemBucket = parameters.assetItemBucket;
            this.rootFolder = parameters.rootFolder;
        }

        @Override
        public void run() {
            for (AssetItem item : assetItemBucket) {
                try {
                    AssetFile assetFile = Preconditions.checkNotNull(item.getSourceFile());

                    Path fromFile = assetFile.getFile().toPath();

                    // the out file is computed from the item key since that includes the
                    // relative folder.
                    Path toFile =
                            new File(rootFolder, item.getKey().replace('/', File.separatorChar))
                                    .toPath();

                    Files.createDirectories(toFile.getParent());

                    if (item.shouldBeUnGzipped()) {
                        // When AAPT processed resources, it would uncompress gzipped files, as they
                        // will be compressed in the APK anyway. They are renamed in
                        // AssetItem#create(File, File)
                        try (GZIPInputStream gzipInputStream =
                                new GZIPInputStream(
                                        new BufferedInputStream(Files.newInputStream(fromFile)))) {
                            Files.copy(
                                    gzipInputStream, toFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        FileUtils.copyFile(fromFile, toFile);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private List<List<AssetItem>> createBuckets() {
        List<List<AssetItem>> jobBuckets = new ArrayList<>();
        // Only write it if the state is TOUCHED.
        int totalJobs = allJobs.values().size();
        if (totalJobs <= MIN_JOBS) { // One bucket is enough
            jobBuckets.add(new ArrayList<>(allJobs.values()));
        } else {
            // Use max buckets if number of buckets is larger than max
            int bucketCount = Math.min(totalJobs / MIN_JOBS, MAX_BUCKETS);

            // Create buckets
            for (int bucket = 0; bucket < bucketCount; bucket++) {
                jobBuckets.add(new ArrayList<>());
            }

            // Distribute jobs between buckets using round-robin
            Iterator<AssetItem> jobsIterator = allJobs.values().iterator();
            int currBucket = 0;
            while (jobsIterator.hasNext()) {
                jobBuckets.get(currBucket).add(jobsIterator.next());
                currBucket = ((currBucket + 1) % bucketCount);
            }
        }
        return jobBuckets;
    }

    @Override
    protected void postWriteAction() throws ConsumerException {
        List<List<AssetItem>> jobBuckets = createBuckets();
        for (List<AssetItem> bucket : jobBuckets) {
            getExecutor()
                    .submit(new AssetWorkAction(new AssetWorkParameters(bucket, getRootFolder())));
        }
    }

    @Override
    public void removeItem(@NonNull AssetItem removedItem, @Nullable AssetItem replacedBy)
            throws ConsumerException {
        if (replacedBy == null) {
            allJobs.remove(removedItem.getName());
            // Delete if file was created previously
            File removedFile = new File(getRootFolder(), removedItem.getName());
            removedFile.delete();
        }
    }

    @Override
    public boolean ignoreItemInMerge(AssetItem item) {
        // never ignore any item
        return false;
    }
}
