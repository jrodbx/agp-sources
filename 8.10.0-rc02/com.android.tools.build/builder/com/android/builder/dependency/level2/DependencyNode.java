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

package com.android.builder.dependency.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.dependency.HashCodeUtils;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;

/**
 * A node in the dependency graph.
 *
 * It contains an unique key/coordinates that allows finding the actual dependency information.
 *
 * It does contain some immutable state related to this particular usage: requested coordinates
 * as well as the transitive dependencies.
 */
@Immutable
public final class DependencyNode {

    public enum NodeType {
        ANDROID,
        JAVA
    }

    @NonNull
    private final Object address;
    @NonNull
    private final NodeType nodeType;
    @NonNull
    private final ImmutableList<DependencyNode> dependencies;
    @Nullable
    private final MavenCoordinates requestedCoordinates;

    private final int hashCode;

    public DependencyNode(
            @NonNull Object address,
            @NonNull NodeType nodeType,
            @NonNull List<DependencyNode> dependencies,
            @Nullable MavenCoordinates requestedCoordinates) {
        this.address = address;
        this.nodeType = nodeType;
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.requestedCoordinates = requestedCoordinates;
        this.hashCode = computeHashCode();
    }

    /**
     * Returns a unique address that matches {@link Dependency#getAddress()}.
     */
    @NonNull
    public Object getAddress() {
        return address;
    }

    @NonNull
    public NodeType getNodeType() {
        return nodeType;
    }

    /**
     * Returns this library's Maven coordinates, as requested in the build file.
     */
    @Nullable
    public MavenCoordinates getRequestedCoordinates() {
        return requestedCoordinates;
    }

    /**
     * Return the direct dependency of this node.
     */
    @NonNull
    public ImmutableList<DependencyNode> getDependencies() {
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

        DependencyNode that = (DependencyNode) o;
        // fast failure on different hashcode since it's precomputed.
        // This avoid having to compare dependency graph if only a deep child is different.
        return hashCode == that.hashCode &&
                Objects.equals(address, that.address) &&
                Objects.equals(nodeType, that.nodeType) &&
                Objects.equals(dependencies, that.dependencies) &&
                Objects.equals(requestedCoordinates, that.requestedCoordinates);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        return HashCodeUtils.hashCode(
                address, nodeType, dependencies, requestedCoordinates);
    }
}
