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
package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT;

import android.databinding.tool.LayoutXmlProcessor;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.VariantOutputFactory;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.VariantType;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.SourceFile;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Sync;

/** Base data about a variant. */
public abstract class BaseVariantData {

    @NonNull
    protected final TaskManager taskManager;
    @NonNull private final VariantDslInfo variantDslInfo;
    @NonNull private final VariantSources variantSources;

    private VariantDependencies variantDependency;

    // Needed for ModelBuilder.  Should be removed once VariantScope can replace BaseVariantData.
    @NonNull protected final VariantScope scope;

    private ImmutableList<ConfigurableFileTree> defaultJavaSources;

    private List<File> extraGeneratedSourceFolders = Lists.newArrayList();
    private List<ConfigurableFileTree> extraGeneratedSourceFileTrees;
    private List<ConfigurableFileTree> externalAptJavaOutputFileTrees;
    private final ConfigurableFileCollection extraGeneratedResFolders;
    private Map<Object, FileCollection> preJavacGeneratedBytecodeMap;
    private FileCollection preJavacGeneratedBytecodeLatest;
    private final ConfigurableFileCollection allPreJavacGeneratedBytecode;
    private final ConfigurableFileCollection allPostJavacGeneratedBytecode;

    private FileCollection rawAndroidResources = null;

    private Set<String> densityFilters;
    private Set<String> languageFilters;
    private Set<String> abiFilters;

    @Nullable
    private LayoutXmlProcessor layoutXmlProcessor;

    /**
     * If true, variant outputs will be considered signed. Only set if you manually set the outputs
     * to point to signed files built by other tasks.
     */
    public boolean outputsAreSigned = false;

    @NonNull private final OutputFactory outputFactory;
    public VariantOutputFactory variantOutputFactory;

    private final MutableTaskContainer taskContainer;
    public TextResource applicationIdTextResource;
    private final ComponentImpl publicVariantApi;
    private final ComponentPropertiesImpl publicVariantPropertiesApi;

