/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ide.common.fonts;

// DO NOT CONVERT TO kotlin.
// Changes to this file should be coordinated with the gmscore fonts team (fonts-team@google.com).

import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_EXACT;
import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WEIGHT;
import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WIDTH;
import static com.android.ide.common.fonts.FontDetailKt.NORMAL;

import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class that resolves font provider queries to FontMatchSpec objects, using the format
 * defined for the initial release (v11) of the fonts module. Support this format in perpetuity.
 */
public class QueryResolverLegacy {

    private static final String NAME_PARAMETER = "name";
    private static final String WIDTH_PARAMETER = "width";
    private static final String WEIGHT_PARAMETER = "weight";
    private static final String ITALIC_PARAMETER = "italic";
    private static final String BEST_EFFORT_PARAMETER = "besteffort";

    public static List<MutableFontDetail> getSpecsForQuery(String query) {
        List<String> queries = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(query);
        List<MutableFontDetail> specs = new ArrayList<>();
        for (String q : queries) {
            specs.add(getSpecForQuery(q));
        }
        return specs;
    }

    private static MutableFontDetail getSpecForQuery(String query) {
        if (!query.contains("=")) {
            return new MutableFontDetail(query, FontType.SINGLE, NORMAL, DEFAULT_EXACT);
        }

        Map<String, String> parameters =
                Splitter.on('&').withKeyValueSeparator(Splitter.on('=').trimResults()).split(query);

        String name = parameters.get(NAME_PARAMETER);
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("'name' parameter is required.");
        }

        String widthString = parameters.get(WIDTH_PARAMETER);
        String weightString = parameters.get(WEIGHT_PARAMETER);
        String italicString = parameters.get(ITALIC_PARAMETER);
        String bestEffortString = parameters.get(BEST_EFFORT_PARAMETER);

        try {
            float width = widthString == null ? DEFAULT_WIDTH : Float.parseFloat(widthString);
            int weight = weightString == null ? DEFAULT_WEIGHT : Integer.parseInt(weightString);
            float italic = italicString == null ? NORMAL : Float.parseFloat(italicString);
            boolean bestEffort =
                    bestEffortString == null
                            ? false
                            : Boolean.parseBoolean(bestEffortString);
            return new MutableFontDetail(name, weight, width, italic, !bestEffort);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid numerical parameter", nfe);
        }
    }
}
