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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.CodeShrinker;
import com.android.builder.model.Dependencies;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.TestOptions;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.file.RegularFile;

/**
 * Implementation of AndroidArtifact that is serializable
 */
@Immutable
final class AndroidArtifactImpl extends BaseArtifactImpl implements AndroidArtifact, Serializable {
    private static final long serialVersionUID = 3L;

    private final boolean isSigned;
    @NonNull private final String baseName;
    @NonNull private final String applicationId;
    @NonNull private final String sourceGenTaskName;
    @NonNull private final List<File> generatedResourceFolders;
    @NonNull private final List<File> additionalRuntimeApks;
    @NonNull private final Map<String, ClassField> buildConfigFields;
    @NonNull private final Map<String, ClassField> resValues;
    @NonNull private final InstantRun instantRun;
    @NonNull
    private final BuildOutputSupplier<Collection<EarlySyncBuildOutput>> splitOutputsSupplier;

    @NonNull private final BuildOutputSupplier<Collection<EarlySyncBuildOutput>> manifestSupplier;
    @NonNull private final Collection<AndroidArtifactOutput> buildOutputs;
    @Nullable private final String signingConfigName;
    @Nullable private final Set<String> abiFilters;
    @Nullable private final TestOptions testOptions;
    @Nullable private final String instrumentedTestTaskName;
    @Nullable private final String bundleTaskName;
    @Nullable private final String bundleTaskOutputListingFile;
    @Nullable private final String apkFromBundleTaskName;
    @Nullable private final String apkFromBundleTaskOutputListingFile;
    @Nullable private final CodeShrinker codeShrinker;

    AndroidArtifactImpl(
            @NonNull String name,
            @NonNull String baseName,
            @NonNull String assembleTaskName,
            @Nullable RegularFile postAssembleTaskModelFile,
            boolean isSigned,
            @Nullable String signingConfigName,
            @NonNull String applicationId,
            @NonNull String sourceGenTaskName,
            @NonNull String compileTaskName,
            @NonNull List<File> generatedSourceFolders,
            @NonNull List<File> generatedResourceFolders,
            @NonNull File classesFolder,
            @NonNull Set<File> additionalClassFolders,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies compileDependencies,
            @NonNull DependencyGraphs dependencyGraphs,
            @NonNull List<File> additionalRuntimeApks,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProviders,
            @Nullable Set<String> abiFilters,
            @NonNull Map<String, ClassField> buildConfigFields,
            @NonNull Map<String, ClassField> resValues,
            @NonNull InstantRun instantRun,
            @NonNull BuildOutputSupplier<Collection<EarlySyncBuildOutput>> splitOutputsSupplier,
            @NonNull BuildOutputSupplier<Collection<EarlySyncBuildOutput>> manifestSupplier,
            @Nullable TestOptions testOptions,
            @Nullable String instrumentedTestTaskName,
            @Nullable String bundleTaskName,
            @Nullable RegularFile bundleTaskOutputListingFile,
            @Nullable String apkFromBundleTaskName,
            @Nullable RegularFile apkFromBundleTaskOutputListingFile,
            @Nullable CodeShrinker codeShrinker) {
        super(
                name,
                assembleTaskName,
                postAssembleTaskModelFile,
                compileTaskName,
                classesFolder,
                additionalClassFolders,
                javaResourcesFolder,
                compileDependencies,
                dependencyGraphs,
                variantSourceProvider,
                multiFlavorSourceProviders,
                generatedSourceFolders);

        this.baseName = baseName;
        this.isSigned = isSigned;
        this.signingConfigName = signingConfigName;
        this.applicationId = applicationId;
        this.sourceGenTaskName = sourceGenTaskName;
        this.generatedResourceFolders = generatedResourceFolders;
        this.additionalRuntimeApks = additionalRuntimeApks;
        this.abiFilters = abiFilters;
        this.buildConfigFields = buildConfigFields;
        this.resValues = resValues;
        this.instantRun = instantRun;
        this.splitOutputsSupplier = splitOutputsSupplier;
        this.manifestSupplier = manifestSupplier;
        this.testOptions = testOptions;
        this.instrumentedTestTaskName = instrumentedTestTaskName;
        this.bundleTaskName = bundleTaskName;
        this.bundleTaskOutputListingFile =
                bundleTaskOutputListingFile != null
                        ? bundleTaskOutputListingFile.getAsFile().getAbsolutePath()
                        : null;
        this.apkFromBundleTaskName = apkFromBundleTaskName;
        this.apkFromBundleTaskOutputListingFile =
                apkFromBundleTaskOutputListingFile != null
                        ? apkFromBundleTaskOutputListingFile.getAsFile().getAbsolutePath()
                        : null;
        this.codeShrinker = codeShrinker;
        this.buildOutputs = computeBuildOutputs();
    }

