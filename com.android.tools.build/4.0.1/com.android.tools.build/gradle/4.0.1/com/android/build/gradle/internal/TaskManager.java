/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;
import static com.android.build.gradle.internal.cxx.model.TryCreateCxxModuleModelKt.tryCreateCxxModuleModel;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_ANDROID_APIS;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_CORE_LIBRARY_DESUGARING;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_LINTCHECKS;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_LINTPUBLISH;
import static com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAVA_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.REVERSE_METADATA_CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToConfiguration;
import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PUBLISH_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NOT_COMPILED_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.PROCESSED_RES;
import static com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.PublicArtifactType;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.cxx.model.CxxModuleModel;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask;
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask;
import com.android.build.gradle.internal.res.namespaced.NamespacedResourcesTaskManager;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MultipleArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.SingleArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask;
import com.android.build.gradle.internal.tasks.CheckProguardFiles;
import com.android.build.gradle.internal.tasks.D8MainDexListTask;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.DesugarTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask;
import com.android.build.gradle.internal.tasks.DexFileDependenciesTask;
import com.android.build.gradle.internal.tasks.DexMergingAction;
import com.android.build.gradle.internal.tasks.DexMergingTask;
import com.android.build.gradle.internal.tasks.DexSplitterTask;
import com.android.build.gradle.internal.tasks.ExtractProguardFiles;
import com.android.build.gradle.internal.tasks.ExtractTryWithResourcesSupportJar;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.GenerateLibraryProguardRulesTask;
import com.android.build.gradle.internal.tasks.InstallVariantTask;
import com.android.build.gradle.internal.tasks.JacocoTask;
import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask;
import com.android.build.gradle.internal.tasks.LintCompile;
import com.android.build.gradle.internal.tasks.MergeAaptProguardFilesCreationAction;
import com.android.build.gradle.internal.tasks.MergeClassesTask;
import com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction;
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask;
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask;
import com.android.build.gradle.internal.tasks.PackageForUnitTest;
import com.android.build.gradle.internal.tasks.PrepareLintJar;
import com.android.build.gradle.internal.tasks.PrepareLintJarForPublish;
import com.android.build.gradle.internal.tasks.ProcessJavaResTask;
import com.android.build.gradle.internal.tasks.ProguardTask;
import com.android.build.gradle.internal.tasks.R8Task;
import com.android.build.gradle.internal.tasks.RecalculateStackFramesTask;
import com.android.build.gradle.internal.tasks.ShrinkResourcesTask;
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.tasks.SourceSetsTask;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.tasks.UninstallTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeBaseClassLogTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeDependencyArtifactsTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitUtils;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.BundleTestDataImpl;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.transforms.CustomClassTransform;
import com.android.build.gradle.internal.transforms.ShrinkBundleResourcesTask;
import com.android.build.gradle.internal.variant.AndroidArtifactVariantData;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.AnalyzeDependenciesTask;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.CleanBuildCache;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeCleanTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.GenerateTestConfig;
import com.android.build.gradle.tasks.JavaCompileCreationAction;
import com.android.build.gradle.tasks.JavaPreCompileTask;
import com.android.build.gradle.tasks.LintFixTask;
import com.android.build.gradle.tasks.LintGlobalTask;
import com.android.build.gradle.tasks.LintPerVariantTask;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PrepareKotlinCompileTask;
import com.android.build.gradle.tasks.ProcessApplicationManifest;
import com.android.build.gradle.tasks.ProcessLibraryManifest;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DesugarProcessArgs;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.model.CodeShrinker;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.builder.utils.FileCache;
import com.android.sdklib.AndroidVersion;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Manages tasks creation. */
public abstract class TaskManager {

    public static final String INSTALL_GROUP = "Install";
    public static final String BUILD_GROUP = BasePlugin.BUILD_GROUP;
    public static final String ANDROID_GROUP = "Android";
    public static final String FEATURE_SUFFIX = "Feature";

    // Task names. These cannot be AndroidTasks as in the component model world there is nothing to
    // force generateTasksBeforeEvaluate to happen before the variant tasks are created.
    public static final String MAIN_PREBUILD = "preBuild";
    public static final String UNINSTALL_ALL = "uninstallAll";
    public static final String DEVICE_CHECK = "deviceCheck";
    public static final String DEVICE_ANDROID_TEST = DEVICE + VariantType.ANDROID_TEST_SUFFIX;
    public static final String CONNECTED_CHECK = "connectedCheck";
    public static final String CONNECTED_ANDROID_TEST = CONNECTED + VariantType.ANDROID_TEST_SUFFIX;
    public static final String ASSEMBLE_ANDROID_TEST = "assembleAndroidTest";
    public static final String LINT = "lint";
    public static final String LINT_FIX = "lintFix";
    public static final String EXTRACT_PROGUARD_FILES = "extractProguardFiles";

    // Temporary static variables for Kotlin+Compose configuration
    public static final String KOTLIN_COMPILER_CLASSPATH_CONFIGURATION_NAME =
            "kotlinCompilerClasspath";
    public static final String COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION = "0.1.0-dev03";
    public static final String COMPOSE_KOTLIN_COMPILER_VERSION =
            "1.3.61-dev-withExperimentalGoogleExtensions-20191127";

    @NonNull protected final Project project;
    @NonNull protected final ProjectOptions projectOptions;
    @NonNull protected final DataBindingBuilder dataBindingBuilder;
    @NonNull protected final BaseExtension extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull protected final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Recorder recorder;
    @NonNull private final Logger logger;
    @Nullable private final FileCache buildCache;
    @NonNull protected final TaskFactory taskFactory;

    // Tasks. TODO: remove the mutable state from here.
    public TaskProvider<Task> createMockableJar;

    public TaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.project = project;
        this.projectOptions = projectOptions;
        this.dataBindingBuilder = dataBindingBuilder;
        this.extension = extension;
        this.variantFactory = variantFactory;
        this.toolingRegistry = toolingRegistry;
        this.recorder = recorder;
        this.logger = Logging.getLogger(this.getClass());

        // It's too early to materialize the project-level cache, we'll need to get it from
        // globalScope later on.
        this.buildCache = globalScope.getBuildCache();