    public BaseVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull VariantScope variantScope,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull ComponentImpl publicVariantApi,
            @NonNull ComponentPropertiesImpl publicVariantPropertiesApi,
            @NonNull VariantSources variantSources) {
        this.scope = variantScope;
        this.variantDslInfo = variantDslInfo;
        this.publicVariantApi = publicVariantApi;
        this.publicVariantPropertiesApi = publicVariantPropertiesApi;
        this.variantSources = variantSources;
        this.taskManager = taskManager;

        final Splits splits = globalScope.getExtension().getSplits();
        boolean splitsEnabled =
                splits.getDensity().isEnable()
                        || splits.getAbi().isEnable()
                        || splits.getLanguage().isEnable();

        // warn the user if we are forced to ignore the generatePureSplits flag.
        if (splitsEnabled && globalScope.getExtension().getGeneratePureSplits()) {
            Logging.getLogger(BaseVariantData.class)
                    .warn(
                            String.format(
                                    "Variant %s requested removed pure splits support, reverted to full splits",
                                    publicVariantApi.getName()));
        }

        outputFactory = new OutputFactory(globalScope.getProjectBaseName(), variantDslInfo);

        // this must be created immediately since the variant API happens after the task that
        // depends on this are created.
        final ObjectFactory objectFactory = globalScope.getDslScope().getObjectFactory();
        extraGeneratedResFolders = objectFactory.fileCollection();
        preJavacGeneratedBytecodeLatest = objectFactory.fileCollection();
        allPreJavacGeneratedBytecode = objectFactory.fileCollection();
        allPostJavacGeneratedBytecode = objectFactory.fileCollection();

        taskContainer = scope.getTaskContainer();
        applicationIdTextResource =
                globalScope.getProject().getResources().getText().fromString("");
    }

    @NonNull
    public LayoutXmlProcessor getLayoutXmlProcessor() {
        if (layoutXmlProcessor == null) {
            File resourceBlameLogDir = scope.getResourceBlameLogDir();
            final MergingLog mergingLog = new MergingLog(resourceBlameLogDir);
            layoutXmlProcessor =
                    new LayoutXmlProcessor(
                            getVariantDslInfo().getOriginalApplicationId(),
                            taskManager
                                    .getDataBindingBuilder()
                                    .createJavaFileWriter(scope.getClassOutputForDataBinding()),
                            file -> {
                                SourceFile input = new SourceFile(file);
                                SourceFile original = mergingLog.find(input);
                                // merged log api returns the file back if original cannot be found.
                                // it is not what we want so we alter the response.
                                return original == input ? null : original.getSourceFile();
                            },
                            scope.getGlobalScope()
                                    .getProjectOptions()
                                    .get(BooleanOption.USE_ANDROID_X));
        }
        return layoutXmlProcessor;
    }

    @NonNull
    public TaskContainer getTaskContainer() {
        return taskContainer;
    }

    @NonNull
    public OutputFactory getOutputFactory() {
        return outputFactory;
    }

    @NonNull
    public VariantDslInfo getVariantDslInfo() {
        return variantDslInfo;
    }

    @NonNull
    public ComponentImpl getPublicVariantApi() {
        return publicVariantApi;
    }

    @NonNull
    public ComponentPropertiesImpl getPublicVariantPropertiesApi() {
        return publicVariantPropertiesApi;
    }

    @NonNull
    public VariantSources getVariantSources() {
        return variantSources;
    }

    public void setVariantDependency(@NonNull VariantDependencies variantDependency) {
        this.variantDependency = variantDependency;
    }

    @NonNull
    public VariantDependencies getVariantDependency() {
        return variantDependency;
    }

    @NonNull
    public abstract String getDescription();

    @NonNull
    public VariantType getType() {
        return variantDslInfo.getVariantType();
    }

    @NonNull
    public String getName() {
        return publicVariantApi.getName();
    }

    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return StringHelper.appendCapitalized(prefix, publicVariantApi.getName(), suffix);
    }

    @NonNull
    public List<File> getExtraGeneratedSourceFolders() {
        return extraGeneratedSourceFolders;
    }

    @Nullable
    public FileCollection getExtraGeneratedResFolders() {
        return extraGeneratedResFolders;
    }

    @NonNull
    public FileCollection getAllPreJavacGeneratedBytecode() {
        return allPreJavacGeneratedBytecode;
    }

    @NonNull
    public FileCollection getAllPostJavacGeneratedBytecode() {
        return allPostJavacGeneratedBytecode;
    }

    @NonNull
    public FileCollection getGeneratedBytecode(@Nullable Object generatorKey) {
        if (generatorKey == null) {
            return allPreJavacGeneratedBytecode;
        }

        FileCollection result = preJavacGeneratedBytecodeMap.get(generatorKey);
        if (result == null) {
            throw new RuntimeException("Bytecode generator key not found");
        }

        return result;
    }

    public void addJavaSourceFoldersToModel(@NonNull File generatedSourceFolder) {
        extraGeneratedSourceFolders.add(generatedSourceFolder);
    }

    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        Collections.addAll(extraGeneratedSourceFolders, generatedSourceFolders);
    }

    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        extraGeneratedSourceFolders.addAll(generatedSourceFolders);
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... generatedSourceFolders) {
        registerJavaGeneratingTask(task, Arrays.asList(generatedSourceFolders));
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedSourceFolders) {
        TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), task);

        if (extraGeneratedSourceFileTrees == null) {
            extraGeneratedSourceFileTrees = new ArrayList<>();
        }

        final Project project = scope.getGlobalScope().getProject();
        for (File f : generatedSourceFolders) {
            ConfigurableFileTree fileTree = project.fileTree(f).builtBy(task);
            extraGeneratedSourceFileTrees.add(fileTree);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    public void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder) {
        if (externalAptJavaOutputFileTrees == null) {
            externalAptJavaOutputFileTrees = new ArrayList<>();
        }

        externalAptJavaOutputFileTrees.add(folder);

        addJavaSourceFoldersToModel(folder.getDir());
    }

    public void registerGeneratedResFolders(@NonNull FileCollection folders) {
        extraGeneratedResFolders.from(folders);
    }

    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull File... generatedResFolders) {
        registerResGeneratingTask(task, Arrays.asList(generatedResFolders));
    }

    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedResFolders) {
        System.out.println(
                "registerResGeneratingTask is deprecated, use registerGeneratedResFolders(FileCollection)");

        final Project project = scope.getGlobalScope().getProject();
        registerGeneratedResFolders(project.files(generatedResFolders).builtBy(task));
    }

    public Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        if (preJavacGeneratedBytecodeMap == null) {
            preJavacGeneratedBytecodeMap = Maps.newHashMap();
        }
        // latest contains the generated bytecode up to now, so create a new key and put it in the
        // map.
        Object key = new Object();
        preJavacGeneratedBytecodeMap.put(key, preJavacGeneratedBytecodeLatest);

        // now create a new file collection that will contains the previous latest plus the new
        // one

        // and make this the latest
        preJavacGeneratedBytecodeLatest = preJavacGeneratedBytecodeLatest.plus(fileCollection);

        // also add the stable all-bytecode file collection. We need a stable collection for
        // queries that request all the generated bytecode before the variant api is called.
        allPreJavacGeneratedBytecode.from(fileCollection);

        return key;
    }

    public void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        allPostJavacGeneratedBytecode.from(fileCollection);
    }

    /**
     * Calculates the filters for this variant. The filters can either be manually specified by
     * the user within the build.gradle or can be automatically discovered using the variant
     * specific folders.
     *
     * This method must be called before {@link #getFilters(OutputFile.FilterType)}.
     *
     * @param splits the splits configuration from the build.gradle.
     */
    public void calculateFilters(Splits splits) {
        densityFilters = getFilters(DiscoverableFilterType.DENSITY, splits);
        languageFilters = getFilters(DiscoverableFilterType.LANGUAGE, splits);
        abiFilters = getFilters(DiscoverableFilterType.ABI, splits);
    }

    /**
     * Returns the filters values (as manually specified or automatically discovered) for a
     * particular {@link com.android.build.OutputFile.FilterType}
     * @param filterType the type of filter in question
     * @return a possibly empty set of filter values.
     * @throws IllegalStateException if {@link #calculateFilters(Splits)} has not been called prior
     * to invoking this method.
     */
    @NonNull
    public Set<String> getFilters(OutputFile.FilterType filterType) {
        if (densityFilters == null || languageFilters == null || abiFilters == null) {
            throw new IllegalStateException("calculateFilters method not called");
        }
        switch(filterType) {
            case DENSITY:
                return densityFilters;
            case LANGUAGE:
                return languageFilters;
            case ABI:
                return abiFilters;
            default:
                throw new RuntimeException("Unhandled filter type");
        }
    }

    @NonNull
    public FileCollection getAllRawAndroidResources() {
        if (rawAndroidResources == null) {
            Project project = scope.getGlobalScope().getProject();
            Iterator<Object> builtBy =
                    Lists.newArrayList(
                                    taskContainer.getRenderscriptCompileTask(),
                                    taskContainer.getGenerateResValuesTask(),
                                    taskContainer.getGenerateApkDataTask(),
                                    extraGeneratedResFolders.getBuiltBy())
                            .stream()
                            .filter(Objects::nonNull)
                            .iterator();
            FileCollection allRes = project.files().builtBy(builtBy);

            FileCollection libraries =
                    scope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ANDROID_RES)
                            .getArtifactFiles();
            allRes = allRes.plus(libraries);

            Iterator<FileCollection> sourceSets = getAndroidResources().values().iterator();
            FileCollection mainSourceSet = sourceSets.next();
            FileCollection generated =
                    project.files(
                            scope.getRenderscriptResOutputDir(),
                            scope.getGeneratedResOutputDir(),
                            scope.getMicroApkResDirectory(),
                            extraGeneratedResFolders);
            allRes = allRes.plus(mainSourceSet.plus(generated));

            while (sourceSets.hasNext()) {
                allRes = allRes.plus(sourceSets.next());
            }

            rawAndroidResources = allRes;
        }

        return rawAndroidResources;
    }

    /**
     * Defines the discoverability attributes of filters.
     */
    private enum DiscoverableFilterType {

        DENSITY {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getDensityFilters();
            }
        },
        LANGUAGE {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getLanguageFilters();
            }
        },
        ABI {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getAbiFilters();
            }
        };

        /**
         * Returns the applicable filters configured in the build.gradle for this filter type.
         * @param splits the build.gradle splits configuration
         * @return a list of filters.
         */
        @NonNull
        abstract Collection<String> getConfiguredFilters(@NonNull Splits splits);
    }

    /**
     * Gets the list of filter values for a filter type either from the user specified build.gradle
     * settings or through a discovery mechanism using folders names.
     * @param filterType the filter type
     * @param splits the variant's configuration for splits.
     * @return a possibly empty list of filter value for this filter type.
     */
    @NonNull
    private static Set<String> getFilters(
            @NonNull DiscoverableFilterType filterType,
            @NonNull Splits splits) {

        return new HashSet<>(filterType.getConfiguredFilters(splits));
    }

    /**
     * Computes the Java sources to use for compilation.
     *
     * <p>Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    @NonNull
    public List<ConfigurableFileTree> getJavaSources() {
        // Shortcut for the common cases, otherwise we build the full list below.
        if (extraGeneratedSourceFileTrees == null && externalAptJavaOutputFileTrees == null) {
            return getDefaultJavaSources();
        }

        // Build the list of source folders.
        ImmutableList.Builder<ConfigurableFileTree> sourceSets = ImmutableList.builder();

        // First the default source folders.
        sourceSets.addAll(getDefaultJavaSources());

        // then the third party ones
        if (extraGeneratedSourceFileTrees != null) {
            sourceSets.addAll(extraGeneratedSourceFileTrees);
        }
        if (externalAptJavaOutputFileTrees != null) {
            sourceSets.addAll(externalAptJavaOutputFileTrees);
        }

        return sourceSets.build();
    }

    public LinkedHashMap<String, FileCollection> getAndroidResources() {
        return variantSources
                .getSortedSourceProviders()
                .stream()
                .collect(
                        Collectors.toMap(
                                SourceProvider::getName,
                                (provider) ->
                                        ((AndroidSourceSet) provider)
                                                .getRes()
                                                .getBuildableArtifact(),
                                (u, v) -> {
                                    throw new IllegalStateException(
                                            String.format("Duplicate key %s", u));
                                },
                                LinkedHashMap::new));
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     *
     * <p>Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    @NonNull
    private List<ConfigurableFileTree> getDefaultJavaSources() {
        if (defaultJavaSources == null) {
            Project project = scope.getGlobalScope().getProject();
            // Build the list of source folders.
            ImmutableList.Builder<ConfigurableFileTree> sourceSets = ImmutableList.builder();

            // First the actual source folders.
            List<SourceProvider> providers = variantSources.getSortedSourceProviders();
            for (SourceProvider provider : providers) {
                sourceSets.addAll(
                        ((AndroidSourceSet) provider).getJava().getSourceDirectoryTrees());
            }

            // then all the generated src folders.
            if (scope.getGlobalScope().getProjectOptions().get(BooleanOption.GENERATE_R_JAVA)) {
                Provider<Directory> rClassSource =
                        scope.getArtifacts()
                                .getFinalProduct(
                                        InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES
                                                .INSTANCE);
                if (rClassSource.isPresent()) {
                    sourceSets.add(project.fileTree(rClassSource).builtBy(rClassSource));
                }
            }

            // for the other, there's no duplicate so no issue.
            if (taskContainer.getGenerateBuildConfigTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getBuildConfigSourceOutputDir())
                                .builtBy(taskContainer.getGenerateBuildConfigTask().getName()));
            }

            if (taskContainer.getAidlCompileTask() != null) {
                Provider<Directory> aidlFC =
                        scope.getArtifacts()
                                .getFinalProduct(
                                        InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR.INSTANCE);
                sourceSets.add(project.fileTree(aidlFC).builtBy(aidlFC));
            }

            final BuildFeatureValues features = scope.getGlobalScope().getBuildFeatures();
            if (features.getDataBinding() || features.getViewBinding()) {
                if (scope.getTaskContainer().getDataBindingExportBuildInfoTask() != null) {
                    sourceSets.add(
                            project.fileTree(scope.getClassOutputForDataBinding())
                                    .builtBy(
                                            scope.getTaskContainer()
                                                    .getDataBindingExportBuildInfoTask()));
                }
                if (scope.getArtifacts()
                        .hasFinalProduct(DATA_BINDING_BASE_CLASS_SOURCE_OUT.INSTANCE)) {
                    Provider<Directory> baseClassSource =
                            scope.getArtifacts()
                                    .getFinalProduct(DATA_BINDING_BASE_CLASS_SOURCE_OUT.INSTANCE);
                    sourceSets.add(project.fileTree(baseClassSource).builtBy(baseClassSource));
                }
            }

            if (!variantDslInfo.getRenderscriptNdkModeEnabled()
                    && taskContainer.getRenderscriptCompileTask() != null) {
                Provider<Directory> rsFC =
                        scope.getArtifacts()
                                .getFinalProduct(
                                        InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR
                                                .INSTANCE);
                sourceSets.add(project.fileTree(rsFC).builtBy(rsFC));
            }

            defaultJavaSources = sourceSets.build();
        }

        return defaultJavaSources;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(publicVariantApi.getName()).toString();
    }

    @NonNull
    public VariantScope getScope() {
        return scope;
    }

    @NonNull
    public File getJavaResourcesForUnitTesting() {
        // FIXME we need to revise this API as it force-configure the tasks
        Sync processJavaResourcesTask = taskContainer.getProcessJavaResourcesTask().get();
        if (processJavaResourcesTask != null) {
            return processJavaResourcesTask.getOutputs().getFiles().getSingleFile();
        } else {
            return scope.getArtifacts()
                    .getFinalProduct(InternalArtifactType.JAVA_RES.INSTANCE)
                    .get()
                    .getAsFile();
        }
    }
}
