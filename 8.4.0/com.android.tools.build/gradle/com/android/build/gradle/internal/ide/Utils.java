/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.KmpComponentCreationConfig;
import com.android.build.gradle.internal.component.NestedComponentCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.compiling.BuildConfigType;
import com.google.common.collect.Streams;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import kotlin.Unit;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;

public class Utils {

    @NonNull
    public static List<File> getGeneratedSourceFolders(@NonNull ComponentCreationConfig component) {
        return Streams.stream(getGeneratedSourceFoldersFileCollection(component))
                .collect(Collectors.toList());
    }

    @NonNull
    public static List<File> getGeneratedSourceFoldersForUnitTests(
            @NonNull ComponentCreationConfig component) {
        return Streams.stream(getGeneratedSourceFoldersFileCollectionForUnitTests(component))
                .collect(Collectors.toList());
    }

    @NonNull
    private static FileCollection getGeneratedSourceFoldersFileCollectionForUnitTests(
            @NonNull ComponentCreationConfig component) {
        ConfigurableFileCollection fileCollection = component.getServices().fileCollection();

        component
                .getSources()
                .java(
                        javaSources -> {
                            fileCollection.from(
                                    javaSources.variantSourcesFileCollectionForModel$gradle_core(
                                            directoryEntry ->
                                                    directoryEntry.isGenerated()
                                                            && directoryEntry
                                                                    .getShouldBeAddedToIdeModel()));
                            return Unit.INSTANCE;
                        });
        if (component.getOldVariantApiLegacySupport() != null) {
            fileCollection.from(
                    component
                            .getOldVariantApiLegacySupport()
                            .getVariantData()
                            .getExtraGeneratedSourceFoldersOnlyInModel());
        }
        if (!(component instanceof KmpComponentCreationConfig)) {
            fileCollection.from(
                    component
                            .getArtifacts()
                            .get(InternalArtifactType.AP_GENERATED_SOURCES.INSTANCE));
        }
        fileCollection.disallowChanges();
        return fileCollection;
    }

    @NonNull
    public static FileCollection getGeneratedSourceFoldersFileCollection(
            @NonNull ComponentCreationConfig component) {
        ConfigurableFileCollection fileCollection = component.getServices().fileCollection();
        ArtifactsImpl artifacts = component.getArtifacts();
        fileCollection.from(getGeneratedSourceFoldersFileCollectionForUnitTests(component));
        if (component.getBuildFeatures().getAidl()) {
            Callable<Directory> aidlCallable =
                    () ->
                            artifacts
                                    .get(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR.INSTANCE)
                                    .getOrNull();
            fileCollection.from(aidlCallable);
        }
        if (component.getBuildConfigCreationConfig() != null
                && component.getBuildConfigCreationConfig().getBuildConfigType()
                        == BuildConfigType.JAVA_SOURCE) {
            Callable<Directory> buildConfigCallable =
                    () -> component.getPaths().getBuildConfigSourceOutputDir().getOrNull();
            fileCollection.from(buildConfigCallable);
        }
        // this is incorrect as it cannot get the final value, we should always add the folder
        // as a potential source origin and let the IDE deal with it.
        boolean ndkMode = false;
        VariantCreationConfig mainVariant;
        if (component instanceof NestedComponentCreationConfig) {
            mainVariant = ((NestedComponentCreationConfig) component).getMainVariant();
        } else {
            mainVariant = (VariantCreationConfig) component;
        }
        if (mainVariant.getRenderscriptCreationConfig() != null) {
            ndkMode =
                    mainVariant.getRenderscriptCreationConfig().getDslRenderscriptNdkModeEnabled();
        }
        if (!ndkMode && component.getBuildFeatures().getRenderScript()) {
            Callable<Directory> renderscriptCallable =
                    () ->
                            artifacts
                                    .get(
                                            InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR
                                                    .INSTANCE)
                                    .getOrNull();
            fileCollection.from(renderscriptCallable);
        }
        boolean isDataBindingEnabled = component.getBuildFeatures().getDataBinding();
        boolean isViewBindingEnabled = component.getBuildFeatures().getViewBinding();
        if (isDataBindingEnabled || isViewBindingEnabled) {
            Callable<Directory> dataBindingCallable =
                    () ->
                            artifacts
                                    .get(
                                            InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT
                                                    .INSTANCE)
                                    .getOrNull();
            fileCollection.from(dataBindingCallable);
        }
        fileCollection.disallowChanges();
        return fileCollection;
    }

    @NonNull
    public static List<File> getGeneratedResourceFolders(
            @NonNull ComponentCreationConfig component) {
        return Streams.stream(getGeneratedResourceFoldersFileCollection(component))
                .collect(Collectors.toList());
    }

    @NonNull
    public static FileCollection getGeneratedResourceFoldersFileCollection(
            @NonNull ComponentCreationConfig component) {
        ConfigurableFileCollection fileCollection = component.getServices().fileCollection();
        if (component.getOldVariantApiLegacySupport() != null) {
            fileCollection.from(
                    component
                            .getOldVariantApiLegacySupport()
                            .getVariantData()
                            .getExtraGeneratedResFolders());
        }
        if (component.getBuildFeatures().getRenderScript()) {
            fileCollection.from(
                    component
                            .getArtifacts()
                            .get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES.INSTANCE));
        }
        if (component.getBuildFeatures().getAndroidResources()) {
            if (component
                    .getArtifacts()
                    .get(InternalArtifactType.GENERATED_RES.INSTANCE)
                    .isPresent()) {
                fileCollection.from(
                        component.getArtifacts().get(InternalArtifactType.GENERATED_RES.INSTANCE));
            }
        }
        fileCollection.disallowChanges();
        return fileCollection;
    }
}
