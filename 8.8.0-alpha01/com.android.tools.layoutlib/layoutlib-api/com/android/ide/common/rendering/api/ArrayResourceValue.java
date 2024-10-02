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

/**
 * Represents an Android array resource with a name and a list of children {@link ResourceValue}
 * items, one for array element.
 */
public interface ArrayResourceValue extends ResourceValue, Iterable<String> {
    /**
     * Returns the number of elements in this array.
     *
     * @return the element count
     */
    int getElementCount();

    /**
     * Returns the array element value at the given index position.
     *
     * @param index index, which must be in the range [0..getElementCount()].
     * @return the corresponding element
     */
    @NonNull
    String getElement(int index);
}
