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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class IdeModel {
    @Nullable
    public static <K, V> V copyNewProperty(
            @NonNull ModelCache modelCache,
            @NonNull Supplier<K> keyCreator,
            @NonNull Function<K, V> mapper,
            @Nullable V defaultValue) {
        try {
            K key = keyCreator.get();
            return key != null ? modelCache.computeIfAbsent(key, mapper) : defaultValue;
        } catch (UnsupportedOperationException ignored) {
            return defaultValue;
        }
    }

    @Nullable
    public static <T> T copyNewProperty(
            @NonNull Supplier<? extends T> propertyInvoker, @Nullable T defaultValue) {
        try {
            return propertyInvoker.get();
        } catch (UnsupportedOperationException ignored) {
            return defaultValue;
        }
    }

    @NonNull
    public static <T> T copyNewPropertyNonNull(
            @NonNull Supplier<? extends T> propertyInvoker, @NonNull T defaultValue) {
        try {
            return propertyInvoker.get();
        } catch (UnsupportedOperationException ignored) {
            return defaultValue;
        }
    }

    @Nullable
    public static <T> T copyNewPropertyWithDefault(
            @NonNull Supplier<T> propertyInvoker, @NonNull Supplier<T> defaultValue) {
        try {
            return propertyInvoker.get();
        } catch (UnsupportedOperationException ignored) {
            return defaultValue.get();
        }
    }

    @NonNull
    public static <K, V> List<V> copy(
            @NonNull Collection<K> original,
            @NonNull ModelCache modelCache,
            @NonNull Function<K, V> mapper) {
        if (original.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<V> copies = ImmutableList.builder();
        for (K item : original) {
            V copy = modelCache.computeIfAbsent(item, mapper);
            copies.add(copy);
        }
        return copies.build();
    }

    @NonNull
    public static <K, V> List<V> copy(
            @NonNull Supplier<Collection<K>> propertyInvoker,
            @NonNull ModelCache modelCache,
            @NonNull Function<K, V> mapper) {

        try {
            return copy(propertyInvoker.get(), modelCache, mapper);
        } catch (UnsupportedOperationException ignored) {
            return Collections.emptyList();
        }
    }

    @NonNull
    public static <K, V> Map<K, V> copy(
            @NonNull Map<K, V> original,
            @NonNull ModelCache modelCache,
            @NonNull Function<V, V> mapper) {
        if (original.isEmpty()) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<K, V> copies = ImmutableMap.builder();
        original.forEach(
                (k, v) -> {
                    V copy = modelCache.computeIfAbsent(v, mapper);
                    copies.put(k, copy);
                });
        return copies.build();
    }

    @Nullable
    public static Set<String> copy(@Nullable Set<String> original) {
        return original != null ? ImmutableSet.copyOf(original) : null;
    }
}
