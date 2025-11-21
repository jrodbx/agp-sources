/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.resources;

/**
 * Grammatical Gender enum.
 *
 * <p>This is used in the resource folder names.
 */
public enum GrammaticalGender implements ResourceEnum {
    NEUTER("neuter", "Neuter", "Neuter"),
    FEMININE("feminine", "Feminine", "Feminine"),
    MASCULINE("masculine", "Masculine", "Masculine");

    private final String mValue;
    private final String mShortDisplayValue;
    private final String mLongDisplayValue;

    GrammaticalGender(String value, String shortDisplayValue, String longDisplayValue) {
        mValue = value;
        mShortDisplayValue = shortDisplayValue;
        mLongDisplayValue = longDisplayValue;
    }

    /**
     * Returns the enum for matching the provided qualifier value.
     *
     * @param value The qualifier value.
     * @return the enum for the qualifier value or null if no matching was found.
     */
    public static GrammaticalGender getEnum(String value) {
        for (GrammaticalGender state : values()) {
            if (state.mValue.equals(value)) {
                return state;
            }
        }

        return null;
    }

    @Override
    public String getResourceValue() {
        return mValue;
    }

    @Override
    public String getShortDisplayValue() {
        return mShortDisplayValue;
    }

    @Override
    public String getLongDisplayValue() {
        return mLongDisplayValue;
    }

    public static int getIndex(GrammaticalGender value) {
        return value == null ? -1 : value.ordinal();
    }

    public static GrammaticalGender getByIndex(int index) {
        GrammaticalGender[] values = values();
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        return null;
    }

    @Override
    public boolean isFakeValue() {
        return false;
    }

    @Override
    public boolean isValidValueForDevice() {
        return true;
    }
}
