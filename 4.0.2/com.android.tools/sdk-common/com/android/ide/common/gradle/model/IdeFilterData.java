/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.FilterData;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of a {@link FilterData}. */
public final class IdeFilterData implements FilterData, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myIdentifier;
    @NonNull private final String myFilterType;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @VisibleForTesting
    @SuppressWarnings("unused")
    public IdeFilterData() {
        myIdentifier = "";
        myFilterType = "";

        myHashCode = 0;
    }

    public IdeFilterData(@NonNull FilterData data) {
        myIdentifier = data.getIdentifier();
        myFilterType = data.getFilterType();

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getIdentifier() {
        return myIdentifier;
    }

    @Override
    @NonNull
    public String getFilterType() {
        return myFilterType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeFilterData)) {
            return false;
        }
        IdeFilterData data = (IdeFilterData) o;
        return Objects.equals(myIdentifier, data.myIdentifier)
                && Objects.equals(myFilterType, data.myFilterType);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myIdentifier, myFilterType);
    }

    @Override
    public String toString() {
        return "IdeFilterData{"
                + "myIdentifier='"
                + myIdentifier
                + '\''
                + ", myFilterType='"
                + myFilterType
                + '\''
                + "}";
    }
}
