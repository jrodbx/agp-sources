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
package com.android.ddmlib.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IUserDataMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class UserDataMapImpl implements IUserDataMap {
    @NonNull
    private final ConcurrentHashMap<Object, Object> mUserDataBag = new ConcurrentHashMap<>();

    @Override
    public <T> @NonNull T computeUserDataIfAbsent(
            @NonNull Key<T> key, @NonNull Function<Key<T>, T> mappingFunction) {
        // Unchecked cast: `mappingFunction` is a `Function` when types are erased
        //noinspection unchecked,rawtypes
        Object value = mUserDataBag.computeIfAbsent(key, (Function) mappingFunction);
        if (value == null) {
            throw new IllegalArgumentException("Value should never be null");
        }
        // Unchecked cast: `mappingFunction` returns a `T`
        //noinspection unchecked
        return (T) value;
    }

    @Override
    public <T> @Nullable T getUserDataOrNull(@NonNull Key<T> key) {
        Object value = mUserDataBag.get(key);
        // Unchecked cast: `mappingFunction` returns a `T`
        //noinspection unchecked
        return (T) value;
    }

    @Override
    public <T> @Nullable T removeUserData(@NonNull Key<T> key) {
        //noinspection unchecked
        return (T) mUserDataBag.remove(key);
    }
}
