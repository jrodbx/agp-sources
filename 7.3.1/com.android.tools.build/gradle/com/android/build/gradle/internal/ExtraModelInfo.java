/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.ide.ArtifactMetaDataImpl;
import com.android.build.gradle.internal.ide.JavaArtifactImpl;
import com.android.build.gradle.internal.variant.DefaultSourceProviderContainer;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.gradle.api.artifacts.Configuration;

/** For storing additional model information. */
public class ExtraModelInfo {

    private final Map<String, ArtifactMetaData> extraArtifactMap = Maps.newHashMap();
    private final ListMultimap<String, AndroidArtifact> extraAndroidArtifacts = ArrayListMultimap.create();
    private final ListMultimap<String, JavaArtifact> extraJavaArtifacts = ArrayListMultimap.create();

    private final ListMultimap<String, SourceProviderContainer> extraBuildTypeSourceProviders = ArrayListMultimap.create();
    private final ListMultimap<String, SourceProviderContainer> extraProductFlavorSourceProviders = ArrayListMultimap.create();
    private final ListMultimap<String, SourceProviderContainer> extraMultiFlavorSourceProviders = ArrayListMultimap.create();

    public ExtraModelInfo() {}

    public Collection<ArtifactMetaData> getExtraArtifacts() {
        return extraArtifactMap.values();
    }

    public Collection<AndroidArtifact> getExtraAndroidArtifacts(@NonNull String variantName) {
        return extraAndroidArtifacts.get(variantName);
    }

    public Collection<JavaArtifact> getExtraJavaArtifacts(@NonNull String variantName) {
        return extraJavaArtifacts.get(variantName);
    }

    public Collection<SourceProviderContainer> getExtraFlavorSourceProviders(
            @NonNull String flavorName) {
        return extraProductFlavorSourceProviders.get(flavorName);
    }

    public Collection<SourceProviderContainer> getExtraBuildTypeSourceProviders(
            @NonNull String buildTypeName) {
        return extraBuildTypeSourceProviders.get(buildTypeName);
    }

    public void registerArtifactType(@NonNull String name,
            boolean isTest,
            int artifactType) {

        if (extraArtifactMap.get(name) != null) {
            throw new IllegalArgumentException(
                    String.format("Artifact with name %1$s already registered.", name));
        }

        extraArtifactMap.put(name, new ArtifactMetaDataImpl(name, isTest, artifactType));
    }

    public void registerBuildTypeSourceProvider(
            @NonNull String name,
            @NonNull BuildType buildType,
            @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }

        extraBuildTypeSourceProviders.put(buildType.getName(),
                new DefaultSourceProviderContainer(name, sourceProvider));

    }

    public void registerProductFlavorSourceProvider(
            @NonNull String name,
            @NonNull ProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }

        extraProductFlavorSourceProviders.put(productFlavor.getName(),
                new DefaultSourceProviderContainer(name, sourceProvider));

    }

    public void registerMultiFlavorSourceProvider(@NonNull String name,
            @NonNull String flavorName,
            @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }

        extraMultiFlavorSourceProviders.put(flavorName,
                new DefaultSourceProviderContainer(name, sourceProvider));
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Collection<File> generatedSourceFolders,
            @NonNull Iterable<String> ideSetupTaskNames,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @NonNull File javaResourcesFolder,
            @Nullable SourceProvider sourceProvider) {
        ArtifactMetaData artifactMetaData = extraArtifactMap.get(name);
        if (artifactMetaData == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }
        if (artifactMetaData.getType() != ArtifactMetaData.TYPE_JAVA) {
            throw new IllegalArgumentException(
                    String.format("Artifact with name %1$s is not of type JAVA", name));
        }

        JavaArtifact artifact =
                new JavaArtifactImpl(
                        name,
                        assembleTaskName,
                        javaCompileTaskName,
                        ideSetupTaskNames,
                        generatedSourceFolders,
                        classesFolder,
                        Collections.emptySet(),
                        javaResourcesFolder,
                        null,
                        new ConfigurationDependencies(configuration),
                        new ConfigurationDependencyGraphs(configuration),
                        sourceProvider,
                        null);

        extraJavaArtifacts.put(variant.getName(), artifact);
    }
}
