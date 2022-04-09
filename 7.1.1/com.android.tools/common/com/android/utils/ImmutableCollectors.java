/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Util for creating {@link Collector} that creates immutable collections, similar to
 * {@link Collectors}.
 */
public class ImmutableCollectors {
    public static <T> Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> toImmutableList() {
        return Collector.of(
                ImmutableList::builder,
                ImmutableList.Builder::add,
                (b1, b2) -> b1.addAll(b2.build()),
                ImmutableList.Builder::build);
    }

    public static <T> Collector<T, ImmutableSet.Builder<T>, ImmutableSet<T>> toImmutableSet() {
        return Collector.of(
                ImmutableSet::builder,
                ImmutableSet.Builder::add,
                (b1, b2) -> b1.addAll(b2.build()),
                ImmutableSet.Builder::build);
    }
}
