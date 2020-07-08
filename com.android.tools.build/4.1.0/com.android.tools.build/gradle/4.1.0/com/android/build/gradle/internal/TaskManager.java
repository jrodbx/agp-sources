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
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES;
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
import com.android.build.api.artifact.Artifact.SingleArtifact;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.component.ApplicationCreationConfig;
import com.android.build.gradle.internal.component.BaseCreationConfig;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.cxx.gradle.generator.ExternalNativeJsonGenerator;
import com.android.build.gradle.internal.cxx.model.CxxModuleModel;
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask;
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask;
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask;
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask;
import com.android.build.gradle.internal.res.namespaced.NamespacedResourcesTaskManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScope.Java8LangSupport;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.CheckAarMetadataTask;
import com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask;
import com.android.build.gradle.internal.tasks.CheckProguardFiles;
import com.android.build.gradle.internal.tasks.CompressAssetsTask;
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
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask;
import com.android.build.gradle.internal.tasks.PackageForUnitTest;
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
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeBaseClassLogTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeDependencyArtifactsTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingTriggerTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitUtils;
import com.android.build.gradle.internal.tasks.mlkit.GenerateMlModelClass;
import com.android.build.gradle.internal.test.AbstractTestDataImpl;
import com.android.build.gradle.internal.test.BundleTestDataImpl;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.transforms.CustomClassTransform;
import com.android.build.gradle.internal.transforms.ShrinkBundleResourcesTask;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.AnalyzeDependenciesTask;
import com.android.build.gradle.tasks.CleanBuildCache;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeCleanTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGeneratorBase;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateManifestJarTask;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.GenerateTestConfig;
import com.android.build.gradle.tasks.JavaCompileCreationAction;
import com.android.build.gradle.tasks.JavaCompileKt;
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
import com.android.build.gradle.tasks.ProcessManifestForBundleTask;
import com.android.build.gradle.tasks.ProcessManifestForInstantAppTask;
import com.android.build.gradle.tasks.ProcessManifestForMetadataFeatureTask;
import com.android.build.gradle.tasks.ProcessMultiApkApplicationManifest;
import com.android.build.gradle.tasks.ProcessPackagedManifestTask;
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
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
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
import org.gradle.api.file.RegularFileProperty;
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

