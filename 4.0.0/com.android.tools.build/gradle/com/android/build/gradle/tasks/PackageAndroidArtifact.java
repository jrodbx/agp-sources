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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_JAVA_RES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.VariantOutputConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactImpl;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.dsl.DslAdaptersKt;
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildElementsTransformParams;
import com.android.build.gradle.internal.scope.BuildElementsTransformRunnable;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MultipleArtifactType;
import com.android.build.gradle.internal.scope.SingleArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.signing.SigningConfigProvider;
import com.android.build.gradle.internal.signing.SigningConfigProviderParams;
import com.android.build.gradle.internal.tasks.NewIncrementalTask;
import com.android.build.gradle.internal.tasks.PerModuleBundleTaskKt;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.utils.DesugarLibUtils;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
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
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileType;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
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

/** Abstract task to package an Android artifact. */
public abstract class PackageAndroidArtifact extends NewIncrementalTask {

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getManifests();

    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceFiles();

    @Input
    @NonNull
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(@Nullable Set<String> abiFilters) {
        this.abiFilters = abiFilters != null ? abiFilters : ImmutableSet.of();
    }

    @OutputDirectory
    public abstract DirectoryProperty getIncrementalFolder();

    protected ArtifactType<Directory> manifestType;

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

    private Set<String> abiFilters;

    private boolean jniDebugBuild;

    private SigningConfigProvider signingConfig;

    @Nullable protected Collection<String> aaptOptionsNoCompress;

    protected List<String> apkFileNames = new ArrayList<>();

    protected String projectBaseName;

    @Nullable protected String buildTargetAbi;

    @Nullable protected String buildTargetDensity;

    @Nullable protected OutputFileProvider outputFileProvider;

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

    @NonNull
    @Input
    public Collection<String> getNoCompressExtensions() {
        return aaptOptionsNoCompress != null ? aaptOptionsNoCompress : Collections.emptyList();
    }

    interface OutputFileProvider {
        @NonNull
        File getOutputFile(@NonNull ApkData apkData);
    }

    protected ArtifactType<Directory> taskInputType;

    @Input
    public String getTaskInputTypeName() {
        return taskInputType.name();
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

    /**
     * Returns the paths to generated APKs as @Input to this task, so that when the output file name
     * is changed (e.g., by the users), the task will be re-executed in non-incremental mode.
     */
    @Input
    public Collection<String> getApkNames() {
        return apkFileNames;
    }

    private static BuildOutput computeBuildOutputFile(
            ApkData apkInfo,
            OutputFileProvider outputFileProvider,
            File outputDirectory,
            ArtifactType<Directory> expectedOutputType) {
        File outputFile =
                outputFileProvider != null
                        ? outputFileProvider.getOutputFile(apkInfo)
                        : new File(
                                outputDirectory,
                                Objects.requireNonNull(apkInfo.getOutputFileName()));
        return new BuildOutput(expectedOutputType, apkInfo, outputFile);
    }

    protected ApkCreatorType apkCreatorType;

    @NonNull
    @Input
    public ApkCreatorType getApkCreatorType() {
        return apkCreatorType;
    }

    @Internal
    protected abstract ArtifactType<Directory> getInternalArtifactType();

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

        // TODO: provide a BuiltArtifacts API that can handle workers.
        BuildElements apkBuiltElements =
                ExistingBuildElements.from(taskInputType, getResourceFiles())
                        .transform(
                                getWorkerFacadeWithWorkers(),
                                IncrementalSplitterRunnable.class,
                                (apkInfo, inputFile) ->
                                        new SplitterParams(
                                                apkInfo,
                                                inputFile,
                                                changedResourceFiles.contains(inputFile),
                                                changes,
                                                this,
                                                apkCreatorType))
                        .into(getInternalArtifactType());

        List<BuiltArtifactImpl> builtArtifactList =
                apkBuiltElements
                        .stream()
                        .map(
                                builtElement -> {
                                    List<FilterConfiguration> filters =
                                            builtElement
                                                    .getApkData()
                                                    .getFilters()
                                                    .stream()
                                                    .map(
                                                            filterData ->
                                                                    new FilterConfiguration(
                                                                            FilterConfiguration
                                                                                    .FilterType
                                                                                    .valueOf(
                                                                                            filterData
                                                                                                    .getFilterType()),
                                                                            filterData
                                                                                    .getIdentifier()))
                                                    .collect(Collectors.toList());

                                    return new BuiltArtifactImpl(
                                            builtElement.getOutputFile().toPath(),
                                            builtElement.getProperties(),
                                            builtElement.getVersionCode(),
                                            String.valueOf(builtElement.getVersionCode()),
                                            true,
                                            builtElement.getApkData().isUniversal()
                                                    ? VariantOutputConfiguration.OutputType
                                                            .UNIVERSAL
                                                    : builtElement
                                                                    .getOutputType()
                                                                    .equals(
                                                                            VariantOutput.OutputType
                                                                                    .MAIN
                                                                                    .toString())
                                                            ? VariantOutputConfiguration.OutputType
                                                                    .SINGLE
                                                            : VariantOutputConfiguration.OutputType
                                                                    .ONE_OF_MANY,
                                            filters);
                                })
                        .collect(Collectors.toList());

        new BuiltArtifactsImpl(
                        BuildElements.METADATA_FILE_VERSION,
                        InternalArtifactType.APK.INSTANCE,
                        getApplicationId().get(),
                        getVariantName(),
                        builtArtifactList)
                .save(getOutputDirectory().get());
    }

