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
import com.android.annotations.Nullable;
import com.android.builder.model.VectorDrawablesOptions;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Creates a deep copy of a {@link VectorDrawablesOptions}. */
public final class IdeVectorDrawablesOptions implements VectorDrawablesOptions, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @Nullable private final Set<String> myGeneratedDensities;
    @Nullable private final Boolean myUseSupportLibrary;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeVectorDrawablesOptions() {
        myGeneratedDensities = null;
        myUseSupportLibrary = null;

        myHashCode = 0;
    }

    public IdeVectorDrawablesOptions(@NonNull VectorDrawablesOptions options) {
        myGeneratedDensities = IdeModel.copy(options.getGeneratedDensities());
        myUseSupportLibrary = options.getUseSupportLibrary();

        myHashCode = calculateHashCode();
    }

    @Override
    @Nullable
    public Set<String> getGeneratedDensities() {
        return myGeneratedDensities;
    }

    @Override
    @Nullable
    public Boolean getUseSupportLibrary() {
        return myUseSupportLibrary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeVectorDrawablesOptions)) {
            return false;
        }
        IdeVectorDrawablesOptions options = (IdeVectorDrawablesOptions) o;
        return Objects.equals(myGeneratedDensities, options.myGeneratedDensities)
                && Objects.equals(myUseSupportLibrary, options.myUseSupportLibrary);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myGeneratedDensities, myUseSupportLibrary);
    }

    @Override
    public String toString() {
        return "IdeVectorDrawablesOptions{"
                + "myGeneratedDensities="
                + myGeneratedDensities
                + ", myUseSupportLibrary="
                + myUseSupportLibrary
                + "}";
    }
}
