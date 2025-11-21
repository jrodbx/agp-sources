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

package com.android.repository.impl.meta;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * An {@link XmlAdapter} that removes leading and trailing whitespace, and converts internal strings
 * of whitespace to a single space. Newlines are also removed, except when it looks like they've
 * been added to improve readability (specifically, when there's more than one newline in a row. In
 * other words, blank lines are preserved).
 *
 * Also interns the cached strings in our own pool.
 */
public class TrimStringAdapter extends XmlAdapter<String, String> {

    private static final Map<String, String> POOL = new HashMap<>();

    @Override
    public String unmarshal(String v) {
        if (v == null) {
            return null;
        }
        String result = v
                .replaceAll("(?<=\\s)[ \t]*", "")      // remove spaces and tabs preceded by
                                                       // space, tab, or newline.
                .replaceAll("(?<!\n)\n(?!\n)", " ")    // replace lone newlines with space
                .replaceAll(" +", " ")                 // remove duplicate spaces possibly caused
                                                       // by previous step
                .trim();                               // remove leading or trailing spaces
        String cached = POOL.get(result);
        if (cached != null) {
            return cached;
        }
        POOL.put(result, result);
        return result;
    }

    @Override
    public String marshal(String s) {
        return s;
    }
}