/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_SIGNING_CONFIG_VERSIONS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPRESSED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.SIGNING_CONFIG_VERSIONS;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.Artifact;
import com.android.build.api.artifact.ArtifactTransformationRequest;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.MultiOutputHandler;
import com.android.build.api.variant.impl.BuiltArtifactImpl;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.api.variant.impl.VariantOutputConfigurationImplKt;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.component.ApplicationCreationConfig;
import com.android.build.gradle.internal.component.KmpComponentCreationConfig;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dependency.AndroidAttributes;
import com.android.build.gradle.internal.manifest.ManifestData;
import com.android.build.gradle.internal.manifest.ManifestDataKt;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType;
import com.android.build.gradle.internal.signing.SigningConfigDataProvider;
import com.android.build.gradle.internal.signing.SigningConfigProviderParams;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.NewIncrementalTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTaskKt;
import com.android.build.gradle.internal.tasks.SigningConfigUtils;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters;
import com.android.build.gradle.internal.workeractions.WorkActionAdapter;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.dexing.DexingType;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.IssueReporter;
import com.android.builder.files.IncrementalChanges;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.KeyedFileCache;
import com.android.builder.files.RelativeFile;
import com.android.builder.files.SerializableChange;
import com.android.builder.files.SerializableInputChanges;
import com.android.builder.files.ZipCentralDirectory;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.packaging.DexPackagingMode;
import com.android.builder.packaging.PackagingUtils;
import com.android.builder.utils.ZipEntryUtils;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import kotlin.Pair;
import kotlin.jvm.functions.Function3;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileType;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

/** Abstract task to package an Android artifact. */
@DisableCachingByDefault
public abstract class PackageAndroidArtifact extends NewIncrementalTask {

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getManifests();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getResourceFiles();

    @Input
    @NonNull
    public abstract SetProperty<String> getAbiFilters();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @Optional
    public abstract ConfigurableFileCollection getBaseModuleMetadata();

    /** App metadata to be packaged in the APK. */
    @InputFile
    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @Optional
    public abstract RegularFileProperty getAppMetadata();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @Optional
    public abstract RegularFileProperty getMergedArtProfile();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @Optional
    public abstract RegularFileProperty getMergedArtProfileMetadata();

    @OutputDirectory
    public abstract DirectoryProperty getIncrementalFolder();

    protected Artifact<Directory> manifestType;

    @Input
    public String getManifestTypeName() {
        return manifestType.name();
    }

    /**
     * List of folders and/or jars that contain the merged java resources.
     */
    @Classpath
    @Incremental
    public abstract ConfigurableFileCollection getJavaResourceFiles();

    /** FileCollection because it comes from another project. */
    @Classpath
    @Incremental
    public abstract ConfigurableFileCollection getFeatureJavaResourceFiles();

    /** FileCollection because of the legacy Transform API. */
    @Classpath
    @Incremental
    public abstract ConfigurableFileCollection getJniFolders();

    /** FileCollection because of the legacy Transform API. */
    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDexFolders();

    /** FileCollection as comes from another project. */
    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getFeatureDexFolder();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getAssets();

    @InputFile
    @Nullable
    @Optional
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getDependencyDataFile();

    @Input
    public abstract Property<String> getCreatedBy();

    private boolean jniDebugBuild;

    private SigningConfigDataProvider signingConfigData;

    @NonNull
    @Input
    public abstract ListProperty<String> getAaptOptionsNoCompress();

    @NonNull
    @Input
    public abstract Property<Boolean> getJniLibsUseLegacyPackaging();

    @NonNull
    @Input
    public abstract Property<Boolean> getDexUseLegacyPackaging();

    @Nullable protected String buildTargetAbi;

    @Input
    public abstract Property<String> getProjectBaseName();

    /**
     * Name of directory, inside the intermediate directory, where zip caches are kept.
     */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";
    private static final String ZIP_64_COPY_DIR = "zip64-copy";

    @Input
    public boolean getJniDebugBuild() {
        return jniDebugBuild;
    }

    public void setJniDebugBuild(boolean jniDebugBuild) {
        this.jniDebugBuild = jniDebugBuild;
    }

    @Input
    public abstract Property<Boolean> getDebugBuild();

    @Input
    public abstract Property<Boolean> getIsDeterministicEntryOrder();

    /**
     * FileCollection of all other RELATIVE file inputs if getIsDeterministic() is true, or an empty
     * FileCollection if getIsDeterministic() is false.
     *
     * <p>The reason for this input is so that any change to an input file will cause the output APK
     * to be built from scratch, which is necessary to create a deterministic APK.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract ConfigurableFileCollection getAllInputFilesWithRelativePathSensitivity();

    /**
     * FileCollection of all other NAME_ONLY file inputs if getIsDeterministic() is true, or an
     * empty FileCollection if getIsDeterministic() is false.
     *
     * <p>The reason for this input is so that any change to an input file will cause the output APK
     * to be built from scratch, which is necessary to create a deterministic APK.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @Optional
    public abstract ConfigurableFileCollection getAllInputFilesWithNameOnlyPathSensitivity();

    /**
     * FileCollection of all other @Classpath file inputs if getIsDeterministic() is true, or an
     * empty FileCollection if getIsDeterministic() is false.
     *
     * <p>The reason for this input is so that any change to an input file will cause the output APK
     * to be built from scratch, which is necessary to create a deterministic APK.
     */
    @Classpath
    @Optional
    public abstract ConfigurableFileCollection getAllClasspathInputFiles();

    @Nested
    public SigningConfigDataProvider getSigningConfigData() {
        return signingConfigData;
    }

    void setSigningConfigData(SigningConfigDataProvider signingConfigData) {
        this.signingConfigData = signingConfigData;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getSigningConfigVersions();

    @Input
    public abstract Property<Integer> getMinSdkVersion();

    @Input
    public abstract Property<String> getApplicationId();

    /*
     * We don't really use this. But this forces a full build if the native libraries or dex
     * packaging mode changes.
     */
    @Input
    public List<String> getNativeLibrariesAndDexPackagingModeNames() {
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        getManifests()
                .get()
                .getAsFileTree()
                .getFiles()
                .forEach(
                        manifest -> {
                            if (manifest.isFile()
                                    && manifest.getName()
                                    .equals(SdkConstants.ANDROID_MANIFEST_XML)) {
                                ManifestAttributeSupplier parser =
                                        new DefaultManifestParser(manifest, () -> true, true, null);
                                String nativeLibsPackagingMode =
                                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                                        parser.getExtractNativeLibs())
                                                .toString();
                                listBuilder.add(nativeLibsPackagingMode);
                                String dexPackagingMode =
                                        PackagingUtils
                                                .getDexPackagingMode(
                                                        parser.getUseEmbeddedDex(),
                                                        getDexUseLegacyPackaging().get())
                                                .toString();
                                listBuilder.add(dexPackagingMode);
                            }
                        });
        return listBuilder.build();
    }

