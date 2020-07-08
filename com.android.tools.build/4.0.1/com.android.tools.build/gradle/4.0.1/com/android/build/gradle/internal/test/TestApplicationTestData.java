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

package com.android.build.gradle.internal.test;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.testing.TestData;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/** Implementation of {@link TestData} for separate test modules. */
public class TestApplicationTestData extends AbstractTestDataImpl {

    private final Supplier<String> testApplicationId;
    private final Map<String, String> testedProperties;

    public TestApplicationTestData(
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantSources variantSources,
            Supplier<String> testApplicationId,
            @NonNull Provider<Directory> testApkDir,
            @NonNull FileCollection testedApksDir) {
        super(variantDslInfo, variantSources, testApkDir, testedApksDir);
        this.testedProperties = new HashMap<>();
        this.testApplicationId = testApplicationId;
    }

    @Override
    public void loadFromMetadataFile(File metadataFile) {
        BuildElements testedManifests =
                ExistingBuildElements.from(
                        InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                        metadataFile.getParentFile());
        // all published manifests have the same package so first one will do.
        Optional<BuildOutput> splitOutput = testedManifests.stream().findFirst();

        if (splitOutput.isPresent()) {
            testedProperties.putAll(splitOutput.get().getProperties());
        } else {
            throw new RuntimeException(
                    "No merged manifest metadata at " + metadataFile.getAbsolutePath());
        }
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return testApplicationId.get();
    }

    @Nullable
    @Override
    public String getTestedApplicationId() {
        return testedProperties.get("packageId");
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @NonNull
    @Override
    public List<File> getTestedApks(
            @NonNull DeviceConfigProvider deviceConfigProvider, @NonNull ILogger logger) {

        if (testedApksDir == null) {
            return ImmutableList.of();
        }
        // retrieve all the published files.
        @Nullable
        BuiltArtifacts builtArtifacts = new BuiltArtifactsLoaderImpl().load(testedApksDir);
        return builtArtifacts != null
                ? builtArtifacts
                        .getElements()
                        .stream()
                        .map(BuiltArtifact::getOutputFile)
                        .map(Path::toFile)
                        .collect(Collectors.toList())
                : ImmutableList.of();
    }
}
