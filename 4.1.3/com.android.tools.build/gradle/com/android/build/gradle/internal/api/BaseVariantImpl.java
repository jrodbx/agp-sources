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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.api.SourceKind;
import com.android.build.gradle.internal.core.InternalBaseVariant;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.errors.IssueReporter;
import com.android.builder.model.BuildType;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Base class for variants.
 *
 * <p>This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public abstract class BaseVariantImpl implements BaseVariant, InternalBaseVariant {

    public static final String TASK_ACCESS_DEPRECATION_URL =
            "https://d.android.com/r/tools/task-configuration-avoidance";

    // TODO : b/142687686
    public static final String USE_PROPERTIES_DEPRECATION_URL =
            "https://d.android.com/r/tools/use-properties";

    @NonNull protected final ComponentPropertiesImpl componentProperties;
    @NonNull protected final BaseServices services;

    @NonNull protected final ReadOnlyObjectProvider readOnlyObjectProvider;

    @NonNull protected final NamedDomainObjectContainer<BaseVariantOutput> outputs;

    BaseVariantImpl(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull BaseServices services,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        this.componentProperties = componentProperties;
        this.services = services;
        this.readOnlyObjectProvider = readOnlyObjectProvider;
        this.outputs = outputs;
    }

    @NonNull
    protected abstract BaseVariantData getVariantData();

    public void addOutputs(@NonNull List<BaseVariantOutput> outputs) {
       this.outputs.addAll(outputs);
    }

    @Override
    @NonNull
    public String getName() {
        return componentProperties.getName();
    }

    @Override
    @NonNull
    public String getDescription() {
        return getVariantData().getDescription();
    }

    @Override
    @NonNull
    public String getDirName() {
        return componentProperties.getDirName();
    }

    @Override
    @NonNull
    public String getBaseName() {
        return componentProperties.getBaseName();
    }

    @NonNull
    @Override
    public String getFlavorName() {
        return componentProperties.getFlavorName();
    }

    @NonNull
    @Override
    public DomainObjectCollection<BaseVariantOutput> getOutputs() {
        return outputs;
    }

    @Override
    @NonNull
    public BuildType getBuildType() {
        // cast for VariantDslInfoImpl since we need to return this.
        // this is to be removed when we can get rid of the old API.
        final VariantDslInfoImpl variantDslInfo =
                (VariantDslInfoImpl) componentProperties.getVariantDslInfo();
        return readOnlyObjectProvider.getBuildType(variantDslInfo.getBuildTypeObj());
    }

    @Override
    @NonNull
    public List<ProductFlavor> getProductFlavors() {
        return new ImmutableFlavorList(
                componentProperties.getVariantDslInfo().getProductFlavorList(),
                readOnlyObjectProvider);
    }

    @Override
    @NonNull
    public MergedFlavor getMergedFlavor() {
        // cast for VariantDslInfoImpl since we need to return this.
        // this is to be removed when we can get rid of the old API.
        final VariantDslInfoImpl variantDslInfo =
                (VariantDslInfoImpl) componentProperties.getVariantDslInfo();
        return variantDslInfo.getMergedFlavor();
    }

    @NonNull
    @Override
    public JavaCompileOptions getJavaCompileOptions() {
        return componentProperties.getVariantDslInfo().getJavaCompileOptions();
    }

    @NonNull
    @Override
    public List<SourceProvider> getSourceSets() {
        return componentProperties.getVariantSources().getSortedSourceProviders();
    }

    @NonNull
    @Override
    public List<ConfigurableFileTree> getSourceFolders(@NonNull SourceKind folderType) {
        switch (folderType) {
            case JAVA:
                return componentProperties.getJavaSources();
            default:
                services.getIssueReporter()
                        .reportError(
                                IssueReporter.Type.GENERIC,
                                "Unknown SourceKind value: " + folderType);
        }

        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Configuration getCompileConfiguration() {
        return getVariantData().getVariantDependencies().getCompileClasspath();
    }

    @NonNull
    @Override
    public Configuration getRuntimeConfiguration() {
        return getVariantData().getVariantDependencies().getRuntimeClasspath();
    }

    @NonNull
    @Override
    public Configuration getAnnotationProcessorConfiguration() {
        return getVariantData().getVariantDependencies().getAnnotationProcessorConfiguration();
    }

    @Override
    @NonNull
    public String getApplicationId() {
        // this getter cannot work for dynamic features as the applicationId comes from somewhere
        // else and cannot be known at config time.
        if (componentProperties.getVariantType().isDynamicFeature()) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "variant.getApplicationId() is not supported by dynamic-feature plugins as it cannot handle delayed setting of the application ID. Please use getApplicationIdTextResource() instead.");
        }

        // FIXME: Break if this is done during configuration
        return componentProperties.getApplicationId().get();
    }

    @Override
    @NonNull
    public TextResource getApplicationIdTextResource() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "VariantProperties.applicationId",
                        "BaseVariant.getApplicationIdTextResource",
                        "TBD",
                        DeprecationReporter.DeprecationTarget.VERSION_5_0);
        return getVariantData().applicationIdTextResource;
    }

    @Override
    @NonNull
    public Task getPreBuild() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getPreBuildProvider()",
                        "variant.getPreBuild()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getPreBuildTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<Task> getPreBuildProvider() {
        //noinspection unchecked
        return (TaskProvider<Task>) componentProperties.getTaskContainer().getPreBuildTask();
    }

    @Override
    @NonNull
    public Task getCheckManifest() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getCheckManifestProvider()",
                        "variant.getCheckManifest()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getCheckManifestTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<Task> getCheckManifestProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>)
                (TaskProvider<?>) componentProperties.getTaskContainer().getCheckManifestTask();
    }

    @Override
    @Nullable
    public AidlCompile getAidlCompile() {
        if (!componentProperties.getBuildFeatures().getAidl()) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "aidl support is disabled via buildFeatures.");
            return null;
        }

        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getAidlCompileProvider()",
                        "variant.getAidlCompile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getAidlCompileTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<AidlCompile> getAidlCompileProvider() {
        if (!componentProperties.getBuildFeatures().getAidl()) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "aidl support is disabled via buildFeatures.");
            return null;
        }

        //noinspection unchecked
        return (TaskProvider<AidlCompile>)
                componentProperties.getTaskContainer().getAidlCompileTask();
    }

    @Override
    @Nullable
    public RenderscriptCompile getRenderscriptCompile() {
        if (!componentProperties.getBuildFeatures().getRenderScript()) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "renderscript support is disabled via buildFeatures.");
            return null;
        }

        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getRenderscriptCompileProvider()",
                        "variant.getRenderscriptCompile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getRenderscriptCompileTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<RenderscriptCompile> getRenderscriptCompileProvider() {
        if (!componentProperties.getBuildFeatures().getRenderScript()) {
            services.getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "renderscript support is disabled via buildFeatures.");
            return null;
        }

        //noinspection unchecked
        return (TaskProvider<RenderscriptCompile>)
                getVariantData().getTaskContainer().getRenderscriptCompileTask();
    }

    @Override
    public MergeResources getMergeResources() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getMergeResourcesProvider()",
                        "variant.getMergeResources()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getMergeResourcesTask().getOrNull();
    }

    @Nullable
    @Override
    public TaskProvider<MergeResources> getMergeResourcesProvider() {
        //noinspection unchecked
        return (TaskProvider<MergeResources>)
                componentProperties.getTaskContainer().getMergeResourcesTask();
    }

    @Override
    public MergeSourceSetFolders getMergeAssets() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getMergeAssetsProvider()",
                        "variant.getMergeAssets()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getMergeAssetsTask().getOrNull();
    }

    @Nullable
    @Override
    public TaskProvider<MergeSourceSetFolders> getMergeAssetsProvider() {
        //noinspection unchecked
        return (TaskProvider<MergeSourceSetFolders>)
                getVariantData().getTaskContainer().getMergeAssetsTask();
    }

    @Override
    public GenerateBuildConfig getGenerateBuildConfig() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getGenerateBuildConfigProvider()",
                        "variant.getGenerateBuildConfig()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getGenerateBuildConfigTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<GenerateBuildConfig> getGenerateBuildConfigProvider() {
        //noinspection unchecked
        return (TaskProvider<GenerateBuildConfig>)
                componentProperties.getTaskContainer().getGenerateBuildConfigTask();
    }

    @Override
    @NonNull
    public JavaCompile getJavaCompile() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getJavaCompileProvider()",
                        "variant.getJavaCompile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getJavacTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<JavaCompile> getJavaCompileProvider() {
        //noinspection unchecked
        return (TaskProvider<JavaCompile>) getVariantData().getTaskContainer().getJavacTask();
    }

    @NonNull
    @Override
    public Task getJavaCompiler() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getJavaCompileProvider()",
                        "variant.getJavaCompiler()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getJavacTask().get();
    }

    @NonNull
    @Override
    public Collection<ExternalNativeBuildTask> getExternalNativeBuildTasks() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getExternalNativeBuildProviders()",
                        "variant.getExternalNativeBuildTask()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);

        return getExternalNativeBuildProviders()
                .stream()
                .map(Provider::get)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<TaskProvider<ExternalNativeBuildTask>> getExternalNativeBuildProviders() {
        //noinspection unchecked
        TaskProvider<ExternalNativeBuildTask> provider =
                (TaskProvider<ExternalNativeBuildTask>)
                        componentProperties.getTaskContainer().getExternalNativeBuildTask();
        if (provider == null) {
            return ImmutableList.of();
        }

        return ImmutableList.of(provider);
    }

    @Nullable
    @Override
    public Task getObfuscation() {
        // This has returned null since before the TaskContainer changes.
        // This is to be removed with the old Variant API.
        return null;
    }

    @Nullable
    @Override
    public File getMappingFile() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getMappingFileProvider()",
                        "variant.getMappingFile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        // bypass the configuration time resolution check as some calls this API during
        // configuration.
        RegularFile mappingFile =
                componentProperties
                        .getArtifacts()
                        .get(InternalArtifactType.APK_MAPPING.INSTANCE)
                        .getOrNull();
        return mappingFile != null ? mappingFile.getAsFile() : null;
    }

    @NonNull
    @Override
    public Provider<FileCollection> getMappingFileProvider() {
        return componentProperties
                .getGlobalScope()
                .getProject()
                .getProviders()
                .provider(
                        () ->
                                componentProperties
                                        .getServices()
                                        .fileCollection(
                                                componentProperties
                                                        .getArtifacts()
                                                        .get(
                                                                InternalArtifactType.APK_MAPPING
                                                                        .INSTANCE)));
    }

    @Override
    @NonNull
    public Sync getProcessJavaResources() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getProcessJavaResourcesProvider()",
                        "variant.getProcessJavaResources()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getProcessJavaResourcesTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<AbstractCopyTask> getProcessJavaResourcesProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<AbstractCopyTask>)
                (TaskProvider<?>) getVariantData().getTaskContainer().getProcessJavaResourcesTask();
    }

    @Override
    @Nullable
    public Task getAssemble() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getAssembleProvider()",
                        "variant.getAssemble()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return componentProperties.getTaskContainer().getAssembleTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<Task> getAssembleProvider() {
        //noinspection unchecked
        return (TaskProvider<Task>) componentProperties.getTaskContainer().getAssembleTask();
    }

    @Override
    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        getVariantData().addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    @Override
    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        getVariantData().addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    @Override
    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... sourceFolders) {
        getVariantData().registerJavaGeneratingTask(task, sourceFolders);
    }

    @Override
    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> sourceFolders) {
        getVariantData().registerJavaGeneratingTask(task, sourceFolders);
    }

    @Override
    public void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder) {
        getVariantData().registerExternalAptJavaOutput(folder);
    }

    @Override
    public void registerGeneratedResFolders(@NonNull FileCollection folders) {
        getVariantData().registerGeneratedResFolders(folders);
    }

    @Override
    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull File... generatedResFolders) {
        getVariantData().registerResGeneratingTask(task, generatedResFolders);
    }

    @Override
    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedResFolders) {
        getVariantData().registerResGeneratingTask(task, generatedResFolders);
    }

    @Override
    public Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        return getVariantData().registerPreJavacGeneratedBytecode(fileCollection);
    }

    @Override
    @Deprecated
    public Object registerGeneratedBytecode(@NonNull FileCollection fileCollection) {
        return registerPreJavacGeneratedBytecode(fileCollection);
    }

    @Override
    public void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        getVariantData().registerPostJavacGeneratedBytecode(fileCollection);
    }

    @NonNull
    @Override
    public FileCollection getCompileClasspath(@Nullable Object generatorKey) {
        return componentProperties.getJavaClasspath(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactType.CLASSES_JAR,
                generatorKey);
    }

    @NonNull
    @Override
    public ArtifactCollection getCompileClasspathArtifacts(@Nullable Object generatorKey) {
        return componentProperties.getJavaClasspathArtifacts(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactType.CLASSES_JAR,
                generatorKey);
    }

    @Override
    public void buildConfigField(
            @NonNull String type, @NonNull String name, @NonNull String value) {
        componentProperties.getVariantDslInfo().addBuildConfigField(type, name, value);
    }

    @Override
    public void resValue(@NonNull String type, @NonNull String name, @NonNull String value) {
        componentProperties.getVariantDslInfo().addResValue(type, name, value);
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull String requestedValue) {
        componentProperties.handleMissingDimensionStrategy(
                dimension, ImmutableList.of(requestedValue));
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull String... requestedValues) {
        componentProperties.handleMissingDimensionStrategy(
                dimension, ImmutableList.copyOf(requestedValues));
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull List<String> requestedValues) {
        componentProperties.handleMissingDimensionStrategy(
                dimension, ImmutableList.copyOf(requestedValues));
    }

    @Override
    public void setOutputsAreSigned(boolean isSigned) {
        getVariantData().outputsAreSigned = isSigned;
    }

    @Override
    public boolean getOutputsAreSigned() {
        return getVariantData().outputsAreSigned;
    }

    @NonNull
    @Override
    public FileCollection getAllRawAndroidResources() {
        return getVariantData().getAllRawAndroidResources();
    }

    @Override
    public void register(Task task) {
        MutableTaskContainer taskContainer = componentProperties.getTaskContainer();
        TaskFactoryUtils.dependsOn(taskContainer.getAssembleTask(), task);
        TaskProvider<? extends Task> bundleTask = taskContainer.getBundleTask();
        if (bundleTask != null) {
            TaskFactoryUtils.dependsOn(bundleTask, task);
        }
        TaskProvider<? extends Zip> bundleLibraryTask = taskContainer.getBundleLibraryTask();
        if (bundleLibraryTask != null) {
            TaskFactoryUtils.dependsOn(bundleLibraryTask, task);
        }
    }
}
