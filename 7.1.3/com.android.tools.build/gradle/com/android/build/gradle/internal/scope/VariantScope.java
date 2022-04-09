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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.component.ConsumableCreationConfig;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.packaging.JarCreatorType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.PublishedConfigSpec;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.builder.internal.packaging.ApkCreatorType;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

/** A scope containing data for a specific variant. */
public interface VariantScope {

    @NonNull
    PublishingSpecs.VariantSpec getPublishingSpec();

    void publishIntermediateArtifact(
            @NonNull Provider<?> artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Set<PublishedConfigSpec> configSpecs,
            @Nullable LibraryElements libraryElements,
            boolean isTestFixturesArtifact);

    @NonNull
    List<File> getConsumerProguardFiles();

    @NonNull
    List<File> getConsumerProguardFilesForFeatures();

    @Nullable
    PostprocessingFeatures getPostprocessingFeatures();

    boolean isCrunchPngs();

    boolean consumesFeatureJars();

    /** Returns whether we need to create original java resource streams */
    boolean getNeedsJavaResStreams();

    boolean isTestOnly(VariantImpl variant);

    boolean isCoreLibraryDesugaringEnabled(ConsumableCreationConfig creationConfig);

    void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath);

    @NonNull
    FileCollection getLocalPackagedJars();

    /**
     * Returns the direct (i.e., non-transitive) local file dependencies matching the given
     * predicate
     *
     * @return a non null, but possibly empty FileCollection
     * @param filePredicate the file predicate used to filter the local file dependencies
     */
    @NonNull
    FileCollection getLocalFileDependencies(Predicate<File> filePredicate);

    @NonNull
    FileCollection getProvidedOnlyClasspath();

    @NonNull
    Provider<RegularFile> getRJarForUnitTests();

    enum Java8LangSupport {
        INVALID,
        UNUSED,
        D8,
        RETROLAMBDA,
        R8,
    }

    @NonNull
    JarCreatorType getJarCreatorType();

    @NonNull
    ApkCreatorType getApkCreatorType();
}