    @Input
    @Optional
    @Nullable
    public String getBuildTargetAbi() {
        return buildTargetAbi;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @org.gradle.api.tasks.OutputFile
    public abstract RegularFileProperty getIdeModelOutputFile();

    @Nested
    public abstract Property<MultiOutputHandler> getOutputsHandler();

    @Input
    public abstract ArtifactTransformationRequest getTransformationRequest();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @Optional
    public abstract RegularFileProperty getVersionControlInfoFile();

    @Override
    public void doTaskAction(@NonNull InputChanges changes) {
        if (!changes.isIncremental()) {
            checkFileNameUniqueness();
        }
        HashSet<File> changedResourceFiles = new HashSet<>();

        for (FileChange fileChange : changes.getFileChanges(getResourceFiles())) {
            if (fileChange.getFileType() == FileType.FILE) {
                changedResourceFiles.add(fileChange.getFile());
            }
        }

        getTransformationRequest()
                .submit(
                        this,
                        getWorkerExecutor().noIsolation(),
                        IncrementalSplitterRunnable.class,
                        configure(changedResourceFiles, changes));
    }

    private Function3<BuiltArtifact, Directory, SplitterParams, File> configure(
            @NonNull HashSet<File> changedResourceFiles, @NonNull InputChanges changes) {

        MultiOutputHandler outputsHandler = getOutputsHandler().get();

        return (builtArtifact, directory, parameter) -> {
            VariantOutputImpl.SerializedForm variantOutput =
                    outputsHandler.getOutput(
                            ((BuiltArtifactImpl) builtArtifact).getVariantOutputConfiguration());

            parameter.getVariantOutput().set(variantOutput);

            parameter.getOutputHandler().set(outputsHandler.toSerializable());

            File outputFile =
                    outputsHandler.computeBuildOutputFile(
                            getOutputDirectory().get().getAsFile(), variantOutput);
            parameter
                    .getIncrementalDirForSplit()
                    .set(
                            outputsHandler.computeUniqueDirForSplit(
                                    getIncrementalFolder().get().getAsFile(),
                                    variantOutput,
                                    variantName));
            parameter.getAndroidResourcesFile().set(new File(builtArtifact.getOutputFile()));
            parameter
                    .getAndroidResourcesChanged()
                    .set(changedResourceFiles.contains(new File(builtArtifact.getOutputFile())));
            parameter.getProjectPath().set(getProjectPath().get());
            parameter.getOutputFile().set(outputFile);
            parameter.getIncrementalFolder().set(getIncrementalFolder());
            if (getFeatureDexFolder().isEmpty()) {
                parameter
                        .getDexFiles()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getDexFolders()));
                parameter
                        .getJavaResourceFiles()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getJavaResourceFiles()));
            } else {
                // We reach this code if we're in a dynamic-feature module and code shrinking is
                // enabled in the base module. In this case, we want to use the feature dex files
                // (and the feature java resource jar if using R8) published from the base.
                parameter
                        .getDexFiles()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getFeatureDexFolder()));
                parameter
                        .getJavaResourceFiles()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getFeatureJavaResourceFiles()));
            }
            parameter
                    .getAssetsFiles()
                    .set(
                            IncrementalChangesUtils.getChangesInSerializableForm(
                                    changes, getAssets()));
            parameter
                    .getJniFiles()
                    .set(
                            IncrementalChangesUtils.getChangesInSerializableForm(
                                    changes, getJniFolders()));
            if (getAppMetadata().isPresent()) {
                parameter
                        .getAppMetadataFiles()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getAppMetadata()));
            } else {
                parameter
                        .getAppMetadataFiles()
                        .set(new SerializableInputChanges(ImmutableList.of(), ImmutableSet.of()));
            }

            if (getMergedArtProfile().isPresent()
                    && getMergedArtProfile().get().getAsFile().exists()) {
                parameter
                        .getMergedArtProfile()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getMergedArtProfile()));
            } else {
                parameter
                        .getMergedArtProfile()
                        .set(new SerializableInputChanges(ImmutableList.of(), ImmutableList.of()));
            }

            if (getMergedArtProfileMetadata().isPresent()
                    && getMergedArtProfileMetadata().get().getAsFile().exists()) {
                parameter
                        .getMergedArtProfileMetadata()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getMergedArtProfileMetadata()));
            } else {
                parameter
                        .getMergedArtProfileMetadata()
                        .set(new SerializableInputChanges(ImmutableList.of(), ImmutableList.of()));
            }

            if (getVersionControlInfoFile().isPresent()
                    && getVersionControlInfoFile().get().getAsFile().exists()) {
                parameter
                        .getVersionControlInfoFile()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getVersionControlInfoFile()));
            } else {
                parameter
                        .getVersionControlInfoFile()
                        .set(new SerializableInputChanges(ImmutableList.of(), ImmutableList.of()));
            }

            parameter.getManifestType().set(manifestType);
            parameter.getSigningConfigData().set(signingConfigData.convertToParams());
            parameter
                    .getSigningConfigVersionsFile()
                    .set(getSigningConfigVersions().getSingleFile());

            if (getBaseModuleMetadata().isEmpty()) {
                parameter.getAbiFilters().set(getAbiFilters());
            } else {
                // Dynamic feature
                List<String> appAbiFilters;
                try {
                    appAbiFilters =
                            ModuleMetadata.load(getBaseModuleMetadata().getSingleFile())
                                    .getAbiFilters();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (appAbiFilters.isEmpty()) {
                    // No ABI Filters were applied from the base application, but we still want
                    // to respect injected filters from studio, so use the task field (rather than
                    // just empty list)
                    parameter.getAbiFilters().set(getAbiFilters());
                } else {
                    // Respect the build author's explicit choice, even in the presence of injected
                    // ABI information from Studio
                    parameter.getAbiFilters().set(appAbiFilters);
                }
            }
            parameter.getJniFolders().set(getJniFolders().getFiles());
            parameter.getManifestDirectory().set(getManifests());
            parameter.getAaptOptionsNoCompress().set(getAaptOptionsNoCompress().get());
            parameter.getJniLibsUseLegacyPackaging().set(getJniLibsUseLegacyPackaging().get());
            parameter.getDexUseLegacyPackaging().set(getDexUseLegacyPackaging().get());
            parameter.getCreatedBy().set(getCreatedBy().get());
            parameter.getMinSdkVersion().set(getMinSdkVersion().get());

            parameter.getIsDebuggableBuild().set(getDebugBuild().get());
            parameter.getIsDeterministicEntryOrder().set(getIsDeterministicEntryOrder().get());
            parameter.getIsJniDebuggableBuild().set(getJniDebugBuild());
            parameter.getDependencyDataFile().set(getDependencyDataFile());
            parameter
                    .getPackagerMode()
                    .set(
                            changes.isIncremental()
                                    ? IncrementalPackagerBuilder.BuildType.INCREMENTAL
                                    : IncrementalPackagerBuilder.BuildType.CLEAN);
            return outputFile;
        };
    }

    private void checkFileNameUniqueness() {
        checkFileNameUniqueness(new BuiltArtifactsLoaderImpl().load(getResourceFiles().get()));
    }

    @VisibleForTesting
    static void checkFileNameUniqueness(@Nullable BuiltArtifactsImpl builtArtifacts) {

        if (builtArtifacts == null) return;

        Collection<File> fileOutputs =
                builtArtifacts.getElements().stream()
                        .map(builtArtifact -> new File(builtArtifact.getOutputFile()))
                        .collect(Collectors.toList());

        java.util.Optional<String> repeatingFileNameOptional =
                fileOutputs
                        .stream()
                        .filter(fileOutput -> Collections.frequency(fileOutputs, fileOutput) > 1)
                        .map(File::getName)
                        .findFirst();
        if (repeatingFileNameOptional.isPresent()) {
            String repeatingFileName = repeatingFileNameOptional.get();
            List<String> conflictingApks =
                    builtArtifacts.getElements().stream()
                            .filter(
                                    buildOutput ->
                                            new File(buildOutput.getOutputFile())
                                                    .getName()
                                                    .equals(repeatingFileName))
                            .map(
                                    buildOutput -> {
                                        if (buildOutput.getFilters().isEmpty()) {
                                            return buildOutput.getOutputType().toString();
                                        } else {
                                            return Joiner.on("-").join(buildOutput.getFilters());
                                        }
                                    })
                            .collect(Collectors.toList());

            throw new RuntimeException(
                    String.format(
                            "Several variant outputs are configured to use "
                                    + "the same file name \"%1$s\", filters : %2$s",
                            repeatingFileName, Joiner.on(":").join(conflictingApks)));
        }
    }

    public abstract static class SplitterParams implements DecoratedWorkParameters {
        @NonNull
        public abstract Property<VariantOutputImpl.SerializedForm> getVariantOutput();

        @NonNull
        public abstract Property<MultiOutputHandler> getOutputHandler();

        @NonNull
        public abstract Property<String> getProjectPath();

        @NonNull
        public abstract RegularFileProperty getAndroidResourcesFile();

        @NonNull
        public abstract Property<Boolean> getAndroidResourcesChanged();

        @NonNull
        public abstract RegularFileProperty getOutputFile();

        @NonNull
        public abstract DirectoryProperty getIncrementalFolder();

        @NonNull
        public abstract DirectoryProperty getIncrementalDirForSplit();

        @NonNull
        public abstract Property<SerializableInputChanges> getDexFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getAssetsFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getJniFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getJavaResourceFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getAppMetadataFiles();

        @NonNull
        public abstract Property<Artifact<Directory>> getManifestType();

        @Optional
        @NonNull
        protected abstract Property<SigningConfigProviderParams> getSigningConfigData();

        @NonNull
        public abstract RegularFileProperty getSigningConfigVersionsFile();

        @NonNull
        public abstract SetProperty<String> getAbiFilters();

        @NonNull
        public abstract ListProperty<File> getJniFolders();

        @NonNull
        public abstract DirectoryProperty getManifestDirectory();

        @NonNull
        public abstract ListProperty<String> getAaptOptionsNoCompress();

        @NonNull
        public abstract Property<Boolean> getJniLibsUseLegacyPackaging();

        @NonNull
        public abstract Property<Boolean> getDexUseLegacyPackaging();

        @Optional
        @NonNull
        public abstract Property<String> getCreatedBy();

        @NonNull
        public abstract Property<Integer> getMinSdkVersion();

        @NonNull
        public abstract Property<Boolean> getIsDebuggableBuild();

        @NonNull
        public abstract Property<Boolean> getIsDeterministicEntryOrder();

        @NonNull
        public abstract Property<Boolean> getIsJniDebuggableBuild();

        @NonNull
        public abstract Property<IncrementalPackagerBuilder.BuildType> getPackagerMode();

        @Optional
        public abstract RegularFileProperty getDependencyDataFile();

        @Optional
        public abstract Property<SerializableInputChanges> getMergedArtProfile();

        @Optional
        public abstract Property<SerializableInputChanges> getMergedArtProfileMetadata();

        @Optional
        public abstract Property<SerializableInputChanges> getVersionControlInfoFile();
    }

    /**
     * Copy the input zip file (probably a Zip64) content into a new Zip in the destination folder
     * stripping out all .class files.
     *
     * @param destinationFolder the destination folder to use, the output jar will have the same
     *     name as the input zip file.
     * @param zip64File the input zip file.
     * @return the path to the stripped Zip file.
     * @throws IOException if the copying failed.
     */
    @VisibleForTesting
    static File copyJavaResourcesOnly(File destinationFolder, File zip64File) throws IOException {
        File cacheDir = new File(destinationFolder, ZIP_64_COPY_DIR);
        File copiedZip = new File(cacheDir, zip64File.getName());
        FileUtils.mkdirs(copiedZip.getParentFile());

        try (ZipFile inFile = new ZipFile(zip64File);
                ZipOutputStream outFile =
                        new ZipOutputStream(
                                new BufferedOutputStream(new FileOutputStream(copiedZip)))) {

            Enumeration<? extends ZipEntry> entries = inFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.getName().endsWith(SdkConstants.DOT_CLASS)
                        && ZipEntryUtils.isValidZipEntryName(zipEntry)) {
                    outFile.putNextEntry(new ZipEntry(zipEntry.getName()));
                    try {
                        ByteStreams.copy(
                                new BufferedInputStream(inFile.getInputStream(zipEntry)), outFile);
                    } finally {
                        outFile.closeEntry();
                    }
                }
            }
        }
        return copiedZip;
    }

    /**
     * Packages the application incrementally.
     *
     * @param outputFile expected output package file
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAssets incremental assets
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @param changedAppMetadata incremental app metadata
     * @param changedVersionControlInfo incremental version control info
     * @throws IOException failed to package the APK
     */
    private static void doTask(
            @NonNull File incrementalDirForSplit,
            @NonNull File outputFile,
            @NonNull KeyedFileCache cache,
            @NonNull BuiltArtifactsImpl manifestOutputs,
            @NonNull Map<RelativeFile, FileStatus> changedDex,
            @NonNull Map<RelativeFile, FileStatus> changedJavaResources,
            @NonNull Collection<SerializableChange> changedAssets,
            @NonNull Map<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull Map<RelativeFile, FileStatus> changedNLibs,
            @NonNull Collection<SerializableChange> changedAppMetadata,
            @NonNull Collection<SerializableChange> artProfile,
            @NonNull Collection<SerializableChange> artProfileMetadata,
            @NonNull Collection<SerializableChange> changedVersionControlInfo,
            @NonNull SplitterParams params)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        BuiltArtifact manifestForSplit = params.getOutputHandler().get().extractArtifactForSplit(
                manifestOutputs,
                params.getVariantOutput().get().getVariantOutputConfiguration()
        );

        if (manifestForSplit == null) {
            throw new RuntimeException(
                    "Found a .ap_ for split "
                            + params.getVariantOutput().get()
                            + " but no "
                            + params.getManifestType().get()
                            + " associated manifest file");
        }
        FileUtils.mkdirs(outputFile.getParentFile());

        // In execution phase, so can parse the manifest.
        ManifestData manifestData =
                ManifestDataKt.parseManifest(
                        new File(manifestForSplit.getOutputFile()),
                        true,
                        () -> true,
                        MANIFEST_DATA_ISSUE_REPORTER);

        NativeLibrariesPackagingMode nativeLibsPackagingMode =
                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                        manifestData.getExtractNativeLibs());
        // Warn if params.getJniLibsUseLegacyPackaging() is not compatible with
        // nativeLibsPackagingMode. We currently fall back to what's specified in the manifest, but
        // in future versions of AGP, we should use what's specified via
        // params.getJniLibsUseLegacyPackaging().
        LoggerWrapper logger = new LoggerWrapper(Logging.getLogger(PackageAndroidArtifact.class));
        if (params.getJniLibsUseLegacyPackaging().get()) {
            // TODO (b/149770867) make this an error in future AGP versions.
            if (nativeLibsPackagingMode == NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED) {
                logger.warning(
                        "PackagingOptions.jniLibs.useLegacyPackaging should be set to false "
                                + "because android:extractNativeLibs is set to \"false\" in "
                                + "AndroidManifest.xml. Avoid setting "
                                + "android:extractNativeLibs=\"false\" explicitly in "
                                + "AndroidManifest.xml, and instead set "
                                + "android.packagingOptions.jniLibs.useLegacyPackaging to false in "
                                + "the build.gradle file.");
            }
        } else {
            if (nativeLibsPackagingMode == NativeLibrariesPackagingMode.COMPRESSED) {
                logger.warning(
                        "PackagingOptions.jniLibs.useLegacyPackaging should be set to true "
                                + "because android:extractNativeLibs is set to \"true\" in "
                                + "AndroidManifest.xml.");
            }
        }

        Boolean useEmbeddedDex = manifestData.getUseEmbeddedDex();
        DexPackagingMode dexPackagingMode =
                PackagingUtils.getDexPackagingMode(
                        useEmbeddedDex, params.getDexUseLegacyPackaging().get());
        if (params.getDexUseLegacyPackaging().get() && Boolean.TRUE.equals(useEmbeddedDex)) {
            // TODO (b/149770867) make this an error in future AGP versions.
            logger.warning(
                    "PackagingOptions.dex.useLegacyPackaging should be set to false because "
                            + "android:useEmbeddedDex is set to \"true\" in AndroidManifest.xml.");
        }

        byte[] dependencyData =
                params.getDependencyDataFile().isPresent()
                        ? Files.readAllBytes(
                                params.getDependencyDataFile().get().getAsFile().toPath())
                        : null;

        try (IncrementalPackager packager =
                new IncrementalPackagerBuilder(params.getPackagerMode().get())
                        .withOutputFile(outputFile)
                        .withSigning(
                                params.getSigningConfigData().get().resolve(),
                                SigningConfigUtils.loadSigningConfigVersions(
                                        params.getSigningConfigVersionsFile().get().getAsFile()),
                                params.getMinSdkVersion().get(),
                                dependencyData)
                        .withCreatedBy(params.getCreatedBy().get())
                        .withNativeLibraryPackagingMode(nativeLibsPackagingMode)
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                        params.getAaptOptionsNoCompress().get(),
                                        nativeLibsPackagingMode,
                                        dexPackagingMode))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withDebuggableBuild(params.getIsDebuggableBuild().get())
                        .withDeterministicEntryOrder(params.getIsDeterministicEntryOrder().get())
                        .withAcceptedAbis(getAcceptedAbis(params))
                        .withJniDebuggableBuild(params.getIsJniDebuggableBuild().get())
                        .withChangedDexFiles(changedDex)
                        .withChangedJavaResources(changedJavaResources)
                        .withChangedAssets(changedAssets)
                        .withChangedAndroidResources(changedAndroidResources)
                        .withChangedNativeLibs(changedNLibs)
                        .withChangedAppMetadata(changedAppMetadata)
                        .withChangedArtProfile(artProfile)
                        .withChangedArtProfileMetadata(artProfileMetadata)
                        .withChangedVersionControlInfo(changedVersionControlInfo)
                        .build()) {
            packager.updateFiles();
        }
        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
                        changedDex.keySet().stream(),
                        Stream.concat(
                                changedJavaResources.keySet().stream(),
                                Stream.concat(
                                        changedAndroidResources.keySet().stream(),
                                        changedNLibs.keySet().stream())))
                .filter(it -> it.getType() == RelativeFile.Type.JAR)
                .map(RelativeFile::getBase)
                .distinct()
                .forEach(
                        (File f) -> {
                            try {
                                cache.add(f);
                            } catch (IOException e) {
                                throw new IOExceptionWrapper(e);
                            }
                        });
    }

    private static final IssueReporter MANIFEST_DATA_ISSUE_REPORTER = new IssueReporter() {
        @Override
        protected void reportIssue(
                @NonNull Type type,
                @NonNull Severity severity,
                @NonNull EvalIssueException exception) {
            if (severity == Severity.ERROR) {
                throw exception;
            }
        }

        @Override
        public boolean hasIssue(@NonNull Type type) {
            return false;
        }
    };

    /**
     * Calculates the accepted ABIs based on the given {@link SplitterParams}. Also checks that the
     * accepted ABIs are all available, and logs a warning if not.
     *
     * @param params the {@link SplitterParams}
     */
    private static Set<String> getAcceptedAbis(@NonNull SplitterParams params) {
        FilterConfiguration splitAbiFilter = VariantOutputConfigurationImplKt.getFilter(
                params.getVariantOutput().get().getVariantOutputConfiguration(),
                FilterConfiguration.FilterType.ABI);

        final Set<String> acceptedAbis =
                splitAbiFilter != null
                        ? ImmutableSet.of(splitAbiFilter.getIdentifier())
                        : ImmutableSet.copyOf(params.getAbiFilters().get());

        // After calculating acceptedAbis, we calculate availableAbis, which is the set of ABIs
        // present in params.jniFolders.
        Set<String> availableAbis = new HashSet<>();
        for (File jniFolder : params.getJniFolders().get()) {
            File[] libDirs = jniFolder.listFiles();
            if (libDirs == null) {
                continue;
            }
            for (File libDir : libDirs) {
                File[] abiDirs = libDir.listFiles();
                if (!"lib".equals(libDir.getName()) || abiDirs == null) {
                    continue;
                }
                for (File abiDir : abiDirs) {
                    File[] soFiles = abiDir.listFiles();
                    if (soFiles != null && soFiles.length > 0) {
                        availableAbis.add(abiDir.getName());
                    }
                }
            }
        }
        // if acceptedAbis and availableAbis both aren't empty, we make sure that the ABIs in
        // acceptedAbis are also in availableAbis, or else we log a warning.
        if (!acceptedAbis.isEmpty() && !availableAbis.isEmpty()) {
            Set<String> missingAbis = Sets.difference(acceptedAbis, availableAbis);
            if (!missingAbis.isEmpty()) {
                LoggerWrapper logger =
                        new LoggerWrapper(Logging.getLogger(PackageAndroidArtifact.class));
                logger.warning(
                        String.format(
                                "There are no .so files available to package in the APK for %s.",
                                Joiner.on(", ")
                                        .join(
                                                missingAbis
                                                        .stream()
                                                        .sorted()
                                                        .collect(Collectors.toList()))));
            }
        }
        return acceptedAbis;
    }

    public abstract static class IncrementalSplitterRunnable
            implements WorkActionAdapter<SplitterParams> {

        @Inject
        public IncrementalSplitterRunnable(SplitterParams splitterParams) {}

        @Override
        public void doExecute() {
            SplitterParams params = getParameters();
            try {
                File incrementalDirForSplit = params.getIncrementalDirForSplit().getAsFile().get();

                File cacheDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
                if (!cacheDir.exists()) {
                    FileUtils.mkdirs(cacheDir);
                }

                // Build a file cache that uses the indexes in the roots.
                // This is to work nicely with classpath sensitivity
                // Mutable as we need to add to it for the zip64 workaround in getChangedJavaResources
                Map<File, String> cacheKeyMap = new HashMap<>();
                addCacheKeys(cacheKeyMap, "dex", params.getDexFiles().get());
                addCacheKeys(cacheKeyMap, "javaResources", params.getJavaResourceFiles().get());
                addCacheKeys(cacheKeyMap, "assets", params.getAssetsFiles().get());
                cacheKeyMap.put(
                        params.getAndroidResourcesFile().get().getAsFile(), "androidResources");
                addCacheKeys(cacheKeyMap, "jniLibs", params.getJniFiles().get());

                KeyedFileCache cache =
                        new KeyedFileCache(
                                cacheDir, file -> Objects.requireNonNull(cacheKeyMap.get(file)));

                Set<Runnable> cacheUpdates = new HashSet<>();

                Map<RelativeFile, FileStatus> changedDexFiles =
                        IncrementalChanges.classpathToRelativeFileSet(
                                params.getDexFiles().get(), cache, cacheUpdates);

                Map<RelativeFile, FileStatus> changedJavaResources =
                        getChangedJavaResources(params, cacheKeyMap, cache, cacheUpdates);

                final Map<RelativeFile, FileStatus> changedAndroidResources;
                if (params.getAndroidResourcesChanged().get()) {
                    changedAndroidResources =
                            IncrementalRelativeFileSets.fromZip(
                                    new ZipCentralDirectory(
                                            params.getAndroidResourcesFile().get().getAsFile()),
                                    cache,
                                    cacheUpdates);
                } else {
                    changedAndroidResources = ImmutableMap.of();
                }

                Map<RelativeFile, FileStatus> changedJniLibs =
                        IncrementalChanges.classpathToRelativeFileSet(
                                params.getJniFiles().get(), cache, cacheUpdates);

                BuiltArtifactsImpl manifestOutputs =
                        new BuiltArtifactsLoaderImpl().load(params.getManifestDirectory());

                doTask(
                        incrementalDirForSplit,
                        params.getOutputFile().get().getAsFile(),
                        cache,
                        manifestOutputs,
                        changedDexFiles,
                        changedJavaResources,
                        params.getAssetsFiles().get().getChanges(),
                        changedAndroidResources,
                        changedJniLibs,
                        params.getAppMetadataFiles().get().getChanges(),
                        params.getMergedArtProfile().get().getChanges(),
                        params.getMergedArtProfileMetadata().get().getChanges(),
                        params.getVersionControlInfoFile().get().getChanges(),
                        params);

                /*
                 * Update the cache
                 */
                cacheUpdates.forEach(Runnable::run);

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                if (params.getPackagerMode().get() == IncrementalPackagerBuilder.BuildType.CLEAN) {
                    PackageApplication.recordMetrics(
                            params.getProjectPath().get(),
                            params.getOutputFile().get().getAsFile(),
                            params.getAndroidResourcesFile().get().getAsFile(),
                            params.getAnalyticsService().get());
                }
            }
        }

        private static void addCacheKeys(
                Map<File, String> builder, String prefix, SerializableInputChanges changes) {
            List<File> roots = changes.getRoots();
            for (int i = 0; i < roots.size(); i++) {
                builder.put(roots.get(i), prefix + i);
            }
        }

        /**
         * An adapted version of {@link
         * IncrementalChanges#classpathToRelativeFileSet(SerializableInputChanges, KeyedFileCache,
         * Set)} that handles zip64 support within this task.
         */
        @NonNull
        private static Map<RelativeFile, FileStatus> getChangedJavaResources(
                SplitterParams params,
                Map<File, String> cacheKeyMap,
                KeyedFileCache cache,
                Set<Runnable> cacheUpdates)
                throws IOException {
            Map<RelativeFile, FileStatus> changedJavaResources = new HashMap<>();
            for (SerializableChange change : params.getJavaResourceFiles().get().getChanges()) {
                if (change.getNormalizedPath().isEmpty()) {
                    try {
                        IncrementalChanges.addZipChanges(
                                changedJavaResources, change.getFile(), cache, cacheUpdates);
                    } catch (Zip64NotSupportedException e) {
                        File nonZip64 =
                                copyJavaResourcesOnly(
                                        params.getIncrementalFolder().get().getAsFile(),
                                        change.getFile());
                        // Map the copied file to the same cache key.
                        cacheKeyMap.put(nonZip64, cacheKeyMap.get(change.getFile()));
                        IncrementalChanges.addZipChanges(
                                changedJavaResources, nonZip64, cache, cacheUpdates);
                    }
                } else {
                    IncrementalChanges.addFileChange(changedJavaResources, change);
                }
            }
            return Collections.unmodifiableMap(changedJavaResources);
        }
    }

    // ----- CreationAction -----

    public abstract static class CreationAction<TaskT extends PackageAndroidArtifact>
            extends VariantTaskCreationAction<TaskT, ApkCreationConfig> {

        @NonNull protected final Provider<Directory> manifests;
        @NonNull private final Artifact<Directory> manifestType;

        public CreationAction(
                @NonNull ApkCreationConfig creationConfig,
                @NonNull Provider<Directory> manifests,
                @NonNull Artifact<Directory> manifestType) {
            super(creationConfig);
            this.manifests = manifests;
            this.manifestType = manifestType;
        }

        @Override
        public void configure(
                @NonNull final TaskT packageAndroidArtifact) {
            super.configure(packageAndroidArtifact);

            packageAndroidArtifact
                    .getMinSdkVersion()
                    .set(
                            packageAndroidArtifact
                                    .getProject()
                                    .provider(() -> creationConfig.getMinSdk().getApiLevel()));
            packageAndroidArtifact.getMinSdkVersion().disallowChanges();
            packageAndroidArtifact.getApplicationId().set(creationConfig.getApplicationId());
            packageAndroidArtifact.getApplicationId().disallowChanges();

            packageAndroidArtifact.getOutputsHandler().set(
                    MultiOutputHandler.Companion.create(creationConfig)
            );
            packageAndroidArtifact.getOutputsHandler().disallowChanges();

            packageAndroidArtifact
                    .getIncrementalFolder()
                    .set(
                            new File(
                                    creationConfig
                                            .getPaths()
                                            .getIncrementalDir(packageAndroidArtifact.getName()),
                                    "tmp"));

            if (creationConfig.getAndroidResourcesCreationConfig() != null) {
                packageAndroidArtifact
                        .getAaptOptionsNoCompress()
                        .set(
                                creationConfig
                                        .getAndroidResourcesCreationConfig()
                                        .getAndroidResources()
                                        .getNoCompress());
            } else {
                packageAndroidArtifact.getAaptOptionsNoCompress().set(Collections.emptySet());
            }
            packageAndroidArtifact.getAaptOptionsNoCompress().disallowChanges();

            packageAndroidArtifact
                    .getJniLibsUseLegacyPackaging()
                    .set(creationConfig.getPackaging().getJniLibs().getUseLegacyPackaging());
            packageAndroidArtifact.getJniLibsUseLegacyPackaging().disallowChanges();

            packageAndroidArtifact
                    .getDexUseLegacyPackaging()
                    .set(creationConfig.getPackaging().getDex().getUseLegacyPackaging());
            packageAndroidArtifact.getDexUseLegacyPackaging().disallowChanges();

            packageAndroidArtifact.getManifests().set(manifests);

            packageAndroidArtifact.getDexFolders().from(getDexFolders(creationConfig));
            final String projectPath = packageAndroidArtifact.getProject().getPath();
            @Nullable
            FileCollection featureDexFolder = getFeatureDexFolder(creationConfig, projectPath);
            if (featureDexFolder != null) {
                packageAndroidArtifact.getFeatureDexFolder().from(featureDexFolder);
            }
            packageAndroidArtifact.getJavaResourceFiles().from(
                    creationConfig.getArtifacts().get(MERGED_JAVA_RES.INSTANCE));
            packageAndroidArtifact.getJavaResourceFiles().disallowChanges();
            @Nullable
            FileCollection featureJavaResources =
                    getFeatureJavaResources(creationConfig, projectPath);
            if (featureJavaResources != null) {
                packageAndroidArtifact.getFeatureJavaResourceFiles().from(featureJavaResources);
            }
            packageAndroidArtifact.getFeatureJavaResourceFiles().disallowChanges();

            ProjectOptions projectOptions = creationConfig.getServices().getProjectOptions();

            packageAndroidArtifact
                    .getIsDeterministicEntryOrder()
                    .set(isDeterministicEntryOrder(creationConfig));
            packageAndroidArtifact.getIsDeterministicEntryOrder().disallowChanges();

            if (creationConfig instanceof ApplicationCreationConfig) {
                creationConfig.getArtifacts().setTaskInputToFinalProduct(
                        InternalArtifactType.APP_METADATA.INSTANCE,
                        packageAndroidArtifact.getAppMetadata());
                if (projectOptions.get(BooleanOption.ENABLE_VCS_INFO)) {
                    creationConfig
                            .getArtifacts()
                            .setTaskInputToFinalProduct(
                                    InternalArtifactType.VERSION_CONTROL_INFO_FILE.INSTANCE,
                                    packageAndroidArtifact.getVersionControlInfoFile());
                }
                if (isDeterministic(creationConfig)) {
                    packageAndroidArtifact
                            .getAllInputFilesWithNameOnlyPathSensitivity()
                            .from(packageAndroidArtifact.getAppMetadata());
                    if (projectOptions.get(BooleanOption.ENABLE_VCS_INFO)) {
                        packageAndroidArtifact
                                .getAllInputFilesWithNameOnlyPathSensitivity()
                                .from(packageAndroidArtifact.getVersionControlInfoFile());
                    }
                }

                if (!creationConfig.getDebuggable()) {
                    creationConfig
                            .getArtifacts()
                            .setTaskInputToFinalProduct(
                                    InternalArtifactType.BINARY_ART_PROFILE.INSTANCE,
                                    packageAndroidArtifact.getMergedArtProfile());
                    if (isDeterministic(creationConfig)) {
                        packageAndroidArtifact
                                .getAllInputFilesWithRelativePathSensitivity()
                                .from(packageAndroidArtifact.getMergedArtProfile());
                    }

                    creationConfig
                            .getArtifacts()
                            .setTaskInputToFinalProduct(
                                    InternalArtifactType.BINARY_ART_PROFILE_METADATA.INSTANCE,
                                    packageAndroidArtifact.getMergedArtProfileMetadata());
                    if (isDeterministic(creationConfig)) {
                        packageAndroidArtifact
                                .getAllInputFilesWithRelativePathSensitivity()
                                .from(packageAndroidArtifact.getMergedArtProfileMetadata());
                    }
                }
            }

            packageAndroidArtifact
                    .getAssets()
                    .set(creationConfig.getArtifacts().get(COMPRESSED_ASSETS.INSTANCE));
            boolean isJniDebuggable;
            if (creationConfig.getNativeBuildCreationConfig() != null) {
                isJniDebuggable = creationConfig.getNativeBuildCreationConfig().isJniDebuggable();
            } else {
                isJniDebuggable = false;
            }
            packageAndroidArtifact.setJniDebugBuild(isJniDebuggable);
            packageAndroidArtifact.getDebugBuild().set(creationConfig.getDebuggable());
            packageAndroidArtifact.getDebugBuild().disallowChanges();

            packageAndroidArtifact
                    .getProjectBaseName()
                    .set(creationConfig.getServices().getProjectInfo().getProjectBaseName());
            packageAndroidArtifact.getProjectBaseName().disallowChanges();
            packageAndroidArtifact.manifestType = manifestType;
            if (creationConfig instanceof KmpComponentCreationConfig) {
                packageAndroidArtifact.buildTargetAbi = null;
            } else {
                packageAndroidArtifact.buildTargetAbi =
                        creationConfig.getGlobal().getSplits().getAbi().isEnable()
                                ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                                : null;
            }
            if (creationConfig.getComponentType().isDynamicFeature()) {
                packageAndroidArtifact
                        .getBaseModuleMetadata()
                        .from(
                                creationConfig
                                        .getVariantDependencies()
                                        .getArtifactFileCollection(
                                                COMPILE_CLASSPATH, PROJECT, BASE_MODULE_METADATA));
            }
            packageAndroidArtifact.getBaseModuleMetadata().disallowChanges();
            final Set<String> supportedAbis;

            if (creationConfig.getNativeBuildCreationConfig() != null) {
                supportedAbis = creationConfig.getNativeBuildCreationConfig().getSupportedAbis();
            } else {
                supportedAbis = Collections.emptySet();
            }
            if (!supportedAbis.isEmpty()) {
                // If the build author has set the supported Abis that is respected
                packageAndroidArtifact.getAbiFilters().set(supportedAbis);
            } else {
                // Otherwise, use the injected Abis if set.
                packageAndroidArtifact
                        .getAbiFilters()
                        .set(
                                projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                                        ? firstValidInjectedAbi(
                                                projectOptions.get(
                                                        StringOption.IDE_BUILD_TARGET_ABI))
                                        : ImmutableSet.of());
            }
            packageAndroidArtifact.getAbiFilters().disallowChanges();

            packageAndroidArtifact.getCreatedBy().set(creationConfig.getGlobal().getCreatedBy());

            if (creationConfig.getComponentType().isBaseModule()
                    && creationConfig
                            .getServices()
                            .getProjectOptions()
                            .get(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS)) {
                creationConfig
                        .getArtifacts()
                        .setTaskInputToFinalProduct(
                                InternalArtifactType.SDK_DEPENDENCY_DATA.INSTANCE,
                                packageAndroidArtifact.getDependencyDataFile());
            }

            // If we're in a dynamic feature, we use FEATURE_SIGNING_CONFIG_VERSIONS, published from
            // the base. Otherwise, we use the SIGNING_CONFIG_VERSIONS internal artifact.
            if (creationConfig.getComponentType().isDynamicFeature()
                    || (creationConfig instanceof TestComponentCreationConfig
                            && ((TestComponentCreationConfig) creationConfig)
                                    .getMainVariant()
                                    .getComponentType()
                                    .isDynamicFeature())) {
                packageAndroidArtifact
                        .getSigningConfigVersions()
                        .from(
                                creationConfig
                                        .getVariantDependencies()
                                        .getArtifactFileCollection(
                                                COMPILE_CLASSPATH,
                                                PROJECT,
                                                FEATURE_SIGNING_CONFIG_VERSIONS));
            } else {
                packageAndroidArtifact
                        .getSigningConfigVersions()
                        .from(
                                creationConfig
                                        .getArtifacts()
                                        .get(SIGNING_CONFIG_VERSIONS.INSTANCE));
            }
            packageAndroidArtifact.getSigningConfigVersions().disallowChanges();

            finalConfigure(packageAndroidArtifact);

            // To produce a deterministic APK, we must force the task to run non-incrementally by
            // adding all of the incremental file inputs again as non-incremental inputs. This is a
            // workaround for https://github.com/gradle/gradle/issues/16976.
            if (isDeterministic(creationConfig)) {
                packageAndroidArtifact
                        .getAllInputFilesWithRelativePathSensitivity()
                        .from(
                                packageAndroidArtifact.getAssets(),
                                packageAndroidArtifact.getDexFolders(),
                                packageAndroidArtifact.getFeatureDexFolder(),
                                packageAndroidArtifact.getManifests(),
                                packageAndroidArtifact.getResourceFiles());
                packageAndroidArtifact
                        .getAllClasspathInputFiles()
                        .from(
                                packageAndroidArtifact.getFeatureJavaResourceFiles(),
                                packageAndroidArtifact.getJavaResourceFiles(),
                                packageAndroidArtifact.getJniFolders());
            }
            packageAndroidArtifact.getAllInputFilesWithRelativePathSensitivity().disallowChanges();
            packageAndroidArtifact.getAllInputFilesWithNameOnlyPathSensitivity().disallowChanges();
            packageAndroidArtifact.getAllClasspathInputFiles().disallowChanges();
        }

        protected void finalConfigure(TaskT task) {
            task.getJniFolders().from(PerModuleBundleTaskKt.getNativeLibsFiles(creationConfig));

            task.setSigningConfigData(SigningConfigDataProvider.create(creationConfig));
        }

        @NonNull
        public static FileCollection getDexFolders(@NonNull ApkCreationConfig creationConfig) {
            ArtifactsImpl artifacts = creationConfig.getArtifacts();
            if (creationConfig instanceof ApplicationCreationConfig
                    && ((ApplicationCreationConfig) creationConfig).getConsumesFeatureJars()) {
                return creationConfig
                        .getServices()
                        .fileCollection(
                                artifacts.get(InternalArtifactType.BASE_DEX.INSTANCE),
                                getDesugarLibDexIfExists(creationConfig),
                                getGlobalSyntheticsDex(creationConfig));
            } else {
                return creationConfig
                        .getServices()
                        .fileCollection(
                                artifacts.getAll(InternalMultipleArtifactType.DEX.INSTANCE),
                                getDesugarLibDexIfExists(creationConfig),
                                getGlobalSyntheticsDex(creationConfig));
            }
        }

        @Nullable
        public static FileCollection getFeatureDexFolder(
                @NonNull ApkCreationConfig creationConfig, @NonNull String projectPath) {
            if (!creationConfig.getComponentType().isDynamicFeature()) {
                return null;
            }
            return creationConfig
                    .getVariantDependencies()
                    .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            PROJECT,
                            AndroidArtifacts.ArtifactType.FEATURE_DEX,
                            new AndroidAttributes(new Pair<>(MODULE_PATH, projectPath)));
        }

        @Nullable
        public FileCollection getFeatureJavaResources(
                @NonNull ApkCreationConfig creationConfig, @NonNull String projectPath) {
            if (!creationConfig.getComponentType().isDynamicFeature()) {
                return null;
            }
            return creationConfig
                    .getVariantDependencies()
                    .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            PROJECT,
                            AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_JAVA_RES,
                            new AndroidAttributes(new Pair<>(MODULE_PATH, projectPath)));
        }

        @NonNull
        private static Set<String> firstValidInjectedAbi(@Nullable String abis) {
            if (abis == null) {
                return ImmutableSet.of();
            }
            Set<String> allowedAbis =
                    Abi.getDefaultValues().stream().map(Abi::getTag).collect(Collectors.toSet());
            java.util.Optional<String> firstValidAbi =
                    Arrays.stream(abis.split(","))
                            .map(String::trim)
                            .filter(allowedAbis::contains)
                            .findFirst();
            return firstValidAbi.map(ImmutableSet::of).orElseGet(ImmutableSet::of);
        }

        @NonNull
        private static FileCollection getDesugarLibDexIfExists(
                @NonNull ApkCreationConfig creationConfig) {
            if (!creationConfig.getDexingCreationConfig().getShouldPackageDesugarLibDex()) {
                return creationConfig.getServices().fileCollection();
            }
            return creationConfig
                    .getServices()
                    .fileCollection(
                            creationConfig
                                    .getArtifacts()
                                    .get(InternalArtifactType.DESUGAR_LIB_DEX.INSTANCE));
        }

        @NonNull
        private static FileCollection getGlobalSyntheticsDex(
                @NonNull ApkCreationConfig creationConfig) {
            // No need to collect global synthetics in three cases:
            //   1. Global synthetics generation is disabled
            //   2. R8 is used and global synthetics are not generated
            //   3. In mono dex and legacy multidex where global synthetics are already merged into
            //      dex files in dex merging tasks
            if (!creationConfig.getEnableGlobalSynthetics()
                    || creationConfig.getDexingCreationConfig().getDexingType()
                            != DexingType.NATIVE_MULTIDEX
                    || creationConfig.getOptimizationCreationConfig().getMinifiedEnabled()) {
                return creationConfig.getServices().fileCollection();
            }
            return creationConfig
                    .getServices()
                    .fileCollection(
                            creationConfig
                                    .getArtifacts()
                                    .get(InternalArtifactType.GLOBAL_SYNTHETICS_DEX.INSTANCE));
        }

        // We always write new APK entries in a deterministic order except for debug builds invoked
        // from the IDE. Writing new APK entries in a deterministic order will produce deterministic
        // APKs for clean builds, but not incremental builds.
        private static boolean isDeterministicEntryOrder(ApkCreationConfig creationConfig) {
            ProjectOptions projectOptions = creationConfig.getServices().getProjectOptions();
            return isDeterministic(creationConfig)
                    || !projectOptions.get(BooleanOption.IDE_INVOKED_FROM_IDE);
        }

        // We produce deterministic APKs for non-debuggable builds or when
        // android.experimental.forceDeterministicApk is true
        private static boolean isDeterministic(ApkCreationConfig creationConfig) {
            ProjectOptions projectOptions = creationConfig.getServices().getProjectOptions();
            return !creationConfig.getDebuggable()
                    || projectOptions.get(BooleanOption.FORCE_DETERMINISTIC_APK);
        }
    }
}
