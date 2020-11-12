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
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProviderContainer;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/** Creates a deep copy of a {@link BuildTypeContainer}. */
public final class IdeBuildTypeContainer implements BuildTypeContainer, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final IdeBuildType myBuildType;
    @NonNull private final IdeSourceProvider mySourceProvider;
    @NonNull private final Collection<SourceProviderContainer> myExtraSourceProviders;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeBuildTypeContainer() {
        myBuildType = new IdeBuildType();
        mySourceProvider = new IdeSourceProvider();
        myExtraSourceProviders = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeBuildTypeContainer(
            @NonNull BuildTypeContainer container, @NonNull ModelCache modelCache) {
        myBuildType =
                modelCache.computeIfAbsent(
                        container.getBuildType(),
                        buildType -> new IdeBuildType(buildType, modelCache));
        mySourceProvider =
                modelCache.computeIfAbsent(
                        container.getSourceProvider(), provider -> new IdeSourceProvider(provider));
        myExtraSourceProviders =
                IdeModel.copy(
                        container.getExtraSourceProviders(),
                        modelCache,
                        sourceProviderContainer ->
                                new IdeSourceProviderContainer(
                                        sourceProviderContainer, modelCache));

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public IdeBuildType getBuildType() {
        return myBuildType;
    }

    @Override
    @NonNull
    public IdeSourceProvider getSourceProvider() {
        return mySourceProvider;
    }

    @Override
    @NonNull
    public Collection<SourceProviderContainer> getExtraSourceProviders() {
        return myExtraSourceProviders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeBuildTypeContainer)) {
            return false;
        }
        IdeBuildTypeContainer container = (IdeBuildTypeContainer) o;
        return Objects.equals(myBuildType, container.myBuildType)
                && Objects.equals(mySourceProvider, container.mySourceProvider)
                && Objects.equals(myExtraSourceProviders, container.myExtraSourceProviders);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myBuildType, mySourceProvider, myExtraSourceProviders);
    }

    @Override
    public String toString() {
        return "IdeBuildTypeContainer{"
                + "myBuildType="
                + myBuildType
                + ", mySourceProvider="
                + mySourceProvider
                + ", myExtraSourceProviders="
                + myExtraSourceProviders
                + '}';
    }
}
