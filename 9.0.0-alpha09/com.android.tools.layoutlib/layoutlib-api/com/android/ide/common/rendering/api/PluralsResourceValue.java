/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/** Represents an Android plurals resource. */
public interface PluralsResourceValue extends ResourceValue {
    /** Returns the number of plural strings. */
    int getPluralsCount();

    /**
     * Returns the quantity at the given index, such as "one", "two", "few", etc.
     *
     * @param index the index, which must be in the range 0..getPluralsCount()-1.
     * @return the corresponding quantity string
     */
    @NonNull
    String getQuantity(int index);

    /**
     * Returns the string element at the given index position.
     *
     * @param index index, which must be in the range 0..getPluralsCount()-1.
     * @return the corresponding element
     */
    @NonNull
    String getValue(int index);

    /**
     * Returns the string element for the given quantity
     *
     * @param quantity the quantity string, such as "one", "two", "few", etc.
     * @return the corresponding string value, or null if not defined
     */
    @Nullable
    String getValue(@NonNull String quantity);
}
