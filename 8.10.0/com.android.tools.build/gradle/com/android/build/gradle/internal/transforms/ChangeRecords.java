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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Book keeper for all recorded changes to files of interest.
 */
public class ChangeRecords {

    // changes records, indexed by file path, and value is the last change made on the file.
    Map<String, Status> records = new HashMap<>();

    public synchronized void add(@NonNull Status status, @NonNull String filePath) {
        records.put(filePath, status);
    }

    /**
     * Add all changes from another {@link ChangeRecords} instance. The passed instance records
     * will be added if there is not already a record with the same file path present in this
     * instance.
     * @param changeRecords another set of file changes records.
     */
    public synchronized void addAll(@NonNull ChangeRecords changeRecords) {
        for (Map.Entry<String, Status> changeRecord : changeRecords.records.entrySet()) {
            if (!records.containsKey(changeRecord.getKey())) {
                records.put(changeRecord.getKey(), changeRecord.getValue());
            }
        }
    }

    /**
     * Returns the last change recorded for a file or null of no change was ever recorded.
     */
    @Nullable
    synchronized Status getChangeFor(String filePath) {
        return records.get(filePath);
    }

    /**
     * Writes the current set of file changes to the passed file.
     * @param file file to write changes to.
     * @throws IOException if the file cannot be opened or wrote.
     */
    void write(File file) throws IOException {
        Files.createParentDirs(file);
        try (FileWriter fileWriter = new FileWriter(file)) {
            for (Map.Entry<String, Status> record : records.entrySet()) {
                fileWriter.write(String.format("%s,%s", record.getValue(), record.getKey()));
                fileWriter.write("\n");
            }
        }
    }

    /**
     * Calculates the list of files which change record has the passed status.
     * @return a possibly empty list of files path.
     */
    @NonNull
    synchronized Set<String> getFilesForStatus(Status status) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (String s : records.keySet()) {
            if (getChangeFor(s) == status) {
                builder.add(s);
            }
        }
        return builder.build();
    }

    /**
     * Interface to process individual change records.
     */
    public interface RecordHandler {

        /**
         * Process an individual file change record.
         *
         * @param filePath the file path that changed
         * @param status the change.
         */
        void handle(String filePath, Status status) throws IOException, TransformException;
    }

    /**
     * Process the incremental file records individually with the passed {@link RecordHandler}.
     * @param incrementalFile a file containing change records.
     * @param handler the record handler.
     */
    public static void process(@NonNull File incrementalFile, @NonNull RecordHandler handler)
            throws IOException, TransformException {
        if (!incrementalFile.isFile()) {
            return;
        }
        Map<String, Status> changeRecords = load(incrementalFile).records;
        // delete the incremental changes file to reset the list of changes
        FileUtils.delete(incrementalFile);
        if (changeRecords.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Status> changeRecord : changeRecords.entrySet()) {
            handler.handle(changeRecord.getKey(), changeRecord.getValue());
        }
    }

    /**
     * Load change records from a persisted file.
     */
    @NonNull
    static ChangeRecords load(File file) throws IOException {
        ChangeRecords changeRecords = new ChangeRecords();
        List<String> rawRecords = Files.readLines(file, Charsets.UTF_8);
        for (String rawRecord : rawRecords) {
            StringTokenizer st = new StringTokenizer(rawRecord, ",");
            if (st.countTokens() != 2) {
                throw new IOException("Invalid incremental change record : " + rawRecord);
            }
            changeRecords.add(Status.valueOf(st.nextToken()), st.nextToken());
        }
        return changeRecords;
    }
}