    private EarlySyncBuildOutput getOutputFor(
            Collection<EarlySyncBuildOutput> outputs,
            VariantOutput.OutputType outputType,
            Collection<FilterData> filtersData) {

        for (EarlySyncBuildOutput output : outputs) {
            if (output.getApkType() == outputType && output.getFiltersData().equals(filtersData)) {
                return output;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public Collection<AndroidArtifactOutput> getOutputs() {
        return buildOutputs;
    }

    @NonNull
    private Collection<AndroidArtifactOutput> computeBuildOutputs() {
        Collection<EarlySyncBuildOutput> manifests = manifestSupplier.get();
        Collection<EarlySyncBuildOutput> outputs = splitOutputsSupplier.get();
        if (outputs.isEmpty()) {
            return manifests.isEmpty()
                    ? guessOutputsBasedOnNothing()
                    : guessOutputsBaseOnManifests();
        }

        return outputs.stream()
                .map(
                        splitOutput ->
                                new AndroidArtifactOutputImpl(
                                        splitOutput,
                                        getOutputFor(
                                                manifests,
                                                splitOutput.getApkType(),
                                                splitOutput.getFiltersData())))
                .collect(Collectors.toList());

    }

    private Collection<AndroidArtifactOutput> guessOutputsBasedOnNothing() {
        ApkData mainApkInfo = ApkData.of(OutputFile.OutputType.MAIN, ImmutableList.of(), -1);

        return ImmutableList.of(
                new AndroidArtifactOutputImpl(
                        new EarlySyncBuildOutput(
                                InternalArtifactType.APK.INSTANCE,
                                mainApkInfo.getType(),
                                mainApkInfo.getFilters(),
                                -1,
                                splitOutputsSupplier.guessOutputFile(
                                        baseName + SdkConstants.DOT_ANDROID_PACKAGE)),
                        new EarlySyncBuildOutput(
                                InternalArtifactType.APK.INSTANCE,
                                mainApkInfo.getType(),
                                mainApkInfo.getFilters(),
                                -1,
                                manifestSupplier.guessOutputFile(
                                        SdkConstants.ANDROID_MANIFEST_XML))));
    }

    private Collection<AndroidArtifactOutput> guessOutputsBaseOnManifests() {

        return manifestSupplier
                .get()
                .stream()
                .map(
                        manifestOutput ->
                                new AndroidArtifactOutputImpl(
                                        new EarlySyncBuildOutput(
                                                InternalArtifactType.APK.INSTANCE,
                                                manifestOutput.getApkType(),
                                                manifestOutput.getFiltersData(),
                                                manifestOutput.getVersionCode(),
                                                splitOutputsSupplier.guessOutputFile(
                                                        baseName
                                                                + Joiner.on("-")
                                                                        .join(
                                                                                manifestOutput
                                                                                        .getFilters()
                                                                                        .stream()
                                                                                        .map(
                                                                                                FilterData
                                                                                                        ::getIdentifier)
                                                                                        .collect(
                                                                                                Collectors
                                                                                                        .toList()))
                                                                + SdkConstants
                                                                        .DOT_ANDROID_PACKAGE)),
                                        manifestOutput))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isSigned() {
        return isSigned;
    }

    @Nullable
    @Override
    public String getSigningConfigName() {
        return signingConfigName;
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @NonNull
    @Override
    public String getSourceGenTaskName() {
        return sourceGenTaskName;
    }

    @NonNull
    @Override
    public Set<String> getIdeSetupTaskNames() {
        return Sets.newHashSet(getSourceGenTaskName());
    }

    @NonNull
    @Override
    public List<File> getGeneratedResourceFolders() {
        return generatedResourceFolders;
    }

    @Nullable
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @NonNull
    @Override
    public Collection<NativeLibrary> getNativeLibraries() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        return buildConfigFields;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return resValues;
    }

    @NonNull
    @Override
    public InstantRun getInstantRun() {
        return instantRun;
    }

    @NonNull
    @Override
    public List<File> getAdditionalRuntimeApks() {
        return additionalRuntimeApks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AndroidArtifactImpl that = (AndroidArtifactImpl) o;
        return isSigned == that.isSigned
                && Objects.equals(signingConfigName, that.signingConfigName)
                && Objects.equals(applicationId, that.applicationId)
                && Objects.equals(sourceGenTaskName, that.sourceGenTaskName)
                && Objects.equals(generatedResourceFolders, that.generatedResourceFolders)
                && Objects.equals(abiFilters, that.abiFilters)
                && Objects.equals(buildConfigFields, that.buildConfigFields)
                && Objects.equals(resValues, that.resValues)
                && Objects.equals(manifestSupplier, that.manifestSupplier)
                && Objects.equals(splitOutputsSupplier, that.splitOutputsSupplier)
                && Objects.equals(instantRun, that.instantRun)
                && Objects.equals(additionalRuntimeApks, that.additionalRuntimeApks)
                && Objects.equals(baseName, that.baseName)
                && Objects.equals(testOptions, that.testOptions)
                && Objects.equals(instrumentedTestTaskName, that.instrumentedTestTaskName)
                && Objects.equals(bundleTaskName, that.bundleTaskName)
                && Objects.equals(bundleTaskOutputListingFile, that.bundleTaskOutputListingFile)
                && Objects.equals(
                        apkFromBundleTaskOutputListingFile, that.apkFromBundleTaskOutputListingFile)
                && Objects.equals(codeShrinker, that.codeShrinker)
                && Objects.equals(apkFromBundleTaskName, that.apkFromBundleTaskName)
                && Objects.equals(buildOutputs, that.buildOutputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                splitOutputsSupplier,
                manifestSupplier,
                isSigned,
                signingConfigName,
                applicationId,
                sourceGenTaskName,
                generatedResourceFolders,
                abiFilters,
                buildConfigFields,
                resValues,
                instantRun,
                additionalRuntimeApks,
                baseName,
                testOptions,
                instrumentedTestTaskName,
                bundleTaskName,
                bundleTaskOutputListingFile,
                codeShrinker,
                apkFromBundleTaskName,
                apkFromBundleTaskOutputListingFile,
                buildOutputs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("manifestProxy", manifestSupplier)
                .add("splitOutputsSupplier", splitOutputsSupplier)
                .add("isSigned", isSigned)
                .add("signingConfigName", signingConfigName)
                .add("applicationId", applicationId)
                .add("sourceGenTaskName", sourceGenTaskName)
                .add("generatedResourceFolders", generatedResourceFolders)
                .add("abiFilters", abiFilters)
                .add("buildConfigFields", buildConfigFields)
                .add("resValues", resValues)
                .add("instantRun", instantRun)
                .add("testOptions", testOptions)
                .add("instrumentedTestTaskName", instrumentedTestTaskName)
                .add("bundleTaskName", bundleTaskName)
                .add("bundleTasOutputListingFile", bundleTaskName)
                .add("codeShrinker", codeShrinker)
                .add("apkFromBundleTaskOutputListingFile", apkFromBundleTaskOutputListingFile)
                .add("apkFromBundleTaskName", apkFromBundleTaskName)
                .add("buildOutputs", buildOutputs)
                .toString();
    }

    @Override
    @Nullable
    public TestOptions getTestOptions() {
        return testOptions;
    }

    @Nullable
    @Override
    public String getInstrumentedTestTaskName() {
        return instrumentedTestTaskName;
    }

    @Nullable
    @Override
    public String getBundleTaskName() {
        return bundleTaskName;
    }

    @Nullable
    @Override
    public String getBundleTaskOutputListingFile() {
        return bundleTaskOutputListingFile;
    }

    @Nullable
    @Override
    public String getApkFromBundleTaskName() {
        return apkFromBundleTaskName;
    }

    @Nullable
    @Override
    public String getApkFromBundleTaskOutputListingFile() {
        return apkFromBundleTaskOutputListingFile;
    }

    @Nullable
    @Override
    public CodeShrinker getCodeShrinker() {
        return codeShrinker;
    }
}
