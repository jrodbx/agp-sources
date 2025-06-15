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

package com.android.build.gradle.internal.ide.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.level2.GraphItem;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 */
public final class GraphItemImpl implements GraphItem, Serializable {

    @NonNull
    private final String address;
    @NonNull
    private final List<GraphItem> dependencies;
    private final int hashcode;

    public GraphItemImpl(
            @NonNull String address,
            @NonNull List<GraphItem> dependencies) {
        this.address = address;
        this.dependencies = dependencies;
        this.hashcode = computeHashCode();
    }

    @NonNull
    @Override
    public String getArtifactAddress() {
        return address;
    }

    @Nullable
    @Override
    public String getRequestedCoordinates() {
        return null;
    }

    @NonNull
    @Override
    public List<GraphItem> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GraphItemImpl graphItem = (GraphItemImpl) o;
        // quick fail on different hashcode, to avoid manually comparing the children nodes
        return hashcode == graphItem.hashcode
                && Objects.equals(address, graphItem.address)
                && Objects.equals(dependencies, graphItem.dependencies);

    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    private int computeHashCode() {
        return Objects.hash(address, dependencies);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("address", address)
                .add("dependenciesSize", dependencies.size())
                .toString();
    }
}
