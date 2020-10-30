/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.LINT;

import com.android.annotations.NonNull;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.core.VariantBuilder;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.google.common.collect.Maps;
import java.util.Map;

/**
 * Implementation of {@link VariantInputModel}.
 *
 * <p>This gets filled by the DSL/API execution.
 */
public class VariantInputModelImpl implements VariantInputModel {

    @NonNull private final BaseExtension extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull private final SourceSetManager sourceSetManager;
    @NonNull private final ProductFlavorData<DefaultConfig> defaultConfigData;
    @NonNull private final Map<String, BuildTypeData> buildTypes;
    @NonNull private final Map<String, ProductFlavorData<ProductFlavor>> productFlavors;
    @NonNull private final Map<String, SigningConfig> signingConfigs;
    @NonNull protected final GlobalScope globalScope;

    public VariantInputModelImpl(
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull SourceSetManager sourceSetManager) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.variantFactory = variantFactory;
        this.sourceSetManager = sourceSetManager;
        this.buildTypes = Maps.newHashMap();
        this.productFlavors = Maps.newHashMap();
        this.signingConfigs = Maps.newHashMap();

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet)
                        extension.getSourceSets().getByName(BuilderConstants.MAIN);

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                    (DefaultAndroidSourceSet)
                            extension.getSourceSets().getByName(VariantType.ANDROID_TEST_PREFIX);
            unitTestSourceSet =
                    (DefaultAndroidSourceSet)
                            extension.getSourceSets().getByName(VariantType.UNIT_TEST_PREFIX);
        }

        this.defaultConfigData =
                new ProductFlavorData<>(
                        extension.getDefaultConfig(),
                        mainSourceSet,
                        androidTestSourceSet,
                        unitTestSourceSet);
    }

    @NonNull
    @Override
    public ProductFlavorData<DefaultConfig> getDefaultConfig() {
        return defaultConfigData;
    }

    @Override
    @NonNull
    public Map<String, BuildTypeData> getBuildTypes() {
        return buildTypes;
    }

    @Override
    @NonNull
    public Map<String, ProductFlavorData<ProductFlavor>> getProductFlavors() {
        return productFlavors;
    }

    @Override
    @NonNull
    public Map<String, SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void addSigningConfig(@NonNull SigningConfig signingConfig) {
        signingConfigs.put(signingConfig.getName(), signingConfig);
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set, and adding it to
     * the map.
     *
     * @param buildType the build type.
     */
    public void addBuildType(@NonNull BuildType buildType) {
        String name = buildType.getName();
        checkName(name, "BuildType");

        if (productFlavors.containsKey(name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names");
        }

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) sourceSetManager.setUpSourceSet(name);

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            if (buildType.getName().equals(extension.getTestBuildType())) {
                androidTestSourceSet =
                        (DefaultAndroidSourceSet)
                                sourceSetManager.setUpTestSourceSet(
                                        VariantBuilder.computeSourceSetName(
                                                buildType.getName(), VariantTypeImpl.ANDROID_TEST));
            }

            unitTestSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpTestSourceSet(
                                    VariantBuilder.computeSourceSetName(
                                            buildType.getName(), VariantTypeImpl.UNIT_TEST));
        }

        BuildTypeData buildTypeData =
                new BuildTypeData(
                        buildType, mainSourceSet, androidTestSourceSet, unitTestSourceSet);

        buildTypes.put(name, buildTypeData);
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets, and adding
     * it to the map.
     *
     * @param productFlavor the product flavor
     */
    public void addProductFlavor(@NonNull ProductFlavor productFlavor) {
        String name = productFlavor.getName();
        checkName(name, "ProductFlavor");

        if (buildTypes.containsKey(name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names");
        }

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) sourceSetManager.setUpSourceSet(productFlavor.getName());

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpTestSourceSet(
                                    VariantBuilder.computeSourceSetName(
                                            productFlavor.getName(), VariantTypeImpl.ANDROID_TEST));
            unitTestSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpTestSourceSet(
                                    VariantBuilder.computeSourceSetName(
                                            productFlavor.getName(), VariantTypeImpl.UNIT_TEST));
        }

        ProductFlavorData<ProductFlavor> productFlavorData =
                new ProductFlavorData<>(
                        productFlavor, mainSourceSet, androidTestSourceSet, unitTestSourceSet);

        productFlavors.put(productFlavor.getName(), productFlavorData);
    }

    private static void checkName(@NonNull String name, @NonNull String displayName) {
        checkPrefix(name, displayName, VariantType.ANDROID_TEST_PREFIX);
        checkPrefix(name, displayName, VariantType.UNIT_TEST_PREFIX);

        if (LINT.equals(name)) {
            throw new RuntimeException(
                    String.format("%1$s names cannot be %2$s", displayName, LINT));
        }
    }

    private static void checkPrefix(String name, String displayName, String prefix) {
        if (name.startsWith(prefix)) {
            throw new RuntimeException(
                    String.format("%1$s names cannot start with '%2$s'", displayName, prefix));
        }
    }
}
