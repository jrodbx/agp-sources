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
package com.android.ide.common.resources.sampledata;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/** Utility class for parsing sample data CSV files. */
public class SampleDataCsvParser {
    private final ImmutableMultimap<String, String> myTable;

    private SampleDataCsvParser(@NonNull ImmutableMultimap<String, String> table) {
        myTable = table;
    }

    @NonNull
    public static SampleDataCsvParser parse(@NonNull Reader reader) throws IOException {
        CSVReader csvReader = new CSVReader(reader);
        String[] headers = csvReader.readNext();

        ImmutableMultimap.Builder<String, String> mapBuilder = ImmutableMultimap.builder();
        String[] values;
        while ((values = csvReader.readNext()) != null) {
            for (int i = 0; i < headers.length && i < values.length; i++) {
                mapBuilder.put("/" + headers[i], values[i]);
            }
        }

        return new SampleDataCsvParser(mapBuilder.build());
    }

    @NonNull
    public Set<String> getPossiblePaths() {
        return myTable.keySet();
    }

    @NonNull
    public ImmutableCollection<String> getPath(@NonNull String path) {
        return myTable.get(path);
    }
}
