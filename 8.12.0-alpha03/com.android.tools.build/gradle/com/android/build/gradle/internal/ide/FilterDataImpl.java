/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.build.FilterData;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.scope.GradleAwareFilterData;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Objects;

/** Implementation of {@link com.android.build.FilterData} interface */
@Immutable
public final class FilterDataImpl implements GradleAwareFilterData, Serializable {
    private static final long serialVersionUID = 1L;

    private final String filterType;
    private final String identifier;

    public FilterDataImpl(VariantOutput.FilterType filterType, String identifier) {
        this(filterType.name(), identifier);
    }

    public FilterDataImpl(String filterType, String identifier) {
        this.filterType = filterType;
        this.identifier = identifier;
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    @Override
    public String getFilterType() {
        return filterType;
    }

    public static GradleAwareFilterData build(final String filterType, final String identifier) {
        return new FilterDataImpl(filterType, identifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FilterDataImpl that = (FilterDataImpl) o;
        return Objects.equals(filterType, that.filterType) &&
                Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filterType, identifier);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FilterData.class)
                .add("type", filterType)
                .add("value", identifier)
                .toString();
    }
}
