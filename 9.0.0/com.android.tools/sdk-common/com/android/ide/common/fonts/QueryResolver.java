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
import static com.android.ide.common.fonts.FontDetailKt.ITALICS;
import static com.android.ide.common.fonts.FontDetailKt.NORMAL;

import com.android.utils.Pair;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class that resolves font provider queries to FontMatchSpec objects.
 */
public class QueryResolver {
    private static final int MAX_QUERY_LENGTH = 512;
    private static final String[] ITALIC_OPTIONS = {"", "i", "italic"};
    private static final ImmutableMap<String, Integer> NAMED_WEIGHTS =
            new ImmutableMap.Builder<String, Integer>()
                    .put("100", 100)
                    .put("200", 200)
                    .put("300", 300)
                    .put("400", 400)
                    .put("500", 500)
                    .put("600", 600)
                    .put("700", 700)
                    .put("800", 800)
                    .put("900", 900)
                    .put("thin", 100)
                    .put("extralight", 200)
                    .put("extra-light", 200)
                    .put("ultralight", 200)
                    .put("ultra-light", 200)
                    .put("light", 300)
                    .put("regular", 400)
                    .put("book", 400)
                    .put("medium", 500)
                    .put("semi-bold", 600)
                    .put("semibold", 600)
                    .put("demi-bold", 600)
                    .put("demibold", 600)
                    .put("bold", 700)
                    .put("extra-bold", 800)
                    .put("extrabold", 800)
                    .put("ultra-bold", 800)
                    .put("ultrabold", 800)
                    .put("black", 900)
                    .put("heavy", 900)
                    .put("l", 300)
                    .put("r", 400)
                    .put("b", 700)
                    .put("", 400)
                    .buildOrThrow();
    private static final Map<String, Pair<Integer, Float>> KNOWN_VARIANTS = getKnownVariants();

    private static Map<String, Pair<Integer, Float>> getKnownVariants() {
        Map<String, Pair<Integer, Float>> knownVariants = new HashMap<>();
        for (Map.Entry<String, Integer> entry : NAMED_WEIGHTS.entrySet()) {
            for (String italicOption : ITALIC_OPTIONS) {
                knownVariants.put(
                        entry.getKey() + italicOption,
                        Pair.of(entry.getValue(), italicOption.isEmpty() ? 0f : 1f));
            }
        }
        return knownVariants;
    }

    public static DownloadableParseResult parseDownloadableFont(String authority, String query) {
        try {
            List<MutableFontDetail> specs = getSpecsForQuery(query);
            Multimap<String, MutableFontDetail> fonts =
                    LinkedHashMultimap.create(); // Keep order for tests
            specs.forEach(
                    (spec) -> {
                        fonts.put(spec.getName(), spec);
                    });
            return new DownloadableParseResult(authority, fonts);
        } catch (Exception ex) {
            throw new FontQueryParserError(ex.getMessage(), ex);
        }
    }

    private static List<MutableFontDetail> getSpecsForQuery(String query) {
        // Case 1: null, empty, or excessively long queries not permitted.
        if (query == null || query.isEmpty() || query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query cannot be null, empty, or over " + MAX_QUERY_LENGTH + " characters");
        }

        // Case 2: query is in legacy KV pair format, or legacy <Name, Name, Name> format.
        if (query.contains("=") || (query.contains(",") && !query.contains(":"))) {
            return QueryResolverLegacy.getSpecsForQuery(query);
        }

        // Case 3: colon format.
        List<String> queries = Splitter.on('|').omitEmptyStrings().trimResults().splitToList(query);
        List<MutableFontDetail> finalSpecs = new ArrayList<>();
        for (String q : queries) {
            finalSpecs.addAll(getSpecsForIndividualQuery(q));
        }
        return finalSpecs;
    }

    static float extractFloat(String component, int position) {
        try {
            return Float.parseFloat(component.substring(position));
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("query contains invalid value (" + component + ")");
        }
    }

