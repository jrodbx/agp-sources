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
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.api.SourceKind;
import com.android.build.gradle.internal.DependencyConfigurator;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
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
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
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
public abstract class BaseVariantImpl implements BaseVariant {

    public static final String TASK_ACCESS_DEPRECATION_URL =
            "https://d.android.com/r/tools/task-configuration-avoidance";

    // TODO : b/142687686
    public static final String USE_PROPERTIES_DEPRECATION_URL =
            "https://d.android.com/r/tools/use-properties";

    @NonNull private final ObjectFactory objectFactory;

    @NonNull protected final ReadOnlyObjectProvider readOnlyObjectProvider;

    @NonNull protected final NamedDomainObjectContainer<BaseVariantOutput> outputs;

    BaseVariantImpl(
            @NonNull ObjectFactory objectFactory,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        this.objectFactory = objectFactory;
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
        return getVariantData().getVariantDslInfo().getComponentIdentity().getName();
    }

    @Override
    @NonNull
    public String getDescription() {
        return getVariantData().getDescription();
    }

    @Override
    @NonNull
    public String getDirName() {
        return getVariantData().getVariantDslInfo().getDirName();
    }

    @Override
    @NonNull
    public String getBaseName() {
        return getVariantData().getVariantDslInfo().getBaseName();
    }

    @NonNull
    @Override
    public String getFlavorName() {
        return getVariantData().getVariantDslInfo().getComponentIdentity().getFlavorName();
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
                (VariantDslInfoImpl) getVariantData().getVariantDslInfo();
        return readOnlyObjectProvider.getBuildType(variantDslInfo.getBuildTypeObj());
    }

    @Override
    @NonNull
    public List<ProductFlavor> getProductFlavors() {
        return new ImmutableFlavorList(
                getVariantData().getVariantDslInfo().getProductFlavorList(),
                readOnlyObjectProvider);
    }

    @Override
    @NonNull
    public ProductFlavor getMergedFlavor() {
        // cast for VariantDslInfoImpl since we need to return this.
        // this is to be removed when we can get rid of the old API.
        final VariantDslInfoImpl variantDslInfo =
                (VariantDslInfoImpl) getVariantData().getVariantDslInfo();
        return variantDslInfo.getMergedFlavor();
    }

    @NonNull
    @Override
    public JavaCompileOptions getJavaCompileOptions() {
        return getVariantData().getVariantDslInfo().getJavaCompileOptions();
    }

    @NonNull
    @Override
    public List<SourceProvider> getSourceSets() {
        return getVariantData().getVariantSources().getSortedSourceProviders();
    }

    @NonNull
    @Override
    public List<ConfigurableFileTree> getSourceFolders(@NonNull SourceKind folderType) {
        switch (folderType) {
            case JAVA:
                return getVariantData().getJavaSources();
            default:
                getVariantData()
                        .getScope()
                        .getGlobalScope()
                        .getDslScope()
                        .getIssueReporter()
                        .reportError(
                                IssueReporter.Type.GENERIC,
                                "Unknown SourceKind value: " + folderType);
        }

        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Configuration getCompileConfiguration() {
        return getVariantData().getVariantDependency().getCompileClasspath();
    }

    @NonNull
    @Override
    public Configuration getRuntimeConfiguration() {
        return getVariantData().getVariantDependency().getRuntimeClasspath();
    }

    @NonNull
    @Override
    public Configuration getAnnotationProcessorConfiguration() {
        return getVariantData().getVariantDependency().getAnnotationProcessorConfiguration();
    }

    @Override
    @NonNull
    public String getApplicationId() {
        BaseVariantData variantData = getVariantData();

        // this getter cannot work for dynamic features as the applicationId comes from somewhere
        // else and cannot be known at config time.
        if (variantData.getType().isDynamicFeature()) {
            variantData
                    .getScope()
                    .getGlobalScope()
                    .getDslScope()
                    .getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "variant.getApplicationId() is not supported by dynamic-feature plugins as it cannot handle delayed setting of the application ID. Please use getApplicationIdTextResource() instead.");
        }

        return variantData.getVariantDslInfo().getApplicationId();
    }