    private void checkFileNameUniqueness() {
        BuildElements buildElements = ExistingBuildElements.from(taskInputType, getResourceFiles());
        checkFileNameUniqueness(buildElements);
    }

    @VisibleForTesting
    static void checkFileNameUniqueness(BuildElements buildElements) {

        Collection<File> fileOutputs =
                buildElements.stream().map(BuildOutput::getOutputFile).collect(Collectors.toList());

        java.util.Optional<String> repeatingFileNameOptional =
                fileOutputs
                        .stream()
                        .filter(fileOutput -> Collections.frequency(fileOutputs, fileOutput) > 1)
                        .map(File::getName)
                        .findFirst();
        if (repeatingFileNameOptional.isPresent()) {
            String repeatingFileName = repeatingFileNameOptional.get();
            List<String> conflictingApks =
                    buildElements
                            .stream()
                            .filter(
                                    buildOutput ->
                                            buildOutput
                                                    .getOutputFile()
                                                    .getName()
                                                    .equals(repeatingFileName))
                            .map(
                                    buildOutput -> {
                                        ApkData apkInfo = buildOutput.getApkData();
                                        if (apkInfo.getFilters().isEmpty()) {
                                            return apkInfo.getType().toString();
                                        } else {
                                            return Joiner.on("-").join(apkInfo.getFilters());
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



    private static class SplitterParams extends BuildElementsTransformParams {
        @NonNull ApkData apkInfo;
        public final String projectPath;
        @NonNull File androidResourcesFile;
        protected final boolean androidResourcesChanged;
        @NonNull protected final File outputFile;
        @NonNull protected final File incrementalFolder;
        @NonNull protected final SerializableInputChanges dexFiles;
        @NonNull protected final SerializableInputChanges assetsFiles;
        @NonNull protected final SerializableInputChanges jniFiles;
        @NonNull protected final SerializableInputChanges javaResourceFiles;
        @NonNull protected final ArtifactType<Directory> manifestType;
        @NonNull protected final IncrementalPackagerBuilder.ApkFormat apkFormat;
        @Nullable protected final SigningConfigProviderParams signingConfig;
        @NonNull protected final Set<String> abiFilters;
        @NonNull protected final Collection<File> jniFolders;
        @NonNull protected final File manifestDirectory;
        @Nullable protected final Collection<String> aaptOptionsNoCompress;
        @Nullable protected final String createdBy;
        protected final int minSdkVersion;
        protected final boolean isDebuggableBuild;
        protected final boolean isJniDebuggableBuild;
        @Nullable protected final Integer targetApi;
        @NonNull protected final IncrementalPackagerBuilder.BuildType packagerMode;
        @NonNull protected final ApkCreatorType apkCreatorType;
        @Nullable protected File dependencyDataFile;

        SplitterParams(
                @NonNull ApkData apkInfo,
                @NonNull File androidResourcesFile,
                boolean androidResourcesChanged,
                @NonNull InputChanges changes,
                @NonNull PackageAndroidArtifact task,
                @NonNull ApkCreatorType apkCreatorType) {
            this.apkInfo = apkInfo;
            this.androidResourcesFile = androidResourcesFile;
            this.androidResourcesChanged = androidResourcesChanged;
            this.projectPath = task.getProject().getPath();
            this.apkCreatorType = apkCreatorType;

            outputFile =
                    computeBuildOutputFile(
                                    apkInfo,
                                    task.outputFileProvider,
                                    task.getOutputDirectory().get().getAsFile(),
                                    task.getInternalArtifactType())
                            .getOutputFile();

            incrementalFolder = task.getIncrementalFolder().get().getAsFile();
            if (task.getFeatureDexFolder().isEmpty()) {
                dexFiles =
                        IncrementalChangesUtils.getChangesInSerializableForm(
                                changes, task.getDexFolders());
                javaResourceFiles =
                        IncrementalChangesUtils.getChangesInSerializableForm(
                                changes, task.getJavaResourceFiles());
            } else {
                // We reach this code if we're in a feature module and minification is enabled in the
                // base module. In this case, we want to use the classes.dex file from the base
                // module's DexSplitterTask.
                dexFiles =
                        IncrementalChangesUtils.getChangesInSerializableForm(
                                changes, task.getFeatureDexFolder());
                javaResourceFiles =
                        new SerializableInputChanges(ImmutableList.of(), ImmutableSet.of());
            }
            assetsFiles =
                    IncrementalChangesUtils.getChangesInSerializableForm(changes, task.getAssets());
            jniFiles =
                    IncrementalChangesUtils.getChangesInSerializableForm(
                            changes, task.getJniFolders());

            manifestType = task.manifestType;
            apkFormat = task.apkFormat;
            signingConfig = task.signingConfig.convertToParams();
            abiFilters = task.abiFilters;
            jniFolders = task.getJniFolders().getFiles();
            manifestDirectory = task.getManifests().get().getAsFile();
            aaptOptionsNoCompress = task.aaptOptionsNoCompress;
            createdBy = task.getCreatedBy().get();
            minSdkVersion = task.getMinSdkVersion().get();
            isDebuggableBuild = task.getDebugBuild().get();
            isJniDebuggableBuild = task.getJniDebugBuild();
            targetApi = task.getTargetApi();
            dependencyDataFile =
                    task.getDependencyDataFile().map(RegularFile::getAsFile).getOrElse(null);
            packagerMode =
                    changes.isIncremental()
                            ? IncrementalPackagerBuilder.BuildType.INCREMENTAL
                            : IncrementalPackagerBuilder.BuildType.CLEAN;
        }

        @NonNull
        @Override
        public File getOutput() {
            return outputFile;
        }
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
     * Packages the application incrementally. In case of instant run packaging, this is not a
     * perfectly incremental task as some files are always rewritten even if no change has occurred.
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
            @NonNull BuildElements manifestOutputs,
            @NonNull Map<RelativeFile, FileStatus> changedDex,
            @NonNull Map<RelativeFile, FileStatus> changedJavaResources,
            @NonNull Map<RelativeFile, FileStatus> changedAssets,
            @NonNull Map<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull Map<RelativeFile, FileStatus> changedNLibs,
            @NonNull SplitterParams params)
            throws IOException {

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        // find the manifest file for this split.
        BuildOutput manifestForSplit = manifestOutputs.element(params.apkInfo);

        if (manifestForSplit == null) {
            throw new RuntimeException(
                    "Found a .ap_ for split "
                            + params.apkInfo
                            + " but no "
                            + params.manifestType
                            + " associated manifest file");
        }
        FileUtils.mkdirs(outputFile.getParentFile());

        // In execution phase, so can parse the manifest.
        ManifestAttributeSupplier manifest =
                new DefaultManifestParser(manifestForSplit.getOutputFile(), () -> true, true, null);

        byte[] dependencyData =
                params.dependencyDataFile != null
                        ? Files.readAllBytes(params.dependencyDataFile.toPath())
                        : null;

        try (IncrementalPackager packager =
                new IncrementalPackagerBuilder(params.apkFormat, params.packagerMode)
                        .withOutputFile(outputFile)
                        .withSigning(
                                params.signingConfig.resolve(),
                                params.minSdkVersion,
                                params.targetApi,
                                dependencyData)
                        .withCreatedBy(params.createdBy)
                        // TODO: allow extra metadata to be saved in the split scope to avoid
                        // reparsing
                        // these manifest files.
                        .withNativeLibraryPackagingMode(
                                PackagingUtils.getNativeLibrariesLibrariesPackagingMode(manifest))
                        .withNoCompressPredicate(
                                PackagingUtils.getNoCompressPredicate(
                                        params.aaptOptionsNoCompress, manifest))
                        .withIntermediateDir(incrementalDirForSplit)
                        .withDebuggableBuild(params.isDebuggableBuild)
                        .withAcceptedAbis(getAcceptedAbis(params))
                        .withJniDebuggableBuild(params.isJniDebuggableBuild)
                        .withApkCreatorType(params.apkCreatorType)
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
        FilterData splitAbiFilter = params.apkInfo.getFilter(OutputFile.FilterType.ABI);
        final Set<String> acceptedAbis =
                splitAbiFilter != null
                        ? ImmutableSet.of(splitAbiFilter.getIdentifier())
                        : ImmutableSet.copyOf(params.abiFilters);
        // After calculating acceptedAbis, we calculate availableAbis, which is the set of ABIs
        // present in params.jniFolders.
        Set<String> availableAbis = new HashSet<>();
        for (File jniFolder : params.jniFolders) {
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


    private static class IncrementalSplitterRunnable extends BuildElementsTransformRunnable {

        @Inject
        public IncrementalSplitterRunnable(@NonNull SplitterParams params) {
            super(params);
        }

        @Override
        public void run() {
            SplitterParams params = (SplitterParams) getParams();
            try {
                File incrementalDirForSplit =
                        new File(params.incrementalFolder, params.apkInfo.getFullName());

                File cacheDir = new File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR);
                if (!cacheDir.exists()) {
                    FileUtils.mkdirs(cacheDir);
                }

                // Build a file cache that uses the indexes in the roots.
                // This is to work nicely with classpath sensitivity
                // Mutable as we need to add to it for the zip64 workaround in getChangedJavaResources
                Map<File, String> cacheKeyMap = new HashMap<>();
                addCacheKeys(cacheKeyMap, "dex", params.dexFiles);
                addCacheKeys(cacheKeyMap, "javaResources", params.javaResourceFiles);
                addCacheKeys(cacheKeyMap, "assets", params.assetsFiles);
                cacheKeyMap.put(params.androidResourcesFile, "androidResources");
                addCacheKeys(cacheKeyMap, "jniLibs", params.jniFiles);

                KeyedFileCache cache =
                        new KeyedFileCache(
                                cacheDir, file -> Objects.requireNonNull(cacheKeyMap.get(file)));

                Set<Runnable> cacheUpdates = new HashSet<>();

                Map<RelativeFile, FileStatus> changedDexFiles =
                        IncrementalChanges.classpathToRelativeFileSet(
                                params.dexFiles, cache, cacheUpdates);

                Map<RelativeFile, FileStatus> changedJavaResources =
                        getChangedJavaResources(params, cacheKeyMap, cache, cacheUpdates);

                Map<RelativeFile, FileStatus> changedAssets =
                        IncrementalChanges.classpathToRelativeFileSet(
                                params.assetsFiles, cache, cacheUpdates);

                final Map<RelativeFile, FileStatus> changedAndroidResources;
                if (params.androidResourcesChanged) {
                    changedAndroidResources =
                            IncrementalRelativeFileSets.fromZip(
                                    new ZipCentralDirectory(params.androidResourcesFile),
                                    cache,
                                    cacheUpdates);
                } else {
                    changedAndroidResources = ImmutableMap.of();
                }

                Map<RelativeFile, FileStatus> changedJniLibs =
                        IncrementalChanges.classpathToRelativeFileSet(
                                params.jniFiles, cache, cacheUpdates);


                BuildElements manifestOutputs =
                        ExistingBuildElements.from(params.manifestType, params.manifestDirectory);

                doTask(
                        incrementalDirForSplit,
                        params.getOutput(),
                        cache,
                        manifestOutputs,
                        changedDexFiles,
                        changedJavaResources,
                        changedAssets,
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
                if (params.packagerMode == IncrementalPackagerBuilder.BuildType.CLEAN) {
                    PackageApplication.recordMetrics(
                            params.projectPath, params.getOutput(), params.androidResourcesFile);
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
            for (SerializableChange change : params.javaResourceFiles.getChanges()) {
                if (change.getNormalizedPath().isEmpty()) {
                    try {
                        IncrementalChanges.addZipChanges(
                                changedJavaResources, change.getFile(), cache, cacheUpdates);
                    } catch (Zip64NotSupportedException e) {
                        File nonZip64 =
                                copyJavaResourcesOnly(params.incrementalFolder, change.getFile());
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

    public abstract static class CreationAction<T extends PackageAndroidArtifact>
            extends VariantTaskCreationAction<T> {

        protected final Project project;
        @NonNull protected final Provider<Directory> manifests;
        @NonNull protected final SingleArtifactType<Directory> inputResourceFilesType;
        @NonNull private final ArtifactType<Directory> manifestType;
        private final boolean packageCustomClassDependencies;

        public CreationAction(
                @NonNull VariantScope variantScope,
                @NonNull SingleArtifactType<Directory> inputResourceFilesType,
                @NonNull Provider<Directory> manifests,
                @NonNull ArtifactType<Directory> manifestType,
                boolean packageCustomClassDependencies) {
            super(variantScope);
            this.project = variantScope.getGlobalScope().getProject();
            this.inputResourceFilesType = inputResourceFilesType;
            this.manifests = manifests;
            this.manifestType = manifestType;
            this.packageCustomClassDependencies = packageCustomClassDependencies;
        }

        @Override
        public void configure(@NonNull final T packageAndroidArtifact) {
            super.configure(packageAndroidArtifact);
            VariantScope variantScope = getVariantScope();

            GlobalScope globalScope = variantScope.getGlobalScope();
            VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();

            packageAndroidArtifact.taskInputType = inputResourceFilesType;
            packageAndroidArtifact
                    .getMinSdkVersion()
                    .set(
                            globalScope
                                    .getProject()
                                    .provider(() -> variantScope.getMinSdkVersion().getApiLevel()));
            packageAndroidArtifact.getMinSdkVersion().disallowChanges();
            packageAndroidArtifact
                    .getApplicationId()
                    .set(
                            variantScope
                                    .getVariantData()
                                    .getPublicVariantPropertiesApi()
                                    .getApplicationId());
            packageAndroidArtifact.getApplicationId().disallowChanges();

            packageAndroidArtifact
                    .getResourceFiles()
                    .from(
                            variantScope
                                    .getArtifacts()
                                    .getFinalProductAsFileCollection(inputResourceFilesType));
            packageAndroidArtifact
                    .getIncrementalFolder()
                    .set(
                            new File(
                                    variantScope.getIncrementalDir(
                                            packageAndroidArtifact.getName()),
                                    "tmp"));

            variantScope
                    .getVariantData()
                    .getPublicVariantPropertiesApi()
                    .getOutputs()
                    .forEach(
                            variantOutput -> {
                                packageAndroidArtifact.apkFileNames.add(
                                        variantOutput.getApkData().getOutputFileName());
                            });
            // sort strings by natural order to make it stable across executions.
            packageAndroidArtifact.apkFileNames.sort(String::compareTo);

            packageAndroidArtifact.aaptOptionsNoCompress =
                    DslAdaptersKt.convert(globalScope.getExtension().getAaptOptions())
                            .getNoCompress();

            packageAndroidArtifact.getManifests().set(manifests);

            packageAndroidArtifact.getDexFolders().from(getDexFolders());
            @Nullable FileCollection featureDexFolder = getFeatureDexFolder();
            if (featureDexFolder != null) {
                packageAndroidArtifact.getFeatureDexFolder().from(featureDexFolder);
            }
            packageAndroidArtifact.getJavaResourceFiles().from(getJavaResources());

            packageAndroidArtifact
                    .getAssets()
                    .set(variantScope.getArtifacts().getFinalProduct(MERGED_ASSETS.INSTANCE));
            packageAndroidArtifact.setJniDebugBuild(variantDslInfo.isJniDebuggable());
            packageAndroidArtifact
                    .getDebugBuild()
                    .set(variantScope.getVariantDslInfo().isDebuggable());
            packageAndroidArtifact.getDebugBuild().disallowChanges();

            ProjectOptions projectOptions = variantScope.getGlobalScope().getProjectOptions();
            packageAndroidArtifact.projectBaseName = globalScope.getProjectBaseName();
            packageAndroidArtifact.manifestType = manifestType;
            packageAndroidArtifact.buildTargetAbi =
                    globalScope.getExtension().getSplits().getAbi().isEnable()
                            ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                            : null;
            if (variantDslInfo.getSupportedAbis() != null) {
                packageAndroidArtifact.setAbiFilters(variantDslInfo.getSupportedAbis());
            } else {
                packageAndroidArtifact.setAbiFilters(
                        projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                                ? firstValidInjectedAbi(
                                        projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                                : null);
            }
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

            packageAndroidArtifact.apkCreatorType = variantScope.getApkCreatorType();

            packageAndroidArtifact.getCreatedBy().set(globalScope.getCreatedBy());

            if (variantScope.getType().isBaseModule() && variantScope.getGlobalScope()
                .getProjectOptions()
                .get(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS)) {
                variantScope
                    .getArtifacts()
                    .setTaskInputToFinalProduct(
                        InternalArtifactType.SDK_DEPENDENCY_DATA.INSTANCE,
                        packageAndroidArtifact.getDependencyDataFile());
            }

            finalConfigure(packageAndroidArtifact);
        }

        protected void finalConfigure(T task) {
            VariantScope variantScope = getVariantScope();

            GlobalScope globalScope = variantScope.getGlobalScope();

            task.getJniFolders()
                    .from(
                            PerModuleBundleTaskKt.getNativeLibsFiles(
                                    variantScope, packageCustomClassDependencies));

            task.setSigningConfig(SigningConfigProvider.create(variantScope));
        }

        @NonNull
        public FileCollection getDexFolders() {
            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            if (artifacts.hasFinalProduct(InternalArtifactType.BASE_DEX.INSTANCE)) {
                return artifacts
                        .getFinalProductAsFileCollection(InternalArtifactType.BASE_DEX.INSTANCE)
                        .get()
                        .plus(getDesugarLibDexIfExists());
            } else {
                return project.files(
                                artifacts.getOperations().getAll(MultipleArtifactType.DEX.INSTANCE))
                        .plus(getDesugarLibDexIfExists());
            }
        }

        @NonNull
        public FileCollection getJavaResources() {
            if (getVariantScope().getArtifacts().hasFinalProduct(SHRUNK_JAVA_RES.INSTANCE)) {
                Provider<RegularFile> mergedJavaResProvider =
                        getVariantScope().getArtifacts().getFinalProduct(SHRUNK_JAVA_RES.INSTANCE);
                return project.getLayout().files(mergedJavaResProvider);
            } else if (getVariantScope().getNeedsMergedJavaResStream()) {
                return getVariantScope()
                        .getTransformManager()
                        .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES);
            } else {
                Provider<RegularFile> mergedJavaResProvider =
                        getVariantScope().getArtifacts().getFinalProduct(MERGED_JAVA_RES.INSTANCE);
                return project.getLayout().files(mergedJavaResProvider);
            }
        }

        @Nullable
        public FileCollection getFeatureDexFolder() {
            if (!getVariantScope().getType().isDynamicFeature()) {
                return null;
            }
            return getVariantScope()
                    .getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.FEATURE_DEX,
                            ImmutableMap.of(MODULE_PATH, project.getPath()));
        }

        @Nullable
        private Set<String> firstValidInjectedAbi(@Nullable String abis) {
            if (abis == null) {
                return null;
            }
            Set<String> allowedAbis =
                    Abi.getDefaultValues().stream().map(Abi::getTag).collect(Collectors.toSet());
            java.util.Optional<String> firstValidAbi =
                    Arrays.asList(abis.split(","))
                            .stream()
                            .map(abi -> abi.trim())
                            .filter(abi -> allowedAbis.contains(abi))
                            .findFirst();
            return firstValidAbi.isPresent() ? ImmutableSet.of(firstValidAbi.get()) : null;
        }

        @NonNull
        private FileCollection getDesugarLibDexIfExists() {
            if (getVariantScope().getType().isDynamicFeature()) {
                return getVariantScope().getGlobalScope().getProject().files();
            }
            BuildArtifactsHolder artifacts = getVariantScope().getArtifacts();
            if (artifacts.hasFinalProduct(InternalArtifactType.DESUGAR_LIB_DEX.INSTANCE)) {
                return project.files(
                        artifacts.getFinalProduct(InternalArtifactType.DESUGAR_LIB_DEX.INSTANCE));
            } else {
                return DesugarLibUtils.getDesugarLibDexFromTransform(getVariantScope());
            }
        }
    }
}
