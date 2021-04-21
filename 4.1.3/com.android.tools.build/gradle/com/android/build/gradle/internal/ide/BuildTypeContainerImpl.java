/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.builder.core.VariantType;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Immutable
final class BuildTypeContainerImpl implements BuildTypeContainer, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final BuildType buildType;
    @NonNull
    private final SourceProvider sourceProvider;
    @NonNull
    private final Collection<SourceProviderContainer> extraSourceProviders;

    /**
     * Create a BuildTypeContainer from a BuildTypeData
     *
     * @param buildTypeData the build type data
     * @param sourceProviderContainers collection of extra source providers
     * @return a non-null BuildTypeContainer
     */
    @NonNull
    static BuildTypeContainer create(
            @NonNull BuildTypeData<com.android.build.gradle.internal.dsl.BuildType> buildTypeData,
            @NonNull Collection<SourceProviderContainer> sourceProviderContainers) {

        List<SourceProviderContainer> clonedContainers =
                SourceProviderContainerImpl.cloneCollection(sourceProviderContainers);

        for (VariantType variantType : VariantType.Companion.getTestComponents()) {
            DefaultAndroidSourceSet testSourceSet = buildTypeData.getTestSourceSet(variantType);
            if (testSourceSet != null) {
                clonedContainers.add(SourceProviderContainerImpl.create(
                        variantType.getArtifactName(),
                        testSourceSet));
            }
        }
        return new BuildTypeContainerImpl(
                new BuildTypeImpl(buildTypeData.getBuildType()),
                new SourceProviderImpl(buildTypeData.getSourceSet()),
                clonedContainers);
    }

    private BuildTypeContainerImpl(
            @NonNull BuildTypeImpl buildType,
            @NonNull SourceProviderImpl sourceProvider,
            @NonNull Collection<SourceProviderContainer> extraSourceProviders) {
        this.buildType = buildType;
        this.sourceProvider = sourceProvider;
        this.extraSourceProviders = extraSourceProviders;
    }

    @Override
    @NonNull
    public BuildType getBuildType() {
        return buildType;
    }

    @Override
    @NonNull
    public SourceProvider getSourceProvider() {
        return sourceProvider;
    }

    @NonNull
    @Override
    public Collection<SourceProviderContainer> getExtraSourceProviders() {
        return extraSourceProviders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildTypeContainerImpl that = (BuildTypeContainerImpl) o;
        return Objects.equals(buildType, that.buildType) &&
                Objects.equals(sourceProvider, that.sourceProvider) &&
                Objects.equals(extraSourceProviders, that.extraSourceProviders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildType, sourceProvider, extraSourceProviders);
    }
}