    static int extractInt(String component, int position) {
        try {
            return Integer.parseInt(component.substring(position));
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("query contains invalid value (" + component + ")");
        }
    }

    // Individual query = query without pipes.
    private static List<MutableFontDetail> getSpecsForIndividualQuery(String query) {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("query cannot be null or empty");
        }
        List<MutableFontDetail> finalSpecs = new ArrayList<>();

        // If the query does not contain a colon, then it's just the default style of that font.
        if (!query.contains(":")) {
            finalSpecs.add(new MutableFontDetail(query.replace('+', ' '), FontType.SINGLE, NORMAL, DEFAULT_EXACT));
            return finalSpecs;
        }
        // Get family name from beginning of query, and then remove it and its trailing colon.
        int firstColonPosition = query.indexOf(':');
        String familyName = query.substring(0, firstColonPosition).replace('+', ' ');
        query = query.substring(firstColonPosition + 1);

        // Generate the final list of specs. First separate font requests by comma, and then
        // separate individual styles per request by colon.
        List<String> queries = Splitter.on(',').omitEmptyStrings().trimResults().splitToList(query);

        // Handle vf queries. If the query is "vf", then it's a vf request for regular style. If the
        // query is "vf:italic", then it's a vf request for italic.
        if (queries.size() == 1) {
            if (queries.get(0).toLowerCase(Locale.ENGLISH).equals("vf")) {
                finalSpecs.add(new MutableFontDetail(familyName, FontType.VARIABLE, NORMAL, DEFAULT_EXACT));
                return finalSpecs;
            } else if (queries.get(0).toLowerCase(Locale.ENGLISH).equals("vf:italic")) {
                finalSpecs.add(new MutableFontDetail(familyName, FontType.VARIABLE, ITALICS, DEFAULT_EXACT));
                return finalSpecs;
            } else if (queries.get(0).toLowerCase(Locale.ENGLISH).startsWith("vf")) {
                throw new IllegalArgumentException("invalid vf query (" + queries.get(0) + ")");
            }
        }

        for (String q : queries) {
            List<String> components =
                    Splitter.on(':').omitEmptyStrings().trimResults().splitToList(q);
            float italicValue = 0f;
            float widthValue = 100f;
            int weightValue = 400;
            boolean nearestValue = false;

            for (String component : components) {
                component = component.toLowerCase(Locale.ENGLISH);
                Pair<Integer, Float> attributes = KNOWN_VARIANTS.get(component);
                if (attributes != null) {
                    weightValue = attributes.getFirst();
                    italicValue = attributes.getSecond();
                } else if (component.equals("nearest")) {
                    nearestValue = true;
                } else if (component.startsWith("wght")) {
                    int possibleWeight = extractInt(component, 4);
                    if (possibleWeight >= 1 && possibleWeight <= 1000) {
                        weightValue = possibleWeight;
                    } else {
                        throw new IllegalArgumentException(
                                "query contains invalid weight (" + component + ")");
                    }
                } else if (component.startsWith("ital")) {
                    float possibleItalic = extractFloat(component, 4);
                    if (possibleItalic >= 0f && possibleItalic <= 1f) {
                        italicValue = possibleItalic;
                    } else {
                        throw new IllegalArgumentException(
                                "query contains invalid italic value (" + component + ")");
                    }
                } else if (component.startsWith("wdth")) {
                    float possibleWidth = extractFloat(component, 4);
                    if (possibleWidth > 0) {
                        widthValue = possibleWidth;
                    } else {
                        throw new IllegalArgumentException(
                                "query contains invalid width (" + component + ")");
                    }
                } else {
                    int possibleWeight = extractInt(component, 0);
                    if (possibleWeight >= 1 && possibleWeight <= 1000) {
                        weightValue = possibleWeight;
                    } else {
                        throw new IllegalArgumentException(
                                "query contains invalid weight (" + component + ")");
                    }
                }
            }
            finalSpecs.add(
                    new MutableFontDetail(
                            familyName, weightValue, widthValue, italicValue, !nearestValue));
        }
        return finalSpecs;
    }
}
