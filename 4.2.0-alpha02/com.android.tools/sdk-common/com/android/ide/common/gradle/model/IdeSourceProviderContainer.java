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
import com.android.builder.model.SourceProviderContainer;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of a {@link SourceProviderContainer}. */
public final class IdeSourceProviderContainer implements SourceProviderContainer, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myArtifactName;
    @NonNull private final IdeSourceProvider mySourceProvider;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeSourceProviderContainer() {
        myArtifactName = "";
        mySourceProvider = new IdeSourceProvider();

        myHashCode = 0;
    }

    public IdeSourceProviderContainer(
            @NonNull SourceProviderContainer container, @NonNull ModelCache modelCache) {
        myArtifactName = container.getArtifactName();
        mySourceProvider =
                modelCache.computeIfAbsent(
                        container.getSourceProvider(), provider -> new IdeSourceProvider(provider));

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getArtifactName() {
        return myArtifactName;
    }

    @Override
    @NonNull
    public IdeSourceProvider getSourceProvider() {
        return mySourceProvider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeSourceProviderContainer)) {
            return false;
        }
        IdeSourceProviderContainer container = (IdeSourceProviderContainer) o;
        return Objects.equals(myArtifactName, container.myArtifactName)
                && Objects.equals(mySourceProvider, container.mySourceProvider);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myArtifactName, mySourceProvider);
    }

    @Override
    public String toString() {
        return "IdeSourceProviderContainer{"
                + "myArtifactName='"
                + myArtifactName
                + '\''
                + ", mySourceProvider="
                + mySourceProvider
                + '}';
    }
}
