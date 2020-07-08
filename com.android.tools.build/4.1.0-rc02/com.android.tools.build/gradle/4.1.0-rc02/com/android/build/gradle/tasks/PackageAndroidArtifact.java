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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPRESSED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_JAVA_RES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.Artifact;
import com.android.build.api.artifact.ArtifactTransformationRequest;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactImpl;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.api.variant.impl.VariantOutputListKt;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType;
import com.android.build.gradle.internal.signing.SigningConfigProvider;
import com.android.build.gradle.internal.signing.SigningConfigProviderParams;
import com.android.build.gradle.internal.tasks.ModuleMetadata;
import com.android.build.gradle.internal.tasks.NewIncrementalTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTaskKt;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.files.IncrementalChanges;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.KeyedFileCache;
import com.android.builder.files.RelativeFile;
import com.android.builder.files.SerializableChange;
import com.android.builder.files.SerializableInputChanges;
import com.android.builder.files.ZipCentralDirectory;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.CodeShrinker;
import com.android.builder.packaging.PackagingUtils;
import com.android.builder.utils.ZipEntryUtils;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper;
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
import java.io.Serializable;
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
import kotlin.jvm.functions.Function3;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileType;
import org.gradle.api.file.RegularFile;
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
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/** Abstract task to package an Android artifact. */
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
    public abstract ConfigurableFileCollection getAppMetadata();

    @OutputDirectory
    public abstract DirectoryProperty getIncrementalFolder();

    protected Artifact<Directory> manifestType;

    @Input
    public String getManifestTypeName() {
        return manifestType.name();
    }

    /**
     * List of folders and/or jars that contain the merged java resources.
     *
     * <p>FileCollection because of the legacy Transform API.
     */
    @Classpath
    @Incremental
    public abstract ConfigurableFileCollection getJavaResourceFiles();

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
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDependencyDataFile();

    @Input
    public abstract Property<String> getCreatedBy();

    private boolean jniDebugBuild;

    private SigningConfigProvider signingConfig;

    @NonNull
    @Input
    public abstract ListProperty<String> getAaptOptionsNoCompress();

    protected String projectBaseName;

    @Nullable protected String buildTargetAbi;

    @Nullable protected String buildTargetDensity;

    @Input
    public String getProjectBaseName() {
        return projectBaseName;
    }

    @Nullable protected Integer targetApi;

    @Nullable
    @Input
    @Optional
    public Integer getTargetApi() {
        return targetApi;
    }

    /** Desired output format. */
    protected IncrementalPackagerBuilder.ApkFormat apkFormat;

    @Input
    public String getApkFormat() {
        return apkFormat.name();
    }

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

    @Nested
    public SigningConfigProvider getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(SigningConfigProvider signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Input
    public abstract Property<Integer> getMinSdkVersion();

    @Input
    public abstract Property<String> getApplicationId();

    /*
     * We don't really use this. But this forces a full build if the native libraries or dex
     * packaging mode changes.
     */
    @Input
    public List<String> getNativeLibrariesAndDexPackagingModeName() {
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
                                String extractNativeLibs =
                                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                                        parser)
                                                .toString();
                                listBuilder.add(extractNativeLibs);
                                String useEmbeddedDex =
                                        PackagingUtils.getUseEmbeddedDex(parser).toString();
                                listBuilder.add(useEmbeddedDex);
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

    @Input
    @Optional
    @Nullable
    public String getBuildTargetDensity() {
        return buildTargetDensity;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @org.gradle.api.tasks.OutputFile
    public abstract RegularFileProperty getIdeModelOutputFile();

    @Nested
    public abstract ListProperty<VariantOutputImpl> getVariantOutputs();

    @Input
    public abstract ArtifactTransformationRequest getTransformationRequest();

    private static File computeBuildOutputFile(
            VariantOutputImpl variantOutput, File outputDirectory) {
        return new File(outputDirectory, variantOutput.getOutputFileName().get());
    }

    protected ApkCreatorType apkCreatorType;

    @NonNull
    @Input
    public ApkCreatorType getApkCreatorType() {
        return apkCreatorType;
    }

    @Internal
    public abstract Property<String> getProjectPath();

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
                        SplitterParams.class,
                        configure(changedResourceFiles, changes));
    }

    private Function3<BuiltArtifact, Directory, SplitterParams, File> configure(
            @NonNull HashSet<File> changedResourceFiles, @NonNull InputChanges changes) {

        return (builtArtifact, directory, parameter) -> {
            VariantOutputImpl variantOutput =
                    VariantOutputListKt.getVariantOutput(
                            getVariantOutputs().get(),
                            ((BuiltArtifactImpl) builtArtifact).getVariantOutputConfiguration());
            File outputFile =
                    computeBuildOutputFile(variantOutput, getOutputDirectory().get().getAsFile());
            parameter.getVariantOutput().set(variantOutput.toSerializedForm());
            parameter.getAndroidResourcesFile().set(new File(builtArtifact.getOutputFile()));
            parameter
                    .getAndroidResourcesChanged()
                    .set(changedResourceFiles.contains(new File(builtArtifact.getOutputFile())));
            parameter.getProjectPath().set(getProjectPath().get());
            parameter.getApkCreatorType().set(apkCreatorType);
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
                // We reach this code if we're in a feature module and
                // minification is enabled in the
                // base module. In this case, we want to use the classes.dex
                // file from the base
                // module's DexSplitterTask.
                parameter
                        .getDexFiles()
                        .set(
                                IncrementalChangesUtils.getChangesInSerializableForm(
                                        changes, getFeatureDexFolder()));
                parameter
                        .getJavaResourceFiles()
                        .set(new SerializableInputChanges(ImmutableList.of(), ImmutableSet.of()));
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
            parameter.getManifestType().set(manifestType);
            parameter.getApkFormat().set(apkFormat);
            parameter.getSigningConfig().set(signingConfig.convertToParams());

            if (getAppMetadata().isEmpty()) {
                parameter.getAbiFilters().set(getAbiFilters());
            } else {
                // Dynamic feature
                List<String> appAbiFilters;
                try {
                    appAbiFilters =
                            ModuleMetadata.load(getAppMetadata().getSingleFile()).getAbiFilters();
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
            parameter.getCreatedBy().set(getCreatedBy().get());
            parameter.getMinSdkVersion().set(getMinSdkVersion().get());

            parameter.getIsDebuggableBuild().set(getDebugBuild().get());
            parameter.getIsJniDebuggableBuild().set(getJniDebugBuild());
            parameter.getTargetApi().set(getTargetApi());
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

    public abstract static class SplitterParams implements WorkParameters, Serializable {
        @NonNull
        public abstract Property<VariantOutputImpl.SerializedForm> getVariantOutput();

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
        public abstract Property<SerializableInputChanges> getDexFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getAssetsFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getJniFiles();

        @NonNull
        public abstract Property<SerializableInputChanges> getJavaResourceFiles();

        @NonNull
        public abstract Property<Artifact<Directory>> getManifestType();

        @NonNull
        public abstract Property<IncrementalPackagerBuilder.ApkFormat> getApkFormat();

        @Optional
        @NonNull
        protected abstract Property<SigningConfigProviderParams> getSigningConfig();

        @NonNull
        public abstract SetProperty<String> getAbiFilters();

        @NonNull
        public abstract ListProperty<File> getJniFolders();

        @NonNull
        public abstract DirectoryProperty getManifestDirectory();

        @Optional
        @NonNull
        public abstract ListProperty<String> getAaptOptionsNoCompress();

        @Optional
        @NonNull
        public abstract Property<String> getCreatedBy();

        @NonNull
        public abstract Property<Integer> getMinSdkVersion();

        @NonNull
        public abstract Property<Boolean> getIsDebuggableBuild();

        @NonNull
        public abstract Property<Boolean> getIsJniDebuggableBuild();

        @Optional
        public abstract Property<Integer> getTargetApi();

        @NonNull
        public abstract Property<IncrementalPackagerBuilder.BuildType> getPackagerMode();

        @NonNull
        public abstract Property<ApkCreatorType> getApkCreatorType();

        @Optional
        public abstract RegularFileProperty getDependencyDataFile();
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
            @NonNull SplitterParams params)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        // find the manifest file for this split.
        BuiltArtifact manifestForSplit =
                manifestOutputs.getBuiltArtifact(
                        params.getVariantOutput().get().getVariantOutputConfiguration());

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
        ManifestAttributeSupplier manifest =
                new DefaultManifestParser(
                        new File(manifestForSplit.getOutputFile()), () -> true, true, null);

        byte[] dependencyData =
                params.getDependencyDataFile().isPresent()
                        ? Files.readAllBytes(
                                params.getDependencyDataFile().get().getAsFile().toPath())
                        : null;

        try (IncrementalPackager packager =
                new IncrementalPackagerBuilder(
                                params.getApkFormat().get(), params.getPackagerMode().get())
                        .withOutputFile(outputFile)
                        .withSigning(
                                params.getSigningConfig().get().resolve(),
                                params.getMinSdkVersion().get(),
                                params.getTargetApi().getOrNull(),
                                dependencyData)
                        .withCreatedBy(params.getCreatedBy().get())
                        // TODO: allow extra metadata to be saved in the split scope to avoid
                        // reparsing
                        // these manifest files.
                        .withNativeLibraryPackagingMode(
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest))
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                        params.getAaptOptionsNoCompress().get(), manifest))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withDebuggableBuild(params.getIsDebuggableBuild().get())
                        .withAcceptedAbis(getAcceptedAbis(params))
                        .withJniDebuggableBuild(params.getIsJniDebuggableBuild().get())
                        .withApkCreatorType(params.getApkCreatorType().get())
                        .withChangedDexFiles(changedDex)
                        .withChangedJavaResources(changedJavaResources)
                        .withChangedAssets(changedAssets)
                        .withChangedAndroidResources(changedAndroidResources)
                        .withChangedNativeLibs(changedNLibs)
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

    /**
     * Calculates the accepted ABIs based on the given {@link SplitterParams}. Also checks that the
     * accepted ABIs are all available, and logs a warning if not.
     *
     * @param params the {@link SplitterParams}
     */
    private static Set<String> getAcceptedAbis(@NonNull SplitterParams params) {
        FilterConfiguration splitAbiFilter =
                params.getVariantOutput()
                        .get()
                        .getVariantOutputConfiguration()
                        .getFilter(FilterConfiguration.FilterType.ABI);
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

    public abstract static class IncrementalSplitterRunnable implements WorkAction<SplitterParams> {

        @Inject
        public IncrementalSplitterRunnable(SplitterParams splitterParams) {}

        @Override
        public void execute() {
            SplitterParams params = getParameters();
            try {
                File incrementalDirForSplit =
                        new File(
                                params.getIncrementalFolder().get().getAsFile(),
                                params.getVariantOutput().get().getFullName());

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
                            params.getAndroidResourcesFile().get().getAsFile());
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

        protected final Project project;
        @NonNull protected final Provider<Directory> manifests;
        protected boolean useResourceShrinker;
        @NonNull private final Artifact<Directory> manifestType;
        private final boolean packageCustomClassDependencies;

        public CreationAction(
                @NonNull ApkCreationConfig creationConfig,
                boolean useResourceShrinker,
                @NonNull Provider<Directory> manifests,
                @NonNull Artifact<Directory> manifestType,
                boolean packageCustomClassDependencies) {
            super(creationConfig);
            this.project = creationConfig.getGlobalScope().getProject();
            this.useResourceShrinker = useResourceShrinker;
            this.manifests = manifests;
            this.manifestType = manifestType;
            this.packageCustomClassDependencies = packageCustomClassDependencies;
        }

        @Override
        public void configure(
                @NonNull final TaskT packageAndroidArtifact) {
            super.configure(packageAndroidArtifact);

            GlobalScope globalScope = creationConfig.getGlobalScope();
            VariantDslInfo variantDslInfo = creationConfig.getVariantDslInfo();

            packageAndroidArtifact
                    .getMinSdkVersion()
                    .set(
                            globalScope
                                    .getProject()
                                    .provider(
                                            () -> creationConfig.getMinSdkVersion().getApiLevel()));
            packageAndroidArtifact.getMinSdkVersion().disallowChanges();
            packageAndroidArtifact.getApplicationId().set(creationConfig.getApplicationId());
            packageAndroidArtifact.getApplicationId().disallowChanges();

            packageAndroidArtifact.getVariantOutputs().set(creationConfig.getOutputs());

            packageAndroidArtifact
                    .getIncrementalFolder()
                    .set(
                            new File(
                                    creationConfig
                                            .getPaths()
                                            .getIncrementalDir(packageAndroidArtifact.getName()),
                                    "tmp"));

            packageAndroidArtifact
                    .getAaptOptionsNoCompress()
                    .set(
                            creationConfig
                                    .getGlobalScope()
                                    .getExtension()
                                    .getAaptOptions()
                                    .getNoCompress());
            packageAndroidArtifact.getAaptOptionsNoCompress().disallowChanges();

            packageAndroidArtifact.getManifests().set(manifests);

            packageAndroidArtifact.getDexFolders().from(getDexFolders(creationConfig));
            @Nullable FileCollection featureDexFolder = getFeatureDexFolder(creationConfig);
            if (featureDexFolder != null) {
                packageAndroidArtifact.getFeatureDexFolder().from(featureDexFolder);
            }
            packageAndroidArtifact.getJavaResourceFiles().from(getJavaResources(creationConfig));

            packageAndroidArtifact
                    .getAssets()
                    .set(creationConfig.getArtifacts().get(COMPRESSED_ASSETS.INSTANCE));
            packageAndroidArtifact.setJniDebugBuild(variantDslInfo.isJniDebuggable());
            packageAndroidArtifact
                    .getDebugBuild()
                    .set(creationConfig.getVariantDslInfo().isDebuggable());
            packageAndroidArtifact.getDebugBuild().disallowChanges();

            ProjectOptions projectOptions = creationConfig.getServices().getProjectOptions();
            packageAndroidArtifact.projectBaseName = globalScope.getProjectBaseName();
            packageAndroidArtifact.manifestType = manifestType;
            packageAndroidArtifact.buildTargetAbi =
                    globalScope.getExtension().getSplits().getAbi().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                            : null;
            if (creationConfig.getVariantType().isDynamicFeature()) {
                packageAndroidArtifact
                        .getAppMetadata()
                        .from(
                                creationConfig
                                        .getVariantDependencies()
                                        .getArtifactFileCollection(
                                                COMPILE_CLASSPATH, PROJECT, BASE_MODULE_METADATA));
            }
            packageAndroidArtifact.getAppMetadata().disallowChanges();
            if (!variantDslInfo.getSupportedAbis().isEmpty()) {
                // If the build author has set the supported Abis that is respected
                packageAndroidArtifact.getAbiFilters().set(variantDslInfo.getSupportedAbis());
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
            packageAndroidArtifact.buildTargetDensity =
                    globalScope.getExtension().getSplits().getDensity().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)
                            : null;

            packageAndroidArtifact.apkFormat =
                    projectOptions.get(BooleanOption.DEPLOYMENT_USES_DIRECTORY)
                            ? IncrementalPackagerBuilder.ApkFormat.DIRECTORY
                            : projectOptions.get(BooleanOption.DEPLOYMENT_PROVIDES_LIST_OF_CHANGES)
                                    ? IncrementalPackagerBuilder.ApkFormat.FILE_WITH_LIST_OF_CHANGES
                                    : IncrementalPackagerBuilder.ApkFormat.FILE;

            packageAndroidArtifact.targetApi =
                    projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API);

            packageAndroidArtifact.apkCreatorType =
                    creationConfig.getVariantScope().getApkCreatorType();

            packageAndroidArtifact.getCreatedBy().set(globalScope.getCreatedBy());

            if (creationConfig.getVariantType().isBaseModule()
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

            packageAndroidArtifact.getProjectPath().set(project.getPath());

            finalConfigure(packageAndroidArtifact);
        }

        protected void finalConfigure(TaskT task) {
            task.getJniFolders()
                    .from(
                            PerModuleBundleTaskKt.getNativeLibsFiles(
                                    creationConfig, packageCustomClassDependencies));

            task.setSigningConfig(SigningConfigProvider.create(creationConfig));
        }

        @NonNull
        public FileCollection getDexFolders(@NonNull ApkCreationConfig creationConfig) {
            ArtifactsImpl artifacts = creationConfig.getArtifacts();
            if (creationConfig.getVariantScope().consumesFeatureJars()) {
                return creationConfig
                        .getGlobalScope()
                        .getProject()
                        .files(artifacts.get(InternalArtifactType.BASE_DEX.INSTANCE))
                        .plus(getDesugarLibDexIfExists(creationConfig));
            } else {
                return project.files(artifacts.getAll(InternalMultipleArtifactType.DEX.INSTANCE))
                        .plus(getDesugarLibDexIfExists(creationConfig));
            }
        }

        @NonNull
        private FileCollection getJavaResources(@NonNull ApkCreationConfig creationConfig) {
            ArtifactsImpl artifacts = creationConfig.getArtifacts();

            if (creationConfig.getVariantScope().getCodeShrinker() == CodeShrinker.R8) {
                Provider<RegularFile> mergedJavaResProvider =
                        artifacts.get(SHRUNK_JAVA_RES.INSTANCE);
                return project.getLayout().files(mergedJavaResProvider);
            } else if (creationConfig.getVariantScope().getNeedsMergedJavaResStream()) {
                return creationConfig
                        .getTransformManager()
                        .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES);
            } else {
                Provider<RegularFile> mergedJavaResProvider =
                        artifacts.get(MERGED_JAVA_RES.INSTANCE);
                return project.getLayout().files(mergedJavaResProvider);
            }
        }

        @Nullable
        public FileCollection getFeatureDexFolder(@NonNull ApkCreationConfig creationConfig) {
            if (!creationConfig.getVariantType().isDynamicFeature()) {
                return null;
            }
            return creationConfig
                    .getVariantDependencies()
                    .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            PROJECT,
                            AndroidArtifacts.ArtifactType.FEATURE_DEX,
                            ImmutableMap.of(MODULE_PATH, project.getPath()));
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
        private FileCollection getDesugarLibDexIfExists(@NonNull ApkCreationConfig creationConfig) {
            if (!creationConfig.getShouldPackageDesugarLibDex()) {
                return creationConfig.getServices().fileCollection();
            }
            return creationConfig
                    .getServices()
                    .fileCollection(
                            creationConfig
                                    .getArtifacts()
                                    .get(InternalArtifactType.DESUGAR_LIB_DEX.INSTANCE));
        }
    }
}
