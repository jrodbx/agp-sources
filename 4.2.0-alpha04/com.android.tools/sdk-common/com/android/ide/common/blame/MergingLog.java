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

package com.android.ide.common.blame;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.FileUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

/**
 * Stores where file and text fragments within files came from, so the original can be found
 * later if a subsequent build step outputs an error.
 *
 * It is implicitly incremental, shards (which correspond to type directories for resource files,
 * eg. layout-land) are created or loaded only when the output files that they store are changed,
 * and only the changed files are written when {@link #write()} is called.
 *
 * For its use by MergeWriter, it uses ConcurrentMaps internally, so it is safe to perform any log
 * operation from any thread.
 */
public class MergingLog {

    /**
     * Map from whole files to other files (e.g. for all non-value resources)
     */
    @NonNull
    private final LoadingCache<String, Map<SourceFile, SourceFile>> mWholeFileMaps =
            CacheBuilder.newBuilder().build(new CacheLoader<String, Map<SourceFile, SourceFile>>() {
                @Override
                public Map<SourceFile, SourceFile> load(@NonNull String shard) {
                    return MergingLogPersistUtil.loadFromSingleFile(mOutputFolder, shard);
                }
            });

    private final CacheLoader<String, Map<SourceFile, Map<SourcePosition, SourceFilePosition>>>
            cacheLoader =
                    new CacheLoader<
                            String, Map<SourceFile, Map<SourcePosition, SourceFilePosition>>>() {
                        @Override
                        public Map<SourceFile, Map<SourcePosition, SourceFilePosition>> load(
                                String shard) throws Exception {
                            return MergingLogPersistUtil.loadFromMultiFileVersion2(
                                    mOutputFolder, shard);
                        }
                    };

    /** Map from positions in a merged file to file positions in their source files. */
    @NonNull
    private final LoadingCache<String, Map<SourceFile, Map<SourcePosition, SourceFilePosition>>>
            mMergedFileMaps = CacheBuilder.newBuilder().build(cacheLoader);

    @NonNull
    private final File mOutputFolder;


    public MergingLog(@NonNull File outputFolder) {
        mOutputFolder = outputFolder;
    }

    /**
     * Store the source of a file in the merging log.
     *
     * @param source the original file.
     * @param destination the destination.
     */
    public void logCopy(@NonNull SourceFile source, @NonNull SourceFile destination) {
        getWholeFileMap(destination).put(destination, source);
    }

    /**
     * Store the source of a file in the merging log.
     *
     * @param source the original file.
     * @param destination the destination.
     */
    public void logCopy(@NonNull File source, @NonNull File destination) {
        logCopy(new SourceFile(source), new SourceFile(destination));
    }


    /**
     * Remove a merged file from the merging log.
     */
    public void logRemove(@NonNull SourceFile merged) {
        getWholeFileMap(merged).remove(merged);
        getMergedFileMap(merged).remove(merged);
    }

    /**
     * Store the source file positions for a merged file.
     *
     * @param mergedFile the destination file.
     * @param map        the map from positions in the destination file to the SourceFilePosition
     *                   that they came from.
     */
    public void logSource(
            @NonNull SourceFile mergedFile,
            @NonNull Map<SourcePosition, SourceFilePosition> map) {
        getMergedFileMap(mergedFile).put(mergedFile, map);
    }


