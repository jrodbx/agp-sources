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
package com.android.utils;

import com.android.annotations.NonNull;
import java.text.DecimalFormatSymbols;

/** Static methods for dealing with floating point numbers in string decimal form. */
public class DecimalUtils {
    /**
     * Removes trailing zeros after the decimal dot and also the dot itself if there are no non-zero
     * digits after it. Use {@link #trimInsignificantZeros(String, DecimalFormatSymbols)} instead of
     * this method if locale specific behavior is desired.
     *
     * @param floatingPointNumber the string representing a floating point number
     * @return the original number with trailing zeros removed
     */
    @NonNull
    public static String trimInsignificantZeros(@NonNull String floatingPointNumber) {
        return trimInsignificantZeros(floatingPointNumber, '.', "E");
    }

    /**
     * Removes trailing zeros after the decimal separator and also the decimal separator itself if
     * there are no non-zero digits after it.
     *
     * @param floatingPointNumber the string representing a floating point number
     * @param symbols the decimal format symbols
     * @return the original number with trailing zeros removed
     */
    public static String trimInsignificantZeros(
            @NonNull String floatingPointNumber, @NonNull DecimalFormatSymbols symbols) {
        return trimInsignificantZeros(
                floatingPointNumber, symbols.getDecimalSeparator(), symbols.getExponentSeparator());
    }

    /**
     * Removes trailing zeros after the decimal separator and also the decimal separator itself if
     * there are no non-zero digits after it.
     *
     * @param floatingPointNumber the string representing a floating point number
     * @param decimalSeparator the decimal separator
     * @param exponentialSeparator the string used to separate the mantissa from the exponent
     * @return the original number with trailing zeros removed
     */
    public static String trimInsignificantZeros(
            @NonNull String floatingPointNumber,
            char decimalSeparator,
            String exponentialSeparator) {
        int pos = floatingPointNumber.lastIndexOf(decimalSeparator);
        if (pos < 0) {
            return floatingPointNumber;
        }
        if (pos == 0) {
            pos = 2;
        }

        int exponent =
                CharSequences.indexOfIgnoreCase(floatingPointNumber, exponentialSeparator, pos);
        int i = exponent >= 0 ? exponent : floatingPointNumber.length();
        while (--i > pos) {
            if (floatingPointNumber.charAt(i) != '0') {
                i++;
                break;
            }
        }
        if (exponent < 0) {
            return floatingPointNumber.substring(0, i);
        } else if (exponent == i) {
            return floatingPointNumber;
        } else {
            return floatingPointNumber.substring(0, i) + floatingPointNumber.substring(exponent);
        }
    }

    /** Do not instantiate. All methods are static. */
    private DecimalUtils() {}
}
