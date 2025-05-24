/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.math.IntMath;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Read-only map that delegates read operations to two disjoint maps.
 *
 * <p>The set of keys of both maps needs to be disjoint. This class is meant to "join" two disjoint
 * maps in a single object, not to implement an overlay.
 */
public class DisjointUnionMap<K, V> implements Map<K, V> {
    private final Map<K, V> first;
    private final Map<K, V> second;
    private Set<K> keySet;
    private Set<Map.Entry<K, V>> entrySet;
    private Collection<V> values;

    public DisjointUnionMap(@NonNull Map<K, V> first, @NonNull Map<K, V> second) {
        Preconditions.checkArgument(
                Sets.intersection(first.keySet(), second.keySet()).isEmpty(),
                "Key sets are not disjoint.");
        this.first = first;
        this.second = second;
    }

    @Override
    public int size() {
        return first.size() + second.size();
    }

    @Override
    public boolean isEmpty() {
        return first.isEmpty() && second.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return first.containsKey(key) || second.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return first.containsValue(value) || second.containsValue(value);
    }

    @Override
    @Nullable
    public V get(Object key) {
        V result = first.get(key);
        if (result == null) {
            result = second.get(key);
        }

        return result;
    }

    @Override
    @NonNull
    public Set<K> keySet() {
        if (keySet == null) {
            keySet = new UnionOfDisjointSets<>(first.keySet(), second.keySet());
        }
        return keySet;
    }

    @Override
    @NonNull
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new UnionOfDisjointSets<>(first.entrySet(), second.entrySet());
        }
        return entrySet;
    }

    @Override
    @NonNull
    public Collection<V> values() {
        if (values == null) {
            values = new Concatenation<>(first.values(), second.values());
        }
        return values;
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NonNull Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    private static class UnionOfDisjointSets<E> extends Concatenation<E> implements Set<E> {
        public UnionOfDisjointSets(Set<E> first, Set<E> second) {
            super(first, second);
        }
    }

    private static class Concatenation<E> extends AbstractCollection<E> {
        private final Collection<E> first;
        private final Collection<E> second;

        public Concatenation(Collection<E> first, Collection<E> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public final int size() {
            return IntMath.saturatedAdd(first.size(), second.size());
        }

        @Override
        public final boolean isEmpty() {
            return first.isEmpty() && second.isEmpty();
        }

        @Override
        public final Iterator<E> iterator() {
            return Iterators.concat(first.iterator(), second.iterator());
        }

        @Override
        public final Stream<E> stream() {
            return Stream.concat(first.stream(), second.stream());
        }

        @Override
        public final Stream<E> parallelStream() {
            return Stream.concat(first.parallelStream(), second.parallelStream());
        }

        @Override
        public final boolean contains(Object object) {
            return first.contains(object) || second.contains(object);
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Concatenation<?> that = (Concatenation<?>) o;
            return first.equals(that.first) && second.equals(that.second);
        }

        @Override
        public final int hashCode() {
            return first.hashCode() + second.hashCode();
        }

        @Override
        public final boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean remove(Object object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean addAll(Collection<? extends E> newElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean removeAll(Collection<?> oldElements) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean removeIf(Predicate<? super E> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean retainAll(Collection<?> elementsToKeep) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final void clear() {
            throw new UnsupportedOperationException();
        }
    }
}
