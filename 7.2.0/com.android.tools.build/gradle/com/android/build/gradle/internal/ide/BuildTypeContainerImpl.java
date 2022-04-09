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
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.builder.core.VariantTypeImpl;
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
    @Nullable private final SourceProvider sourceProvider;
    @NonNull
    private final Collection<SourceProviderContainer> extraSourceProviders;

    /**
     * Create a BuildTypeContainer from a BuildTypeData
     *
     * @param buildTypeData the build type data
     * @param includeProdSourceSet whether to include that source set in the model
     * @param includeAndroidTest whether to include that source set in the model
     * @param includeUnitTest whether to include that source set in the model
     * @param sourceProviderContainers collection of extra source providers
     * @return a non-null BuildTypeContainer
     */
    @NonNull
    static BuildTypeContainer create(
            @NonNull BuildTypeData<com.android.build.gradle.internal.dsl.BuildType> buildTypeData,
            boolean includeProdSourceSet,
            boolean includeAndroidTest,
            boolean includeUnitTest,
            @NonNull Collection<SourceProviderContainer> sourceProviderContainers) {

        List<SourceProviderContainer> clonedContainers =
                SourceProviderContainerImpl.cloneCollection(sourceProviderContainers);

        if (includeAndroidTest) {
            DefaultAndroidSourceSet sourceSet =
                    buildTypeData.getTestSourceSet(VariantTypeImpl.ANDROID_TEST);
            if (sourceSet != null) {
                clonedContainers.add(
                        SourceProviderContainerImpl.create(
                                VariantTypeImpl.ANDROID_TEST.getArtifactName(), sourceSet));
            }
        }

        if (includeUnitTest) {
            DefaultAndroidSourceSet sourceSet =
                    buildTypeData.getTestSourceSet(VariantTypeImpl.UNIT_TEST);
            if (sourceSet != null) {
                clonedContainers.add(
                        SourceProviderContainerImpl.create(
                                VariantTypeImpl.UNIT_TEST.getArtifactName(), sourceSet));
            }
        }

        SourceProviderImpl prodSourceSet = null;
        if (includeProdSourceSet) {
            prodSourceSet = new SourceProviderImpl(buildTypeData.getSourceSet());
        }

        return new BuildTypeContainerImpl(
                new BuildTypeImpl(buildTypeData.getBuildType()), prodSourceSet, clonedContainers);
    }

    private BuildTypeContainerImpl(
            @NonNull BuildTypeImpl buildType,
            @Nullable SourceProviderImpl sourceProvider,
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
    @Nullable
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