    @NonNull
    private Map<SourceFile, SourceFile> getWholeFileMap(@NonNull SourceFile file) {
        String shard = getShard(file);
        try {
            return mWholeFileMaps.get(shard);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private Map<SourceFile, Map<SourcePosition, SourceFilePosition>> getMergedFileMap(
            @NonNull SourceFile file) {
        String shard = getShard(file);
        try {
            return mMergedFileMaps.get(shard);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find the original source file corresponding to an intermediate file.
     */
    @NonNull
    public SourceFile find(@NonNull SourceFile mergedFile) {
        SourceFile sourceFile = getWholeFileMap(mergedFile).get(mergedFile);
        return sourceFile != null ? sourceFile : mergedFile;
    }

    /**
     * Find the original source file and position for a position in an intermediate merged file.
     *
     * <p>Returns the original position if none found.
     */
    @NonNull
    public SourceFilePosition find(@NonNull final SourceFilePosition mergedFilePosition) {
        SourceFile mergedSourceFile = mergedFilePosition.getFile();
        Map<SourcePosition, SourceFilePosition> positionMap =
                getMergedFileMap(mergedSourceFile).get(mergedSourceFile);
        if (positionMap == null) {
            SourceFile sourceFile = find(mergedSourceFile);
            return new SourceFilePosition(sourceFile, mergedFilePosition.getPosition());
        }
        SourceFilePosition position = find(mergedFilePosition.getPosition(), positionMap);
        // we failed to find a link, return where we are.
        return position != null ? position : mergedFilePosition;
    }

    /**
     * Find the original source file and position for a position in an intermediate merged file.
     *
     * <p>Returns null if none found.
     */
    @Nullable
    public static SourceFilePosition find(
            @NonNull final SourcePosition position,
            @NonNull final Map<SourcePosition, SourceFilePosition> positionMap) {

        // TODO: this is not very efficient, which matters if we start processing debug messages.
        NavigableMap<SourcePosition, SourceFilePosition> sortedMap =
                new TreeMap<>(SourcePosition::compareStart);
        sortedMap.putAll(positionMap);

        /*

         e.g. if we have
         <pre>
                   error1     error2
                    /--/       /--/
         <a> <b key="c"  value="d" /> </a>
         \----------------a---------------\
             \-----------b-----------\
                    \--\
                     c
        </pre>
        we want to find c for error 1 and b for error 2.
          */

        // get the element just before this one.
        @Nullable
        Map.Entry<SourcePosition, SourceFilePosition> candidate =
                position.getStartColumn() == -1
                        ? sortedMap.ceilingEntry(position)
                        : sortedMap.floorEntry(position);

        // If the search failed then we should check starting from the last element in the map in
        // case of ceiling search, or the first element in case of a floor search.
        if (candidate == null) {
            if (position.getStartColumn() == -1) {
                candidate = sortedMap.lastEntry();
            } else {
                candidate = sortedMap.firstEntry();
            }
        }

        // Don't traverse the whole file.
        // This is the product of the depth and breadth of nesting that can be handled.
        int patience = 20;
        // check if it encompasses the error position.
        while (candidate != null
                && (position.compareEnd(candidate.getKey()) > 0
                        || position.compareStart(candidate.getKey()) < 0)) {
            patience--;
            if (patience == 0) {
                candidate = null;
                break;
            }
            candidate = sortedMap.lowerEntry(candidate.getKey());
        }

        if (candidate == null) {
            // we failed to find a link.
            return null;
        }

        return candidate.getValue();

    }

    @NonNull
    private static String getShard(@NonNull SourceFile sourceFile) {
        File file = sourceFile.getSourceFile();
        return file != null ? file.getParentFile().getName() : "unknown";
    }

    /**
     * Persist the current state of the merging log.
     */
    public void write() throws IOException {
        FileUtils.mkdirs(mOutputFolder);

        // This is intrinsically incremental, any shards that were touched were loaded, and so
        // will be saved. Empty map will result in the deletion of the file.
        for (Map.Entry<String, Map<SourceFile, Map<SourcePosition, SourceFilePosition>>> entry :
                mMergedFileMaps.asMap().entrySet()) {
            MergingLogPersistUtil.saveToMultiFileVersion2(
                    mOutputFolder, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Map<SourceFile, SourceFile>> entry :
                mWholeFileMaps.asMap().entrySet()) {
            MergingLogPersistUtil
                    .saveToSingleFile(mOutputFolder, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Find a destination file that was mapped from an original file.
     *
     * @param original the original file
     * @return a destination file that was generated from the original file
     */
    @NonNull
    public SourceFile destinationFor(@NonNull SourceFile original) {
        String shard = getShard(original);

        try {
            /*
             * Search the whole file maps.
             */
            Optional<SourceFile> dst = mWholeFileMaps.get(shard).entrySet().stream()
                    .filter(e -> e.getValue().equals(original))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (dst.isPresent()) {
                return dst.get();
            }

            /*
             * Search the merged file maps.
             */
            dst = mMergedFileMaps.get(shard).entrySet().stream()
                    .filter(e -> e.getValue().values().stream()
                            .anyMatch(sfp -> sfp.getFile().equals(original)))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (dst.isPresent()) {
                return dst.get();
            }

            throw new RuntimeException("No destination found for " + original);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
