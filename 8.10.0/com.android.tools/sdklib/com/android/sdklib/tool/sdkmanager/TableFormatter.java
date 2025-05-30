/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.sdklib.tool.sdkmanager;

import com.android.annotations.NonNull;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mechanism for outputting tabular data to a stream.
 */
public class TableFormatter<T> {

    private final List<Column<T>> mColumns = new ArrayList<>();

    /**
     * Adds a column to be printed in this table.
     *
     * @param title       The title to be shown at the top of the column.
     * @param valueGetter {@link Function} returning the value to be shown in the column.
     * @param prefixLimit Max characters to show before "..." if the value is long.
     * @param suffixLimit Max characters to show after "..." if the value is long.
     */
    public void addColumn(@NonNull String title, @NonNull Function<T, String> valueGetter,
            int prefixLimit, int suffixLimit) {
        mColumns.add(new Column<>(title, valueGetter, prefixLimit, suffixLimit));
    }

    /**
     * Print out a table with the given values to the given stream.
     * At least one column must have been added using {@link #addColumn(String, Function, int, int)}
     */
    public void print(@NonNull Collection<T> values, @NonNull PrintStream out) {
        assert !mColumns.isEmpty();

        String separator = "-------";
        Map<Column<T>, Integer> maxLengths =
                mColumns.stream().collect(Collectors.toMap(
                        Function.identity(),
                        column -> Math.max(values.stream()
                                        .mapToInt(value -> column.getValue(value).length())
                                        .map(length -> Math.min(length, column.getMaxLength()))
                                        .max().orElse(0),
                                Math.max(separator.length(), column.getTitle().length()))));

        String pattern = "  " +
                String.join(" | ", mColumns.stream()
                        .map(maxLengths::get)
                        .map(max -> String.format("%%-%ds", max))
                        .collect(Collectors.toList())) +
                "\n";

        out.printf(pattern, mColumns.stream().map(Column::getTitle).toArray());
        out.printf(pattern, mColumns.stream().map(column -> separator).toArray());
        values.forEach(value -> out.printf(pattern,
                mColumns.stream().map(column -> column.getValue(value)).toArray()));
    }

    private static class Column<T> {

        private final String mTitle;
        private final Function<T, String> mValueGetter;
        private final int mPrefixLimit;
        private final int mSuffixLimit;

        public Column(@NonNull String title, @NonNull Function<T, String> valueGetter,
                int prefixLimit, int suffixLimit) {
            mTitle = title;
            mValueGetter = valueGetter;
            mPrefixLimit = prefixLimit;
            mSuffixLimit = suffixLimit;
        }

        @NonNull
        public String getTitle() {
            return mTitle;
        }

        public int getMaxLength() {
            return mPrefixLimit + mSuffixLimit + 3;
        }

        @NonNull
        public String getValue(@NonNull T input) {
            String value = mValueGetter.apply(input);
            if (value.length() > getMaxLength()) {
                value = value.substring(0, mPrefixLimit) + "..." +
                        value.substring(value.length() - mSuffixLimit);
            }
            return value;
        }
    }
}
