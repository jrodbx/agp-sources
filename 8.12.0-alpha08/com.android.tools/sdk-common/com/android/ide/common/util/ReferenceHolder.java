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

package com.android.ide.common.util;

/**
 * A simple holder for a reference.
 *
 * This is mutable (unlike Optional), and more light weight than Atomic*, however this is not
 * thread-safe at all.
 */
public class ReferenceHolder<T> {

    private T value;

    public static <T> ReferenceHolder<T> of(T value) {
        return new ReferenceHolder<T>(value);
    }

    public static <T> ReferenceHolder<T> empty() {
        return new ReferenceHolder<T>(null);
    }

    private ReferenceHolder(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public T setValue(T value) {
        this.value = value;
        return value;
    }
}
