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
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;

public class FullDependencyGraphsImpl implements DependencyGraphs, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final List<GraphItem> compileItems;
    @NonNull
    private final List<GraphItem> packageItems;
    @NonNull
    private final List<String> providedLibraries;
    @NonNull
    private final List<String> skippedLibraries;
    private final int hashCode;

    public FullDependencyGraphsImpl(
            @NonNull List<GraphItem> compileItems,
            @NonNull List<GraphItem> packageItems,
            @NonNull List<String> providedLibraries,
            @NonNull List<String> skippedLibraries) {
        this.compileItems = compileItems;
        this.packageItems = packageItems;
        this.providedLibraries = ImmutableList.copyOf(providedLibraries);
        this.skippedLibraries = ImmutableList.copyOf(skippedLibraries);
        this.hashCode = computeHashCode();
    }

    @NonNull
    @Override
    public List<GraphItem> getCompileDependencies() {
        return compileItems;
    }

    @NonNull
    @Override
    public List<GraphItem> getPackageDependencies() {
        return packageItems;
    }

    @NonNull
    @Override
    public List<String> getProvidedLibraries() {
        return providedLibraries;
    }

    @NonNull
    @Override
    public List<String> getSkippedLibraries() {
        return skippedLibraries;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FullDependencyGraphsImpl that = (FullDependencyGraphsImpl) o;

        if (!compileItems.equals(that.compileItems)) {
            return false;
        }
        if (!packageItems.equals(that.packageItems)) {
            return false;
        }
        if (!providedLibraries.equals(that.providedLibraries)) {
            return false;
        }
        return skippedLibraries.equals(that.skippedLibraries);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int result = compileItems.hashCode();
        result = 31 * result + packageItems.hashCode();
        result = 31 * result + providedLibraries.hashCode();
        result = 31 * result + skippedLibraries.hashCode();
        return result;
    }
}