/** Manages tasks creation. */
public abstract class TaskManager<
        VariantT extends VariantImpl<? extends VariantPropertiesT>,
        VariantPropertiesT extends VariantPropertiesImpl> {
    private static final String MULTIDEX_VERSION = "1.0.2";

    private static final String COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:" + MULTIDEX_VERSION;

    private static final String ANDROIDX_MULTIDEX_MULTIDEX =
            AndroidXDependencySubstitution.getAndroidXMappings()
                    .get("com.android.support:multidex");

    private static final String COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
            "com.android.support:multidex-instrumentation:" + MULTIDEX_VERSION;
    private static final String ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION =
            AndroidXDependencySubstitution.getAndroidXMappings()
                    .get("com.android.support:multidex-instrumentation");

    // name of the task that triggers compilation of the custom lint Checks
    private static final String COMPILE_LINT_CHECKS_TASK = "compileLintChecks";

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
    public static final String COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION = "0.1.0-dev09";
    public static final String COMPOSE_KOTLIN_COMPILER_VERSION =
            "1.3.61-dev-withExperimentalGoogleExtensions-20191127";
    public static final String CREATE_MOCKABLE_JAR_TASK_NAME = "createMockableJar";

    @NonNull protected final Project project;
    @NonNull protected final BaseExtension extension;
    @NonNull private final List<ComponentInfo<VariantT, VariantPropertiesT>> variants;

    @NonNull
    private final List<
                    ComponentInfo<
                            TestComponentImpl<? extends TestComponentPropertiesImpl>,
                            TestComponentPropertiesImpl>>
            testComponents;

    private final boolean hasFlavors;
    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Recorder recorder;
    @NonNull private final Logger logger;
    @NonNull protected final TaskFactory taskFactory;
    @NonNull protected final ImmutableList<VariantPropertiesT> variantPropertiesList;
    @NonNull private final ImmutableList<TestComponentPropertiesImpl> testComponentPropertiesList;
    @NonNull private final ImmutableList<ComponentPropertiesImpl> allPropertiesList;

    /**
     * Creates the TaskManager
     *
     * @param variants these are all the variants
     * @param testComponents these are all the test components
     * @param hasFlavors whether there are flavors
     * @param globalScope the global scope
     * @param extension the extension
     * @param recorder the recorder
     */
    public TaskManager(
            @NonNull List<ComponentInfo<VariantT, VariantPropertiesT>> variants,
            @NonNull
                    List<
                                    ComponentInfo<
                                            TestComponentImpl<
                                                    ? extends TestComponentPropertiesImpl>,
                                            TestComponentPropertiesImpl>>
                            testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull Recorder recorder) {
        this.variants = variants;
        this.testComponents = testComponents;
        this.hasFlavors = hasFlavors;
        this.globalScope = globalScope;
        this.project = globalScope.getProject();
        this.extension = extension;
        this.recorder = recorder;
        this.logger = Logging.getLogger(this.getClass());

        taskFactory = new TaskFactoryImpl(project.getTasks());

        // pre-compute some lists.

        variantPropertiesList =
                variants.stream()
                        .map(ComponentInfo::getProperties)
                        .collect(ImmutableList.toImmutableList());
        testComponentPropertiesList =
                testComponents.stream()
                        .map(ComponentInfo::getProperties)
                        .collect(ImmutableList.toImmutableList());
        allPropertiesList =
                ImmutableList.<ComponentPropertiesImpl>builder()
                        .addAll(variantPropertiesList)
                        .addAll(testComponentPropertiesList)
                        .build();
    }

    /**
     * This is the main entry point into the task manager
     *
     * <p>This creates the tasks for all the variants and all the test components
     */
    public void createTasks() {
        // this is call before all the variants are created since they are all going to depend
        // on the global LINT_PUBLISH_JAR task output
        // setup the task that reads the config and put the lint jar in the intermediate folder
        // so that the bundle tasks can copy it, and the inter-project publishing can publish it
        taskFactory.register(new PrepareLintJarForPublish.CreationAction(globalScope));

        // create a lifecycle task to build the lintChecks dependencies
        taskFactory.register(
                COMPILE_LINT_CHECKS_TASK,
                task -> task.dependsOn(globalScope.getLocalCustomLintChecks()));

        // Create top level test tasks.
        createTopLevelTestTasks();

        // Create tasks for all variants (main and tests)
        for (ComponentInfo<VariantT, VariantPropertiesT> variant : variants) {
            createTasksForVariant(variant, variants);
        }
        for (ComponentInfo<
                        TestComponentImpl<? extends TestComponentPropertiesImpl>,
                        TestComponentPropertiesImpl>
                testComponent : testComponents) {
            createTasksForTest(testComponent);
        }

        createReportTasks();
    }

    public void createPostApiTasks() {

        // must run this after scopes are created so that we can configure kotlin
        // kapt tasks
        addBindingDependenciesIfNecessary(extension.getDataBinding());

        // configure compose related tasks.
        configureKotlinPluginTasksForComposeIfNecessary();

        // create the global lint task that depends on all the variants
        configureGlobalLintTask();

        int flavorDimensionCount = 0;
        if (extension.getFlavorDimensionList() != null) {
            flavorDimensionCount = extension.getFlavorDimensionList().size();
        }

        createAnchorAssembleTasks(extension.getProductFlavors().size(), flavorDimensionCount);
    }

    /**
     * Create tasks for the specified variant.
     *
     * <p>This creates tasks common to all variant types.
     */
    private void createTasksForVariant(
            @NonNull ComponentInfo<VariantT, VariantPropertiesT> variant,
            @NonNull List<ComponentInfo<VariantT, VariantPropertiesT>> variants) {
        final VariantPropertiesT variantProperties = variant.getProperties();

        final VariantType variantType = variantProperties.getVariantType();

        VariantDependencies variantDependencies = variantProperties.getVariantDependencies();

        if (variantProperties.getVariantDslInfo().isLegacyMultiDexMode()
                && variantProperties.getVariantType().isApk()) {
            String multiDexDependency =
                    variantProperties
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.USE_ANDROID_X)
                            ? ANDROIDX_MULTIDEX_MULTIDEX
                            : COM_ANDROID_SUPPORT_MULTIDEX;
            project.getDependencies()
                    .add(variantDependencies.getCompileClasspath().getName(), multiDexDependency);
            project.getDependencies()
                    .add(variantDependencies.getRuntimeClasspath().getName(), multiDexDependency);
        }

        if (variantProperties.getVariantDslInfo().getRenderscriptSupportModeEnabled()) {
            final ConfigurableFileCollection fileCollection =
                    project.files(
                            globalScope
                                    .getSdkComponents()
                                    .flatMap(
                                            SdkComponentsBuildService
                                                    ::getRenderScriptSupportJarProvider));
            project.getDependencies()
                    .add(variantDependencies.getCompileClasspath().getName(), fileCollection);
            if (variantType.isApk() && !variantType.isForTesting()) {
                project.getDependencies()
                        .add(variantDependencies.getRuntimeClasspath().getName(), fileCollection);
            }
        }

        createAssembleTask(variantProperties);
        if (variantType.isBaseModule()) {
            createBundleTask(variantProperties);
        }

        doCreateTasksForVariant(variant, variants);
    }

    /**
     * Entry point for each specialized TaskManager to create the tasks for a given
     * VariantPropertiesT
     *
     * @param variant the variant for which to create the tasks
     * @param allVariants all the other variants. This is needed for lint.
     */
    protected abstract void doCreateTasksForVariant(
            @NonNull ComponentInfo<VariantT, VariantPropertiesT> variant,
            @NonNull List<ComponentInfo<VariantT, VariantPropertiesT>> allVariants);

    /** Create tasks for the specified variant. */
    private void createTasksForTest(
            @NonNull
                    ComponentInfo<
                                    TestComponentImpl<? extends TestComponentPropertiesImpl>,
                                    TestComponentPropertiesImpl>
                            testComponent) {
        final TestComponentPropertiesImpl componentProperties = testComponent.getProperties();

        createAssembleTask(componentProperties);

        VariantPropertiesImpl testedVariant = componentProperties.getTestedVariant();

        VariantDslInfo variantDslInfo = componentProperties.getVariantDslInfo();
        VariantDependencies variantDependencies = componentProperties.getVariantDependencies();

        if (testedVariant.getVariantDslInfo().getRenderscriptSupportModeEnabled()) {
            project.getDependencies()
                    .add(
                            variantDependencies.getCompileClasspath().getName(),
                            project.files(
                                    globalScope
                                            .getSdkComponents()
                                            .flatMap(
                                                    SdkComponentsBuildService
                                                            ::getRenderScriptSupportJarProvider)));
        }

        if (componentProperties.getVariantType().isApk()) { // ANDROID_TEST
            if (variantDslInfo.isLegacyMultiDexMode()) {
                String multiDexInstrumentationDep =
                        componentProperties
                                        .getServices()
                                        .getProjectOptions()
                                        .get(BooleanOption.USE_ANDROID_X)
                                ? ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION
                                : COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION;
                project.getDependencies()
                        .add(
                                variantDependencies.getCompileClasspath().getName(),
                                multiDexInstrumentationDep);
                project.getDependencies()
                        .add(
                                variantDependencies.getRuntimeClasspath().getName(),
                                multiDexInstrumentationDep);
            }

            createAndroidTestVariantTasks((AndroidTestPropertiesImpl) componentProperties);
        } else {
            // UNIT_TEST
            createUnitTestVariantTasks((UnitTestPropertiesImpl) componentProperties);
        }
    }

    /**
     * Create tasks before the evaluation (on plugin apply). This is useful for tasks that could be
     * referenced by custom build logic.
     *
     * @param globalScope the global scope
     * @param variantType the main variant type as returned by the {@link
     *     com.android.build.gradle.internal.variant.VariantFactory}
     * @param sourceSetContainer the container of source set from the DSL.
     */
    public static void createTasksBeforeEvaluate(
            @NonNull GlobalScope globalScope,
            @NonNull VariantType variantType,
            @NonNull Iterable<AndroidSourceSet> sourceSetContainer) {
        final Project project = globalScope.getProject();

        TaskFactoryImpl taskFactory = new TaskFactoryImpl(project.getTasks());

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

        taskFactory.register(new ExtractProguardFiles.CreationAction(globalScope))
                .configure(it -> it.dependsOn(MAIN_PREBUILD));

        taskFactory.register(new SourceSetsTask.CreationAction(sourceSetContainer));

        taskFactory.register(
                ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
                });

        taskFactory.register(new LintCompile.CreationAction(globalScope));

        if (!variantType.isForTesting()) {
            taskFactory.register(LINT, LintGlobalTask.class, task -> {});
            taskFactory.configure(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));
            taskFactory.register(LINT_FIX, LintFixTask.class, task -> {});
        }

        // create a single configuration to point to a project or a local file that contains
        // the lint.jar for this project.
        // This is not the configuration that consumes lint.jar artifacts from normal dependencies,
        // or publishes lint.jar to consumers. These are handled at the variant level.
        globalScope.setLintChecks(createCustomLintChecksConfig(project));
        globalScope.setLintPublish(createCustomLintPublishConfig(project));

        globalScope.setAndroidJarConfig(createAndroidJarConfig(project));

        taskFactory.register(new CleanBuildCache.CreationAction(globalScope));

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

    // this is run after all the variants are created.
    protected void configureGlobalLintTask() {

        // configure the global lint tasks.
        taskFactory.configure(
                LINT,
                LintGlobalTask.class,
                task ->
                        new LintGlobalTask.GlobalCreationAction(globalScope, variantPropertiesList)
                                .configure(task));
        taskFactory.configure(
                LINT_FIX,
                LintFixTask.class,
                task ->
                        new LintFixTask.GlobalCreationAction(globalScope, variantPropertiesList)
                                .configure(task));
    }

    private void configureKotlinPluginTasksForComposeIfNecessary() {

        boolean composeIsEnabled =
                allPropertiesList.stream()
                        .anyMatch(
                                componentProperties ->
                                        componentProperties.getBuildFeatures().getCompose());
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
        for (ComponentPropertiesImpl component : allPropertiesList) {
            try {
                TaskProvider<Task> compileKotlin =
                        globalScope
                                .getProject()
                                .getTasks()
                                .named(component.computeTaskName("compile", "Kotlin"));

                TaskProvider<PrepareKotlinCompileTask> prepareKotlinCompileTaskTaskProvider =
                        taskFactory.register(
                                new PrepareKotlinCompileTask.CreationAction(
                                        component, compileKotlin, kotlinExtension));
                // make the dependency !
                compileKotlin.configure(
                        task -> task.dependsOn(prepareKotlinCompileTaskTaskProvider));
            } catch (UnknownTaskException e) {
                // ignore
            }
        }
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
                                                        .flatMap(
                                                                SdkComponentsBuildService
                                                                        ::getAndroidJarProvider)
                                                        .getOrNull()));

        // Adding this task to help the IDE find the mockable JAR.
        taskFactory.register(
                CREATE_MOCKABLE_JAR_TASK_NAME,
                task -> task.dependsOn(globalScope.getMockableJarArtifact()));
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

    protected void createDependencyStreams(@NonNull ComponentPropertiesImpl componentProperties) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.
        handleJacocoDependencies(componentProperties);

        TransformManager transformManager = componentProperties.getTransformManager();

        // This might be consumed by RecalculateFixedStackFrames if that's created
        transformManager.addStream(
                OriginalStream.builder(project, "ext-libs-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.EXTERNAL_LIBRARIES)
                        .setArtifactCollection(
                                componentProperties
                                        .getVariantDependencies()
                                        .getArtifactCollection(
                                                RUNTIME_CLASSPATH, EXTERNAL, CLASSES_JAR))
                        .build());

        // Add stream of external java resources if EXTERNAL_LIBRARIES isn't in the set of java res
        // merging scopes.
        if (!getJavaResMergingScopes(componentProperties, RESOURCES)
                .contains(Scope.EXTERNAL_LIBRARIES)) {
            transformManager.addStream(
                    OriginalStream.builder(project, "ext-libs-java-res")
                            .addContentTypes(RESOURCES)
                            .addScope(Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    componentProperties
                                            .getVariantDependencies()
                                            .getArtifactCollection(
                                                    RUNTIME_CLASSPATH, EXTERNAL, JAVA_RES))
                            .build());
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(
                OriginalStream.builder(project, "sub-projects-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(Scope.SUB_PROJECTS)
                        .setArtifactCollection(
                                componentProperties
                                        .getVariantDependencies()
                                        .getArtifactCollection(
                                                RUNTIME_CLASSPATH, PROJECT, CLASSES_JAR))
                        .build());

        // same for the java resources, if SUB_PROJECTS isn't in the set of java res merging scopes.
        if (!getJavaResMergingScopes(componentProperties, RESOURCES).contains(Scope.SUB_PROJECTS)) {
            transformManager.addStream(
                    OriginalStream.builder(project, "sub-projects-java-res")
                            .addContentTypes(RESOURCES)
                            .addScope(Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    componentProperties
                                            .getVariantDependencies()
                                            .getArtifactCollection(
                                                    RUNTIME_CLASSPATH, PROJECT, JAVA_RES))
                            .build());
        }

        VariantScope variantScope = componentProperties.getVariantScope();

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
                                    componentProperties
                                            .getVariantDependencies()
                                            .getArtifactCollection(
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

        componentProperties.onTestedConfig(
                testedConfig -> {
                    transformManager.addStream(
                            OriginalStream.builder(project, "tested-code-deps")
                                    .addContentTypes(DefaultContentType.CLASSES)
                                    .addScope(Scope.TESTED_CODE)
                                    .setArtifactCollection(
                                            testedConfig
                                                    .getVariantDependencies()
                                                    .getArtifactCollection(
                                                            RUNTIME_CLASSPATH, ALL, CLASSES_JAR))
                                    .build());
                    return null;
                });
    }

    public void createMergeApkManifestsTask(@NonNull ComponentPropertiesImpl componentProperties) {
        ApkVariantData apkVariantData = (ApkVariantData) componentProperties.getVariantData();
        Set<String> screenSizes = apkVariantData.getCompatibleScreens();

        // FIXME
        ApkCreationConfig creationConfig = (ApkCreationConfig) componentProperties;

        taskFactory.register(
                new CompatibleScreensManifest.CreationAction(creationConfig, screenSizes));

        TaskProvider<? extends ManifestProcessorTask> processManifestTask =
                createMergeManifestTasks(creationConfig);

        final MutableTaskContainer taskContainer = componentProperties.getTaskContainer();
        if (taskContainer.getMicroApkTask() != null) {
            TaskFactoryUtils.dependsOn(processManifestTask, taskContainer.getMicroApkTask());
        }
    }

    /** Returns whether or not dependencies from the {@link CustomClassTransform} are packaged */
    protected static boolean packagesCustomClassDependencies(
            @NonNull BaseCreationConfig creationConfig) {
        return appliesCustomClassTransforms(creationConfig)
                && !creationConfig.getVariantType().isDynamicFeature();
    }

    /** Returns whether or not custom class transforms are applied */
    protected static boolean appliesCustomClassTransforms(
            @NonNull BaseCreationConfig creationConfig) {
        if (creationConfig instanceof ApkCreationConfig) {
            return ((ApkCreationConfig) creationConfig).getDebuggable()
                    && !creationConfig.getVariantType().isForTesting()
                    && !getAdvancedProfilingTransforms(
                                    creationConfig.getServices().getProjectOptions())
                            .isEmpty();
        }
        return false;
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
    protected TaskProvider<? extends ManifestProcessorTask> createMergeManifestTasks(
            @NonNull ApkCreationConfig creationConfig) {

        taskFactory.register(new ProcessManifestForBundleTask.CreationAction(creationConfig));
        taskFactory.register(
                new ProcessManifestForMetadataFeatureTask.CreationAction(creationConfig));
        taskFactory.register(new ProcessManifestForInstantAppTask.CreationAction(creationConfig));
        taskFactory.register(new ProcessPackagedManifestTask.CreationAction(creationConfig));
        taskFactory.register(new GenerateManifestJarTask.CreationAction(creationConfig));

        taskFactory.register(
                new ProcessApplicationManifest.CreationAction(
                        creationConfig,
                        !getAdvancedProfilingTransforms(
                                        creationConfig.getServices().getProjectOptions())
                                .isEmpty()));
        return taskFactory.register(
                new ProcessMultiApkApplicationManifest.CreationAction(creationConfig));
    }

    protected void createProcessTestManifestTask(
            @NonNull TestComponentPropertiesImpl componentProperties) {
        taskFactory.register(new ProcessTestManifest.CreationAction(componentProperties));
    }

    public void createRenderscriptTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getBuildFeatures().getRenderScript()) {
            final MutableTaskContainer taskContainer = componentProperties.getTaskContainer();

            TaskProvider<RenderscriptCompile> rsTask =
                    taskFactory.register(
                            new RenderscriptCompile.CreationAction(componentProperties));

            VariantDslInfo variantDslInfo = componentProperties.getVariantDslInfo();

            TaskFactoryUtils.dependsOn(taskContainer.getResourceGenTask(), rsTask);
            // only put this dependency if rs will generate Java code
            if (!variantDslInfo.getRenderscriptNdkModeEnabled()) {
                TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), rsTask);
            }
        }
    }

    public void createMergeResourcesTask(
            @NonNull ComponentPropertiesImpl componentProperties,
            boolean processResources,
            ImmutableSet<MergeResources.Flag> flags) {

        boolean alsoOutputNotCompiledResources =
                componentProperties.getVariantType().isApk()
                        && !componentProperties.getVariantType().isForTesting()
                        && componentProperties.getVariantScope().useResourceShrinker();

        basicCreateMergeResourcesTask(
                componentProperties,
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
            public SingleArtifact<Directory> getOutputType() {
                return InternalArtifactType.MERGED_RES.INSTANCE;
            }
        },
        /**
         * Merge all resources without the dependencies resources for an aar (i.e. "small merge").
         */
        PACKAGE {
            @Override
            public SingleArtifact<Directory> getOutputType() {
                return InternalArtifactType.PACKAGED_RES.INSTANCE;
            }
        };

        public abstract SingleArtifact<Directory> getOutputType();
    }

    public TaskProvider<MergeResources> basicCreateMergeResourcesTask(
            @NonNull ComponentPropertiesImpl componentProperties,
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
                                        + componentProperties.getDirName())
                        : null;

        TaskProvider<MergeResources> mergeResourcesTask =
                taskFactory.register(
                        new MergeResources.CreationAction(
                                componentProperties,
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

        componentProperties
                .getArtifacts()
                .setInitialProvider(mergeResourcesTask, MergeResources::getOutputDir)
                .atLocation(
                        MoreObjects.firstNonNull(
                                        outputLocation,
                                        componentProperties
                                                .getPaths()
                                                .getDefaultMergeResourcesOutputDir())
                                .getAbsolutePath())
                .on(mergeType.getOutputType());

        if (alsoOutputNotCompiledResources) {
            componentProperties
                    .getArtifacts()
                    .setInitialProvider(
                            mergeResourcesTask,
                            MergeResources::getMergedNotCompiledResourcesOutputDirectory)
                    .atLocation(mergedNotCompiledDir.getAbsolutePath())
                    .on(MERGED_NOT_COMPILED_RES.INSTANCE);
        }

        if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
            TaskFactoryUtils.dependsOn(
                    componentProperties.getTaskContainer().getCompileTask(), mergeResourcesTask);
        }

        return mergeResourcesTask;
    }

    public void createMergeAssetsTask(@NonNull ComponentPropertiesImpl componentProperties) {
        taskFactory.register(
                new MergeSourceSetFolders.MergeAppAssetCreationAction(componentProperties));
    }

    public void createMergeJniLibFoldersTasks(
            @NonNull ComponentPropertiesImpl componentProperties) {
        // merge the source folders together using the proper priority.
        taskFactory.register(
                new MergeSourceSetFolders.MergeJniLibFoldersCreationAction(componentProperties));

        // Compute the scopes that need to be merged.
        Set<ScopeType> mergeScopes = getJavaResMergingScopes(componentProperties, NATIVE_LIBS);

        taskFactory.register(
                new MergeNativeLibsTask.CreationAction(mergeScopes, componentProperties));
    }

    public void createBuildConfigTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getBuildFeatures().getBuildConfig()) {
            TaskProvider<GenerateBuildConfig> generateBuildConfigTask = taskFactory.register(
                    new GenerateBuildConfig.CreationAction(componentProperties));
            boolean isBuildConfigBytecodeEnabled =
                    componentProperties
                            .getServices()
                            .getProjectOptions()
                            .get(BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE);

            if (!isBuildConfigBytecodeEnabled
                    || !componentProperties.getVariantDslInfo().getBuildConfigFields().isEmpty()) {
                TaskFactoryUtils.dependsOn(
                        componentProperties.getTaskContainer().getSourceGenTask(),
                        generateBuildConfigTask);
            }
        }
    }

    public void createGenerateResValuesTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getBuildFeatures().getResValues()) {
            TaskProvider<GenerateResValues> generateResValuesTask =
                    taskFactory.register(new GenerateResValues.CreationAction(componentProperties));
            TaskFactoryUtils.dependsOn(
                    componentProperties.getTaskContainer().getResourceGenTask(),
                    generateResValuesTask);
        }
    }

    public void createMlkitTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getBuildFeatures().getMlModelBinding()) {
            taskFactory.register(
                    new MergeSourceSetFolders.MergeMlModelsSourceFoldersCreationAction(
                            componentProperties));
            TaskProvider<GenerateMlModelClass> generateMlModelClassTask =
                    taskFactory.register(
                            new GenerateMlModelClass.CreationAction(componentProperties));
            TaskFactoryUtils.dependsOn(
                    componentProperties.getTaskContainer().getSourceGenTask(),
                    generateMlModelClassTask);
        }
    }

    public void createApkProcessResTask(@NonNull ComponentPropertiesImpl componentProperties) {
        VariantType variantType = componentProperties.getVariantType();
        InternalArtifactType<Directory> packageOutputType =
                (variantType.isApk() && !variantType.isForTesting())
                        ? FEATURE_RESOURCE_PKG.INSTANCE
                        : null;

        createApkProcessResTask(componentProperties, packageOutputType);

        if (componentProperties.getVariantScope().consumesFeatureJars()) {
            taskFactory.register(new MergeAaptProguardFilesCreationAction(componentProperties));
        }
    }

    private void createApkProcessResTask(
            @NonNull ComponentPropertiesImpl componentProperties,
            @Nullable SingleArtifact<Directory> packageOutputType) {
        final GlobalScope globalScope = componentProperties.getGlobalScope();

        // Check AAR metadata files
        taskFactory.register(new CheckAarMetadataTask.CreationAction(componentProperties));

        // Create the APK_ file with processed resources and manifest. Generate the R class.
        createProcessResTask(
                componentProperties,
                packageOutputType,
                MergeType.MERGE,
                globalScope.getProjectBaseName());

        ProjectOptions projectOptions = componentProperties.getServices().getProjectOptions();
        // TODO(b/156339511): get rid of separate flag for app modules.
        boolean nonTransitiveR =
                projectOptions.get(BooleanOption.NON_TRANSITIVE_R_CLASS)
                        && projectOptions.get(BooleanOption.NON_TRANSITIVE_APP_R_CLASS);
        boolean namespaced =
                componentProperties
                        .getGlobalScope()
                        .getExtension()
                        .getAaptOptions()
                        .getNamespaced();

        // TODO(b/138780301): Also use compile time R class in android tests.
        if ((projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS) || nonTransitiveR)
                && !componentProperties.getVariantType().isForTesting()
                && !namespaced) {
            // Generate the COMPILE TIME only R class using the local resources instead of waiting
            // for the above full link to finish. Linking will still output the RUN TIME R class.
            // Since we're gonna use AAPT2 to generate the keep rules, do not generate them here.
            createProcessResTask(
                    componentProperties,
                    packageOutputType,
                    MergeType.PACKAGE,
                    globalScope.getProjectBaseName());
        }
    }

    protected boolean isLibrary() {
        return false;
    }

    public void createProcessResTask(
            @NonNull BaseCreationConfig creationConfig,
            @Nullable SingleArtifact<Directory> packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName) {
        VariantScope scope = creationConfig.getVariantScope();
        // FIXME
        ComponentPropertiesImpl componentPropertiesImpl = (ComponentPropertiesImpl) creationConfig;
        BaseVariantData variantData = componentPropertiesImpl.getVariantData();

        variantData.calculateFilters(creationConfig.getGlobalScope().getExtension().getSplits());

        // The manifest main dex list proguard rules are always needed for the bundle,
        // even if legacy multidex is not explicitly enabled.
        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules =
                creationConfig.getNeedsMainDexList();

        if (creationConfig.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            // TODO: make sure we generate the proguard rules in the namespaced case.
            new NamespacedResourcesTaskManager(globalScope, taskFactory, componentPropertiesImpl)
                    .createNamespacedResourceTasks(
                            packageOutputType,
                            baseName,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules);

            FileCollection rFiles =
                    project.files(
                            creationConfig.getArtifacts().get(RUNTIME_R_CLASS_CLASSES.INSTANCE));

            creationConfig
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "final-r-classes")
                                    .addContentTypes(
                                            scope.getNeedsJavaResStreams()
                                                    ? TransformManager.CONTENT_JARS
                                                    : ImmutableSet.of(DefaultContentType.CLASSES))
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(rFiles)
                                    .build());

            creationConfig.getArtifacts().appendToAllClasses(rFiles);
            return;
        }
        createNonNamespacedResourceTasks(
                componentPropertiesImpl,
                packageOutputType,
                mergeType,
                baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules);
    }

    private void createNonNamespacedResourceTasks(
            @NonNull ComponentPropertiesImpl componentProperties,
            SingleArtifact<Directory> packageOutputType,
            @NonNull MergeType mergeType,
            @NonNull String baseName,
            boolean useAaptToGenerateLegacyMultidexMainDexProguardRules) {

        ArtifactsImpl artifacts = componentProperties.getArtifacts();
        ProjectOptions projectOptions = componentProperties.getServices().getProjectOptions();

        if (mergeType == MergeType.PACKAGE) {
            // MergeType.PACKAGE means we will only merged the resources from our current module
            // (little merge). This is used for finding what goes into the AAR (packaging), and also
            // for parsing the local resources and merging them with the R.txt files from its
            // dependencies to write the R.txt for this module and R.jar for this module and its
            // dependencies.

            // First collect symbols from this module.
            taskFactory.register(new ParseLibraryResourcesTask.CreateAction(componentProperties));

            // Only generate the keep rules when we need them. We don't need to generate them here
            // for non-library modules since AAPT2 will generate them from MergeType.MERGE.
            if (generatesProguardOutputFile(componentProperties) && isLibrary()) {
                taskFactory.register(
                        new GenerateLibraryProguardRulesTask.CreationAction(componentProperties));
            }

            boolean nonTransitiveRClassInApp =
                    projectOptions.get(BooleanOption.NON_TRANSITIVE_R_CLASS)
                            && projectOptions.get(BooleanOption.NON_TRANSITIVE_APP_R_CLASS);
            // Generate the R class for a library using both local symbols and symbols
            // from dependencies.
            // TODO: double check this (what about dynamic features?)
            if (!nonTransitiveRClassInApp || isLibrary()) {
                taskFactory.register(
                        new GenerateLibraryRFileTask.CreationAction(
                                componentProperties, isLibrary()));
            }

            if (!componentProperties.getVariantDslInfo().isDebuggable()
                    && projectOptions.get(BooleanOption.ENABLE_RESOURCE_OPTIMIZATIONS)) {
                if (componentProperties.getVariantScope().useResourceShrinker()) {
                    taskFactory.register(
                            new OptimizeResourcesTask.CreateAction(componentProperties));
                    // Republish the RES_PROCESSED_OPTIMIZED as PROCESSED_RES
                    componentProperties
                            .getArtifacts()
                            .republish(
                                    InternalArtifactType.OPTIMIZED_PROCESSED_RES.INSTANCE,
                                    InternalArtifactType.PROCESSED_RES.INSTANCE);
                } else {
                    logger.error(
                            "Cannot apply AAPT2 OPTIMIZE without resource shrinker being enabled.");
                }
            }
        } else {
            // MergeType.MERGE means we merged the whole universe.
            taskFactory.register(
                    new LinkApplicationAndroidResourcesTask.CreationAction(
                            componentProperties,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules,
                            mergeType,
                            baseName,
                            isLibrary()));

            if (packageOutputType != null) {
                componentProperties
                        .getArtifacts()
                        .republish(PROCESSED_RES.INSTANCE, packageOutputType);
            }

            // TODO: also support stable IDs for the bundle (does it matter?)
            // create the task that creates the aapt output for the bundle.
            if (componentProperties instanceof ApkCreationConfig
                    && !componentProperties.getVariantType().isForTesting()) {
                taskFactory.register(
                        new LinkAndroidResForBundleTask.CreationAction(
                                (ApkCreationConfig) componentProperties));
            }

            componentProperties
                    .getArtifacts()
                    .appendToAllClasses(
                            project.files(
                                    artifacts.get(
                                            COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                                                    .INSTANCE)));
        }
    }

    private static boolean generatesProguardOutputFile(
            @NonNull ComponentPropertiesImpl componentProperties) {
        return componentProperties.getVariantScope().getCodeShrinker() != null
                || componentProperties.getVariantType().isDynamicFeature();
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     *
     * @param componentProperties the scope of the variant being processed.
     * @param contentType the contentType of java resources, must be RESOURCES or NATIVE_LIBS
     * @return the list of scopes for which to merge the java resources.
     */
    @NonNull
    protected abstract Set<ScopeType> getJavaResMergingScopes(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull QualifiedContent.ContentType contentType);

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
     * #createMergeJavaResTask(ComponentPropertiesImpl)}
     */
    public void createProcessJavaResTask(@NonNull ComponentPropertiesImpl componentProperties) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.
        taskFactory.register(new ProcessJavaResTask.CreationAction(componentProperties));

        // create the stream generated from this task, but only if a library with custom transforms,
        // in which case the custom transforms must be applied before java res merging.
        if (componentProperties.getVariantScope().getNeedsJavaResStreams()) {
            componentProperties
                    .getTransformManager()
                    .addStream(
                            OriginalStream.builder(project, "processed-java-res")
                                    .addContentType(RESOURCES)
                                    .addScope(Scope.PROJECT)
                                    .setFileCollection(
                                            componentProperties
                                                    .getServices()
                                                    .fileCollection(
                                                            componentProperties
                                                                    .getArtifacts()
                                                                    .get(
                                                                            InternalArtifactType
                                                                                    .JAVA_RES
                                                                                    .INSTANCE)))
                                    .build());
        }
    }

    /**
     * Sets up the Merge Java Res task.
     *
     * @see #createProcessJavaResTask(ComponentPropertiesImpl)
     */
    public void createMergeJavaResTask(@NonNull ComponentPropertiesImpl componentProperties) {
        TransformManager transformManager = componentProperties.getTransformManager();

        // Compute the scopes that need to be merged.
        Set<ScopeType> mergeScopes = getJavaResMergingScopes(componentProperties, RESOURCES);

        taskFactory.register(
                new MergeJavaResourceTask.CreationAction(mergeScopes, componentProperties));

        // also add a new merged java res stream if needed.
        if (componentProperties.getVariantScope().getNeedsMergedJavaResStream()) {
            Provider<RegularFile> mergedJavaResProvider =
                    componentProperties.getArtifacts().get(MERGED_JAVA_RES.INSTANCE);
            transformManager.addStream(
                    OriginalStream.builder(project, "merged-java-res")
                            .addContentTypes(TransformManager.CONTENT_RESOURCES)
                            .addScopes(mergeScopes)
                            .setFileCollection(project.getLayout().files(mergedJavaResProvider))
                            .build());
        }


    }

    public void createAidlTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getBuildFeatures().getAidl()) {
            MutableTaskContainer taskContainer = componentProperties.getTaskContainer();

            TaskProvider<AidlCompile> aidlCompileTask =
                    taskFactory.register(new AidlCompile.CreationAction(componentProperties));

            TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), aidlCompileTask);
        }
    }

    public void createShaderTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getBuildFeatures().getShaders()) {
            // merge the shader folders together using the proper priority.
            taskFactory.register(
                    new MergeSourceSetFolders.MergeShaderSourceFoldersCreationAction(
                            componentProperties));

            // compile the shaders
            TaskProvider<ShaderCompile> shaderCompileTask =
                    taskFactory.register(new ShaderCompile.CreationAction(componentProperties));

            TaskFactoryUtils.dependsOn(
                    componentProperties.getTaskContainer().getAssetGenTask(), shaderCompileTask);
        }
    }

    protected abstract void postJavacCreation(@NonNull ComponentPropertiesImpl componentProperties);

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    public TaskProvider<? extends JavaCompile> createJavacTask(
            @NonNull ComponentPropertiesImpl componentProperties) {
        taskFactory.register(new JavaPreCompileTask.CreationAction(componentProperties));

        final TaskProvider<? extends JavaCompile> javacTask =
                taskFactory.register(new JavaCompileCreationAction(componentProperties));

        postJavacCreation(componentProperties);

        return javacTask;
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     * <p>This should not be called for classes that will also be compiled from source by jack.
     */
    protected void addJavacClassesStream(@NonNull ComponentPropertiesImpl componentProperties) {
        ArtifactsImpl artifacts = componentProperties.getArtifacts();
        Provider<Directory> javaOutputs = artifacts.get(JAVAC.INSTANCE);
        Preconditions.checkNotNull(javaOutputs);

        // create separate streams for the output of JAVAC and for the pre/post javac
        // bytecode hooks

        TransformManager transformManager = componentProperties.getTransformManager();
        boolean needsJavaResStreams =
                componentProperties.getVariantScope().getNeedsJavaResStreams();

        transformManager.addStream(
                OriginalStream.builder(project, "javac-output")
                        // Need both classes and resources because some annotation
                        // processors generate resources
                        .addContentTypes(
                                needsJavaResStreams
                                        ? TransformManager.CONTENT_JARS
                                        : ImmutableSet.of(DefaultContentType.CLASSES))
                        .addScope(Scope.PROJECT)
                        .setFileCollection(project.getLayout().files(javaOutputs))
                        .build());

        BaseVariantData variantData = componentProperties.getVariantData();
        transformManager.addStream(
                OriginalStream.builder(project, "pre-javac-generated-bytecode")
                        .addContentTypes(
                                needsJavaResStreams
                                        ? TransformManager.CONTENT_JARS
                                        : ImmutableSet.of(DefaultContentType.CLASSES))
                        .addScope(Scope.PROJECT)
                        .setFileCollection(variantData.getAllPreJavacGeneratedBytecode())
                        .build());

        transformManager.addStream(
                OriginalStream.builder(project, "post-javac-generated-bytecode")
                        .addContentTypes(
                                needsJavaResStreams
                                        ? TransformManager.CONTENT_JARS
                                        : ImmutableSet.of(DefaultContentType.CLASSES))
                        .addScope(Scope.PROJECT)
                        .setFileCollection(variantData.getAllPostJavacGeneratedBytecode())
                        .build());
    }

    /** Makes the given task the one used by top-level "compile" task. */
    public static void setJavaCompilerTask(
            @NonNull TaskProvider<? extends JavaCompile> javaCompilerTask,
            @NonNull ComponentPropertiesImpl componentProperties) {
        TaskFactoryUtils.dependsOn(
                componentProperties.getTaskContainer().getCompileTask(), javaCompilerTask);
    }

    public void createExternalNativeBuildJsonGenerators(
            @NonNull ComponentPropertiesImpl componentProperties) {
        CxxModuleModel module = tryCreateCxxModuleModel(componentProperties);

        if (module == null) {
            return;
        }

        componentProperties
                .getTaskContainer()
                .setExternalNativeJsonGenerator(
                        project.provider(
                                () ->
                                        ExternalNativeJsonGeneratorBase.create(
                                                module, componentProperties)));
    }

    public void createExternalNativeBuildTasks(
            @NonNull ComponentPropertiesImpl componentProperties) {
        final MutableTaskContainer taskContainer = componentProperties.getTaskContainer();
        Provider<ExternalNativeJsonGenerator> generator =
                taskContainer.getExternalNativeJsonGenerator();
        if (generator == null) {
            return;
        }

        // Set up JSON generation tasks
        TaskProvider<? extends Task> generateTask =
                taskFactory.register(
                        ExternalNativeBuildJsonTask.createTaskConfigAction(
                                generator, componentProperties));

        // Set up build tasks
        TaskProvider<ExternalNativeBuildTask> buildTask =
                taskFactory.register(
                        new ExternalNativeBuildTask.CreationAction(
                                generator, generateTask, componentProperties));

        TaskFactoryUtils.dependsOn(taskContainer.getCompileTask(), buildTask);

        // Set up clean tasks
        TaskProvider<Task> cleanTask = taskFactory.named("clean");
        CxxModuleModel module = tryCreateCxxModuleModel(componentProperties);

        if (module != null) {
            TaskProvider<ExternalNativeCleanTask> externalNativeCleanTask =
                    taskFactory.register(
                            new ExternalNativeCleanTask.CreationAction(
                                    module, componentProperties));
            TaskFactoryUtils.dependsOn(cleanTask, externalNativeCleanTask);
        }
    }

    /** Creates the tasks to build unit tests. */
    private void createUnitTestVariantTasks(@NonNull UnitTestPropertiesImpl unitTestProperties) {
        final MutableTaskContainer taskContainer = unitTestProperties.getTaskContainer();

        VariantPropertiesImpl testedVariant = unitTestProperties.getTestedVariant();

        boolean includeAndroidResources = extension.getTestOptions().getUnitTests()
                .isIncludeAndroidResources();

        createAnchorTasks(unitTestProperties);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(unitTestProperties);

        // process java resources
        createProcessJavaResTask(unitTestProperties);

        if (includeAndroidResources) {
            if (testedVariant.getVariantType().isAar()) {
                // Add a task to process the manifest
                createProcessTestManifestTask(unitTestProperties);

                // Add a task to create the res values
                createGenerateResValuesTask(unitTestProperties);

                // Add a task to merge the assets folders
                createMergeAssetsTask(unitTestProperties);

                createMergeResourcesTask(unitTestProperties, true, ImmutableSet.of());
                // Add a task to process the Android Resources and generate source files
                createApkProcessResTask(unitTestProperties, FEATURE_RESOURCE_PKG.INSTANCE);
                taskFactory.register(new PackageForUnitTest.CreationAction(unitTestProperties));

                // Add data binding tasks if enabled
                createDataBindingTasksIfNecessary(unitTestProperties);
            } else if (testedVariant.getVariantType().isApk()) {
                // The IDs will have been inlined for an non-namespaced application
                // so just re-export the artifacts here.
                unitTestProperties
                        .getArtifacts()
                        .copy(PROCESSED_RES.INSTANCE, testedVariant.getArtifacts());
                unitTestProperties
                        .getArtifacts()
                        .copy(MERGED_ASSETS.INSTANCE, testedVariant.getArtifacts());

                taskFactory.register(new PackageForUnitTest.CreationAction(unitTestProperties));
            } else {
                throw new IllegalStateException(
                        "Tested variant "
                                + testedVariant.getName()
                                + " in "
                                + globalScope.getProject().getPath()
                                + " must be a library or an application to have unit tests.");
            }

            TaskProvider<GenerateTestConfig> generateTestConfig =
                    taskFactory.register(new GenerateTestConfig.CreationAction(unitTestProperties));
            TaskProvider<? extends Task> compileTask = taskContainer.getCompileTask();
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
                                new GenerateTestConfig.TestConfigInputs(unitTestProperties);
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
                        taskInputs.property(
                                "mainVariantOutput", testConfigInputs.getMainVariantOutput());
                        taskInputs.property(
                                "packageNameOfFinalRClassProvider",
                                testConfigInputs.getPackageNameOfFinalRClass());
                    });
        } else {
            if (testedVariant.getVariantType().isAar()) {
                // With compile classpath R classes, we need to generate a dummy R class for unit tests
                // See https://issuetracker.google.com/143762955 for more context.
                taskFactory.register(
                        new GenerateLibraryRFileTask.TestRuntimeStubRClassCreationAction(
                                unitTestProperties));
            }
        }

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
        // dependencies on tasks that prepare necessary data files.
        TaskProvider<? extends Task> compileTask = taskContainer.getCompileTask();
        //noinspection unchecked
        TaskFactoryUtils.dependsOn(
                compileTask,
                taskContainer.getProcessJavaResourcesTask(),
                testedVariant.getTaskContainer().getProcessJavaResourcesTask());

        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(unitTestProperties);
        addJavacClassesStream(unitTestProperties);
        setJavaCompilerTask(javacTask, unitTestProperties);
        // This should be done automatically by the classpath
        //        TaskFactoryUtils.dependsOn(javacTask, testedVariantScope.getTaskContainer().getJavacTask());

        // TODO: use merged java res for unit tests (bug 118690729)

        createRunUnitTestTask(unitTestProperties);

        // This hides the assemble unit test task from the task list.

        taskContainer.getAssembleTask().configure(task -> task.setGroup(null));
    }

    protected void registerRClassTransformStream(
            @NonNull ComponentPropertiesImpl componentProperties) {
        if (globalScope.getExtension().getAaptOptions().getNamespaced()) {
            return;
        }

        Provider<RegularFile> rClassJar =
                componentProperties
                        .getArtifacts()
                        .get(
                                InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                                        .INSTANCE);

        componentProperties
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "compile-and-runtime-light-r-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(QualifiedContent.Scope.PROJECT)
                                .setFileCollection(project.files(rClassJar))
                                .build());
    }

    /** Creates the tasks to build android tests. */
    private void createAndroidTestVariantTasks(
            @NonNull AndroidTestPropertiesImpl androidTestProperties) {

        createAnchorTasks(androidTestProperties);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(androidTestProperties);

        // Add a task to process the manifest
        createProcessTestManifestTask(androidTestProperties);

        // Add a task to create the res values
        createGenerateResValuesTask(androidTestProperties);

        // Add a task to compile renderscript files.
        createRenderscriptTask(androidTestProperties);

        // Add a task to merge the resource folders
        createMergeResourcesTask(androidTestProperties, true, ImmutableSet.of());

        // Add tasks to compile shader
        createShaderTask(androidTestProperties);

        // Add a task to merge the assets folders
        createMergeAssetsTask(androidTestProperties);

        taskFactory.register(new CompressAssetsTask.CreationAction(androidTestProperties));

        // Add a task to create the BuildConfig class
        createBuildConfigTask(androidTestProperties);

        // Add a task to generate resource source files
        createApkProcessResTask(androidTestProperties);

        registerRClassTransformStream(androidTestProperties);

        // process java resources
        createProcessJavaResTask(androidTestProperties);

        createAidlTask(androidTestProperties);

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(androidTestProperties);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(androidTestProperties);

        // Add a task to compile the test application
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(androidTestProperties);
        addJavacClassesStream(androidTestProperties);
        setJavaCompilerTask(javacTask, androidTestProperties);
        createPostCompilationTasks(androidTestProperties);

        // Add a task to produce the signing config file
        createValidateSigningTask(androidTestProperties);
        taskFactory.register(new SigningConfigWriterTask.CreationAction(androidTestProperties));

        createPackagingTask(androidTestProperties);

        taskFactory.configure(
                ASSEMBLE_ANDROID_TEST,
                assembleTest ->
                        assembleTest.dependsOn(
                                androidTestProperties
                                        .getTaskContainer()
                                        .getAssembleTask()
                                        .getName()));

        createConnectedTestForVariant(androidTestProperties);
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a lint task earlier
     * which runs on all variants.
     */
    public void createLintTasks(
            @NonNull VariantPropertiesT variantProperties,
            @NonNull List<ComponentInfo<VariantT, VariantPropertiesT>> allVariants) {
        taskFactory.register(
                new LintPerVariantTask.CreationAction(
                        variantProperties,
                        allVariants.stream()
                                .map(ComponentInfo::getProperties)
                                .collect(Collectors.toList())));
    }

    /** Returns the full path of a task given its name. */
    private String getTaskPath(String taskName) {
        return project.getRootProject() == project
                ? ':' + taskName
                : project.getPath() + ':' + taskName;
    }

    public void maybeCreateLintVitalTask(
            @NonNull VariantPropertiesT variant,
            @NonNull List<ComponentInfo<VariantT, VariantPropertiesT>> allVariants) {
        if (variant.getVariantDslInfo().isDebuggable()
                || !extension.getLintOptions().isCheckReleaseBuilds()) {
            return;
        }

        TaskProvider<LintPerVariantTask> lintReleaseCheck =
                taskFactory.register(
                        new LintPerVariantTask.VitalCreationAction(
                                variant,
                                allVariants.stream()
                                        .map(ComponentInfo::getProperties)
                                        .collect(Collectors.toList())),
                        null,
                        task -> task.dependsOn(variant.getTaskContainer().getJavacTask()),
                        null);

        TaskFactoryUtils.dependsOn(variant.getTaskContainer().getAssembleTask(), lintReleaseCheck);

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

    private void createRunUnitTestTask(@NonNull UnitTestPropertiesImpl unitTestProperties) {
        TaskProvider<AndroidUnitTest> runTestsTask =
                taskFactory.register(new AndroidUnitTest.CreationAction(unitTestProperties));

        taskFactory.configure(JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask));
    }

    private void createTopLevelTestTasks() {
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

    protected void createConnectedTestForVariant(
            @NonNull AndroidTestPropertiesImpl androidTestProperties) {
        VariantPropertiesImpl testedVariant = androidTestProperties.getTestedVariant();

        boolean isLibrary = testedVariant.getVariantType().isAar();

        AbstractTestDataImpl testData;
        if (testedVariant.getVariantType().isDynamicFeature()) {
            testData =
                    new BundleTestDataImpl(
                            androidTestProperties,
                            androidTestProperties
                                    .getArtifacts()
                                    .get(InternalArtifactType.APK.INSTANCE),
                            FeatureSplitUtils.getFeatureName(globalScope.getProject().getPath()),
                            testedVariant
                                    .getVariantDependencies()
                                    .getArtifactFileCollection(
                                            RUNTIME_CLASSPATH, PROJECT, APKS_FROM_BUNDLE));
        } else {
            ConfigurableFileCollection testedApkFileCollection =
                    project.files(
                            testedVariant.getArtifacts().get(InternalArtifactType.APK.INSTANCE));

            testData =
                    new TestDataImpl(
                            androidTestProperties,
                            androidTestProperties
                                    .getArtifacts()
                                    .get(InternalArtifactType.APK.INSTANCE),
                            isLibrary ? null : testedApkFileCollection);
        }

        configureTestData(androidTestProperties, testData);

        TaskProvider<DeviceProviderInstrumentTestTask> connectedTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                androidTestProperties,
                                new ConnectedDeviceProvider(
                                        globalScope
                                                .getSdkComponents()
                                                .flatMap(
                                                        SdkComponentsBuildService
                                                                ::getAdbExecutableProvider),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(logger)),
                                DeviceProviderInstrumentTestTask.CreationAction.Type
                                        .INTERNAL_CONNECTED_DEVICE_PROVIDER,
                                testData));

        taskFactory.configure(
                CONNECTED_ANDROID_TEST,
                connectedAndroidTest -> connectedAndroidTest.dependsOn(connectedTask));

        if (testedVariant.getVariantDslInfo().isTestCoverageEnabled()) {

            Configuration jacocoAntConfiguration =
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                            project, JacocoTask.getJacocoVersion(androidTestProperties));
            TaskProvider<JacocoReportTask> reportTask =
                    taskFactory.register(
                            new JacocoReportTask.CreationAction(
                                    androidTestProperties, jacocoAntConfiguration));

            TaskFactoryUtils.dependsOn(
                    testedVariant.getTaskContainer().getCoverageReportTask(), reportTask);

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
                                    androidTestProperties,
                                    deviceProvider,
                                    DeviceProviderInstrumentTestTask.CreationAction.Type
                                            .CUSTOM_DEVICE_PROVIDER,
                                    testData));

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
                                    androidTestProperties, testServer));
            TaskFactoryUtils.dependsOn(
                    serverTask, androidTestProperties.getTaskContainer().getAssembleTask());

            taskFactory.configure(
                    DEVICE_CHECK, deviceAndroidTest -> deviceAndroidTest.dependsOn(serverTask));
        }
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     * <p>These tasks create the dex file from the .class files, plus optional intermediary steps
     * like proguard and jacoco
     */
    public void createPostCompilationTasks(@NonNull ApkCreationConfig creationConfig) {
        // FIXME we need to remove this but this has large impact in the code
        ComponentPropertiesImpl componentProperties = (ComponentPropertiesImpl) creationConfig;

        checkNotNull(componentProperties.getTaskContainer().getJavacTask());

        VariantDslInfo variantDslInfo = componentProperties.getVariantDslInfo();
        VariantScope variantScope = componentProperties.getVariantScope();

        TransformManager transformManager = componentProperties.getTransformManager();

        taskFactory.register(new MergeGeneratedProguardFilesCreationAction(componentProperties));

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled =
                variantDslInfo.isTestCoverageEnabled()
                        && !componentProperties.getVariantType().isForTesting();
        if (isTestCoverageEnabled) {
            createJacocoTask(componentProperties);
        }

        maybeCreateDesugarTask(
                componentProperties,
                componentProperties.getMinSdkVersion(),
                transformManager,
                isTestCoverageEnabled);

        BaseExtension extension = componentProperties.getGlobalScope().getExtension();

        // Merge Java Resources.
        createMergeJavaResTask(componentProperties);

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
                                    componentProperties,
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
                                                    componentProperties
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
        if (componentProperties.getVariantType().isDynamicFeature()
                || variantScope.consumesFeatureJars()) {
            taskFactory.register(new MergeClassesTask.CreationAction(componentProperties));
        }

        // ----- Android studio profiling transforms

        if (appliesCustomClassTransforms(componentProperties)) {
            for (String jar :
                    getAdvancedProfilingTransforms(
                            componentProperties.getServices().getProjectOptions())) {
                if (jar != null) {
                    transformManager.addTransform(
                            taskFactory,
                            componentProperties,
                            new CustomClassTransform(
                                    jar, packagesCustomClassDependencies(componentProperties)));
                }
            }
        }

        // ----- Minify next -----
        maybeCreateCheckDuplicateClassesTask(componentProperties);
        maybeCreateJavaCodeShrinkerTask(componentProperties);
        if (componentProperties.getVariantScope().getCodeShrinker() == CodeShrinker.R8) {
            maybeCreateResourcesShrinkerTasks(componentProperties);
            maybeCreateDexDesugarLibTask(creationConfig, componentProperties, false);
            return;
        }

        // ----- Multi-Dex support
        DexingType dexingType = creationConfig.getDexingType();

        // Upgrade from legacy multi-dex to native multi-dex if possible when using with a device
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            if (variantDslInfo.isMultiDexEnabled()
                    && variantDslInfo.getMinSdkVersionWithTargetDeviceApi().getFeatureLevel()
                            >= 21) {
                dexingType = DexingType.NATIVE_MULTIDEX;
            }
        }

        if (componentProperties.getNeedsMainDexList()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(componentProperties, false));
        }

        if (variantScope.getNeedsMainDexListForBundle()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(componentProperties, true));
        }

        createDexTasks(
                creationConfig, componentProperties, dexingType, registeredExternalTransform);

        maybeCreateResourcesShrinkerTasks(componentProperties);

        maybeCreateDexSplitterTask(creationConfig);
    }

    private void maybeCreateDesugarTask(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull AndroidVersion minSdk,
            @NonNull TransformManager transformManager,
            boolean isTestCoverageEnabled) {
        VariantScope variantScope = componentProperties.getVariantScope();
        if (variantScope.getJava8LangSupportType() == Java8LangSupport.DESUGAR) {
            componentProperties
                    .getTransformManager()
                    .consumeStreams(
                            ImmutableSet.of(Scope.EXTERNAL_LIBRARIES),
                            TransformManager.CONTENT_CLASS);

            taskFactory.register(
                    new RecalculateStackFramesTask.CreationAction(
                            componentProperties, isTestCoverageEnabled));

            taskFactory.register(new DesugarTask.CreationAction(componentProperties));

            if (minSdk.getFeatureLevel()
                    >= DesugarProcessArgs.MIN_SUPPORTED_API_TRY_WITH_RESOURCES) {
                return;
            }

            ScopeType testedType =
                    componentProperties.onTestedConfig(
                            testedConfig -> {
                                if (!testedConfig.getVariantType().isAar()) {
                                    // test variants, except for library, should not package
                                    // try-with-resources jar
                                    // as the tested variant already contains it.
                                    return Scope.PROVIDED_ONLY;
                                }

                                return null;
                            });

            QualifiedContent.ScopeType scopeType =
                    testedType != null ? testedType : Scope.EXTERNAL_LIBRARIES;

            // add runtime classes for try-with-resources support
            TaskProvider<ExtractTryWithResourcesSupportJar> extractTryWithResources =
                    taskFactory.register(
                            new ExtractTryWithResourcesSupportJar.CreationAction(
                                    componentProperties));
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
            @NonNull ApkCreationConfig apkCreationConfig,
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull DexingType dexingType,
            boolean registeredExternalTransform) {
        DefaultDexOptions dexOptions;
        final VariantType variantType = componentProperties.getVariantType();
        if (variantType.isTestComponent()) {
            // Don't use custom dx flags when compiling the test FULL_APK. They can break the test FULL_APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        Java8LangSupport java8SLangSupport =
                componentProperties.getVariantScope().getJava8LangSupportType();
        boolean minified = componentProperties.getVariantScope().getCodeShrinker() != null;
        boolean supportsDesugaring =
                java8SLangSupport == Java8LangSupport.UNUSED
                        || (java8SLangSupport == Java8LangSupport.D8
                                && componentProperties
                                        .getServices()
                                        .getProjectOptions()
                                        .get(
                                                BooleanOption
                                                        .ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM));
        boolean enableDexingArtifactTransform =
                componentProperties
                                .getServices()
                                .getProjectOptions()
                                .get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)
                        && !registeredExternalTransform
                        && !minified
                        && supportsDesugaring
                        && !appliesCustomClassTransforms(componentProperties);

        taskFactory.register(
                new DexArchiveBuilderTask.CreationAction(
                        dexOptions,
                        enableDexingArtifactTransform,
                        componentProperties));

        maybeCreateDexDesugarLibTask(
                apkCreationConfig, componentProperties, enableDexingArtifactTransform);

        createDexMergingTasks(componentProperties, dexingType, enableDexingArtifactTransform);
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
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull DexingType dexingType,
            boolean dexingUsingArtifactTransforms) {

        // When desugaring, The file dependencies are dexed in a task with the whole
        // remote classpath present, as they lack dependency information to desugar
        // them correctly in an artifact transform.
        boolean separateFileDependenciesDexingTask =
                componentProperties.getVariantScope().getJava8LangSupportType()
                                == Java8LangSupport.D8
                        && dexingUsingArtifactTransforms;
        if (separateFileDependenciesDexingTask) {
            DexFileDependenciesTask.CreationAction desugarFileDeps =
                    new DexFileDependenciesTask.CreationAction(componentProperties);
            taskFactory.register(desugarFileDeps);
        }

        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            DexMergingTask.CreationAction configAction =
                    new DexMergingTask.CreationAction(
                            componentProperties,
                            DexMergingAction.MERGE_ALL,
                            dexingType,
                            dexingUsingArtifactTransforms,
                            separateFileDependenciesDexingTask);
            taskFactory.register(configAction);
        } else if (componentProperties.getVariantScope().getCodeShrinker() != null) {
            DexMergingTask.CreationAction configAction =
                    new DexMergingTask.CreationAction(
                            componentProperties,
                            DexMergingAction.MERGE_ALL,
                            dexingType,
                            dexingUsingArtifactTransforms);
            taskFactory.register(configAction);
        } else {
            boolean produceSeparateOutputs =
                    dexingType == DexingType.NATIVE_MULTIDEX
                            && componentProperties.getVariantDslInfo().isDebuggable();

            taskFactory.register(
                    new DexMergingTask.CreationAction(
                            componentProperties,
                            DexMergingAction.MERGE_EXTERNAL_LIBS,
                            DexingType.NATIVE_MULTIDEX,
                            dexingUsingArtifactTransforms,
                            separateFileDependenciesDexingTask,
                            produceSeparateOutputs
                                    ? InternalMultipleArtifactType.DEX.INSTANCE
                                    : InternalMultipleArtifactType.EXTERNAL_LIBS_DEX.INSTANCE));

            if (produceSeparateOutputs) {
                DexMergingTask.CreationAction mergeProject =
                        new DexMergingTask.CreationAction(
                                componentProperties,
                                DexMergingAction.MERGE_PROJECT,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(mergeProject);

                DexMergingTask.CreationAction mergeLibraries =
                        new DexMergingTask.CreationAction(
                                componentProperties,
                                DexMergingAction.MERGE_LIBRARY_PROJECTS,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(mergeLibraries);
            } else {
                DexMergingTask.CreationAction configAction =
                        new DexMergingTask.CreationAction(
                                componentProperties,
                                DexMergingAction.MERGE_ALL,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(configAction);
            }
        }
    }

    protected void handleJacocoDependencies(@NonNull ComponentPropertiesImpl componentProperties) {
        VariantDslInfo variantDslInfo = componentProperties.getVariantDslInfo();
        // we add the jacoco jar if coverage is enabled, but we don't add it
        // for test apps as it's already part of the tested app.
        // For library project, since we cannot use the local jars of the library,
        // we add it as well.
        boolean isTestCoverageEnabled =
                variantDslInfo.isTestCoverageEnabled()
                        && (!componentProperties.getVariantType().isTestComponent()
                                || (variantDslInfo.getTestedVariant() != null
                                        && variantDslInfo
                                                .getTestedVariant()
                                                .getVariantType()
                                                .isAar()));
        if (isTestCoverageEnabled) {
            if (componentProperties.getVariantScope().getDexer() == DexerTool.DX) {
                componentProperties
                        .getServices()
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
                            JacocoTask.getJacocoVersion(componentProperties));
            project.getDependencies()
                    .add(
                            componentProperties
                                    .getVariantDependencies()
                                    .getRuntimeClasspath()
                                    .getName(),
                            jacocoAgentRuntimeDependency);

            // we need to force the same version of Jacoco we use for instrumentation
            componentProperties
                    .getVariantDependencies()
                    .getRuntimeClasspath()
                    .resolutionStrategy(r -> r.force(jacocoAgentRuntimeDependency));
        }
    }

    public void createJacocoTask(@NonNull ComponentPropertiesImpl componentProperties) {
        componentProperties
                .getTransformManager()
                .consumeStreams(
                        ImmutableSet.of(Scope.PROJECT),
                        ImmutableSet.of(DefaultContentType.CLASSES));
        taskFactory.register(new JacocoTask.CreationAction(componentProperties));

        FileCollection instumentedClasses =
                project.files(
                        componentProperties
                                .getArtifacts()
                                .get(InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES.INSTANCE),
                        project.files(
                                        componentProperties
                                                .getArtifacts()
                                                .get(
                                                        InternalArtifactType
                                                                .JACOCO_INSTRUMENTED_JARS
                                                                .INSTANCE))
                                .getAsFileTree());
        componentProperties
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "jacoco-instrumented-classes")
                                .addContentTypes(DefaultContentType.CLASSES)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(instumentedClasses)
                                .build());
    }

    protected void createDataBindingTasksIfNecessary(
            @NonNull ComponentPropertiesImpl componentProperties) {
        boolean dataBindingEnabled = componentProperties.getBuildFeatures().getDataBinding();
        boolean viewBindingEnabled = componentProperties.getBuildFeatures().getViewBinding();
        if (!dataBindingEnabled && !viewBindingEnabled) {
            return;
        }

        taskFactory.register(
                new DataBindingMergeBaseClassLogTask.CreationAction(componentProperties));

        taskFactory.register(
                new DataBindingMergeDependencyArtifactsTask.CreationAction(componentProperties));

        DataBindingBuilder.setDebugLogEnabled(getLogger().isDebugEnabled());

        taskFactory.register(new DataBindingGenBaseClassesTask.CreationAction(componentProperties));

        // DATA_BINDING_TRIGGER artifact is created for data binding only (not view binding)
        if (dataBindingEnabled) {
            taskFactory.register(new DataBindingTriggerTask.CreationAction(componentProperties));
            setDataBindingAnnotationProcessorParams(componentProperties);
        }
    }

    private void setDataBindingAnnotationProcessorParams(
            @NonNull ComponentPropertiesImpl componentProperties) {
        VariantDslInfo variantDslInfo = componentProperties.getVariantDslInfo();
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
                            componentProperties,
                            getLogger().isDebugEnabled(),
                            DataBindingBuilder.getPrintMachineReadableOutput());
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
     */
    public void createPackagingTask(@NonNull ApkCreationConfig creationConfig) {
        // ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();
        final MutableTaskContainer taskContainer = creationConfig.getTaskContainer();

        boolean signedApk = creationConfig.getVariantDslInfo().isSigningReady();

        /*
         * PrePackaging step class that will look if the packaging of the main FULL_APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split FULL_APK. However when a warm swap
         * is possible, it is not necessary to produce immediately the new main SPLIT since the
         * runtime use the resources.ap_ file directly. However, as soon as an incompatible change
         * forcing a cold swap is triggered, the main FULL_APK must be rebuilt (even if the
         * resources were changed in a previous build).
         */
        VariantScope variantScope = creationConfig.getVariantScope();
        InternalArtifactType<Directory> manifestType = creationConfig.getManifestArtifactType();

        Provider<Directory> manifests = creationConfig.getArtifacts().get(manifestType);

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
                                creationConfig,
                                creationConfig.getPaths().getApkLocation(),
                                variantScope.useResourceShrinker(),
                                manifests,
                                manifestType,
                                packagesCustomClassDependencies(creationConfig)),
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
        creationConfig
                .getArtifacts()
                .republish(InternalArtifactType.APK.INSTANCE, ArtifactType.APK.INSTANCE);

        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            createInstallTask(creationConfig);
        }

        // add an uninstall task
        final TaskProvider<UninstallTask> uninstallTask =
                taskFactory.register(new UninstallTask.CreationAction(creationConfig));

        taskFactory.configure(UNINSTALL_ALL, uninstallAll -> uninstallAll.dependsOn(uninstallTask));
    }

    protected void createInstallTask(@NonNull ApkCreationConfig creationConfig) {
        taskFactory.register(new InstallVariantTask.CreationAction(creationConfig));
    }

    protected void createValidateSigningTask(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getVariantDslInfo().getSigningConfig() == null) {
            return;
        }

        // FIXME create one per signing config instead of one per variant.
        taskFactory.register(
                new ValidateSigningTask.CreationAction(
                        componentProperties,
                        GradleKeystoreHelper.getDefaultDebugKeystoreLocation(
                                componentProperties.getServices().getGradleEnvironmentProvider())));
    }

    /**
     * Create assemble* and bundle* anchor tasks.
     *
     * <p>This does not create the variant specific version of these tasks only the ones that are
     * per build-type, per-flavor, per-flavor-combo and the main 'assemble' and 'bundle' ones.
     *
     * @param flavorCount the number of flavors
     * @param flavorDimensionCount whether there are flavor dimensions at all.
     */
    public void createAnchorAssembleTasks(
            int flavorCount,
            int flavorDimensionCount) {

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

            for (ComponentPropertiesImpl component : allPropertiesList) {
                final VariantType variantType = component.getVariantType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = component.getTaskContainer();
                    final VariantDslInfo variantDslInfo = component.getVariantDslInfo();
                    final String buildType = component.getBuildType();

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
                        assembleMap.put(component.getFlavorName(), assembleTask);
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
                            bundleMap.put(component.getFlavorName(), bundleTask);
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
            for (ComponentPropertiesImpl component : allPropertiesList) {
                final VariantType variantType = component.getVariantType();
                if (!variantType.isTestComponent()) {
                    final MutableTaskContainer taskContainer = component.getTaskContainer();

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

    public void createAssembleTask(@NonNull ComponentPropertiesImpl componentProperties) {
        taskFactory.register(
                componentProperties.computeTaskName("assemble"),
                null /*preConfigAction*/,
                task ->
                        task.setDescription(
                                "Assembles main output for variant "
                                        + componentProperties.getName()),
                taskProvider ->
                        componentProperties.getTaskContainer().setAssembleTask(taskProvider));
    }

    public void createBundleTask(@NonNull ComponentPropertiesImpl componentProperties) {
        taskFactory.register(
                componentProperties.computeTaskName("bundle"),
                null,
                task -> {
                    task.setDescription(
                            "Assembles bundle for variant " + componentProperties.getName());
                    task.dependsOn(
                            componentProperties
                                    .getArtifacts()
                                    .get(InternalArtifactType.BUNDLE.INSTANCE));
                },
                taskProvider -> componentProperties.getTaskContainer().setBundleTask(taskProvider));

        // republish Bundle to the external world.
        componentProperties
                .getArtifacts()
                .republish(InternalArtifactType.BUNDLE.INSTANCE, ArtifactType.BUNDLE.INSTANCE);
    }

    protected void maybeCreateJavaCodeShrinkerTask(
            @NonNull ComponentPropertiesImpl componentProperties) {
        CodeShrinker codeShrinker = componentProperties.getVariantScope().getCodeShrinker();

        if (codeShrinker != null) {
            doCreateJavaCodeShrinkerTask(
                    componentProperties,
                    // No mapping in non-test modules.
                    codeShrinker);
        }
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules.
     */
    protected final void doCreateJavaCodeShrinkerTask(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull CodeShrinker codeShrinker) {
        doCreateJavaCodeShrinkerTask(componentProperties, codeShrinker, false);
    }

    protected final void doCreateJavaCodeShrinkerTask(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull CodeShrinker codeShrinker,
            Boolean isTestApplication) {
        @NonNull TaskProvider<? extends Task> task;
        switch (codeShrinker) {
            case PROGUARD:
                task = createProguardTask(componentProperties, isTestApplication);
                break;
            case R8:
                task = createR8Task(componentProperties, isTestApplication);
                break;
            default:
                throw new AssertionError("Unknown value " + codeShrinker);
        }
        if (componentProperties.getVariantScope().getPostprocessingFeatures() != null) {
            TaskProvider<CheckProguardFiles> checkFilesTask =
                    taskFactory.register(
                            new CheckProguardFiles.CreationAction(componentProperties));

            TaskFactoryUtils.dependsOn(task, checkFilesTask);
        }
    }

    @NonNull
    private TaskProvider<ProguardTask> createProguardTask(
            @NonNull ComponentPropertiesImpl componentProperties, boolean isTestApplication) {
        return taskFactory.register(
                new ProguardTask.CreationAction(componentProperties, isTestApplication));
    }

    @NonNull
    private TaskProvider<R8Task> createR8Task(
            @NonNull BaseCreationConfig creationConfig, Boolean isTestApplication) {
        if (creationConfig instanceof ApplicationCreationConfig) {
            publishFeatureDex((ApplicationCreationConfig) creationConfig);
        }
        return taskFactory.register(new R8Task.CreationAction(creationConfig, isTestApplication));
    }

    private void maybeCreateDexSplitterTask(@NonNull ApkCreationConfig creationConfig) {
        if (!creationConfig.getVariantScope().consumesFeatureJars()) {
            return;
        }

        taskFactory.register(new DexSplitterTask.CreationAction(creationConfig));

        if (creationConfig instanceof ApplicationCreationConfig) {
            publishFeatureDex((ApplicationCreationConfig) creationConfig);
        }
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
    private void publishFeatureDex(@NonNull ApplicationCreationConfig creationConfig) {
        // first calculate the list of module paths
        final Collection<String> modulePaths;
        final BaseExtension extension = globalScope.getExtension();
        if (extension instanceof BaseAppModuleExtension) {
            modulePaths = ((BaseAppModuleExtension) extension).getDynamicFeatures();
        } else {
            return;
        }

        Configuration configuration =
                creationConfig.getVariantDependencies().getElements(RUNTIME_ELEMENTS);
        Preconditions.checkNotNull(
                configuration,
                "Publishing to Runtime Element with no Runtime Elements configuration object. "
                        + "VariantType: "
                        + creationConfig.getVariantType());
        Provider<Directory> artifact =
                creationConfig.getArtifacts().get(InternalArtifactType.FEATURE_DEX.INSTANCE);

        DirectoryProperty artifactDirectory =
                creationConfig.getGlobalScope().getProject().getObjects().directoryProperty();
        artifactDirectory.set(artifact);

        for (String modulePath : modulePaths) {
            Provider<RegularFile> file =
                    artifactDirectory.file(getFeatureFileName(modulePath, null));
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
    protected void maybeCreateResourcesShrinkerTasks(
            @NonNull ComponentPropertiesImpl componentProperties) {
        if (!componentProperties.getVariantScope().useResourceShrinker()) {
            return;
        }

        // if resources are shrink, create task per variant output
        // to transform the res package into a stripped res package

        taskFactory.register(new ShrinkResourcesTask.CreationAction(componentProperties));

        // And for the bundle
        taskFactory.register(new ShrinkBundleResourcesTask.CreationAction(componentProperties));
    }

    private void createReportTasks() {

        taskFactory.register(
                "androidDependencies",
                DependencyReportTask.class,
                task -> {
                    task.setDescription("Displays the Android dependencies of the project.");
                    task.variants = variantPropertiesList;
                    task.testComponents = testComponentPropertiesList;
                    task.setGroup(ANDROID_GROUP);
                });

        List<ComponentPropertiesImpl> signingReportComponents =
                allPropertiesList.stream()
                        .filter(
                                component ->
                                        component.getVariantType().isForTesting()
                                                || component.getVariantType().isBaseModule())
                        .collect(Collectors.toList());
        if (!signingReportComponents.isEmpty()) {
            taskFactory.register(
                    "signingReport",
                    SigningReportTask.class,
                    task -> {
                        task.setDescription(
                                "Displays the signing info for the base and test modules");
                        task.setComponents(signingReportComponents);
                        task.setGroup(ANDROID_GROUP);
                    });
        }

        createDependencyAnalyzerTask();
    }

    protected void createDependencyAnalyzerTask() {

        for (VariantPropertiesT variant : variantPropertiesList) {
            taskFactory.register(new AnalyzeDependenciesTask.CreationAction(variant));
        }

        for (TestComponentPropertiesImpl testComponent : testComponentPropertiesList) {
            taskFactory.register(new AnalyzeDependenciesTask.CreationAction(testComponent));
        }
    }

    public void createAnchorTasks(@NonNull ComponentPropertiesImpl componentProperties) {
        createVariantPreBuildTask(componentProperties);

        // also create sourceGenTask
        final BaseVariantData variantData = componentProperties.getVariantData();
        componentProperties
                .getTaskContainer()
                .setSourceGenTask(
                        taskFactory.register(
                                componentProperties.computeTaskName("generate", "Sources"),
                                task -> {
                                    task.dependsOn(COMPILE_LINT_CHECKS_TASK);
                                    if (componentProperties.getVariantType().isAar()) {
                                        task.dependsOn(PrepareLintJarForPublish.NAME);
                                    }
                                    task.dependsOn(variantData.getExtraGeneratedResFolders());
                                }));
        // and resGenTask
        componentProperties
                .getTaskContainer()
                .setResourceGenTask(
                        taskFactory.register(
                                componentProperties.computeTaskName("generate", "Resources")));

        componentProperties
                .getTaskContainer()
                .setAssetGenTask(
                        taskFactory.register(
                                componentProperties.computeTaskName("generate", "Assets")));

        if (!componentProperties.getVariantType().isForTesting()
                && componentProperties.getVariantDslInfo().isTestCoverageEnabled()) {
            componentProperties
                    .getTaskContainer()
                    .setCoverageReportTask(
                            taskFactory.register(
                                    componentProperties.computeTaskName("create", "CoverageReport"),
                                    task -> {
                                        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                                        task.setDescription(
                                                String.format(
                                                        "Creates test coverage reports for the %s variant.",
                                                        componentProperties.getName()));
                                    }));
        }

        // and compile task
        createCompileAnchorTask(componentProperties);
    }

    protected void createVariantPreBuildTask(@NonNull ComponentPropertiesImpl componentProperties) {
        // default pre-built task.
        createDefaultPreBuildTask(componentProperties);
    }

    protected void createDefaultPreBuildTask(@NonNull ComponentPropertiesImpl componentProperties) {
        taskFactory.register(new PreBuildCreationAction(componentProperties));
    }

    public abstract static class AbstractPreBuildCreationAction<TaskT extends AndroidVariantTask>
            extends VariantTaskCreationAction<TaskT, ComponentPropertiesImpl> {

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("pre", "Build");
        }

        public AbstractPreBuildCreationAction(
                @NonNull ComponentPropertiesImpl componentProperties) {
            super(componentProperties, false);
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<TaskT> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig.getVariantData().getTaskContainer().setPreBuildTask(taskProvider);
        }

        @Override
        public void configure(@NonNull TaskT task) {
            super.configure(task);
            task.dependsOn(MAIN_PREBUILD);

            if (creationConfig.getVariantScope().getCodeShrinker() != null) {
                task.dependsOn(EXTRACT_PROGUARD_FILES);
            }
        }
    }

    private static class PreBuildCreationAction
            extends AbstractPreBuildCreationAction<AndroidVariantTask> {
        public PreBuildCreationAction(@NonNull ComponentPropertiesImpl componentProperties) {
            super(componentProperties);
        }

        @NonNull
        @Override
        public Class<AndroidVariantTask> getType() {
            return AndroidVariantTask.class;
        }
    }

    private void createCompileAnchorTask(@NonNull ComponentPropertiesImpl componentProperties) {
        final MutableTaskContainer taskContainer = componentProperties.getTaskContainer();
        taskContainer.setCompileTask(
                taskFactory.register(
                        componentProperties.computeTaskName("compile", "Sources"),
                        task -> task.setGroup(BUILD_GROUP)));

        // FIXME is that really needed?
        TaskFactoryUtils.dependsOn(taskContainer.getAssembleTask(), taskContainer.getCompileTask());
    }

    @NonNull
    protected Logger getLogger() {
        return logger;
    }

    private void addBindingDependenciesIfNecessary(@NonNull DataBindingOptions dataBindingOptions) {
        boolean viewBindingEnabled =
                allPropertiesList.stream()
                        .anyMatch(
                                componentProperties ->
                                        componentProperties.getBuildFeatures().getViewBinding());
        boolean dataBindingEnabled =
                allPropertiesList.stream()
                        .anyMatch(
                                componentProperties ->
                                        componentProperties.getBuildFeatures().getDataBinding());

        ProjectOptions projectOptions = globalScope.getProjectOptions();
        boolean useAndroidX = projectOptions.get(BooleanOption.USE_ANDROID_X);

        DataBindingBuilder dataBindingBuilder = globalScope.getDataBindingBuilder();

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
                                    configureKotlinKaptTasksForDataBinding(project, version));
        }
    }

    private void configureKotlinKaptTasksForDataBinding(
            @NonNull Project project,
            @NonNull String version) {
        DependencySet kaptDeps = project.getConfigurations().getByName("kapt").getAllDependencies();
        kaptDeps.forEach(
                (Dependency dependency) -> {
                    // if it is a data binding compiler dependency w/ a different version, report
                    // error
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
                                .getDslServices()
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
        Map<String, ComponentPropertiesImpl> kaptTaskLookup =
                allPropertiesList.stream()
                        .collect(
                                Collectors.toMap(
                                        component -> component.computeTaskName("kapt", "Kotlin"),
                                        component -> component));
        project.getTasks()
                .withType(
                        kaptTaskClass,
                        (Action<Task>)
                                kaptTask -> {
                                    // find matching scope.
                                    ComponentPropertiesImpl matchingComponent =
                                            kaptTaskLookup.get(kaptTask.getName());
                                    if (matchingComponent != null) {
                                        configureKaptTaskInScopeForDataBinding(
                                                matchingComponent, kaptTask);
                                    }
                                });
    }

    private void configureKaptTaskInScopeForDataBinding(
            @NonNull ComponentPropertiesImpl componentProperties, @NonNull Task kaptTask) {
        DirectoryProperty dataBindingArtifactDir =
                componentProperties.getGlobalScope().getProject().getObjects().directoryProperty();
        RegularFileProperty exportClassListFile =
                componentProperties.getGlobalScope().getProject().getObjects().fileProperty();
        TaskProvider<Task> kaptTaskProvider = taskFactory.named(kaptTask.getName());

        // Data binding artifacts are part of the annotation processing outputs
        JavaCompileKt.registerDataBindingOutputs(
                dataBindingArtifactDir,
                exportClassListFile,
                componentProperties.getVariantType().isExportDataBindingClassList(),
                false, // Set to false to replace the first registration done by JavaCompile earlier
                kaptTaskProvider,
                componentProperties.getArtifacts());

        // Register the DirectoryProperty / RegularFileProperty as outputs as they are not yet
        // annotated as outputs (same with the code in JavaCompileCreationAction.configure).
        // Ideally we need to unset the corresponding properties from JavaCompile's outputs, but
        // there's currently no way to do it.
        kaptTask.getOutputs()
                .dir(dataBindingArtifactDir)
                .withPropertyName("dataBindingArtifactDir");
        if (componentProperties.getVariantType().isExportDataBindingClassList()) {
            kaptTask.getOutputs()
                    .file(exportClassListFile)
                    .withPropertyName("dataBindingExportClassListFile");
        }
    }

    protected void configureTestData(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull AbstractTestDataImpl testData) {
        testData.setAnimationsDisabled(extension.getTestOptions().getAnimationsDisabled());
        testData.setExtraInstrumentationTestRunnerArgs(
                componentProperties
                        .getServices()
                        .getProjectOptions()
                        .getExtraInstrumentationTestRunnerArgs());
    }

    private void maybeCreateCheckDuplicateClassesTask(
            @NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties
                .getServices()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK)) {
            taskFactory.register(new CheckDuplicateClassesTask.CreationAction(componentProperties));
        }
    }

    private void maybeCreateDexDesugarLibTask(
            @NonNull ApkCreationConfig apkCreationConfig,
            @NonNull ComponentPropertiesImpl componentProperties,
            boolean enableDexingArtifactTransform) {
        boolean separateFileDependenciesDexingTask =
                componentProperties.getVariantScope().getJava8LangSupportType()
                                == Java8LangSupport.D8
                        && enableDexingArtifactTransform;
        if (apkCreationConfig.getShouldPackageDesugarLibDex()) {
            taskFactory.register(
                    new L8DexDesugarLibTask.CreationAction(
                            componentProperties,
                            enableDexingArtifactTransform,
                            separateFileDependenciesDexingTask));
        }
    }
}
