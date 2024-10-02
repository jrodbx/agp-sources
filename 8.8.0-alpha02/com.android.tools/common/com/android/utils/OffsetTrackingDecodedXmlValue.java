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
package com.android.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import static com.android.utils.CharSequences.regionMatches;

/**
 * Decodes an XML string value keeping track of the original character offsets.
 */
public class OffsetTrackingDecodedXmlValue {
    /** Characters that are encoded in XML attribute values. */
    private static final char[] DECODED = {'<', '>', '&', '\'', '"'};
    /** XML-encoded representation of the characters in {@link #DECODED}. */
    private static final String[] ENCODED = {"&lt;", "&gt;", "&amp;", "&apos;", "&quot;"};

    private final CharSequence myDecodedCharacters;
    /**
     * The element at position N represents offset in the decoded string corresponding to the offset
     * <b>after</b> the character at position N in the XML-encoded string. Some elements at the end
     * of the array may remain unused. Null if the encoded and decoded character sequences are
     * equal.
     */
    @Nullable
    private final int[] myOffsetMap;

    /** Creates a decoded value for the given XML-encoded character sequence. */
    public OffsetTrackingDecodedXmlValue(@NonNull CharSequence encodedValue) {
        StringBuilder decodedValue = null;
        int[] offsetMap = null;
        int escapedLength = encodedValue.length();
        outer: for (int i = 0; i < escapedLength;) {
            for (int j = 0; j < ENCODED.length; j += 1) {
                String toReplace = ENCODED[j];
                if (i + toReplace.length() <= escapedLength
                        && regionMatches(encodedValue, i, toReplace, 0, toReplace.length())) {
                    if (decodedValue == null) {
                        decodedValue = new StringBuilder(escapedLength);
                        offsetMap = new int[escapedLength];
                        for (int k = 0; k < i; k++) {
                            offsetMap[k] = k;
                            decodedValue.append(encodedValue.charAt(k));
                        }
                    }
                    decodedValue.append(DECODED[j]);
                    i += toReplace.length();
                    offsetMap[decodedValue.length() - 1] = i;
                    continue outer;
                }
            }
            if (decodedValue != null) {
                decodedValue.append(encodedValue.charAt(i));
                offsetMap[decodedValue.length() - 1] = i;
            }
            i++;
        }
        myOffsetMap = offsetMap;
        myDecodedCharacters = decodedValue == null ? encodedValue : decodedValue.toString();
    }

    /**
     * Returns the unescaped value.
     */
    public CharSequence getDecodedCharacters() {
        return myDecodedCharacters;
    }

    /**
     * Returns the offset in the original XML-encoded value given an offset in the decoded value.
     * Offset mapping function is extrapolated linearly beyond the bounds of the decoded value
     * allowing the argument of the method to be outside of the decoded value bounds.
     *
     * @param decodedOffset the offset in the decoded value
     * @return the offset in the original encoded value
     */
    public int getEncodedOffset(int decodedOffset) {
        if (myOffsetMap == null || decodedOffset <= 0) {
            return decodedOffset;
        }
        if (decodedOffset <= myDecodedCharacters.length()) {
            return myOffsetMap[decodedOffset - 1];
        }
        return myOffsetMap[myDecodedCharacters.length() - 1]
                + decodedOffset - myDecodedCharacters.length();
    }
}
