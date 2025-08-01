/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.function.Function;

/**
 * A class implements the {@link IUserDataMap} interface if it allows arbitrary component to
 * associate arbitrary values to instances of the class.
 *
 * <p>Note: The implementations are guaranteed to be thread-safe
 *
 * @see java.util.concurrent.ConcurrentHashMap
 */
public interface IUserDataMap {

    /**
     * Returns the value associated to the key, or the result of the mapping function after adding
     * it to this map.
     *
     * <p>If the specified key is not already associated with a value, attempts to compute its value
     * using the given mapping function and enters it into this map. The entire method invocation is
     * performed atomically. The supplied function is invoked exactly once per invocation of this
     * method if the key is absent, else not at all. Some attempted update operations on this map by
     * other threads may be blocked while computation is in progress, so the computation should be
     * short and simple.
     *
     * <p>Note: Unlike {@link java.util.concurrent.ConcurrentHashMap#computeIfAbsent(Object,
     * Function)}, it is illegal for the mappingFunction to return a `null` value, so that the
     * return value of this function is never `null`.
     *
     * @see java.util.concurrent.ConcurrentHashMap#computeIfAbsent(Object, Function)
     * @throws UnsupportedOperationException if the object does support not this operation
     * @throws IllegalArgumentException if the mapping function returns `null`
     */
    @NonNull
    <T> T computeUserDataIfAbsent(
            @NonNull Key<T> key, @NonNull Function<Key<T>, T> mappingFunction);

    @Nullable
    <T> T getUserDataOrNull(@NonNull Key<T> key);

    /**
     * Removes the key (and its corresponding value) from this map. This method does nothing if the
     * key is not in the map.
     *
     * @param key the key that needs to be removed
     * @return the previous value associated with {@code key}, or {@code null} if there was no
     *     mapping for {@code key}
     * @see java.util.concurrent.ConcurrentHashMap#remove(Object)
     * @throws UnsupportedOperationException if the object does not this operation
     */
    @Nullable
    <T> T removeUserData(@NonNull Key<T> key);

    class Key<T> {}
}
