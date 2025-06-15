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
import com.android.build.gradle.internal.VariantDimensionData;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.dsl.BaseFlavor;
import com.android.builder.core.ComponentTypeImpl;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 */
@Immutable
final class ProductFlavorContainerImpl implements ProductFlavorContainer, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final ProductFlavor productFlavor;
    @Nullable private final SourceProvider sourceProvider;
    @NonNull
    private final Collection<SourceProviderContainer> extraSourceProviders;

    /**
     * Create a ProductFlavorContainer from a ProductFlavorData
     *
     * @param variantDimensionData the data containing the source set
     * @param productFlavor the product flavor object
     * @param includeProdSourceSet whether to include that source set in the model
     * @param includeAndroidTest whether to include that source set in the model
     * @param includeUnitTest whether to include that source set in the model
     * @param sourceProviderContainers collection of extra source providers
     * @return a non-null ProductFlavorContainer
     */
    @NonNull
    static ProductFlavorContainer createProductFlavorContainer(
            @NonNull VariantDimensionData variantDimensionData,
            @NonNull BaseFlavor productFlavor,
            boolean includeProdSourceSet,
            boolean includeAndroidTest,
            boolean includeUnitTest,
            @NonNull Collection<SourceProviderContainer> sourceProviderContainers) {

        List<SourceProviderContainer> clonedContainers =
                SourceProviderContainerImpl.cloneCollection(sourceProviderContainers);

        if (includeAndroidTest) {
            DefaultAndroidSourceSet sourceSet =
                    variantDimensionData.getSourceSet(ComponentTypeImpl.ANDROID_TEST);
            if (sourceSet != null) {
                clonedContainers.add(
                        SourceProviderContainerImpl.create(
                                ComponentTypeImpl.ANDROID_TEST.getArtifactName(), sourceSet));
            }
        }

        if (includeUnitTest) {
            DefaultAndroidSourceSet sourceSet =
                    variantDimensionData.getSourceSet(ComponentTypeImpl.UNIT_TEST);
            if (sourceSet != null) {
                clonedContainers.add(
                        SourceProviderContainerImpl.create(
                                ComponentTypeImpl.UNIT_TEST.getArtifactName(), sourceSet));
            }
        }

        SourceProviderImpl prodSourceSet = null;
        if (includeProdSourceSet) {
            prodSourceSet = new SourceProviderImpl(variantDimensionData.getSourceSet());
        }

        return new ProductFlavorContainerImpl(
                new ProductFlavorImpl(productFlavor, null), prodSourceSet, clonedContainers);
    }

    private ProductFlavorContainerImpl(
            @NonNull ProductFlavorImpl productFlavor,
            @Nullable SourceProviderImpl sourceProvider,
            @NonNull Collection<SourceProviderContainer> extraSourceProviders) {

        this.productFlavor = productFlavor;
        this.sourceProvider = sourceProvider;
        this.extraSourceProviders = extraSourceProviders;
    }

    @NonNull
    @Override
    public ProductFlavor getProductFlavor() {
        return productFlavor;
    }

    @Nullable
    @Override
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
        ProductFlavorContainerImpl that = (ProductFlavorContainerImpl) o;
        return Objects.equals(productFlavor, that.productFlavor) &&
                Objects.equals(sourceProvider, that.sourceProvider) &&
                Objects.equals(extraSourceProviders, that.extraSourceProviders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productFlavor, sourceProvider, extraSourceProviders);
    }
}
