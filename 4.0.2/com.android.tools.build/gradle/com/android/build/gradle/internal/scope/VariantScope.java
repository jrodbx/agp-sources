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
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.packaging.JarCreatorType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.model.CodeShrinker;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

/** A scope containing data for a specific variant. */
public interface VariantScope extends TransformVariantScope {
    @Override
    @NonNull
    GlobalScope getGlobalScope();

    @NonNull
    VariantDslInfo getVariantDslInfo();

    @NonNull
    VariantSources getVariantSources();

    @NonNull
    PublishingSpecs.VariantSpec getPublishingSpec();

    void publishIntermediateArtifact(
            @NonNull Provider<?> artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<AndroidArtifacts.PublishedConfigType> configTypes);

    @NonNull
    BaseVariantData getVariantData();

    @Nullable
    CodeShrinker getCodeShrinker();

    @NonNull
    List<File> getProguardFiles();

    /**
     * Returns the proguardFiles explicitly specified in the build.gradle. This method differs from
     * getProguardFiles() because getProguardFiles() may include a default proguard file which
     * wasn't specified in the build.gradle file.
     */
    @NonNull
    List<File> getExplicitProguardFiles();

    @NonNull
    List<File> getTestProguardFiles();

    @NonNull
    List<File> getConsumerProguardFiles();

    @NonNull
    List<File> getConsumerProguardFilesForFeatures();

    @Nullable
    PostprocessingFeatures getPostprocessingFeatures();

    boolean useResourceShrinker();

    boolean isPrecompileDependenciesResourcesEnabled();

    boolean isCrunchPngs();

    boolean consumesFeatureJars();

    /** Returns whether we need to create original java resource streams */
    boolean getNeedsJavaResStreams();

    /** Returns whether we need to create a stream from the merged java resources */
    boolean getNeedsMergedJavaResStream();

    boolean getNeedsMainDexListForBundle();

    boolean isTestOnly();

    boolean isCoreLibraryDesugaringEnabled();

    /** Returns if we need to shrink desugar lib when desugaring Core Library. */
    boolean getNeedsShrinkDesugarLibrary();

    @NonNull
    VariantType getType();

    @NonNull
    DexingType getDexingType();

    boolean getNeedsMainDexList();

    @NonNull
    AndroidVersion getMinSdkVersion();

    @NonNull
    TransformManager getTransformManager();

    @Nullable
    File getNdkDebuggableLibraryFolders(@NonNull Abi abi);

    void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath);

    @Nullable
    BaseVariantData getTestedVariantData();

    @NonNull
    FileCollection getJavaClasspath(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType);

    @NonNull
    FileCollection getJavaClasspath(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey);

    /** Returns the path(s) to compiled R classes (R.jar). */
    @NonNull
    FileCollection getCompiledRClasses(@NonNull AndroidArtifacts.ConsumedConfigType configType);

    @NonNull
    ArtifactCollection getJavaClasspathArtifacts(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey);

    @NonNull
    BuildArtifactsHolder getArtifacts();

    @NonNull
    FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap);

    @NonNull
    FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

    @NonNull
    ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

    @NonNull
    ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap);

    @NonNull
    ArtifactCollection getArtifactCollectionForToolingModel(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull ArtifactType artifactType);

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

    @NonNull
    File getDefaultMergeResourcesOutputDir();

    @NonNull
    File getCompiledResourcesOutputDir();

    @NonNull
    File getResourceBlameLogDir();

    @NonNull
    File getBuildConfigSourceOutputDir();

    @NonNull
    File getGeneratedResOutputDir();

    @NonNull
    File getGeneratedPngsOutputDir();

    @NonNull
    File getRenderscriptResOutputDir();

    @NonNull
    File getRenderscriptObjOutputDir();

    /**
     * Returns a place to store incremental build data. The {@code name} argument has to be unique
     * per task, ideally generated with {@link
     * com.android.build.gradle.internal.tasks.factory.TaskInformation#getName()}.
     */
    @NonNull
    File getIncrementalDir(String name);

    @NonNull
    File getCoverageReportDir();

    @NonNull
    File getClassOutputForDataBinding();

    @NonNull
    File getGeneratedClassListOutputFileForDataBinding();

    @NonNull
    File getFullApkPackagesOutputDirectory();

    @NonNull
    File getMicroApkManifestFile();

    @NonNull
    File getMicroApkResDirectory();

    @NonNull
    File getAarLocation();

    @NonNull
    File getManifestOutputDirectory();

    @NonNull
    File getApkLocation();

    @NonNull
    MutableTaskContainer getTaskContainer();

    @NonNull
    VariantDependencies getVariantDependencies();

    @NonNull
    File getIntermediateDir(
            @NonNull com.android.build.api.artifact.ArtifactType<Directory> taskOutputType);

    enum Java8LangSupport {
        INVALID,
        UNUSED,
        D8,
        DESUGAR,
        RETROLAMBDA,
        R8,
    }

    @NonNull
    Java8LangSupport getJava8LangSupportType();

    @NonNull
    DexerTool getDexer();

    @NonNull
    DexMergerTool getDexMerger();

    @NonNull
    ConfigurableFileCollection getTryWithResourceRuntimeSupportJar();

    @NonNull
    FileCollection getBootClasspath();

    @NonNull
    InternalArtifactType<Directory> getManifestArtifactType();

    @NonNull
    File getSymbolTableFile();

    @NonNull
    JarCreatorType getJarCreatorType();

    @NonNull
    ApkCreatorType getApkCreatorType();

    /**
     * Returns a {@link Provider} for the name of the feature.
     *
     * @return the provider
     */
    @NonNull
    Provider<String> getFeatureName();

    @NonNull
    Provider<Integer> getResOffset();
}
