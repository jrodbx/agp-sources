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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class handles the element selection for sample data sources. The class receives the full
 * content of the sample data and, depending on the resource name, allow for two different
 * selectors:
 *
 * <ul>
 *   <li>Cursor selection: This class keeps a cursor associated to the resource name that moves in
 *       each call. If only the resource name or a subarray selectors are given, the cursor is used.
 *   <li>Index selection: Elements can be referred by position (e.g. [4]) or by string match (e.g.
 *       [hiking.png])
 * </ul>
 */
public class SampleDataManager {
    public static final String SUBARRAY_SEPARATOR = ":";

    /**
     * Holds the current cursor position for the sample data file so a consistent view is provided
     * for a given resolver (i.e. entries are not repeated and they are different for each element).
     */
    private final Map<String, AtomicInteger> mSampleDataPosition = new HashMap<>();

    /**
     * Returns a line of sample data content from the given content and fileName. The resourceName
     * is used to track the current cursor position within that file.
     */
    @NonNull
    private String getSampleDataLineFromCursor(
            @NonNull String resourceName, @NonNull List<String> content) {
        if (content.isEmpty()) {
            return "";
        }

        AtomicInteger position = mSampleDataPosition.get(resourceName);
        if (position == null) {
            position = new AtomicInteger(0);
            mSampleDataPosition.put(resourceName, position);
        }

        int contentSize = content.size();
        int cursorPosition = position.getAndIncrement() % contentSize;
        return content.get(cursorPosition);
    }

    /**
     * Returns a line of sample data content from the given content and fileName. The resource name
     * is used to track the current cursor position within that file.
     *
     * @param resourcePath The resource path consists of the resource name plus an optional array
     *     selector. Arrays selectors can point at specific elements [4] or use string matching
     *     [biking.png] (this will match elements ending with "biking.png"). Selectors also allow
     *     for sub-arrays like:
     *     <ul>
     *       <li>[:3] Selects elements from 0 up to the 3rd element
     *       <li>[2:] Selects the elements from the second position on
     *       <li>[10:20] Selects elements from position 10 up to position 20
     *     </ul>
     *
     * @param contentList The content of the data source
     */
    @NonNull
    public String getSampleDataLine(
            @NonNull String resourcePath, @NonNull ImmutableList<String> contentList) {
        if (contentList.isEmpty()) {
            return "";
        }

        // Trim the last line if it's empty (since it's usually unintended)
        if (contentList.get(contentList.size() - 1).isEmpty()) {
            contentList = contentList.subList(0, contentList.size() - 1);
        }

        // Parse any possible index reference
        int openBracket = resourcePath.indexOf('[');
        if (openBracket != -1) {
            // Contains an index
            int closeBracket = resourcePath.indexOf(']');
            if (closeBracket != -1 && closeBracket > openBracket + 1) {
                String indexValue = resourcePath.substring(openBracket + 1, closeBracket);

                if (indexValue.contains(SUBARRAY_SEPARATOR)) {
                    contentList = getContentSubArray(contentList, indexValue);
                } else {
                    try {
                        return contentList.get(
                                Integer.parseUnsignedInt(indexValue) % contentList.size());
                    } catch (Throwable e) {
                        // You can specify a string as an index and that is used to match the end of the
                        // content. This is used to index one specific image (e.g: @sample/images[biking.png])
                        // would return that image
                        return contentList
                                .stream()
                                .filter(line -> line.endsWith(indexValue))
                                .findFirst()
                                .orElse("");
                    }
                }

                resourcePath = resourcePath.substring(0, openBracket);
            }
        }

        return getSampleDataLineFromCursor(resourcePath, contentList);
    }

    @NonNull
    private static ImmutableList<String> getContentSubArray(
            @NonNull ImmutableList<String> contentList, @NonNull String value) {
        if (SUBARRAY_SEPARATOR.equals(value)) {
            // No indexes
            return contentList;
        }

        List<String> subArrayIndexes = Splitter.on(SUBARRAY_SEPARATOR).limit(2).splitToList(value);
        assert subArrayIndexes.size() == 2;

        String bottom = subArrayIndexes.get(0);
        String top = subArrayIndexes.get(1);
        try {
            int bottomIndex = bottom.isEmpty() ? 0 : Integer.parseUnsignedInt(bottom);
            int topIndex = top.isEmpty() ? contentList.size() - 1 : Integer.parseUnsignedInt(top);
            return contentList.subList(bottomIndex, topIndex + 1);
        } catch (Throwable ignored) {
            // Invalid index
        }
        return ImmutableList.of();
    }

    @NonNull
    public static String getResourceNameFromSampleReference(@NonNull String sampleReference) {
        int openBracket = sampleReference.indexOf('[');
        if (openBracket != -1) {
            return sampleReference.substring(0, openBracket);
        }

        return sampleReference;
    }
}