        taskFactory = new TaskFactoryImpl(project.getTasks());
    }

    @NonNull
    public TaskFactory getTaskFactory() {
        return taskFactory;
    }

    @NonNull
    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    /** Creates the tasks for a given BaseVariantData. */
    public abstract void createTasksForVariantScope(
            @NonNull VariantScope variantScope, @NonNull List<VariantScope> variantScopesForLint);

    /**
     * Create tasks before the evaluation (on plugin apply). This is useful for tasks that could be
     * referenced by custom build logic.
     */
    public void createTasksBeforeEvaluate() {
        taskFactory.register(
                UNINSTALL_ALL,
                uninstallAllTask -> {
                    uninstallAllTask.setDescription("Uninstall all applications.");
                    uninstallAllTask.setGroup(INSTALL_GROUP);
                });

        taskFactory.register(
                DEVICE_CHECK,
                deviceCheckTask -> {
                    deviceCheckTask.setDescription(
                            "Runs all device checks using Device Providers and Test Servers.");
                    deviceCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        taskFactory.register(
                CONNECTED_CHECK,
                connectedCheckTask -> {
                    connectedCheckTask.setDescription(
                            "Runs all device checks on currently connected devices.");
                    connectedCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        // Make sure MAIN_PREBUILD runs first:
        taskFactory.register(MAIN_PREBUILD);

        taskFactory.register(
                EXTRACT_PROGUARD_FILES,
                ExtractProguardFiles.class,
                task -> task.dependsOn(MAIN_PREBUILD));

        taskFactory.register(new SourceSetsTask.CreationAction(extension));

        taskFactory.register(
                ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
                });

        taskFactory.register(new LintCompile.CreationAction(globalScope));

        // Lint task is configured in afterEvaluate, but created upfront as it is used as an
        // anchor task.
        createGlobalLintTask();
        configureCustomLintChecksConfig();

        globalScope.setAndroidJarConfig(createAndroidJarConfig(project));

        if (buildCache != null) {
            taskFactory.register(new CleanBuildCache.CreationAction(buildCache));
        }

        // for testing only.
        taskFactory.register(
                "resolveConfigAttr", ConfigAttrTask.class, task -> task.resolvable = true);
        taskFactory.register(
                "consumeConfigAttr", ConfigAttrTask.class, task -> task.consumable = true);

        // This needs to be resolved before tasks evaluation since it does some configuration inside
        // By resolving it here we avoid configuration problems. The value returned will be cached
        // and returned immediately later when this method is invoked.
        Aapt2MavenUtils.getAapt2FromMavenAndVersion(globalScope);

        createCoreLibraryDesugaringConfig(project);
    }

    private void configureCustomLintChecksConfig() {
        // create a single configuration to point to a project or a local file that contains
        // the lint.jar for this project.
        // This is not the configuration that consumes lint.jar artifacts from normal dependencies,
        // or publishes lint.jar to consumers. These are handled at the variant level.
        globalScope.setLintChecks(createCustomLintChecksConfig(project));
        globalScope.setLintPublish(createCustomLintPublishConfig(project));
    }

    @NonNull
    public static Configuration createCustomLintChecksConfig(@NonNull Project project) {
        Configuration lintChecks = project.getConfigurations().maybeCreate(CONFIG_NAME_LINTCHECKS);
        lintChecks.setVisible(false);
        lintChecks.setDescription("Configuration to apply external lint check jar");
        lintChecks.setCanBeConsumed(false);
        return lintChecks;
    }

    @NonNull
    public static Configuration createCustomLintPublishConfig(@NonNull Project project) {
        Configuration lintChecks = project.getConfigurations().maybeCreate(CONFIG_NAME_LINTPUBLISH);
        lintChecks.setVisible(false);
        lintChecks.setDescription("Configuration to publish external lint check jar");
        lintChecks.setCanBeConsumed(false);
        return lintChecks;
    }

    // this is call before all the variants are created since they are all going to depend
    // on the global LINT_JAR and LINT_PUBLISH_JAR task output
    public void configureCustomLintChecks() {
        // setup the task that reads the config and put the lint jar in the intermediate folder
        // so that the bundle tasks can copy it, and the inter-project publishing can publish it
        taskFactory.register(new PrepareLintJar.CreationAction(globalScope));
        taskFactory.register(new PrepareLintJarForPublish.CreationAction(globalScope));
    }

    public void createGlobalLintTask() {
        taskFactory.register(LINT, LintGlobalTask.class, task -> {});
        taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));
        taskFactory.register(LINT_FIX, LintFixTask.class, task -> {});
    }

    // this is run after all the variants are created.
    public void configureGlobalLintTask(@NonNull final Collection<VariantScope> variants) {
        // we only care about non testing and non feature variants
        List<VariantScope> filteredVariants =
                variants.stream().filter(TaskManager::isLintVariant).collect(Collectors.toList());

        if (filteredVariants.isEmpty()) {
            return;
        }

        // configure the global lint tasks.
        taskFactory.configure(
                LINT,
                LintGlobalTask.class,
                task ->
                        new LintGlobalTask.GlobalCreationAction(globalScope, filteredVariants)
                                .configure(task));
        taskFactory.configure(
                LINT_FIX,
                LintFixTask.class,
                task ->
                        new LintFixTask.GlobalCreationAction(globalScope, filteredVariants)
                                .configure(task));

        // publish the local lint.jar to all the variants. This is not for the task output itself
        // but for the artifact publishing.
        for (VariantScope scope : variants) {
            scope.getArtifacts().copy(LINT_PUBLISH_JAR.INSTANCE, globalScope.getArtifacts());
        }
    }

    public void configureKotlinPluginTasksForComposeIfNecessary(
            GlobalScope globalScope, List<VariantScope> variantScopes) {

        boolean composeIsEnabled = globalScope.getBuildFeatures().getCompose();
        if (!composeIsEnabled) {
            return;
        }

        // any override coming from the DSL.
        String kotlinCompilerVersionInDsl =
                globalScope.getExtension().getComposeOptions().getKotlinCompilerVersion();
        String kotlinCompilerExtensionVersionInDsl =
                globalScope.getExtension().getComposeOptions().getKotlinCompilerExtensionVersion();

        String kotlinCompilerDependency =
                "org.jetbrains.kotlin:kotlin-compiler-embeddable:"
                        + (kotlinCompilerVersionInDsl != null
                        ? kotlinCompilerVersionInDsl
                        : COMPOSE_KOTLIN_COMPILER_VERSION);
        project.getConfigurations()
                .maybeCreate(KOTLIN_COMPILER_CLASSPATH_CONFIGURATION_NAME)
                .withDependencies(
                        configuration -> configuration.add(
                                project.getDependencies().create(kotlinCompilerDependency)))
                .getResolutionStrategy().force(kotlinCompilerDependency);

        // record in our metrics that compose is enabled.
        ProcessProfileWriter.getProject(project.getPath()).setComposeEnabled(true);

        // Create a project configuration that holds the androidx compose kotlin
        // compiler extension
        Configuration kotlinExtension = project.getConfigurations().create("kotlin-extension");
        project.getDependencies()
                .add(
                        kotlinExtension.getName(),
                        "androidx.compose:compose-compiler:"
                                + (kotlinCompilerExtensionVersionInDsl != null
                                        ? kotlinCompilerExtensionVersionInDsl
                                        : COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION));
        kotlinExtension.setTransitive(false);
        kotlinExtension.setDescription(
                "Configuration for Compose related kotlin compiler extension");

        // register for all variant the prepareKotlinCompileTask if necessary.
        variantScopes.forEach(
                variantScope -> {
                    Set<Task> kotlinCompiles =
                            globalScope
                                    .getProject()
                                    .getTasksByName(
                                            variantScope
                                                    .getVariantData()
                                                    .getTaskName("compile", "Kotlin"),
                                            false);
                    if (!kotlinCompiles.isEmpty()) {
                        TaskProvider<PrepareKotlinCompileTask>
                                prepareKotlinCompileTaskTaskProvider =
                                        taskFactory.register(
                                                new PrepareKotlinCompileTask.CreationAction(
                                                        variantScope,
                                                        kotlinCompiles,
                                                        kotlinExtension));
                        // make the dependency !
                        for (Task kotlinCompile : kotlinCompiles) {
                            kotlinCompile.dependsOn(prepareKotlinCompileTaskTaskProvider);
                        }
                    }
                });
    }

    // This is for config attribute debugging
    public static class ConfigAttrTask extends DefaultTask {
        boolean consumable = false;
        boolean resolvable = false;
        @TaskAction
        public void run() {
            for (Configuration config : getProject().getConfigurations()) {
                AttributeContainer attributes = config.getAttributes();
                if ((consumable && config.isCanBeConsumed())
                        || (resolvable && config.isCanBeResolved())) {
                    System.out.println(config.getName());
                    System.out.println("\tcanBeResolved: " + config.isCanBeResolved());
                    System.out.println("\tcanBeConsumed: " + config.isCanBeConsumed());
                    for (Attribute<?> attr : attributes.keySet()) {
                        System.out.println(
                                "\t" + attr.getName() + ": " + attributes.getAttribute(attr));
                    }
                    if (consumable && config.isCanBeConsumed()) {
                        for (PublishArtifact artifact : config.getArtifacts()) {
                            System.out.println("\tArtifact: " + artifact.getName() + " (" + artifact.getFile().getName() + ")");
                        }
                        for (ConfigurationVariant cv : config.getOutgoing().getVariants()) {
                            System.out.println("\tConfigurationVariant: " + cv.getName());
                            for (PublishArtifact pa : cv.getArtifacts()) {
                                System.out.println("\t\tArtifact: " + pa.getFile());
                                System.out.println("\t\tType:" + pa.getType());
                            }
                        }
                    }
                }
            }
        }
    }

    public void createMockableJarTask() {
        project.getDependencies()
                .add(
                        CONFIG_NAME_ANDROID_APIS,
                        project.files(
                                (Callable)
                                        () ->
                                                globalScope
                                                        .getSdkComponents()
                                                        .getAndroidJarProvider()
                                                        .getOrNull()));

        // Adding this task to help the IDE find the mockable JAR.
        createMockableJar = project.getTasks().register("createMockableJar");
        createMockableJar.configure(task -> task.dependsOn(globalScope.getMockableJarArtifact()));
    }

    @NonNull
    public static Configuration createAndroidJarConfig(@NonNull Project project) {
        Configuration androidJarConfig =
                project.getConfigurations().maybeCreate(CONFIG_NAME_ANDROID_APIS);
        androidJarConfig.setDescription(
                "Configuration providing various types of Android JAR file");
        androidJarConfig.setCanBeConsumed(false);
        return androidJarConfig;
    }

    public static void createCoreLibraryDesugaringConfig(@NonNull Project project) {
        Configuration coreLibraryDesugaring =
                project.getConfigurations().findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING);
        if (coreLibraryDesugaring == null) {
            coreLibraryDesugaring =
                    project.getConfigurations().create(CONFIG_NAME_CORE_LIBRARY_DESUGARING);
            coreLibraryDesugaring.setVisible(false);
            coreLibraryDesugaring.setCanBeConsumed(false);
            coreLibraryDesugaring.setDescription("Configuration to desugar libraries");
        }
    }

    protected void createDependencyStreams(@NonNull final VariantScope variantScope) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.
        handleJacocoDependencies(variantScope);

        TransformManager transformManager = variantScope.getTransformManager();

        // This might be consumed by RecalculateFixedStackFrames if that's created
        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, EXTERNAL, CLASSES_JAR))
                        .build());

        // Add stream of external java resources if EXTERNAL_LIBRARIES isn't in the set of java res
        // merging scopes.
        if (!getJavaResMergingScopes(variantScope, RESOURCES).contains(Scope.EXTERNAL_LIBRARIES)) {
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-java-res")
                            .addContentTypes(RESOURCES)
                            .addScope(Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            RUNTIME_CLASSPATH, EXTERNAL, JAVA_RES))
                            .build());
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                variantScope.getArtifactCollection(
                                        RUNTIME_CLASSPATH, PROJECT, CLASSES_JAR))
                        .build());

        // same for the java resources, if SUB_PROJECTS isn't in the set of java res merging scopes.
        if (!getJavaResMergingScopes(variantScope, RESOURCES).contains(Scope.SUB_PROJECTS)) {
            transformManager.addStream(
                    OriginalStream.builder(project, "sub-projects-java-res")
                            .addContentTypes(RESOURCES)
                            .addScope(Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            RUNTIME_CLASSPATH, PROJECT, JAVA_RES))
                            .build());
        }

        // if variantScope.consumesFeatureJars(), add streams of classes from features or
        // dynamic-features.
        // The main dex list calculation for the bundle also needs the feature classes for reference
        // only
        if (variantScope.consumesFeatureJars() || variantScope.getNeedsMainDexListForBundle()) {
            transformManager.addStream(
                    OriginalStream.builder(project, "metadata-classes")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(InternalScope.FEATURES)
                            .setArtifactCollection(
                                    variantScope.getArtifactCollection(
                                            REVERSE_METADATA_VALUES,
                                            PROJECT,
                                            REVERSE_METADATA_CLASSES))
                            .build());
        }

        // provided only scopes.
        transformManager.addStream(
                OriginalStream.builder(project, "provided-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.PROVIDED_ONLY)
                        .setFileCollection(variantScope.getProvidedOnlyClasspath())
                        .build());

        if (variantScope.getTestedVariantData() != null) {
            final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

            VariantScope testedVariantScope = testedVariantData.getScope();

            PublishingSpecs.VariantSpec testedSpec =
                    testedVariantScope
                            .getPublishingSpec()
                            .getTestingSpec(variantScope.getVariantDslInfo().getVariantType());

            // get the OutputPublishingSpec from the ArtifactType for this particular variant spec
            PublishingSpecs.OutputSpec taskOutputSpec =
                    testedSpec.getSpec(
                            AndroidArtifacts.ArtifactType.CLASSES_JAR,
                            AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS);
            // now get the output type
            SingleArtifactType<Directory> testedOutputType =
                    (SingleArtifactType<Directory>)
                            Objects.requireNonNull(taskOutputSpec).getOutputType();

            variantScope
                    .getArtifacts()
                    .copy(
                            InternalArtifactType.TESTED_CODE_CLASSES.INSTANCE,
                            testedVariantScope.getArtifacts(),
                            testedOutputType);

            // create two streams of different types.
            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-classes")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setFileCollection(
                                    testedVariantScope
                                            .getArtifacts()
                                            .getFinalProductAsFileCollection(testedOutputType)
                                            .get())
                            .build());

            transformManager.addStream(
                    OriginalStream.builder(project, "tested-code-deps")
                            .addContentTypes(DefaultContentType.CLASSES)
                            .addScope(Scope.TESTED_CODE)
                            .setArtifactCollection(
                                    testedVariantScope.getArtifactCollection(
                                            RUNTIME_CLASSPATH, ALL, CLASSES_JAR))
                            .build());
        }
    }

    public void createBuildArtifactReportTask(@NonNull VariantScope scope) {
        taskFactory.register(new BuildArtifactReportTask.BuildArtifactReportCreationAction(scope));
    }

    public void createMergeApkManifestsTask(@NonNull VariantScope variantScope) {
        AndroidArtifactVariantData androidArtifactVariantData =
                (AndroidArtifactVariantData) variantScope.getVariantData();
        Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

        taskFactory.register(
                new CompatibleScreensManifest.CreationAction(variantScope, screenSizes));

        TaskProvider<? extends ManifestProcessorTask> processManifestTask =
                createMergeManifestTask(variantScope);

        final MutableTaskContainer taskContainer = variantScope.getTaskContainer();
        if (taskContainer.getMicroApkTask() != null) {
            TaskFactoryUtils.dependsOn(processManifestTask, taskContainer.getMicroApkTask());
        }
    }

    /** Returns whether or not dependencies from the {@link CustomClassTransform} are packaged */
    protected static boolean packagesCustomClassDependencies(
            @NonNull VariantScope scope, @NonNull ProjectOptions options) {
        return appliesCustomClassTransforms(scope, options) && !scope.getType().isDynamicFeature();
    }

    /** Returns whether or not custom class transforms are applied */
    protected static boolean appliesCustomClassTransforms(
            @NonNull VariantScope scope, @NonNull ProjectOptions options) {
        final VariantType type = scope.getType();
        return scope.getVariantDslInfo().isDebuggable()
                && type.isApk()
                && !type.isForTesting()
                && !getAdvancedProfilingTransforms(options).isEmpty();
    }

    @NonNull
    private static List<String> getAdvancedProfilingTransforms(@NonNull ProjectOptions options) {
        String string = options.get(StringOption.IDE_ANDROID_CUSTOM_CLASS_TRANSFORMS);
        if (string == null) {
            return ImmutableList.of();
        }
        return Splitter.on(',').splitToList(string);
    }

    /** Creates the merge manifests task. */
    @NonNull
    protected TaskProvider<? extends ManifestProcessorTask> createMergeManifestTask(
            @NonNull VariantScope variantScope) {
        return taskFactory.register(
                new ProcessApplicationManifest.CreationAction(
                        variantScope.getVariantData().getPublicVariantPropertiesApi(),
                        !getAdvancedProfilingTransforms(projectOptions).isEmpty()));
    }

    public void createMergeLibManifestsTask(@NonNull VariantScope scope) {
        taskFactory.register(
                new ProcessLibraryManifest.CreationAction(
                        scope.getVariantData().getPublicVariantPropertiesApi(), scope));
    }

    protected void createProcessTestManifestTask(
            @NonNull VariantScope scope,
            @NonNull VariantScope testedScope) {

        Provider<Directory> mergedManifest =
                testedScope.getArtifacts().getFinalProduct(MERGED_MANIFESTS.INSTANCE);
        taskFactory.register(
                new ProcessTestManifest.CreationAction(scope, project.files(mergedManifest)));
    }

    public void createRenderscriptTask(@NonNull VariantScope scope) {
        if (scope.getGlobalScope().getBuildFeatures().getRenderScript()) {
            final MutableTaskContainer taskContainer = scope.getTaskContainer();

            TaskProvider<RenderscriptCompile> rsTask =
                    taskFactory.register(new RenderscriptCompile.CreationAction(scope));

            VariantDslInfo variantDslInfo = scope.getVariantDslInfo();

            TaskFactoryUtils.dependsOn(taskContainer.getResourceGenTask(), rsTask);
            // only put this dependency if rs will generate Java code
            if (!variantDslInfo.getRenderscriptNdkModeEnabled()) {
                TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), rsTask);
            }
        }
    }

    public void createMergeResourcesTask(
            @NonNull VariantScope scope,
            boolean processResources,
            ImmutableSet<MergeResources.Flag> flags) {

        boolean alsoOutputNotCompiledResources =
                scope.getType().isApk()
                        && !scope.getType().isForTesting()
                        && scope.useResourceShrinker();

        basicCreateMergeResourcesTask(
                scope,
                MergeType.MERGE,
                null /*outputLocation*/,
                true /*includeDependencies*/,
                processResources,
                alsoOutputNotCompiledResources,
                flags,
                null /*configCallback*/);
    }

    /** Defines the merge type for {@link #basicCreateMergeResourcesTask} */
    public enum MergeType {
        /** Merge all resources with all the dependencies resources (i.e. "big merge"). */
        MERGE {
            @Override
            public SingleArtifactType<Directory> getOutputType() {
                return InternalArtifactType.MERGED_RES.INSTANCE;
            }
        },
        /**
         * Merge all resources without the dependencies resources for an aar (i.e. "small merge").
         */
        PACKAGE {
            @Override
            public SingleArtifactType<Directory> getOutputType() {
                return InternalArtifactType.PACKAGED_RES.INSTANCE;
            }
        };

        public abstract SingleArtifactType<Directory> getOutputType();
    }

    public TaskProvider<MergeResources> basicCreateMergeResourcesTask(
            @NonNull VariantScope scope,
            @NonNull MergeType mergeType,
            @Nullable File outputLocation,
            final boolean includeDependencies,
            final boolean processResources,
            boolean alsoOutputNotCompiledResources,
            @NonNull ImmutableSet<MergeResources.Flag> flags,
            @Nullable TaskProviderCallback<MergeResources> taskProviderCallback) {

        String taskNamePrefix = mergeType.name().toLowerCase(Locale.ENGLISH);

        File mergedNotCompiledDir =
                alsoOutputNotCompiledResources
                        ? new File(
                                globalScope.getIntermediatesDir()
                                        + "/merged-not-compiled-resources/"
                                        + scope.getVariantDslInfo().getDirName())
                        : null;

        TaskProvider<MergeResources> mergeResourcesTask =
                taskFactory.register(
                        new MergeResources.CreationAction(
                                scope,
                                mergeType,
                                taskNamePrefix,
                                mergedNotCompiledDir,
                                includeDependencies,
                                processResources,
                                flags,
                                isLibrary()),
                        null,
                        null,
                        taskProviderCallback);

        scope.getArtifacts()
                .producesDir(
                        mergeType.getOutputType(),
                        mergeResourcesTask,
                        MergeResources::getOutputDir,
                        MoreObjects.firstNonNull(
                                        outputLocation, scope.getDefaultMergeResourcesOutputDir())
                                .getAbsolutePath(),
                        "");

        if (alsoOutputNotCompiledResources) {
            scope.getArtifacts()
                    .producesDir(
                            MERGED_NOT_COMPILED_RES.INSTANCE,
                            mergeResourcesTask,
                            MergeResources::getMergedNotCompiledResourcesOutputDirectory,
                            mergedNotCompiledDir.getAbsolutePath(),
                            "");
        }

        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            TaskFactoryUtils.dependsOn(
                    scope.getTaskContainer().getCompileTask(), mergeResourcesTask);
        }

        return mergeResourcesTask;
    }

    public void createMergeAssetsTask(@NonNull VariantScope scope) {
        taskFactory.register(new MergeSourceSetFolders.MergeAppAssetCreationAction(scope));
    }

    public void createMergeJniLibFoldersTasks(@NonNull final VariantScope variantScope) {
        // merge the source folders together using the proper priority.
        taskFactory.register(
                new MergeSourceSetFolders.MergeJniLibFoldersCreationAction(variantScope));

        // Compute the scopes that need to be merged.
        Set<ScopeType> mergeScopes = getJavaResMergingScopes(variantScope, NATIVE_LIBS);

        taskFactory.register(new MergeNativeLibsTask.CreationAction(mergeScopes, variantScope));
    }

    public void createBuildConfigTask(@NonNull VariantScope scope) {
        if (scope.getGlobalScope().getBuildFeatures().getBuildConfig()) {
            TaskProvider<GenerateBuildConfig> generateBuildConfigTask =
                    taskFactory.register(
                            new GenerateBuildConfig.CreationAction(
                                    scope.getVariantData().getPublicVariantPropertiesApi()));

            TaskFactoryUtils.dependsOn(
                    scope.getTaskContainer().getSourceGenTask(), generateBuildConfigTask);
        }
    }

    public void createGenerateResValuesTask(@NonNull VariantScope scope) {
        if (scope.getGlobalScope().getBuildFeatures().getResValues()) {
            TaskProvider<GenerateResValues> generateResValuesTask =
                    taskFactory.register(new GenerateResValues.CreationAction(scope));
            TaskFactoryUtils.dependsOn(
                    scope.getTaskContainer().getResourceGenTask(), generateResValuesTask);
        }
    }

    public void createApkProcessResTask(
            @NonNull VariantScope scope) {
        VariantType variantType = scope.getVariantData().getVariantDslInfo().getVariantType();
        InternalArtifactType<Directory> packageOutputType =
                (variantType.isApk() && !variantType.isForTesting())
                        ? FEATURE_RESOURCE_PKG.INSTANCE
                        : null;

        createApkProcessResTask(scope, packageOutputType);

        if (scope.consumesFeatureJars()) {
            taskFactory.register(new MergeAaptProguardFilesCreationAction(scope));
        }
    }

    private void createApkProcessResTask(
            @NonNull VariantScope scope,
            @Nullable SingleArtifactType<Directory> packageOutputType) {

        // Create the APK_ file with processed resources and manifest. Generate the R class.
        createProcessResTask(
                scope,
                packageOutputType,
                MergeType.MERGE,
                scope.getGlobalScope().getProjectBaseName());

        // TODO(b/138780301): Also use it in android tests.
        if (projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS)
                && !scope.getType().isForTesting()
                && !scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            // Generate the COMPILE TIME only R class using the local resources instead of waiting
            // for the above full link to finish. Linking will still output the RUN TIME R class.
            // Since we're gonna use AAPT2 to generate the keep rules, do not generate them here.
            createProcessResTask(
                    scope,
                    packageOutputType,
                    MergeType.PACKAGE,
                    scope.getGlobalScope().getProjectBaseName());
        }
    }

    protected boolean isLibrary() {
        return false;
    }

    public void createProcessResTask(
            @NonNull VariantScope scope,
            @Nullable SingleArtifactType<Directory> packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        BaseVariantData variantData = scope.getVariantData();

        variantData.calculateFilters(scope.getGlobalScope().getExtension().getSplits());

        // The manifest main dex list proguard rules are always needed for the bundle,
        // even if legacy multidex is not explicitly enabled.
        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules = scope.getNeedsMainDexList();

        if (scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            // TODO: make sure we generate the proguard rules in the namespaced case.
            new NamespacedResourcesTaskManager(globalScope, taskFactory, scope)
                    .createNamespacedResourceTasks(
                            packageOutputType,
                            baseName,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules);

            FileCollection rFiles =
                    project.files(
                            scope.getArtifacts().getFinalProduct(RUNTIME_R_CLASS_CLASSES.INSTANCE));

            scope.getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "final-r-classes")
                                    .addContentTypes(
                                            scope.getNeedsJavaResStreams()
                                                    ? TransformManager.CONTENT_JARS
                                                    : ImmutableSet.of(DefaultContentType.CLASSES))
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(rFiles)
                                    .build());

            scope.getArtifacts().appendToAllClasses(rFiles);
            return;
        }
        createNonNamespacedResourceTasks(
                scope,
                packageOutputType,
                mergeType,
                baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules);
    }

    private void createNonNamespacedResourceTasks(
            @NonNull VariantScope scope,
            SingleArtifactType<Directory> packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules) {

        BuildArtifactsHolder artifacts = scope.getArtifacts();
        if (mergeType == MergeType.PACKAGE) {
            // MergeType.PACKAGE means we will only merged the resources from our current module
            // (little merge). This is used for finding what goes into the AAR (packaging), and also
            // for parsing the local resources and merging them with the R.txt files from its
            // dependencies to write the R.txt for this module and R.jar for this module and its
            // dependencies.

            // First collect symbols from this module.
            taskFactory.register(new ParseLibraryResourcesTask.CreateAction(scope));

            // Only generate the keep rules when we need them. We don't need to generate them here
            // for non-library modules since AAPT2 will generate them from MergeType.MERGE.
            if (generatesProguardOutputFile(scope) && isLibrary()) {
                taskFactory.register(new GenerateLibraryProguardRulesTask.CreationAction(scope));
            }

            // Generate the R class for a library using both local symbols and symbols
            // from dependencies.
            taskFactory.register(
                    new GenerateLibraryRFileTask.CreationAction(
                            scope.getVariantData().getPublicVariantPropertiesApi(), isLibrary()));
        } else {
            // MergeType.MERGE means we merged the whole universe.
            taskFactory.register(
                    createProcessAndroidResourcesConfigAction(
                            scope,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules,
                            mergeType,
                            baseName));

            if (packageOutputType != null) {
                artifacts.republish(PROCESSED_RES.INSTANCE, packageOutputType);
            }

            // create the task that creates the aapt output for the bundle.
            taskFactory.register(new LinkAndroidResForBundleTask.CreationAction(scope));

            if (!projectOptions.get(BooleanOption.GENERATE_R_JAVA)) {
                scope.getArtifacts()
                        .appendToAllClasses(
                                artifacts
                                        .getFinalProductAsFileCollection(
                                                COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                                                        .INSTANCE)
                                        .get());
            }
        }
    }

    private static boolean generatesProguardOutputFile(VariantScope variantScope) {
        return variantScope.getCodeShrinker() != null || variantScope.getType().isDynamicFeature();
    }

    protected VariantTaskCreationAction<LinkApplicationAndroidResourcesTask>
            createProcessAndroidResourcesConfigAction(
                    @NonNull VariantScope scope,
                    boolean useAaptToGenerateLegacyMultidexMainDexProguardRules,
                    @NonNull MergeType sourceArtifactType,
                    @NonNull String baseName) {

        return new LinkApplicationAndroidResourcesTask.CreationAction(
                scope.getVariantData().getPublicVariantPropertiesApi(),
                useAaptToGenerateLegacyMultidexMainDexProguardRules,
                sourceArtifactType,
                baseName,
                isLibrary());
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     *
     * @param variantScope the scope of the variant being processed.
     * @param contentType the contentType of java resources, must be RESOURCES or NATIVE_LIBS
     * @return the list of scopes for which to merge the java resources.
     */
    @NonNull
    protected abstract Set<ScopeType> getJavaResMergingScopes(
            @NonNull VariantScope variantScope, @NonNull QualifiedContent.ContentType contentType);

    /**
     * Creates the java resources processing tasks.
     *
     * <p>The java processing will happen in two steps:
     *
     * <ul>
     *   <li>{@link Sync} task configured with {@link ProcessJavaResTask.CreationAction} will sync
     *       all source folders into a single folder identified by {@link InternalArtifactType}
     *   <li>{@link MergeJavaResourceTask} will take the output of this merge plus the dependencies
     *       and will create a single merge with the {@link PackagingOptions} settings applied.
     * </ul>
     *
     * This sets up only the Sync part. The java res merging is setup via {@link
     * #createMergeJavaResTask(VariantScope)}
     *
     * @param variantScope the variant scope we are operating under.
     */
    public void createProcessJavaResTask(@NonNull VariantScope variantScope) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.
        taskFactory.register(new ProcessJavaResTask.CreationAction(variantScope));

        // create the stream generated from this task, but only if a library with custom transforms,
        // in which case the custom transforms must be applied before java res merging.
        if (variantScope.getNeedsJavaResStreams()) {
            variantScope
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "processed-java-res")
                                    .addContentType(RESOURCES)
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(
                                            variantScope
                                                    .getGlobalScope()
                                                    .getProject()
                                                    .files(
                                                            variantScope
                                                                    .getArtifacts()
                                                                    .getFinalProduct(
                                                                            InternalArtifactType
                                                                                    .JAVA_RES
                                                                                    .INSTANCE)))
                                    .build());
        }
    }

    /**
     * Sets up the Merge Java Res task.
     *
     * @param variantScope the variant scope we are operating under.
     * @see #createProcessJavaResTask(VariantScope)
     */
    public void createMergeJavaResTask(@NonNull VariantScope variantScope) {
        TransformManager transformManager = variantScope.getTransformManager();

        // Compute the scopes that need to be merged.
        Set<ScopeType> mergeScopes = getJavaResMergingScopes(variantScope, RESOURCES);

        taskFactory.register(new MergeJavaResourceTask.CreationAction(mergeScopes, variantScope));

        // also add a new merged java res stream if needed.
        if (variantScope.getNeedsMergedJavaResStream()) {
            Provider<RegularFile> mergedJavaResProvider =
                    variantScope.getArtifacts().getFinalProduct(MERGED_JAVA_RES.INSTANCE);
            transformManager.addStream(
                    OriginalStream.builder(project, "merged-java-res")
                            .addContentTypes(TransformManager.CONTENT_RESOURCES)
                            .addScopes(mergeScopes)
                            .setFileCollection(project.getLayout().files(mergedJavaResProvider))
                            .build());
        }


    }

    public void createAidlTask(@NonNull VariantScope scope) {
        if (scope.getGlobalScope().getBuildFeatures().getAidl()) {
            MutableTaskContainer taskContainer = scope.getTaskContainer();

            TaskProvider<AidlCompile> aidlCompileTask =
                    taskFactory.register(new AidlCompile.CreationAction(scope));

            TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), aidlCompileTask);
        }
    }

    public void createShaderTask(@NonNull VariantScope scope) {
        if (scope.getGlobalScope().getBuildFeatures().getShaders()) {
            // merge the shader folders together using the proper priority.
            taskFactory.register(
                    new MergeSourceSetFolders.MergeShaderSourceFoldersCreationAction(scope));

            // compile the shaders
            TaskProvider<ShaderCompile> shaderCompileTask =
                    taskFactory.register(new ShaderCompile.CreationAction(scope));

            TaskFactoryUtils.dependsOn(
                    scope.getTaskContainer().getAssetGenTask(), shaderCompileTask);
        }
    }

    protected abstract void postJavacCreation(@NonNull final VariantScope scope);

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    public TaskProvider<? extends JavaCompile> createJavacTask(@NonNull final VariantScope scope) {
        taskFactory.register(new JavaPreCompileTask.CreationAction(scope));

        final TaskProvider<? extends JavaCompile> javacTask =
                taskFactory.register(new JavaCompileCreationAction(scope));

        postJavacCreation(scope);

        return javacTask;
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     * <p>This should not be called for classes that will also be compiled from source by jack.
     */
    protected void addJavacClassesStream(VariantScope scope) {
        BuildArtifactsHolder artifacts = scope.getArtifacts();
        Provider<Directory> javaOutputs = artifacts.getFinalProduct(JAVAC.INSTANCE);
        Preconditions.checkNotNull(javaOutputs);
        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks
        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "javac-output")
                                // Need both classes and resources because some annotation
                                // processors generate resources
                                .addContentTypes(
                                        scope.getNeedsJavaResStreams()
                                                ? TransformManager.CONTENT_JARS
                                                : ImmutableSet.of(DefaultContentType.CLASSES))
                                .addScope(Scope.PROJECT)
                                .setFileCollection(project.getLayout().files(javaOutputs))
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "pre-javac-generated-bytecode")
                                .addContentTypes(
                                        scope.getNeedsJavaResStreams()
                                                ? TransformManager.CONTENT_JARS
                                                : ImmutableSet.of(DefaultContentType.CLASSES))
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getAllPreJavacGeneratedBytecode())
                                .build());

        scope.getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "post-javac-generated-bytecode")
                                .addContentTypes(
                                        scope.getNeedsJavaResStreams()
                                                ? TransformManager.CONTENT_JARS
                                                : ImmutableSet.of(DefaultContentType.CLASSES))
                                .addScope(Scope.PROJECT)
                                .setFileCollection(
                                        scope.getVariantData().getAllPostJavacGeneratedBytecode())
                                .build());

        if (scope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()
                && projectOptions.get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
            // This might be consumed by RecalculateFixedStackFrames if that's created
            scope.getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "auto-namespaced-dependencies-classes")
                                    .addContentTypes(DefaultContentType.CLASSES)
                                    .addScope(Scope.EXTERNAL_LIBRARIES)
                                    .setFileCollection(
                                            artifacts
                                                    .getFinalProductAsFileCollection(
                                                            InternalArtifactType
                                                                    .NAMESPACED_CLASSES_JAR
                                                                    .INSTANCE)
                                                    .get())
                                    .build());
        }
    }

    protected void createCompileTask(@NonNull VariantScope variantScope) {
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);
    }


    /** Makes the given task the one used by top-level "compile" task. */
    public static void setJavaCompilerTask(
            @NonNull TaskProvider<? extends JavaCompile> javaCompilerTask,
            @NonNull VariantScope scope) {
        TaskFactoryUtils.dependsOn(scope.getTaskContainer().getCompileTask(), javaCompilerTask);
    }

    /**
     * Creates the task that will handle micro apk.
     *
     * New in 2.2, it now supports the unbundled mode, in which the apk is not bundled
     * anymore, but we still have an XML resource packaged, and a custom entry in the manifest.
     * This is triggered by passing a null {@link Configuration} object.
     *
     * @param scope the variant scope
     * @param config an optional Configuration object. if non null, this will embed the micro apk,
     *               if null this will trigger the unbundled mode.
     */
    public void createGenerateMicroApkDataTask(
            @NonNull VariantScope scope,
            @Nullable FileCollection config) {
        TaskProvider<GenerateApkDataTask> generateMicroApkTask =
                taskFactory.register(
                        new GenerateApkDataTask.CreationAction(
                                scope.getVariantData().getPublicVariantPropertiesApi(), config));

        // the merge res task will need to run after this one.
        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getResourceGenTask(), generateMicroApkTask);
    }

    public void createExternalNativeBuildJsonGenerators(@NonNull VariantScope scope) {
        CxxModuleModel module = tryCreateCxxModuleModel(scope.getGlobalScope());

        if (module == null) {
            return;
        }

        scope.getTaskContainer()
                .setExternalNativeJsonGenerator(
                        project.provider(() -> ExternalNativeJsonGenerator.create(module, scope)));
    }

    public void createExternalNativeBuildTasks(@NonNull VariantScope scope) {
        final MutableTaskContainer taskContainer = scope.getTaskContainer();
        Provider<ExternalNativeJsonGenerator> generator =
                taskContainer.getExternalNativeJsonGenerator();
        if (generator == null) {
            return;
        }

        // Set up JSON generation tasks
        TaskProvider<? extends Task> generateTask =
                taskFactory.register(
                        ExternalNativeBuildJsonTask.createTaskConfigAction(generator, scope));

        // Set up build tasks
        TaskProvider<ExternalNativeBuildTask> buildTask =
                taskFactory.register(
                        new ExternalNativeBuildTask.CreationAction(generator, generateTask, scope));

        TaskFactoryUtils.dependsOn(taskContainer.getCompileTask(), buildTask);

        // Set up clean tasks
        TaskProvider<Task> cleanTask = taskFactory.named("clean");
        CxxModuleModel module = tryCreateCxxModuleModel(scope.getGlobalScope());

        if (module != null) {
            TaskProvider<ExternalNativeCleanTask> externalNativeCleanTask =
                    taskFactory.register(new ExternalNativeCleanTask.CreationAction(module, scope));
            TaskFactoryUtils.dependsOn(cleanTask, externalNativeCleanTask);
        }
    }

    /** Creates the tasks to build unit tests. */
    public void createUnitTestVariantTasks(@NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        BuildArtifactsHolder artifacts = variantScope.getArtifacts();
        BaseVariantData testedVariantData =
                checkNotNull(variantScope.getTestedVariantData(), "Not a unit test variant");
        VariantScope testedVariantScope = testedVariantData.getScope();

        boolean includeAndroidResources = extension.getTestOptions().getUnitTests()
                .isIncludeAndroidResources();

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // process java resources
        createProcessJavaResTask(variantScope);

        if (includeAndroidResources) {
            if (testedVariantScope.getType().isAar()) {
                // Add a task to process the manifest
                createProcessTestManifestTask(variantScope, testedVariantData.getScope());

                // Add a task to create the res values
                createGenerateResValuesTask(variantScope);

                // Add a task to merge the assets folders
                createMergeAssetsTask(variantScope);

                createMergeResourcesTask(variantScope, true, ImmutableSet.of());
                // Add a task to process the Android Resources and generate source files
                createApkProcessResTask(variantScope, FEATURE_RESOURCE_PKG.INSTANCE);
                taskFactory.register(new PackageForUnitTest.CreationAction(variantScope));

                // Add data binding tasks if enabled
                createDataBindingTasksIfNecessary(variantScope);
            } else if (testedVariantScope.getType().isApk()) {
                // The IDs will have been inlined for an non-namespaced application
                // so just re-export the artifacts here.
                artifacts.copy(PROCESSED_RES.INSTANCE, testedVariantScope.getArtifacts());
                artifacts.copy(MERGED_ASSETS.INSTANCE, testedVariantScope.getArtifacts());

                taskFactory.register(new PackageForUnitTest.CreationAction(variantScope));
            } else {
                throw new IllegalStateException(
                        "Tested variant "
                                + testedVariantScope.getName()
                                + " in "
                                + globalScope.getProject().getPath()
                                + " must be a library or an application to have unit tests.");
            }

            TaskProvider<GenerateTestConfig> generateTestConfig =
                    taskFactory.register(new GenerateTestConfig.CreationAction(variantScope));
            TaskProvider<? extends Task> compileTask =
                    variantScope.getTaskContainer().getCompileTask();
            TaskFactoryUtils.dependsOn(compileTask, generateTestConfig);
            // The GenerateTestConfig task has 2 types of inputs: direct inputs and indirect inputs.
            // Only the direct inputs are registered with Gradle, whereas the indirect inputs are
            // not (see that class for details).
            // Since the compile task also depends on the indirect inputs to the GenerateTestConfig
            // task, making the compile task depend on the GenerateTestConfig task is not enough, we
            // also need to register those inputs with Gradle explicitly here. (We can't register
            // @Nested objects programmatically, so it's important to keep these inputs consistent
            // with those defined in TestConfigInputs.)
            compileTask.configure(
                    task -> {
                        GenerateTestConfig.TestConfigInputs testConfigInputs =
                                new GenerateTestConfig.TestConfigInputs(variantScope);
                        TaskInputs taskInputs = task.getInputs();
                        taskInputs.property(
                                "isUseRelativePathEnabled",
                                testConfigInputs.isUseRelativePathEnabled());
                        taskInputs
                                .files(testConfigInputs.getResourceApk())
                                .withPropertyName("resourceApk")
                                .optional()
                                .withPathSensitivity(PathSensitivity.RELATIVE);
                        taskInputs
                                .files(testConfigInputs.getMergedAssets())
                                .withPropertyName("mergedAssets")
                                .withPathSensitivity(PathSensitivity.RELATIVE);
                        taskInputs
                                .files(testConfigInputs.getMergedManifest())
                                .withPropertyName("mergedManifest")
                                .withPathSensitivity(PathSensitivity.RELATIVE);
                        taskInputs.property("mainApkInfo", testConfigInputs.getMainApkInfo());
                        taskInputs.property(
                                "packageNameOfFinalRClassProvider",
                                (Supplier<String>) testConfigInputs::getPackageNameOfFinalRClass);
                    });
        } else {
            if (testedVariantScope.getType().isAar()) {
                // With compile classpath R classes, we need to generate a dummy R class for unit tests
                // See https://issuetracker.google.com/143762955 for more context.
                taskFactory.register(
                        new GenerateLibraryRFileTask.TestRuntimeStubRClassCreationAction(
                                variantScope.getVariantData().getPublicVariantPropertiesApi()));
            }
        }

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
        // dependencies on tasks that prepare necessary data files.
        TaskProvider<? extends Task> compileTask = variantScope.getTaskContainer().getCompileTask();
        //noinspection unchecked
        TaskFactoryUtils.dependsOn(
                compileTask,
                variantScope.getTaskContainer().getProcessJavaResourcesTask(),
                testedVariantScope.getTaskContainer().getProcessJavaResourcesTask());

        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        // This should be done automatically by the classpath
        //        TaskFactoryUtils.dependsOn(javacTask, testedVariantScope.getTaskContainer().getJavacTask());

        // TODO: use merged java res for unit tests (bug 118690729)

        createRunUnitTestTask(variantScope);

        // This hides the assemble unit test task from the task list.

        variantScope.getTaskContainer().getAssembleTask().configure(task -> task.setGroup(null));
    }

    protected void registerRClassTransformStream(@NonNull VariantScope variantScope) {
        if (globalScope.getExtension().getAaptOptions().getNamespaced()
                || projectOptions.get(BooleanOption.GENERATE_R_JAVA)) {
            return;
        }

        Provider<FileCollection> rClassJar =
                variantScope
                        .getArtifacts()
                        .getFinalProductAsFileCollection(
                                InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                                        .INSTANCE);

        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "compile-and-runtime-light-r-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(QualifiedContent.Scope.PROJECT)
                                .setFileCollection(rClassJar.get())
                                .build());
    }

    /** Creates the tasks to build android tests. */
    public void createAndroidTestVariantTasks(
            @NonNull TestVariantData variantData,
            @NonNull List<VariantScope> variantScopesForLint) {
        VariantScope variantScope = variantData.getScope();

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // Add a task to process the manifest
        createProcessTestManifestTask(
                variantScope, checkNotNull(variantScope.getTestedVariantData()).getScope());

        // Add a task to create the res values
        createGenerateResValuesTask(variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(variantScope, true, ImmutableSet.of());

        // Add tasks to compile shader
        createShaderTask(variantScope);

        // Add a task to merge the assets folders
        createMergeAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to generate resource source files
        createApkProcessResTask(variantScope);

        registerRClassTransformStream(variantScope);

        // process java resources
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(variantScope);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope);

        // Add a task to compile the test application
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);

        // Add a task to produce the signing config file
        createValidateSigningTask(variantScope);
        taskFactory.register(new SigningConfigWriterTask.CreationAction(variantScope));

        createPackagingTask(variantScope);

        maybeCreateLintVitalTask(
                (ApkVariantData) variantScope.getVariantData(), variantScopesForLint);

        taskFactory.configure(
                ASSEMBLE_ANDROID_TEST,
                assembleTest ->
                        assembleTest.dependsOn(
                                variantData.getTaskContainer().getAssembleTask().getName()));

        createConnectedTestForVariant(variantScope);
    }

    /** Is the given variant relevant for lint? */
    static boolean isLintVariant(@NonNull VariantScope variantScope) {
        // Only create lint targets for variants like debug and release, not debugTest
        final VariantType variantType = variantScope.getVariantDslInfo().getVariantType();
        return !variantType.isForTesting();
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a lint task earlier
     * which runs on all variants.
     */
    public void createLintTasks(
            final VariantScope scope, @NonNull List<VariantScope> variantScopes) {
        if (!isLintVariant(scope)) {
            return;
        }
        taskFactory.register(new LintPerVariantTask.CreationAction(scope, variantScopes));
    }

    /** Returns the full path of a task given its name. */
    private String getTaskPath(String taskName) {
        return project.getRootProject() == project
                ? ':' + taskName
                : project.getPath() + ':' + taskName;
    }

    public void maybeCreateLintVitalTask(
            @NonNull ApkVariantData variantData, @NonNull List<VariantScope> variantScopes) {
        VariantScope variantScope = variantData.getScope();

        if (!isLintVariant(variantScope)
                || variantScope.getVariantDslInfo().isDebuggable()
                || !extension.getLintOptions().isCheckReleaseBuilds()) {
            return;
        }

        TaskProvider<LintPerVariantTask> lintReleaseCheck =
                taskFactory.register(
                        new LintPerVariantTask.VitalCreationAction(variantScope, variantScopes),
                        null,
                        task -> task.dependsOn(variantScope.getTaskContainer().getJavacTask()),
                        null);

        TaskFactoryUtils.dependsOn(
                variantScope.getTaskContainer().getAssembleTask(), lintReleaseCheck);

        // If lint is being run, we do not need to run lint vital.
        project.getGradle()
                .getTaskGraph()
                .whenReady(
                        taskGraph -> {
                            if (taskGraph.hasTask(getTaskPath(LINT))) {
                                project.getTasks()
                                        .getByName(lintReleaseCheck.getName())
                                        .setEnabled(false);
                            }
                        });
    }

    private void createRunUnitTestTask(
            @NonNull final VariantScope variantScope) {
        TaskProvider<AndroidUnitTest> runTestsTask =
                taskFactory.register(new AndroidUnitTest.CreationAction(variantScope));

        taskFactory.configure(JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask));
    }

    public void createTopLevelTestTasks(boolean hasFlavors) {
        createMockableJarTask();

        final List<String> reportTasks = Lists.newArrayListWithExpectedSize(2);

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // Task.

        TaskProvider<? extends Task> connectedAndroidTestTask;
        if (hasFlavors) {
            connectedAndroidTestTask =
                    taskFactory.register(
                            new AndroidReportTask.CreationAction(
                                    globalScope,
                                    AndroidReportTask.CreationAction.TaskKind.CONNECTED));
            reportTasks.add(connectedAndroidTestTask.getName());
        } else {
            connectedAndroidTestTask =
                    taskFactory.register(
                            CONNECTED_ANDROID_TEST,
                            connectedTask -> {
                                connectedTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                connectedTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "for all flavors on connected devices.");
                            });
        }

        taskFactory.configure(
                CONNECTED_CHECK, check -> check.dependsOn(connectedAndroidTestTask.getName()));

        TaskProvider<? extends Task> deviceAndroidTestTask;
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            deviceAndroidTestTask =
                    taskFactory.register(
                            new AndroidReportTask.CreationAction(
                                    globalScope,
                                    AndroidReportTask.CreationAction.TaskKind.DEVICE_PROVIDER));
            reportTasks.add(deviceAndroidTestTask.getName());
        } else {
            deviceAndroidTestTask =
                    taskFactory.register(
                            DEVICE_ANDROID_TEST,
                            providerTask -> {
                                providerTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                providerTask.setDescription(
                                        "Installs and runs instrumentation tests "
                                                + "using all Device Providers.");
                            });
        }

        taskFactory.configure(
                DEVICE_CHECK, check -> check.dependsOn(deviceAndroidTestTask.getName()));

        // Create top level unit test tasks.

        taskFactory.register(
                JavaPlugin.TEST_TASK_NAME,
                unitTestTask -> {
                    unitTestTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                    unitTestTask.setDescription("Run unit tests for all variants.");
                });
        taskFactory.configure(
                JavaBasePlugin.CHECK_TASK_NAME,
                check -> check.dependsOn(JavaPlugin.TEST_TASK_NAME));

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        //TODO: move to mustRunAfter once is stable.
        if (!reportTasks.isEmpty() && project.getGradle().getStartParameter()
                .isContinueOnFailure()) {
            project.getGradle()
                    .getTaskGraph()
                    .whenReady(
                            taskGraph -> {
                                for (String reportTask : reportTasks) {
                                    if (taskGraph.hasTask(getTaskPath(reportTask))) {
                                        taskFactory.configure(
                                                reportTask,
                                                task -> ((AndroidReportTask) task).setWillRun());
                                    }
                                }
                            });
        }
    }

    protected void createConnectedTestForVariant(@NonNull final VariantScope testVariantScope) {
        final BaseVariantData baseVariantData =
                checkNotNull(testVariantScope.getTestedVariantData());
        final TestVariantData testVariantData = (TestVariantData) testVariantScope.getVariantData();

        boolean isLibrary = baseVariantData.getVariantDslInfo().getVariantType().isAar();

        AbstractTestDataImpl testData;
        if (baseVariantData.getVariantDslInfo().getVariantType().isDynamicFeature()) {
            testData =
                    new BundleTestDataImpl(
                            testVariantData,
                            testVariantScope
                                    .getArtifacts()
                                    .getFinalProduct(InternalArtifactType.APK.INSTANCE),
                            FeatureSplitUtils.getFeatureName(globalScope.getProject().getPath()),
                            baseVariantData
                                    .getScope()
                                    .getArtifactFileCollection(
                                            RUNTIME_CLASSPATH, PROJECT, APKS_FROM_BUNDLE));
        } else {
            ConfigurableFileCollection testedApkFileCollection =
                    project.files(
                            testVariantData
                                    .getTestedVariantData()
                                    .getScope()
                                    .getArtifacts()
                                    .getFinalProduct(InternalArtifactType.APK.INSTANCE));

            testData =
                    new TestDataImpl(
                            testVariantData,
                            testVariantScope
                                    .getArtifacts()
                                    .getFinalProduct(InternalArtifactType.APK.INSTANCE),
                            isLibrary ? null : testedApkFileCollection);
        }

        configureTestData(testData);

        TaskProvider<DeviceProviderInstrumentTestTask> connectedTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                testVariantScope,
                                new ConnectedDeviceProvider(
                                        () ->
                                                globalScope
                                                        .getSdkComponents()
                                                        .getAdbExecutableProvider()
                                                        .get(),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(logger)),
                                DeviceProviderInstrumentTestTask.CreationAction.Type
                                        .INTERNAL_CONNECTED_DEVICE_PROVIDER,
                                testData,
                                project.files() /* testTargetMetadata */));

        taskFactory.configure(
                CONNECTED_ANDROID_TEST,
                connectedAndroidTest -> connectedAndroidTest.dependsOn(connectedTask));

        if (baseVariantData.getVariantDslInfo().isTestCoverageEnabled()) {

            Configuration jacocoAntConfiguration =
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                            project, JacocoTask.getJacocoVersion(testVariantScope));
            TaskProvider<JacocoReportTask> reportTask =
                    taskFactory.register(
                            new JacocoReportTask.CreationAction(
                                    testVariantScope, jacocoAntConfiguration));

            TaskFactoryUtils.dependsOn(
                    baseVariantData.getScope().getTaskContainer().getCoverageReportTask(),
                    reportTask);

            taskFactory.configure(
                    CONNECTED_ANDROID_TEST,
                    connectedAndroidTest -> connectedAndroidTest.dependsOn(reportTask));
        }

        List<DeviceProvider> providers = extension.getDeviceProviders();

        // now the providers.
        for (DeviceProvider deviceProvider : providers) {

            final TaskProvider<DeviceProviderInstrumentTestTask> providerTask =
                    taskFactory.register(
                            new DeviceProviderInstrumentTestTask.CreationAction(
                                    testVariantData.getScope(),
                                    deviceProvider,
                                    DeviceProviderInstrumentTestTask.CreationAction.Type
                                            .CUSTOM_DEVICE_PROVIDER,
                                    testData,
                                    project.files() /* testTargetMetadata */));

            taskFactory.configure(
                    DEVICE_ANDROID_TEST,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(providerTask));
        }

        // now the test servers
        List<TestServer> servers = extension.getTestServers();
        for (final TestServer testServer : servers) {
            final TaskProvider<TestServerTask> serverTask =
                    taskFactory.register(
                            new TestServerTask.TestServerTaskCreationAction(
                                    testVariantScope, testServer));
            TaskFactoryUtils.dependsOn(
                    serverTask, testVariantScope.getTaskContainer().getAssembleTask());

            taskFactory.configure(
                    DEVICE_CHECK, deviceAndroidTest -> deviceAndroidTest.dependsOn(serverTask));
        }
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps like
     * proguard and jacoco
     */
    public void createPostCompilationTasks(
            @NonNull final VariantScope variantScope) {

        checkNotNull(variantScope.getTaskContainer().getJavacTask());

        final BaseVariantData variantData = variantScope.getVariantData();
        final VariantDslInfo variantDslInfo = variantData.getVariantDslInfo();

        TransformManager transformManager = variantScope.getTransformManager();

        taskFactory.register(new MergeGeneratedProguardFilesCreationAction(variantScope));

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled =
                variantDslInfo.isTestCoverageEnabled()
                        && !variantDslInfo.getVariantType().isForTesting();
        if (isTestCoverageEnabled) {
            createJacocoTask(variantScope);
        }

        maybeCreateDesugarTask(
                variantScope,
                variantDslInfo.getMinSdkVersion(),
                transformManager,
                isTestCoverageEnabled);

        BaseExtension extension = variantScope.getGlobalScope().getExtension();

        // Merge Java Resources.
        createMergeJavaResTask(variantScope);

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        boolean registeredExternalTransform = false;
        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            List<Object> deps = customTransformsDependencies.get(i);
            registeredExternalTransform |=
                    transformManager
                            .addTransform(
                                    taskFactory,
                                    variantScope,
                                    transform,
                                    null,
                                    task -> {
                                        if (!deps.isEmpty()) {
                                            task.dependsOn(deps);
                                        }
                                    },
                                    taskProvider -> {
                                        // if the task is a no-op then we make assemble task depend on it.
                                        if (transform.getScopes().isEmpty()) {
                                            TaskFactoryUtils.dependsOn(
                                                    variantScope
                                                            .getTaskContainer()
                                                            .getAssembleTask(),
                                                    taskProvider);
                                        }
                                    })
                            .isPresent();
        }

        // Add a task to create merged runtime classes if this is a dynamic-feature,
        // or a base module consuming feature jars. Merged runtime classes are needed if code
        // minification is enabled in a project with features or dynamic-features.
        if (variantData.getType().isDynamicFeature() || variantScope.consumesFeatureJars()) {
            taskFactory.register(new MergeClassesTask.CreationAction(variantScope));
        }

        // ----- Android studio profiling transforms
        if (appliesCustomClassTransforms(variantScope, projectOptions)) {
            for (String jar : getAdvancedProfilingTransforms(projectOptions)) {
                if (jar != null) {
                    transformManager.addTransform(
                            taskFactory,
                            variantScope,
                            new CustomClassTransform(
                                    jar,
                                    packagesCustomClassDependencies(variantScope, projectOptions)));
                }
            }
        }

        // ----- Minify next -----
        maybeCreateCheckDuplicateClassesTask(variantScope);
        CodeShrinker shrinker = maybeCreateJavaCodeShrinkerTask(variantScope);
        if (shrinker == CodeShrinker.R8) {
            maybeCreateResourcesShrinkerTasks(variantScope);
            maybeCreateDexDesugarLibTask(variantScope, false);
            return;
        }

        // ----- Multi-Dex support
        DexingType dexingType = variantScope.getDexingType();

        // Upgrade from legacy multi-dex to native multi-dex if possible when using with a device
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            if (variantScope.getVariantDslInfo().isMultiDexEnabled()
                    && variantScope
                                    .getVariantDslInfo()
                                    .getMinSdkVersionWithTargetDeviceApi()
                                    .getFeatureLevel()
                            >= 21) {
                dexingType = DexingType.NATIVE_MULTIDEX;
            }
        }

        if (variantScope.getNeedsMainDexList()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, false));
        }

        if (variantScope.getNeedsMainDexListForBundle()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, true));
        }

        createDexTasks(variantScope, dexingType, registeredExternalTransform);

        maybeCreateResourcesShrinkerTasks(variantScope);

        maybeCreateDexSplitterTask(variantScope);
    }

    private void maybeCreateDesugarTask(
            @NonNull VariantScope variantScope,
            @NonNull AndroidVersion minSdk,
            @NonNull TransformManager transformManager,
            boolean isTestCoverageEnabled) {
        if (variantScope.getJava8LangSupportType() == Java8LangSupport.DESUGAR) {
            FileCache userCache = getUserIntermediatesCache();

            variantScope
                    .getTransformManager()
                    .consumeStreams(
                            ImmutableSet.of(Scope.EXTERNAL_LIBRARIES),
                            TransformManager.CONTENT_CLASS);

            taskFactory.register(
                    new RecalculateStackFramesTask.CreationAction(
                            variantScope, userCache, isTestCoverageEnabled));

            taskFactory.register(new DesugarTask.CreationAction(variantScope));

            if (minSdk.getFeatureLevel()
                    >= DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
                return;
            }

            QualifiedContent.ScopeType scopeType = Scope.EXTERNAL_LIBRARIES;
            if (variantScope.getVariantDslInfo().getVariantType().isTestComponent()) {
                BaseVariantData testedVariant =
                        Objects.requireNonNull(variantScope.getTestedVariantData());
                if (!testedVariant.getType().isAar()) {
                    // test variants, except for library, should not package try-with-resources jar
                    // as the tested variant already contains it.
                    scopeType = Scope.PROVIDED_ONLY;
                }
            }

            // add runtime classes for try-with-resources support
            TaskProvider<ExtractTryWithResourcesSupportJar> extractTryWithResources =
                    taskFactory.register(
                            new ExtractTryWithResourcesSupportJar.CreationAction(variantScope));
            variantScope.getTryWithResourceRuntimeSupportJar().builtBy(extractTryWithResources);
            transformManager.addStream(
                    OriginalStream.builder(project, "runtime-deps-try-with-resources")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(scopeType)
                            .setFileCollection(variantScope.getTryWithResourceRuntimeSupportJar())
                            .build());
        }
    }

    /**
     * Creates tasks used for DEX generation. This will use an incremental pipeline that uses dex
     * archives in order to enable incremental dexing support.
     */
    private void createDexTasks(
            @NonNull VariantScope variantScope,
            @NonNull DexingType dexingType,
            boolean registeredExternalTransform) {
        DefaultDexOptions dexOptions;
        if (variantScope.getVariantData().getType().isTestComponent()) {
            // Don't use custom dx flags when compiling the test FULL_APK. They can break the test FULL_APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        Java8LangSupport java8SLangSupport = variantScope.getJava8LangSupportType();
        boolean minified = variantScope.getCodeShrinker() != null;
        boolean supportsDesugaring =
                java8SLangSupport == Java8LangSupport.UNUSED
                        || (java8SLangSupport == Java8LangSupport.D8
                                && projectOptions.get(
                                        BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM));
        boolean enableDexingArtifactTransform =
                globalScope.getProjectOptions().get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)
                        && !registeredExternalTransform
                        && !minified
                        && supportsDesugaring
                        && !appliesCustomClassTransforms(variantScope, projectOptions);
        FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());

        taskFactory.register(
                new DexArchiveBuilderTask.CreationAction(
                        dexOptions, enableDexingArtifactTransform, userLevelCache, variantScope));

        maybeCreateDexDesugarLibTask(variantScope, enableDexingArtifactTransform);

        createDexMergingTasks(variantScope, dexingType, enableDexingArtifactTransform);
    }

    /**
     * Set up dex merging tasks when artifact transforms are used.
     *
     * <p>External libraries are merged in mono-dex and native multidex modes. In case of a native
     * multidex debuggable variant these dex files get packaged. In mono-dex case, we will re-merge
     * these files. Because this task will be almost always up-to-date, having a second merger run
     * over the external libraries will not cause a performance regression. In addition to that,
     * second dex merger will perform less I/O compared to reading all external library dex files
     * individually. For legacy multidex, we must merge all dex files in a single invocation in
     * order to generate correct primary dex file in presence of desugaring. See b/120039166.
     *
     * <p>When merging native multidex, debuggable variant, project's dex files are merged
     * independently. Also, the library projects' dex files are merged independently.
     *
     * <p>For all other variants (release, mono-dex, legacy-multidex), we merge all dex files in a
     * single invocation. This means that external libraries, library projects and project dex files
     * will be merged in a single task.
     */
    private void createDexMergingTasks(
            @NonNull VariantScope variantScope,
            @NonNull DexingType dexingType,
            boolean dexingUsingArtifactTransforms) {

        // When desugaring, The file dependencies are dexed in a task with the whole
        // remote classpath present, as they lack dependency information to desugar
        // them correctly in an artifact transform.
        boolean separateFileDependenciesDexingTask =
                variantScope.getJava8LangSupportType() == Java8LangSupport.D8
                        && dexingUsingArtifactTransforms;
        if (separateFileDependenciesDexingTask) {
            DexFileDependenciesTask.CreationAction desugarFileDeps =
                    new DexFileDependenciesTask.CreationAction(variantScope);
            taskFactory.register(desugarFileDeps);
        }

        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            DexMergingTask.CreationAction configAction =
                    new DexMergingTask.CreationAction(
                            variantScope,
                            DexMergingAction.MERGE_ALL,
                            dexingType,
                            dexingUsingArtifactTransforms,
                            separateFileDependenciesDexingTask);
            taskFactory.register(configAction);
        } else if (variantScope.getCodeShrinker() != null) {
            DexMergingTask.CreationAction configAction =
                    new DexMergingTask.CreationAction(
                            variantScope,
                            DexMergingAction.MERGE_ALL,
                            dexingType,
                            dexingUsingArtifactTransforms);
            taskFactory.register(configAction);
        } else {
            boolean produceSeparateOutputs =
                    dexingType == DexingType.NATIVE_MULTIDEX
                            && variantScope.getVariantDslInfo().isDebuggable();

            taskFactory.register(
                    new DexMergingTask.CreationAction(
                            variantScope,
                            DexMergingAction.MERGE_EXTERNAL_LIBS,
                            DexingType.NATIVE_MULTIDEX,
                            dexingUsingArtifactTransforms,
                            separateFileDependenciesDexingTask,
                            produceSeparateOutputs
                                    ? MultipleArtifactType.DEX.INSTANCE
                                    : MultipleArtifactType.EXTERNAL_LIBS_DEX.INSTANCE));

            if (produceSeparateOutputs) {
                DexMergingTask.CreationAction mergeProject =
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_PROJECT,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(mergeProject);

                DexMergingTask.CreationAction mergeLibraries =
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_LIBRARY_PROJECTS,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(mergeLibraries);
            } else {
                DexMergingTask.CreationAction configAction =
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_ALL,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(configAction);
            }
        }
    }

    @Nullable
    private FileCache getUserDexCache(boolean isMinifiedEnabled, boolean preDexLibraries) {
        if (!preDexLibraries || isMinifiedEnabled) {
            return null;
        }

        return getUserIntermediatesCache();
    }

    @Nullable
    private FileCache getUserIntermediatesCache() {
        if (globalScope
                .getProjectOptions()
                .get(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE)) {
            return globalScope.getBuildCache();
        } else {
            return null;
        }
    }

    protected void handleJacocoDependencies(@NonNull VariantScope variantScope) {
        VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();
        // we add the jacoco jar if coverage is enabled, but we don't add it
        // for test apps as it's already part of the tested app.
        // For library project, since we cannot use the local jars of the library,
        // we add it as well.
        boolean isTestCoverageEnabled =
                variantDslInfo.isTestCoverageEnabled()
                        && (!variantDslInfo.getVariantType().isTestComponent()
                                || (variantDslInfo.getTestedVariant() != null
                                        && variantDslInfo
                                                .getTestedVariant()
                                                .getVariantType()
                                                .isAar()));
        if (isTestCoverageEnabled) {
            if (variantScope.getDexer() == DexerTool.DX) {
                globalScope
                        .getDslScope()
                        .getIssueReporter()
                        .reportWarning(
                                Type.GENERIC,
                                String.format(
                                        "Jacoco version is downgraded to %s because dx is used. "
                                                + "This is due to -P%s=false flag. See "
                                                + "https://issuetracker.google.com/37116789 for "
                                                + "more details.",
                                        JacocoConfigurations.VERSION_FOR_DX,
                                        BooleanOption.ENABLE_D8.getPropertyName()));
            }

            String jacocoAgentRuntimeDependency =
                    JacocoConfigurations.getAgentRuntimeDependency(
                            JacocoTask.getJacocoVersion(variantScope));
            project.getDependencies()
                    .add(
                            variantScope.getVariantDependencies().getRuntimeClasspath().getName(),
                            jacocoAgentRuntimeDependency);

            // we need to force the same version of Jacoco we use for instrumentation
            variantScope
                    .getVariantDependencies()
                    .getRuntimeClasspath()
                    .resolutionStrategy(r -> r.force(jacocoAgentRuntimeDependency));
        }
    }

    public void createJacocoTask(@NonNull final VariantScope variantScope) {
        variantScope
                .getTransformManager()
                .consumeStreams(
                        ImmutableSet.of(Scope.PROJECT),
                        ImmutableSet.of(DefaultContentType.CLASSES));
        taskFactory.register(new JacocoTask.CreationAction(variantScope));

        FileCollection instumentedClasses =
                project.files(
                        variantScope
                                .getArtifacts()
                                .getFinalProduct(
                                        InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES.INSTANCE),
                        project.files(
                                        variantScope
                                                .getArtifacts()
                                                .getFinalProduct(
                                                        InternalArtifactType
                                                                .JACOCO_INSTRUMENTED_JARS
                                                                .INSTANCE))
                                .getAsFileTree());
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "jacoco-instrumented-classes")
                                .addContentTypes(DefaultContentType.CLASSES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(instumentedClasses)
                                .build());
    }

    protected void createDataBindingTasksIfNecessary(@NonNull VariantScope scope) {
        final BuildFeatureValues features = scope.getGlobalScope().getBuildFeatures();
        boolean dataBindingEnabled = features.getDataBinding();
        if (!dataBindingEnabled && !features.getViewBinding()) {
            return;
        }

        VariantType type = scope.getType();
        if (type.isForTesting()) {
            BaseVariantData testedVariantData = scope.getTestedVariantData();
            if (testedVariantData == null) {
                // This is a com.android.test module.
                if (dataBindingEnabled) {
                    getLogger()
                            .error("Data binding cannot be enabled in a com.android.test project");
                    return;
                }
                // else viewBinding must be enabled which is fine.
            } else if (!extension.getDataBinding().isEnabledForTests()
                    && !testedVariantData.getType().isAar()) {
                return;
            }
        }

        taskFactory.register(new DataBindingMergeBaseClassLogTask.CreationAction(scope));

        taskFactory.register(
                new DataBindingMergeDependencyArtifactsTask.CreationAction(scope));

        dataBindingBuilder.setDebugLogEnabled(getLogger().isDebugEnabled());

        taskFactory.register(new DataBindingGenBaseClassesTask.CreationAction(scope));

        if (dataBindingEnabled) {
            taskFactory.register(new DataBindingExportBuildInfoTask.CreationAction(scope));
            setDataBindingAnnotationProcessorParams(scope);
        }
    }

    private void setDataBindingAnnotationProcessorParams(@NonNull VariantScope scope) {
        BaseVariantData variantData = scope.getVariantData();
        VariantDslInfo variantDslInfo = variantData.getVariantDslInfo();
        JavaCompileOptions javaCompileOptions = variantDslInfo.getJavaCompileOptions();
        AnnotationProcessorOptions processorOptions =
                javaCompileOptions.getAnnotationProcessorOptions();
        if (processorOptions
                instanceof com.android.build.gradle.internal.dsl.AnnotationProcessorOptions) {
            com.android.build.gradle.internal.dsl.AnnotationProcessorOptions options =
                    (com.android.build.gradle.internal.dsl.AnnotationProcessorOptions)
                            processorOptions;
            // We want to pass data binding processor's class name to the Java compiler. However, if
            // the class names of other annotation processors were not added previously, adding the
            // class name of data binding alone would disable Java compiler's automatic discovery of
            // annotation processors and the other annotation processors would not be invoked.
            // Therefore, we add data binding only if another class name was specified before.
            if (!options.getClassNames().isEmpty()
                    && !options.getClassNames().contains(DataBindingBuilder.PROCESSOR_NAME)) {
                options.className(DataBindingBuilder.PROCESSOR_NAME);
            }

            DataBindingCompilerArguments dataBindingArgs =
                    DataBindingCompilerArguments.createArguments(
                            scope,
                            getLogger().isDebugEnabled(),
                            dataBindingBuilder.getPrintMachineReadableOutput());
            options.compilerArgumentProvider(dataBindingArgs);
        } else {
            getLogger()
                    .error(
                            "Cannot setup data binding for {} because java compiler options"
                                    + " is not an instance of AnnotationProcessorOptions",
                            processorOptions);
        }
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     *
     * @param variantScope VariantScope object.
     */
    public void createPackagingTask(@NonNull VariantScope variantScope) {
        ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();
        final MutableTaskContainer taskContainer = variantData.getScope().getTaskContainer();

        boolean signedApk = variantData.isSigned();

        /*
         * PrePackaging step class that will look if the packaging of the main FULL_APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split FULL_APK. However when a warm swap
         * is possible, it is not necessary to produce immediately the new main SPLIT since the
         * runtime use the resources.ap_ file directly. However, as soon as an incompatible change
         * forcing a cold swap is triggered, the main FULL_APK must be rebuilt (even if the
         * resources were changed in a previous build).
         */
        InternalArtifactType<Directory> manifestType = variantScope.getManifestArtifactType();

        Provider<Directory> manifests = variantScope.getArtifacts().getFinalProduct(manifestType);

        InternalArtifactType resourceFilesInputType =
                variantScope.useResourceShrinker()
                        ? InternalArtifactType.SHRUNK_PROCESSED_RES.INSTANCE
                        : InternalArtifactType.PROCESSED_RES.INSTANCE;

        // Common code for both packaging tasks.
        Action<Task> configureResourcesAndAssetsDependencies =
                task -> {
                    task.dependsOn(taskContainer.getMergeAssetsTask());
                    if (taskContainer.getProcessAndroidResTask() != null) {
                        task.dependsOn(taskContainer.getProcessAndroidResTask());
                    }
                };

        TaskProvider<PackageApplication> packageApp =
                taskFactory.register(
                        new PackageApplication.CreationAction(
                                variantScope,
                                variantScope.getApkLocation(),
                                resourceFilesInputType,
                                manifests,
                                manifestType,
                                packagesCustomClassDependencies(variantScope, projectOptions)),
                        null,
                        task -> {
                            task.dependsOn(taskContainer.getJavacTask());

                            if (taskContainer.getPackageSplitResourcesTask() != null) {
                                task.dependsOn(taskContainer.getPackageSplitResourcesTask());
                            }
                            if (taskContainer.getPackageSplitAbiTask() != null) {
                                task.dependsOn(taskContainer.getPackageSplitAbiTask());
                            }

                            configureResourcesAndAssetsDependencies.execute(task);
                        },
                        null);

        TaskFactoryUtils.dependsOn(taskContainer.getAssembleTask(), packageApp.getName());

        // republish APK to the external world.
        variantScope.getArtifacts().republish(
                InternalArtifactType.APK.INSTANCE,
                PublicArtifactType.APK.INSTANCE
        );

        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            createInstallTask(variantScope);
        }

        // add an uninstall task
        final TaskProvider<UninstallTask> uninstallTask =
                taskFactory.register(new UninstallTask.CreationAction(variantScope));

        taskFactory.configure(UNINSTALL_ALL, uninstallAll -> uninstallAll.dependsOn(uninstallTask));
    }

    protected void createInstallTask(VariantScope variantScope) {
        taskFactory.register(new InstallVariantTask.CreationAction(variantScope));
    }

    protected void createValidateSigningTask(@NonNull VariantScope variantScope) {
        if (variantScope.getVariantDslInfo().getSigningConfig() == null) {
            return;
        }

        // FIXME create one per signing config instead of one per variant.
        taskFactory.register(
                new ValidateSigningTask.CreationAction(
                        variantScope, GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));
    }

    /**
     * Create assemble* and bundle* anchor tasks.
     *
     * <p>This does not create the variant specific version of these tasks only the ones that are
     * per build-type, per-flavor, per-flavor-combo and the main 'assemble' and 'bundle' ones.
     *
     * @param variantScopes the list of variant scopes.
     * @param flavorCount the number of flavors
     * @param flavorDimensionCount whether there are flavor dimensions at all.
     */
    public void createAnchorAssembleTasks(
            @NonNull List<VariantScope> variantScopes, int flavorCount, int flavorDimensionCount) {

        // sub anchor tasks that the main 'assemble' and 'bundle task will depend.
        List<TaskProvider<? extends Task>> subAssembleTasks = Lists.newArrayList();
        List<TaskProvider<? extends Task>> subBundleTasks = Lists.newArrayList();

        // There are 2 different scenarios:
        // 1. There are 1+ flavors. In this case the variant-specific assemble task is
        //    different from all the assemble<BuildType> or assemble<Flavor>
        // 2. Else, the assemble<BuildType> is the same as the variant specific assemble task.

        // Case #1
        if (flavorCount > 0) {
            // loop on the variants and record their build type/flavor usage.
            // map from build type/flavor names to the variant-specific assemble/bundle tasks
            ListMultimap<String, TaskProvider<? extends Task>> assembleMap =
                    ArrayListMultimap.create();
            ListMultimap<String, TaskProvider<? extends Task>> bundleMap =
                    ArrayListMultimap.create();

            for (VariantScope variantScope : variantScopes) {
                final VariantType variantType = variantScope.getType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = variantScope.getTaskContainer();
                    final VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();
                    final ComponentIdentity variantConfiguration =
                            variantDslInfo.getComponentIdentity();
                    final String buildType = variantConfiguration.getBuildType();

                    final TaskProvider<? extends Task> assembleTask =
                            taskContainer.getAssembleTask();
                    if (buildType != null) {
                        assembleMap.put(buildType, assembleTask);
                    }

                    for (ProductFlavor flavor : variantDslInfo.getProductFlavorList()) {
                        assembleMap.put(flavor.getName(), assembleTask);
                    }

                    // if 2+ flavor dimensions, then make an assemble for the flavor combo
                    if (flavorDimensionCount > 1) {
                        assembleMap.put(variantConfiguration.getFlavorName(), assembleTask);
                    }

                    // fill the bundle map only if the variant supports bundles.
                    if (variantType.isBaseModule()) {
                        TaskProvider<? extends Task> bundleTask = taskContainer.getBundleTask();

                        if (buildType != null) {
                            bundleMap.put(buildType, bundleTask);
                        }

                        for (ProductFlavor flavor : variantDslInfo.getProductFlavorList()) {
                            bundleMap.put(flavor.getName(), bundleTask);
                        }

                        // if 2+ flavor dimensions, then make an assemble for the flavor combo
                        if (flavorDimensionCount > 1) {
                            bundleMap.put(variantConfiguration.getFlavorName(), bundleTask);
                        }
                    }
                }
            }

            // loop over the map of build-type/flavor to create tasks for each, setting a dependency
            // on the variant-specific task.
            // these keys should be the same for bundle and assemble
            Set<String> dimensionKeys = assembleMap.keySet();

            for (String dimensionKey : dimensionKeys) {
                final String dimensionName = StringHelper.usLocaleCapitalize(dimensionKey);

                // create the task and add it to the list
                subAssembleTasks.add(
                        taskFactory.register(
                                "assemble" + dimensionName,
                                task -> {
                                    task.setDescription(
                                            "Assembles main outputs for all "
                                                    + dimensionName
                                                    + " variants.");
                                    task.setGroup(BasePlugin.BUILD_GROUP);
                                    task.dependsOn(assembleMap.get(dimensionKey));
                                }));

                List<TaskProvider<? extends Task>> subBundleMap = bundleMap.get(dimensionKey);
                if (!subBundleMap.isEmpty()) {

                    // create the task and add it to the list
                    subBundleTasks.add(
                            taskFactory.register(
                                    "bundle" + dimensionName,
                                    task -> {
                                        task.setDescription(
                                                "Assembles bundles for all "
                                                        + dimensionName
                                                        + " variants.");
                                        task.setGroup(BasePlugin.BUILD_GROUP);
                                        task.dependsOn(subBundleMap);
                                    }));
                }
            }
        } else {
            // Case #2
            for (VariantScope variantScope : variantScopes) {
                final VariantType variantType = variantScope.getType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = variantScope.getTaskContainer();

                    subAssembleTasks.add(taskContainer.getAssembleTask());

                    if (variantType.isBaseModule()) {
                        subBundleTasks.add(taskContainer.getBundleTask());
                    }
                }
            }
        }

        // ---
        // ok now we can create the main 'assemble' and 'bundle' tasks and make them depend on the
        // sub-tasks.

        if (!subAssembleTasks.isEmpty()) {
            // "assemble" task is already created by the java base plugin.
            taskFactory.configure(
                    "assemble",
                    task -> {
                        task.setDescription("Assemble main outputs for all the variants.");
                        task.setGroup(BasePlugin.BUILD_GROUP);
                        task.dependsOn(subAssembleTasks);
                    });
        }

        if (!subBundleTasks.isEmpty()) {
            // root bundle task
            taskFactory.register(
                    "bundle",
                    task -> {
                        task.setDescription("Assemble bundles for all the variants.");
                        task.setGroup(BasePlugin.BUILD_GROUP);
                        task.dependsOn(subBundleTasks);
                    });
        }
    }

    public void createAssembleTask(@NonNull final BaseVariantData variantData) {
        final VariantScope scope = variantData.getScope();
        taskFactory.register(
                getAssembleTaskName(scope, "assemble"),
                null /*preConfigAction*/,
                task ->
                        task.setDescription(
                                "Assembles main output for variant "
                                        + scope.getVariantDslInfo()
                                                .getComponentIdentity()
                                                .getName()),
                taskProvider -> scope.getTaskContainer().setAssembleTask(taskProvider));
    }

    @NonNull
    private String getAssembleTaskName(VariantScope scope, @NonNull String prefix) {
        return scope.getTaskName(prefix);
    }

    public void createBundleTask(@NonNull final BaseVariantData variantData) {
        final VariantScope scope = variantData.getScope();
        taskFactory.register(
                getAssembleTaskName(scope, "bundle"),
                null,
                task -> {
                    task.setDescription(
                            "Assembles bundle for variant "
                                    + scope.getVariantDslInfo().getComponentIdentity().getName());
                    task.dependsOn(
                            scope.getArtifacts()
                                    .getFinalProduct(InternalArtifactType.BUNDLE.INSTANCE));
                },
                taskProvider -> scope.getTaskContainer().setBundleTask(taskProvider));
    }

    /** Returns created shrinker type, or null if none was created. */
    @Nullable
    protected CodeShrinker maybeCreateJavaCodeShrinkerTask(
            @NonNull final VariantScope variantScope) {
        CodeShrinker codeShrinker = variantScope.getCodeShrinker();

        if (codeShrinker != null) {
            return doCreateJavaCodeShrinkerTask(
                    variantScope,
                    // No mapping in non-test modules.
                    codeShrinker);
        } else {
            return null;
        }
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules. Returns a type of the {@link CodeShrinker} shrinker that was
     * created, or {@code null} if none was created.
     */
    @NonNull
    protected final CodeShrinker doCreateJavaCodeShrinkerTask(
            @NonNull final VariantScope variantScope, @NonNull CodeShrinker codeShrinker) {
        return doCreateJavaCodeShrinkerTask(variantScope, codeShrinker, false);
    }

    @NonNull
    protected final CodeShrinker doCreateJavaCodeShrinkerTask(
            @NonNull final VariantScope variantScope,
            @NonNull CodeShrinker codeShrinker,
            Boolean isTestApplication) {
        @NonNull TaskProvider<? extends Task> task;
        CodeShrinker createdShrinker = codeShrinker;
        switch (codeShrinker) {
            case PROGUARD:
                task = createProguardTask(variantScope, isTestApplication);
                break;
            case R8:
                if (variantScope.getVariantDslInfo().getVariantType().isAar()
                        && !projectOptions.get(BooleanOption.ENABLE_R8_LIBRARIES)) {
                    task = createProguardTask(variantScope, isTestApplication);
                    createdShrinker = CodeShrinker.PROGUARD;
                } else {
                    task = createR8Task(variantScope, isTestApplication);
                }
                break;
            default:
                throw new AssertionError("Unknown value " + codeShrinker);
        }
        if (variantScope.getPostprocessingFeatures() != null) {
            TaskProvider<CheckProguardFiles> checkFilesTask =
                    taskFactory.register(new CheckProguardFiles.CreationAction(variantScope));

            TaskFactoryUtils.dependsOn(task, checkFilesTask);
        }

        return createdShrinker;
    }

    @NonNull
    private TaskProvider<ProguardTask> createProguardTask(
            @NonNull VariantScope variantScope, boolean isTestApplication) {
        return taskFactory.register(
                new ProguardTask.CreationAction(variantScope, isTestApplication));
    }

    @NonNull
    private TaskProvider<R8Task> createR8Task(
            @NonNull VariantScope variantScope, Boolean isTestApplication) {
        if (variantScope.consumesFeatureJars()) {
            publishFeatureDex(variantScope);
        }
        return taskFactory.register(new R8Task.CreationAction(variantScope, isTestApplication));
    }

    private void maybeCreateDexSplitterTask(@NonNull VariantScope variantScope) {
        if (!variantScope.consumesFeatureJars()) {
            return;
        }

        taskFactory.register(new DexSplitterTask.CreationAction(variantScope));

        publishFeatureDex(variantScope);
    }

    /**
     * We have a separate method for publishing the classes.dex files back to the features (instead
     * of using the typical PublishingSpecs pipeline) because multiple artifacts are published per
     * BuildableArtifact in this case.
     *
     * <p>This method is similar to VariantScopeImpl.publishIntermediateArtifact, and some of the
     * code was pulled from there. Once there's support for publishing multiple artifacts per
     * BuildableArtifact in the PublishingSpecs pipeline, we can get rid of this method.
     */
    private void publishFeatureDex(@NonNull VariantScope variantScope) {
        // first calculate the list of module paths
        final Collection<String> modulePaths;
        final BaseExtension extension = globalScope.getExtension();
        if (extension instanceof BaseAppModuleExtension) {
            modulePaths = ((BaseAppModuleExtension) extension).getDynamicFeatures();
        } else {
            return;
        }

        Configuration configuration =
                variantScope.getVariantData().getVariantDependency().getElements(RUNTIME_ELEMENTS);
        Preconditions.checkNotNull(
                configuration,
                "Publishing to Runtime Element with no Runtime Elements configuration object. "
                        + "VariantType: "
                        + variantScope.getType());
        Provider<Directory> artifact =
                variantScope
                        .getArtifacts()
                        .getFinalProduct(InternalArtifactType.FEATURE_DEX.INSTANCE);
        for (String modulePath : modulePaths) {
            Provider<RegularFile> file =
                    artifact.map(directory -> directory.file(getFeatureFileName(modulePath, null)));
            Map<Attribute<String>, String> attributeMap =
                    ImmutableMap.of(MODULE_PATH, project.absoluteProjectPath(modulePath));
            publishArtifactToConfiguration(
                    configuration,
                    file,
                    AndroidArtifacts.ArtifactType.FEATURE_DEX,
                    attributeMap);
        }
    }

    /**
     * Method to reliably generate matching feature file names when dex splitter is used.
     *
     * @param modulePath the gradle module path for the feature
     * @param fileExtension the desired file extension (e.g., ".jar"), or null if no file extension
     *     (e.g., for a folder)
     * @return name of file
     */
    public static String getFeatureFileName(
            @NonNull String modulePath, @Nullable String fileExtension) {
        final String featureName = FeatureSplitUtils.getFeatureName(modulePath);
        final String sanitizedFeatureName = ":".equals(featureName) ? "" : featureName;
        // Prepend "feature-" to fileName in case a non-base module has module path ":base".
        return "feature-" + sanitizedFeatureName + nullToEmpty(fileExtension);
    }

    /**
     * Checks if {@link ShrinkResourcesTask} and {@link ShrinkBundleResourcesTask} should be added
     * to the build pipeline and creates the tasks
     */
    protected void maybeCreateResourcesShrinkerTasks(@NonNull VariantScope scope) {
        if (!scope.useResourceShrinker()) {
            return;
        }

        // if resources are shrink, create task per variant output
        // to transform the res package into a stripped res package

        taskFactory.register(new ShrinkResourcesTask.CreationAction(scope));

        // And for the bundle
        taskFactory.register(new ShrinkBundleResourcesTask.CreationAction(scope));
    }

    public void createReportTasks(final List<VariantScope> variantScopes) {
        taskFactory.register(
                "androidDependencies",
                DependencyReportTask.class,
                task -> {
                    task.setDescription("Displays the Android dependencies of the project.");
                    task.setVariants(variantScopes);
                    task.setGroup(ANDROID_GROUP);
                });


        List<VariantScope> signingReportScopes =
                variantScopes
                        .stream()
                        .filter(
                                variantScope ->
                                        variantScope.getType().isForTesting()
                                                || variantScope.getType().isBaseModule())
                        .collect(Collectors.toList());
        if (!signingReportScopes.isEmpty()) {
            taskFactory.register(
                    "signingReport",
                    SigningReportTask.class,
                    task -> {
                        task.setDescription(
                                "Displays the signing info for the base and test modules");
                        task.setVariants(signingReportScopes);
                        task.setGroup(ANDROID_GROUP);
                    });
        }

        createDependencyAnalyzerTask(variantScopes);
    }

    protected void createDependencyAnalyzerTask(Collection<VariantScope> scopes) {
        scopes.forEach(
                (VariantScope scope) ->
                        taskFactory.register(new AnalyzeDependenciesTask.CreationAction(scope)));
    }

    public void createAnchorTasks(@NonNull VariantScope scope) {
        createVariantPreBuildTask(scope);

        // also create sourceGenTask
        final BaseVariantData variantData = scope.getVariantData();
        scope.getTaskContainer()
                .setSourceGenTask(
                        taskFactory.register(
                                scope.getTaskName("generate", "Sources"),
                                task -> {
                                    task.dependsOn(PrepareLintJar.NAME);
                                    task.dependsOn(PrepareLintJarForPublish.NAME);
                                    task.dependsOn(variantData.getExtraGeneratedResFolders());
                                }));
        // and resGenTask
        scope.getTaskContainer()
                .setResourceGenTask(
                        taskFactory.register(scope.getTaskName("generate", "Resources")));

        scope.getTaskContainer()
                .setAssetGenTask(taskFactory.register(scope.getTaskName("generate", "Assets")));

        if (!variantData.getType().isForTesting()
                && variantData.getVariantDslInfo().isTestCoverageEnabled()) {
            scope.getTaskContainer()
                    .setCoverageReportTask(
                            taskFactory.register(
                                    scope.getTaskName("create", "CoverageReport"),
                                    task -> {
                                        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                        task.setDescription(
                                                String.format(
                                                        "Creates test coverage reports for the %s variant.",
                                                        variantData.getName()));
                                    }));
        }

        // and compile task
        createCompileAnchorTask(scope);
    }

    protected void createVariantPreBuildTask(@NonNull VariantScope scope) {
        // default pre-built task.
        createDefaultPreBuildTask(scope);
    }

    protected void createDefaultPreBuildTask(@NonNull VariantScope scope) {
        taskFactory.register(new PreBuildCreationAction(scope));
    }

    public abstract static class AbstractPreBuildCreationAction<T extends AndroidVariantTask>
            extends VariantTaskCreationAction<T> {

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("pre", "Build");
        }

        public AbstractPreBuildCreationAction(VariantScope variantScope) {
            super(variantScope, false);
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends T> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPreBuildTask(taskProvider);
        }

        @Override
        public void configure(@NonNull T task) {
            super.configure(task);
            task.dependsOn(MAIN_PREBUILD);

            if (getVariantScope().getCodeShrinker() != null) {
                task.dependsOn(EXTRACT_PROGUARD_FILES);
            }
        }
    }

    private static class PreBuildCreationAction
            extends AbstractPreBuildCreationAction<AndroidVariantTask> {
        public PreBuildCreationAction(VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public Class<AndroidVariantTask> getType() {
            return AndroidVariantTask.class;
        }
    }

    private void createCompileAnchorTask(@NonNull final VariantScope scope) {
        scope.getTaskContainer()
                .setCompileTask(
                        taskFactory.register(
                                scope.getTaskName("compile", "Sources"),
                                task -> task.setGroup(BUILD_GROUP)));

        // FIXME is that really needed?
        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getAssembleTask(),
                scope.getTaskContainer().getCompileTask());
    }

    @NonNull
    protected Logger getLogger() {
        return logger;
    }

    public void addBindingDependenciesIfNecessary(
            boolean viewBindingEnabled,
            boolean dataBindingEnabled,
            DataBindingOptions dataBindingOptions,
            List<VariantScope> variantScopes) {
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        boolean useAndroidX = projectOptions.get(BooleanOption.USE_ANDROID_X);

        if (viewBindingEnabled) {
            String version =
                    dataBindingBuilder.getLibraryVersion(dataBindingBuilder.getCompilerVersion());
            String groupAndArtifact =
                    useAndroidX
                            ? SdkConstants.ANDROIDX_VIEW_BINDING_ARTIFACT
                            : SdkConstants.VIEW_BINDING_ARTIFACT;
            project.getDependencies().add("api", groupAndArtifact + ":" + version);
        }

        if (dataBindingEnabled) {
            String version =
                    MoreObjects.firstNonNull(
                            dataBindingOptions.getVersion(),
                            dataBindingBuilder.getCompilerVersion());
            String baseLibArtifact =
                    useAndroidX
                            ? SdkConstants.ANDROIDX_DATA_BINDING_BASELIB_ARTIFACT
                            : SdkConstants.DATA_BINDING_BASELIB_ARTIFACT;
            project.getDependencies()
                    .add(
                            "api",
                            baseLibArtifact
                                    + ":"
                                    + dataBindingBuilder.getBaseLibraryVersion(version));
            project.getDependencies()
                    .add(
                            "annotationProcessor",
                            SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT
                                    + ":"
                                    + version);
            // TODO load config name from source sets
            if (dataBindingOptions.isEnabledForTests() || this instanceof LibraryTaskManager) {
                String dataBindingArtifact =
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version;
                project.getDependencies()
                        .add("androidTestAnnotationProcessor", dataBindingArtifact);
                if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
                    project.getDependencies().add("testAnnotationProcessor", dataBindingArtifact);
                }
            }
            if (dataBindingOptions.getAddDefaultAdapters()) {
                String libArtifact =
                        useAndroidX
                                ? SdkConstants.ANDROIDX_DATA_BINDING_LIB_ARTIFACT
                                : SdkConstants.DATA_BINDING_LIB_ARTIFACT;
                String adaptersArtifact =
                        useAndroidX
                                ? SdkConstants.ANDROIDX_DATA_BINDING_ADAPTER_LIB_ARTIFACT
                                : SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT;
                project.getDependencies()
                        .add(
                                "api",
                                libArtifact + ":" + dataBindingBuilder.getLibraryVersion(version));
                project.getDependencies()
                        .add(
                                "api",
                                adaptersArtifact
                                        + ":"
                                        + dataBindingBuilder.getBaseAdaptersVersion(version));
            }
            project.getPluginManager()
                    .withPlugin(
                            "org.jetbrains.kotlin.kapt",
                            appliedPlugin ->
                                    configureKotlinKaptTasksForDataBinding(
                                            project, variantScopes, version));
        }
    }

    private void configureKotlinKaptTasksForDataBinding(
            Project project, List<VariantScope> variantScopes, String version) {
        DependencySet kaptDeps = project.getConfigurations().getByName("kapt").getAllDependencies();
        kaptDeps.forEach(
                (Dependency dependency) -> {
                    // if it is a data binding compiler dependency w/ a different version, report error
                    if (Objects.equals(
                                    dependency.getGroup() + ":" + dependency.getName(),
                                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT)
                            && !Objects.equals(dependency.getVersion(), version)) {
                        String depString =
                                dependency.getGroup()
                                        + ":"
                                        + dependency.getName()
                                        + ":"
                                        + dependency.getVersion();
                        globalScope
                                .getDslScope()
                                .getIssueReporter()
                                .reportError(
                                        Type.GENERIC,
                                        "Data Binding annotation processor version needs to match the"
                                                + " Android Gradle Plugin version. You can remove the kapt"
                                                + " dependency "
                                                + depString
                                                + " and Android Gradle Plugin will inject"
                                                + " the right version.");
                    }
                });
        project.getDependencies()
                .add(
                        "kapt",
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version);
        Class<? extends Task> kaptTaskClass = null;
        try {
            //noinspection unchecked
            kaptTaskClass =
                    (Class<? extends Task>)
                            Class.forName("org.jetbrains.kotlin.gradle.internal.KaptTask");
        } catch (ClassNotFoundException e) {
            logger.error(
                    "Kotlin plugin is applied to the project "
                            + project.getPath()
                            + " but we cannot find the KaptTask. Make sure you apply the"
                            + " kotlin-kapt plugin because it is necessary to use kotlin"
                            + " with data binding.");
        }
        if (kaptTaskClass == null) {
            return;
        }
        // create a map from kapt task name to variant scope
        Map<String, VariantScope> kaptTaskLookup =
                variantScopes
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        variantScope ->
                                                variantScope
                                                        .getVariantData()
                                                        .getTaskName("kapt", "Kotlin"),
                                        variantScope -> variantScope));
        project.getTasks()
                .withType(
                        kaptTaskClass,
                        (Action<Task>)
                                kaptTask -> {
                                    // find matching scope.
                                    VariantScope matchingScope =
                                            kaptTaskLookup.get(kaptTask.getName());
                                    if (matchingScope != null) {
                                        configureKaptTaskInScope(matchingScope, kaptTask);
                                    }
                                });
    }

    private static void configureKaptTaskInScope(
            @NonNull VariantScope scope, @NonNull Task kaptTask) {
        // The data binding artifact is created through annotation processing, which is invoked
        // by the Kapt task (when the Kapt plugin is used). Therefore, we register Kapt as the
        // generating task. (This will overwrite the registration of JavaCompile as the generating
        // task that took place earlier before this method is called).
        DirectoryProperty databindingArtifact =
                scope.getGlobalScope().getProject().getObjects().directoryProperty();

        TaskProvider<Task> kaptTaskProvider =
                scope.getGlobalScope().getProject().getTasks().named(kaptTask.getName());

        scope.getArtifacts()
                .getOperations()
                .replace(kaptTaskProvider, (Task task) -> databindingArtifact)
                .on(InternalArtifactType.DATA_BINDING_ARTIFACT.INSTANCE);

        // manually add the output property as a task output so Gradle can wire providers correctly
        kaptTask.getOutputs().dir(databindingArtifact);
    }

    protected void configureTestData(AbstractTestDataImpl testData) {
        testData.setAnimationsDisabled(extension.getTestOptions().getAnimationsDisabled());
        testData.setExtraInstrumentationTestRunnerArgs(
                projectOptions.getExtraInstrumentationTestRunnerArgs());
    }

    private void maybeCreateCheckDuplicateClassesTask(@NonNull VariantScope variantScope) {
        if (projectOptions.get(BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK)) {
            taskFactory.register(new CheckDuplicateClassesTask.CreationAction(variantScope));
        }
    }

    private void maybeCreateDexDesugarLibTask(
            @NonNull VariantScope variantScope, boolean enableDexingArtifactTransform) {
        boolean separateFileDependenciesDexingTask =
                variantScope.getJava8LangSupportType() == Java8LangSupport.D8
                        && enableDexingArtifactTransform;
        if (variantScope.getNeedsShrinkDesugarLibrary()) {
            taskFactory.register(
                    new L8DexDesugarLibTask.CreationAction(
                            variantScope,
                            enableDexingArtifactTransform,
                            separateFileDependenciesDexingTask));
        }
    }
}