    @Override
    @NonNull
    public TextResource getApplicationIdTextResource() {
        return getVariantData().applicationIdTextResource;
    }

    @Override
    @NonNull
    public Task getPreBuild() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getPreBuildProvider()",
                        "variant.getPreBuild()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getPreBuildTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<Task> getPreBuildProvider() {
        //noinspection unchecked
        return (TaskProvider<Task>) getVariantData().getTaskContainer().getPreBuildTask();
    }

    @Override
    @NonNull
    public Task getCheckManifest() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getCheckManifestProvider()",
                        "variant.getCheckManifest()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getCheckManifestTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<Task> getCheckManifestProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>)
                (TaskProvider<?>) getVariantData().getTaskContainer().getCheckManifestTask();
    }

    @Override
    @Nullable
    public AidlCompile getAidlCompile() {
        BaseVariantData variantData = getVariantData();
        GlobalScope globalScope = variantData.getScope().getGlobalScope();

        if (!globalScope.getBuildFeatures().getAidl()) {
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "aidl support is disabled via buildFeatures.");
            return null;
        }

        globalScope
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getAidlCompileProvider()",
                        "variant.getAidlCompile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getAidlCompileTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<AidlCompile> getAidlCompileProvider() {
        final GlobalScope globalScope = getVariantData().getScope().getGlobalScope();

        if (!globalScope.getBuildFeatures().getAidl()) {
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "aidl support is disabled via buildFeatures.");
            return null;
        }

        //noinspection unchecked
        return (TaskProvider<AidlCompile>) getVariantData().getTaskContainer().getAidlCompileTask();
    }

    @Override
    @Nullable
    public RenderscriptCompile getRenderscriptCompile() {
        BaseVariantData variantData = getVariantData();
        GlobalScope globalScope = variantData.getScope().getGlobalScope();

        if (!globalScope.getBuildFeatures().getRenderScript()) {
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportError(
                            IssueReporter.Type.GENERIC,
                            "renderscript support is disabled via buildFeatures.");
            return null;
        }

        globalScope
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getRenderscriptCompileProvider()",
                        "variant.getRenderscriptCompile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getRenderscriptCompileTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<RenderscriptCompile> getRenderscriptCompileProvider() {
        final GlobalScope globalScope = getVariantData().getScope().getGlobalScope();
        if (!globalScope.getBuildFeatures().getRenderScript()) {
            globalScope
                    .getDslScope()
                    .getIssueReporter()
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
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getMergeResourcesProvider()",
                        "variant.getMergeResources()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getMergeResourcesTask().getOrNull();
    }

    @Nullable
    @Override
    public TaskProvider<MergeResources> getMergeResourcesProvider() {
        //noinspection unchecked
        return (TaskProvider<MergeResources>)
                getVariantData().getTaskContainer().getMergeResourcesTask();
    }

    @Override
    public MergeSourceSetFolders getMergeAssets() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getMergeAssetsProvider()",
                        "variant.getMergeAssets()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getMergeAssetsTask().getOrNull();
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
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getGenerateBuildConfigProvider()",
                        "variant.getGenerateBuildConfig()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getGenerateBuildConfigTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<GenerateBuildConfig> getGenerateBuildConfigProvider() {
        //noinspection unchecked
        return (TaskProvider<GenerateBuildConfig>)
                getVariantData().getTaskContainer().getGenerateBuildConfigTask();
    }

    @Override
    @NonNull
    public JavaCompile getJavaCompile() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getJavaCompileProvider()",
                        "variant.getJavaCompile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getJavacTask().get();
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
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getJavaCompileProvider()",
                        "variant.getJavaCompiler()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getJavacTask().get();
    }

    @NonNull
    @Override
    public Collection<ExternalNativeBuildTask> getExternalNativeBuildTasks() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
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
                        getVariantData().getTaskContainer().getExternalNativeBuildTask();
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
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getMappingFileProvider()",
                        "variant.getMappingFile()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        BuildArtifactsHolder artifacts = getVariantData().getScope().getArtifacts();
        if (artifacts.hasFinalProduct(InternalArtifactType.APK_MAPPING.INSTANCE)) {
            //     bypass the configuration time resolution check as some calls this API during
            // configuration.
            return artifacts
                    .getFinalProduct(InternalArtifactType.APK_MAPPING.INSTANCE)
                    .get()
                    .getAsFile();
        }
        return null;
    }

    @NonNull
    @Override
    public Provider<FileCollection> getMappingFileProvider() {
        return getVariantData()
                .getScope()
                .getArtifacts()
                .getFinalProductAsFileCollection(InternalArtifactType.APK_MAPPING.INSTANCE);
    }

    @Override
    @NonNull
    public Sync getProcessJavaResources() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getProcessJavaResourcesProvider()",
                        "variant.getProcessJavaResources()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getProcessJavaResourcesTask().get();
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
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getAssembleProvider()",
                        "variant.getAssemble()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getAssembleTask().get();
    }

    @Nullable
    @Override
    public TaskProvider<Task> getAssembleProvider() {
        //noinspection unchecked
        return (TaskProvider<Task>) getVariantData().getTaskContainer().getAssembleTask();
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
        return getVariantData()
                .getScope()
                .getJavaClasspath(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR,
                        generatorKey);
    }

    @NonNull
    @Override
    public ArtifactCollection getCompileClasspathArtifacts(@Nullable Object generatorKey) {
        return getVariantData()
                .getScope()
                .getJavaClasspathArtifacts(
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR,
                        generatorKey);
    }

    @Override
    public void buildConfigField(
            @NonNull String type, @NonNull String name, @NonNull String value) {
        getVariantData().getVariantDslInfo().addBuildConfigField(type, name, value);
    }

    @Override
    public void resValue(@NonNull String type, @NonNull String name, @NonNull String value) {
        getVariantData().getVariantDslInfo().addResValue(type, name, value);
    }


    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull String requestedValue) {
        _missingDimensionStrategy(dimension, ImmutableList.of(requestedValue));
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull String... requestedValues) {
        _missingDimensionStrategy(dimension, ImmutableList.copyOf(requestedValues));
    }

    @Override
    public void missingDimensionStrategy(
            @NonNull String dimension, @NonNull List<String> requestedValues) {
        _missingDimensionStrategy(dimension, ImmutableList.copyOf(requestedValues));
    }

    private void _missingDimensionStrategy(
            @NonNull String dimension, @NonNull ImmutableList<String> alternatedValues) {
        final VariantScope variantScope = getVariantData().getScope();

        // First, setup the requested value, which isn't the actual requested value, but
        // the variant name, modified
        final String requestedValue = VariantManager.getModifiedName(getName());

        final Attribute<ProductFlavorAttr> attributeKey =
                Attribute.of(dimension, ProductFlavorAttr.class);
        final ProductFlavorAttr attributeValue =
                objectFactory.named(ProductFlavorAttr.class, requestedValue);

        VariantDependencies dependencies = variantScope.getVariantDependencies();
        dependencies.getCompileClasspath().getAttributes().attribute(attributeKey, attributeValue);
        dependencies.getRuntimeClasspath().getAttributes().attribute(attributeKey, attributeValue);
        dependencies
                .getAnnotationProcessorConfiguration()
                .getAttributes()
                .attribute(attributeKey, attributeValue);

        // then add the fallbacks which contain the actual requested value
        AttributesSchema schema =
                variantScope.getGlobalScope().getProject().getDependencies().getAttributesSchema();

        DependencyConfigurator.addFlavorStrategy(
                schema, dimension, ImmutableMap.of(requestedValue, alternatedValues));
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
        MutableTaskContainer taskContainer = getVariantData().getScope().getTaskContainer();
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
