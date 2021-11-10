/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins;

import static com.android.build.gradle.internal.ManagedDeviceUtilsKt.getManagedDeviceAvdFolder;
import static com.android.build.gradle.internal.dependency.JdkImageTransformKt.CONFIG_NAME_ANDROID_JDK_IMAGE;
import static com.google.common.base.Preconditions.checkState;

import com.android.SdkConstants;
import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestFixturesImpl;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import com.android.build.api.variant.VariantBuilder;
import com.android.build.api.variant.impl.GradleProperty;
import com.android.build.api.variant.impl.VariantBuilderImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.AndroidBasePlugin;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApiObjectFactory;
import com.android.build.gradle.internal.AvdComponentsBuildService;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.ClasspathVerifier;
import com.android.build.gradle.internal.DependencyConfigurator;
import com.android.build.gradle.internal.DependencyResolutionChecks;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NonFinalPluginExpiry;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader;
import com.android.build.gradle.internal.SdkComponentsKt;
import com.android.build.gradle.internal.SdkLocator;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.attribution.BuildAttributionService;
import com.android.build.gradle.internal.crash.CrashReporting;
import com.android.build.gradle.internal.dependency.JacocoInstrumentationService;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.AbstractPublishing;
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.InternalApplicationExtension;
import com.android.build.gradle.internal.dsl.InternalLibraryExtension;
import com.android.build.gradle.internal.dsl.LibraryPublishingImpl;
import com.android.build.gradle.internal.dsl.Lockable;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.errors.DeprecationReporterImpl;
import com.android.build.gradle.internal.errors.IncompatibleProjectOptionsReporter;
import com.android.build.gradle.internal.errors.MessageReceiverImpl;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.ide.ModelBuilder;
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService;
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService;
import com.android.build.gradle.internal.ide.v2.GlobalLibraryBuildService;
import com.android.build.gradle.internal.ide.v2.NativeModelBuilder;
import com.android.build.gradle.internal.lint.AndroidLintInputs;
import com.android.build.gradle.internal.lint.LintFixBuildService;
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService;
import com.android.build.gradle.internal.profile.AnalyticsService;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.profile.NoOpAnalyticsConfiguratorService;
import com.android.build.gradle.internal.profile.NoOpAnalyticsService;
import com.android.build.gradle.internal.res.Aapt2FromMaven;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.DelayedActionsExecutor;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService;
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService;
import com.android.build.gradle.internal.services.AndroidLocationsBuildService;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.DslServicesImpl;
import com.android.build.gradle.internal.services.LintClassLoaderBuildService;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.StringCachingBuildService;
import com.android.build.gradle.internal.services.SymbolTableBuildService;
import com.android.build.gradle.internal.utils.AgpVersionChecker;
import com.android.build.gradle.internal.utils.GradlePluginUtils;
import com.android.build.gradle.internal.utils.KgpUtils;
import com.android.build.gradle.internal.utils.PublishingUtils;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.LegacyVariantInputManager;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.internal.variant.VariantModelImpl;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptionService;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.model.v2.ide.ProjectType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Base class for all Android plugins */
public abstract class BasePlugin<
                AndroidT extends CommonExtension<?, ?, ?, ?>,
                AndroidComponentsT extends
                        AndroidComponentsExtension<
                                        ? extends CommonExtension<?, ?, ?, ?>,
                                        ? extends VariantBuilder,
                                        ? extends Variant>,
                VariantBuilderT extends VariantBuilderImpl,
                VariantT extends VariantImpl>
        implements Plugin<Project> {

    // TODO: BaseExtension should be changed into AndroidT
    private BaseExtension extension;
    private AndroidComponentsT androidComponentsExtension;

    private VariantManager<AndroidT, AndroidComponentsT, VariantBuilderT, VariantT> variantManager;
    private LegacyVariantInputManager variantInputModel;

    protected Project project;

    protected ProjectServices projectServices;
    protected DslServicesImpl dslServices;
    protected GlobalScope globalScope;
    protected SyncIssueReporterImpl syncIssueReporter;

    private VariantFactory<VariantBuilderT, VariantT> variantFactory;

    @NonNull private final ToolingModelBuilderRegistry registry;
    @NonNull private final SoftwareComponentFactory componentFactory;

    private LoggerWrapper loggerWrapper;

    protected ExtraModelInfo extraModelInfo;

    private String creator;

    private boolean hasCreatedTasks = false;

    private AnalyticsConfiguratorService configuratorService;

    private ProjectOptionService optionService;

    @NonNull private final BuildEventsListenerRegistry listenerRegistry;

    private final VariantApiOperationsRegistrar<
            AndroidT,
            VariantBuilderT,
            VariantT> variantApiOperations = new VariantApiOperationsRegistrar<>();

    public BasePlugin(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull SoftwareComponentFactory componentFactory,
            @NonNull BuildEventsListenerRegistry listenerRegistry) {
        ClasspathVerifier.checkClasspathSanity();
        this.registry = registry;
        this.componentFactory = componentFactory;
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION;
        NonFinalPluginExpiry.verifyRetirementAge();
        this.listenerRegistry = listenerRegistry;
    }

    @NonNull
    protected abstract BaseExtension createExtension(
            @NonNull DslServices dslServices,
            @NonNull GlobalScope globalScope,
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo);

    @NonNull
    protected abstract AndroidComponentsT createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<AndroidT, VariantBuilderT, VariantT>
                            variantApiOperationsRegistrar);

    @NonNull
    protected abstract GradleBuildProject.PluginType getAnalyticsPluginType();

    @NonNull
    protected abstract VariantFactory<VariantBuilderT, VariantT> createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope);

    @NonNull
    protected abstract TaskManager<VariantBuilderT, VariantT> createTaskManager(
            @NonNull List<ComponentInfo<VariantBuilderT, VariantT>> variants,
            @NonNull List<TestComponentImpl> testComponents,
            @NonNull List<TestFixturesImpl> testFixturesComponents,
            boolean hasFlavors,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ProjectInfo projectInfo);

    protected abstract int getProjectType();

    /** The project type of the IDE model v2. */
    protected abstract ProjectType getProjectTypeV2();

    @VisibleForTesting
    public VariantManager<AndroidT, AndroidComponentsT, VariantBuilderT, VariantT>
            getVariantManager() {
        return variantManager;
    }

    @VisibleForTesting
    public VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
            getVariantInputModel() {
        return variantInputModel;
    }

    public BaseExtension getExtension() {
        return extension;
    }

    private ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.getLogger());
        }

        return loggerWrapper;
    }

    @Override
    public final void apply(@NonNull Project project) {
        CrashReporting.runAction(
                () -> {
                    basePluginApply(project);
                    pluginSpecificApply(project);
                    project.getPluginManager().apply(AndroidBasePlugin.class);
                });
    }

    private void basePluginApply(@NonNull Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;

        new AndroidLocationsBuildService.RegistrationAction(project).execute();

        optionService = new ProjectOptionService.RegistrationAction(project).execute().get();

        createProjectServices(project);
        checkMinJvmVersion();

        ProjectOptions projectOptions = projectServices.getProjectOptions();

        if (projectOptions.isAnalyticsEnabled()) {
            new AnalyticsService.RegistrationAction(project).execute();

            configuratorService
                    = new AnalyticsConfiguratorService.RegistrationAction(project).execute().get();
        } else {
            project.getGradle().getSharedServices().registerIfAbsent(
                    BuildServicesKt.getBuildServiceName(AnalyticsService.class),
                    NoOpAnalyticsService.class,
                    spec -> {}
            );
            configuratorService = project.getGradle().getSharedServices().registerIfAbsent(
                    BuildServicesKt.getBuildServiceName(AnalyticsConfiguratorService.class),
                    NoOpAnalyticsConfiguratorService.class,
                    spec -> {}
            ).get();
        }

        DependencyResolutionChecks.registerDependencyCheck(project, projectOptions);

        checkPathForErrors();

        AgpVersionChecker.enforceTheSamePluginVersions(project);

        String attributionFileLocation =
                projectOptions.get(StringOption.IDE_ATTRIBUTION_FILE_LOCATION);
        if (attributionFileLocation != null) {
            new BuildAttributionService.RegistrationAction(project).execute();
            BuildAttributionService.Companion.init(
                    project, attributionFileLocation, listenerRegistry);
        }

        configuratorService.createAnalyticsService(project, listenerRegistry);

        GradleBuildProject.Builder projectBuilder =
                configuratorService.getProjectBuilder(project.getPath());
        if (projectBuilder != null) {
            projectBuilder
                    .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                    .setAndroidPlugin(getAnalyticsPluginType())
                    .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                    .setOptions(AnalyticsUtil.toProto(projectOptions));
        }

        configuratorService.recordBlock(
                ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.getPath(),
                null,
                this::configureProject);

        configuratorService.recordBlock(
                ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                project.getPath(),
                null,
                this::configureExtension);

        configuratorService.recordBlock(
                ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                project.getPath(),
                null,
                this::createTasks);
    }

    protected abstract void pluginSpecificApply(@NonNull Project project);

    private void configureProject() {
        final Gradle gradle = project.getGradle();

        Provider<StringCachingBuildService> stringCachingService =
                new StringCachingBuildService.RegistrationAction(project).execute();
        Provider<MavenCoordinatesCacheBuildService> mavenCoordinatesCacheBuildService =
                new MavenCoordinatesCacheBuildService.RegistrationAction(
                                project, stringCachingService)
                        .execute();

        new LibraryDependencyCacheBuildService.RegistrationAction(
                project, mavenCoordinatesCacheBuildService
        ).execute();

        new GlobalLibraryBuildService.RegistrationAction(
                project, mavenCoordinatesCacheBuildService
        ).execute();

        extraModelInfo = new ExtraModelInfo(mavenCoordinatesCacheBuildService);

        ProjectOptions projectOptions = projectServices.getProjectOptions();
        IssueReporter issueReporter = projectServices.getIssueReporter();

        new Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute();
        new Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute();
        new SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
                        project,
                        SyncOptions.getModelQueryMode(projectOptions),
                        SyncOptions.getErrorFormatMode(projectOptions))
                .execute();
        Provider<SdkComponentsBuildService> sdkComponentsBuildService =
                new SdkComponentsBuildService.RegistrationAction(project, projectOptions).execute();

        Provider<AndroidLocationsBuildService> locationsProvider =
                BuildServicesKt.getBuildService(
                        project.getGradle().getSharedServices(),
                        AndroidLocationsBuildService.class);

        ProviderFactory providers = project.getProviders();
        Provider<VersionedSdkLoader> versionSdkLoader =
                sdkComponentsBuildService.map(
                        buildService ->
                                buildService.sdkLoader(
                                        providers.provider(() -> extension.getCompileSdkVersion()),
                                        providers.provider(
                                                () -> extension.getBuildToolsRevision())));
        Provider<AvdComponentsBuildService> avdComponentsBuildService =
                new AvdComponentsBuildService.RegistrationAction(
                                project,
                                getManagedDeviceAvdFolder(
                                        project.getObjects(),
                                        project.getProviders(),
                                        locationsProvider.get()),
                                sdkComponentsBuildService,
                                project.getProviders()
                                        .provider(() -> extension.getCompileSdkVersion()),
                                project.getProviders()
                                        .provider(() -> extension.getBuildToolsRevision()))
                        .execute();

        new SymbolTableBuildService.RegistrationAction(project).execute();
        new ClassesHierarchyBuildService.RegistrationAction(project).execute();
        new LintFixBuildService.RegistrationAction(project).execute();
        new LintClassLoaderBuildService.RegistrationAction(project).execute();
        new JacocoInstrumentationService.RegistrationAction(project).execute();

        projectOptions
                .getAllOptions()
                .forEach(projectServices.getDeprecationReporter()::reportOptionIssuesIfAny);
        IncompatibleProjectOptionsReporter.check(
                projectOptions, projectServices.getIssueReporter());

        // Enforce minimum versions of certain plugins
        GradlePluginUtils.enforceMinimumVersionsOfPlugins(project, issueReporter);

        // Apply the Java plugin
        project.getPlugins().apply(JavaBasePlugin.class);

        dslServices =
                new DslServicesImpl(
                        projectServices,
                        sdkComponentsBuildService);

        MessageReceiverImpl messageReceiver =
                new MessageReceiverImpl(
                        SyncOptions.getErrorFormatMode(projectOptions),
                        projectServices.getLogger());

        globalScope =
                new GlobalScope(
                        project,
                        creator,
                        dslServices,
                        sdkComponentsBuildService,
                        avdComponentsBuildService,
                        registry,
                        messageReceiver,
                        componentFactory);

        project.getTasks()
                .named("assemble")
                .configure(
                        task ->
                                task.setDescription(
                                        "Assembles all variants of all applications and secondary packages."));

        // As soon as project is evaluated we can clear the shared state for deprecation reporting.
        gradle.projectsEvaluated(action -> DeprecationReporterImpl.Companion.clean());

        AndroidLintInputs.createLintClasspathConfiguration(project, projectServices);

        createAndroidJdkImageConfiguration(project, globalScope);
    }

    /** Creates the androidJdkImage configuration */
    public static void createAndroidJdkImageConfiguration(
            @NonNull Project project, @NonNull GlobalScope globalScope) {
        Configuration config = project.getConfigurations().create(CONFIG_NAME_ANDROID_JDK_IMAGE);
        config.setVisible(false);
        config.setCanBeConsumed(false);
        config.setDescription("Configuration providing JDK image for compiling Java 9+ sources");

        project.getDependencies()
                .add(
                        CONFIG_NAME_ANDROID_JDK_IMAGE,
                        project.files(
                                globalScope
                                        .getVersionedSdkLoader()
                                        .flatMap(
                                                VersionedSdkLoader
                                                        ::getCoreForSystemModulesProvider)));
    }

    private void configureExtension() {
        DslServices dslServices = globalScope.getDslServices();

        final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs =
                project.container(BaseVariantOutput.class);

        project.getExtensions().add("buildOutputs", buildOutputs);

        variantFactory = createVariantFactory(projectServices, globalScope);

        variantInputModel =
                new LegacyVariantInputManager(
                        dslServices,
                        variantFactory.getVariantType(),
                        new SourceSetManager(
                                project,
                                isPackagePublished(),
                                dslServices,
                                new DelayedActionsExecutor()));

        extension =
                createExtension(
                        dslServices, globalScope, variantInputModel, buildOutputs, extraModelInfo);

        globalScope.setExtension(extension);

        androidComponentsExtension = createComponentExtension(dslServices, variantApiOperations);

        variantManager =
                new VariantManager(
                        globalScope,
                        project,
                        projectServices.getProjectOptions(),
                        (CommonExtension<?, ?, ?, ?>) extension,
                        androidComponentsExtension,
                        variantApiOperations,
                        variantFactory,
                        variantInputModel,
                        projectServices);

        registerModels(
                registry,
                globalScope,
                variantInputModel,
                extension,
                extraModelInfo);

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(variantInputModel);

        createAndroidTestUtilConfiguration();
    }


    protected void registerModels(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            variantInputModel,
            @NonNull BaseExtension extension,
            @NonNull ExtraModelInfo extraModelInfo) {
        // Register a builder for the custom tooling model
        VariantModel variantModel = createVariantModel();

        registerModelBuilder(registry, globalScope, variantModel, extension, extraModelInfo);

        registry.register(
                new com.android.build.gradle.internal.ide.v2.ModelBuilder(
                        globalScope,
                        projectServices.getProjectOptions(),
                        variantModel,
                        (CommonExtension) extension,
                        projectServices.getIssueReporter(),
                        getProjectTypeV2()));

        // Register a builder for the native tooling model

        NativeModelBuilder nativeModelBuilderV2 =
                new NativeModelBuilder(
                        projectServices.getIssueReporter(),
                        projectServices.getProjectOptions(),
                        globalScope,
                        variantModel,
                        projectServices.getProjectInfo());
        registry.register(nativeModelBuilderV2);
    }

    @NonNull
    private VariantModel createVariantModel() {
        return new VariantModelImpl(
                variantInputModel,
                extension::getTestBuildType,
                () ->
                        variantManager.getMainComponents().stream()
                                .map(ComponentInfo::getVariant)
                                .collect(Collectors.toList()),
                () ->
                        variantManager.getTestComponents(),
                dslServices.getIssueReporter());
    }

    /** Registers a builder for the custom tooling model. */
    protected void registerModelBuilder(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantModel variantModel,
            @NonNull BaseExtension extension,
            @NonNull ExtraModelInfo extraModelInfo) {
        registry.register(
                new ModelBuilder<>(
                        globalScope,
                        variantModel,
                        extension,
                        extraModelInfo,
                        projectServices.getProjectOptions(),
                        projectServices.getIssueReporter(),
                        getProjectType(),
                        projectServices.getProjectInfo()));
    }

    private void createTasks() {
        configuratorService.recordBlock(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () ->
                        TaskManager.createTasksBeforeEvaluate(
                                projectServices.getProjectOptions(),
                                globalScope,
                                variantFactory.getVariantType(),
                                extension.getSourceSets(),
                                projectServices.getProjectInfo()));

        project.afterEvaluate(
                CrashReporting.afterEvaluate(
                        p -> {
                            variantInputModel.getSourceSetManager().runBuildableArtifactsActions();

                            configuratorService.recordBlock(
                                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                    project.getPath(),
                                    null,
                                    this::createAndroidTasks);
                        }));
    }

    @VisibleForTesting
    final void createAndroidTasks() {

        if (extension.getCompileSdkVersion() == null) {
            if (SyncOptions.getModelQueryMode(projectServices.getProjectOptions())
                    .equals(SyncOptions.EvaluationMode.IDE)) {
                String newCompileSdkVersion = findHighestSdkInstalled();
                if (newCompileSdkVersion == null) {
                    newCompileSdkVersion = "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
                }
                extension.setCompileSdkVersion(newCompileSdkVersion);
            }

            dslServices
                    .getIssueReporter()
                    .reportError(
                            Type.COMPILE_SDK_VERSION_NOT_SET,
                            "compileSdkVersion is not specified. Please add it to build.gradle");
        }

        // Make sure unit tests set the required fields.
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");

        // get current plugins and look for the default Java plugin.
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
        }

        if (project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
            String warningMsg =
                    "One of the plugins you are using supports Java 8 "
                            + "language features. To try the support built into"
                            + " the Android plugin, remove the following from "
                            + "your build.gradle:\n"
                            + "    apply plugin: 'me.tatarka.retrolambda'\n"
                            + "To learn more, go to https://d.android.com/r/"
                            + "tools/java-8-support-message.html\n";
            dslServices.getIssueReporter().reportWarning(IssueReporter.Type.GENERIC, warningMsg);
        }

        project.getRepositories()
                .forEach(
                        it -> {
                            if (it instanceof FlatDirectoryArtifactRepository) {
                                String warningMsg =
                                        String.format(
                                                "Using %s should be avoided because it doesn't support any meta-data formats.",
                                                it.getName());
                                dslServices
                                        .getIssueReporter()
                                        .reportWarning(IssueReporter.Type.GENERIC, warningMsg);
                            }
                        });

        checkMavenPublishing();

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if ((!project.getState().getExecuted() || project.getState().getFailure() != null)
                && SdkLocator.getSdkTestDirectory() == null) {
            return;
        }

        if (hasCreatedTasks) {
            return;
        }
        hasCreatedTasks = true;

        variantApiOperations.executeDslFinalizationBlocks(
                (AndroidT) extension
        );

        variantInputModel.lock();
        extension.disableWrite();

        GradleBuildProject.Builder projectBuilder =
                configuratorService.getProjectBuilder(project.getPath());

        if (projectBuilder != null) {
            projectBuilder
                    .setCompileSdk(extension.getCompileSdkVersion())
                    .setBuildToolsVersion(extension.getBuildToolsRevision().toString())
                    .setSplits(AnalyticsUtil.toProto(extension.getSplits()));

            String kotlinPluginVersion = getKotlinPluginVersion();
            if (kotlinPluginVersion != null) {
                projectBuilder.setKotlinPluginVersion(kotlinPluginVersion);
            }
        }

        AnalyticsUtil.recordFirebasePerformancePluginVersion(project);

        // create the build feature object that will be re-used everywhere
        BuildFeatureValues buildFeatureValues =
                variantFactory.createBuildFeatureValues(
                        extension.getBuildFeatures(), projectServices.getProjectOptions());

        variantManager.createVariants(buildFeatureValues);

        List<ComponentInfo<VariantBuilderT, VariantT>> variants =
                variantManager.getMainComponents();

        TaskManager<VariantBuilderT, VariantT> taskManager =
                createTaskManager(
                        variants,
                        variantManager.getTestComponents(),
                        variantManager.getTestFixturesComponents(),
                        !variantInputModel.getProductFlavors().isEmpty(),
                        projectServices.getProjectOptions(),
                        globalScope,
                        extension,
                        projectServices.getProjectInfo());

        taskManager.createTasks(variantFactory.getVariantType(), createVariantModel());

        new DependencyConfigurator(
                        project,
                        project.getName(),
                        projectServices.getProjectOptions(),
                        globalScope,
                        variantInputModel,
                        projectServices)
                .configureDependencySubstitutions()
                .configureDependencyChecks()
                .configureGeneralTransforms()
                .configureVariantTransforms(variants, variantManager.getTestComponents())
                .configureAttributeMatchingStrategies();

        // Run the old Variant API, after the variants and tasks have been created.
        ApiObjectFactory apiObjectFactory =
                new ApiObjectFactory(extension, variantFactory, globalScope);

        for (ComponentInfo<VariantBuilderT, VariantT> variant : variants) {
            apiObjectFactory.create(variant.getVariant());
        }

        // lock the Properties of the variant API after the old API because
        // of the versionCode/versionName properties that are shared between the old and new APIs.
        variantManager.lockVariantProperties();

        // Make sure no SourceSets were added through the DSL without being properly configured
        variantInputModel.getSourceSetManager().checkForUnconfiguredSourceSets();
        KgpUtils.syncAgpAndKgpSources(project, extension.getSourceSets());

        // configure compose related tasks.
        taskManager.createPostApiTasks();

        // now publish all variant artifacts for non test variants since
        // tests don't publish anything.
        for (ComponentInfo<VariantBuilderT, VariantT> component : variants) {
            component.getVariant().publishBuildArtifacts();
        }

        // now publish all testFixtures components artifacts.
        for (TestFixturesImpl testFixturesComponent :
                variantManager.getTestFixturesComponents()) {
            testFixturesComponent.publishBuildArtifacts();
        }

        checkSplitConfiguration();
        variantManager.setHasCreatedTasks(true);
        for (ComponentInfo<VariantBuilderT, VariantT> variant : variants) {
            variant.getVariant().getArtifacts().ensureAllOperationsAreSatisfied();
        }
        // notify our properties that configuration is over for us.
        GradleProperty.Companion.endOfEvaluation();
    }

    private String findHighestSdkInstalled() {
        String highestSdk = null;
        File folder =
                new File(
                        SdkComponentsKt.getSdkDir(project.getRootDir(), syncIssueReporter),
                        "platforms");
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            Arrays.sort(listOfFiles, Comparator.comparing(File::getName).reversed());
            for (File file : listOfFiles) {
                if (AndroidTargetHash.getPlatformVersion(file.getName()) != null) {
                    highestSdk = file.getName();
                    break;
                }
            }
        }

        return highestSdk;
    }

    private void checkSplitConfiguration() {
        String configApkUrl = "https://d.android.com/topic/instant-apps/guides/config-splits.html";

        boolean generatePureSplits = extension.getGeneratePureSplits();
        Splits splits = extension.getSplits();
        boolean splitsEnabled =
                splits.getDensity().isEnable()
                        || splits.getAbi().isEnable()
                        || splits.getLanguage().isEnable();

        // The Play Store doesn't allow Pure splits
        if (generatePureSplits) {
            dslServices
                    .getIssueReporter()
                    .reportWarning(
                            Type.GENERIC,
                            "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false. For more information, go to "
                                    + configApkUrl);
        }

        if (!generatePureSplits && splits.getLanguage().isEnable()) {
            dslServices
                    .getIssueReporter()
                    .reportWarning(
                            Type.GENERIC,
                            "Per-language APKs are supported only when building Android Instant Apps. For more information, go to "
                                    + configApkUrl);
        }
    }

    private void checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            return;
        }

        // See if the user disabled the check:
        if (projectServices.getProjectOptions().get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)) {
            return;
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ascii().matchesAllOf(project.getRootDir().getAbsolutePath())) {
            return;
        }

        String message =
                "Your project path contains non-ASCII characters. This will most likely "
                        + "cause the build to fail on Windows. Please move your project to a different "
                        + "directory. See http://b.android.com/95744 for details. "
                        + "This warning can be disabled by adding the line '"
                        + BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY.getPropertyName()
                        + "=true' to gradle.properties file in the project directory.";

        throw new StopExecutionException(message);
    }

    /**
     * returns the kotlin plugin version, or null if plugin is not applied to this project, or
     * "unknown" if plugin is applied but version can't be determined.
     */
    @Nullable
    private String getKotlinPluginVersion() {
        Plugin plugin = project.getPlugins().findPlugin("kotlin-android");
        if (plugin == null) {
            return null;
        }
        try {
            // No null checks below because we're catching all exceptions.
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method method = plugin.getClass().getMethod("getKotlinPluginVersion");
            method.setAccessible(true);
            return method.invoke(plugin).toString();
        } catch (Throwable e) {
            // Defensively catch all exceptions because we don't want it to crash
            // if kotlin plugin code changes unexpectedly.
            return "unknown";
        }
    }

    /**
     * If overridden in a subclass to return "true," the package Configuration will be named
     * "publish" instead of "apk"
     */
    protected boolean isPackagePublished() {
        return false;
    }

    // Create the "special" configuration for test buddy APKs. It will be resolved by the test
    // running task, so that we can install all the found APKs before running tests.
    private void createAndroidTestUtilConfiguration() {
        project.getLogger()
                .debug(
                        "Creating configuration "
                                + SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION);
        Configuration configuration =
                project.getConfigurations()
                        .maybeCreate(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION);
        configuration.setVisible(false);
        configuration.setDescription("Additional APKs used during instrumentation testing.");
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(true);
    }

    private void createProjectServices(@NonNull Project project) {
        ObjectFactory objectFactory = project.getObjects();
        final Logger logger = project.getLogger();
        final String projectPath = project.getPath();

        ProjectOptions projectOptions = optionService.getProjectOptions();

        syncIssueReporter =
                new SyncIssueReporterImpl(
                        SyncOptions.getModelQueryMode(projectOptions),
                        SyncOptions.getErrorFormatMode(projectOptions),
                        logger);

        DeprecationReporterImpl deprecationReporter =
                new DeprecationReporterImpl(syncIssueReporter, projectOptions, projectPath);

        Aapt2FromMaven aapt2FromMaven = Aapt2FromMaven.create(project, projectOptions);

        projectServices =
                new ProjectServices(
                        syncIssueReporter,
                        deprecationReporter,
                        objectFactory,
                        project.getLogger(),
                        project.getProviders(),
                        project.getLayout(),
                        projectOptions,
                        project.getGradle().getSharedServices(),
                        aapt2FromMaven,
                        project.getGradle().getStartParameter().getMaxWorkerCount(),
                        new ProjectInfo(project),
                        project::file);
    }

    private void checkMinJvmVersion() {
        JavaVersion current = JavaVersion.current();
        JavaVersion minRequired = JavaVersion.VERSION_11;
        if (!current.isCompatibleWith(minRequired)) {
            syncIssueReporter.reportError(
                    Type.AGP_USED_JAVA_VERSION_TOO_LOW,
                    "Android Gradle plugin requires Java "
                            + minRequired.toString()
                            + " to run. You are currently using Java "
                            + current.toString()
                            + ".\n"
                            + "You can try some of the following options:\n"
                            + "  - changing the IDE settings.\n"
                            + "  - changing the JAVA_HOME environment variable.\n"
                            + "  - changing `org.gradle.java.home` in `gradle.properties`.");
        }
    }

    private void checkMavenPublishing() {
        if (project.getPlugins().hasPlugin("maven-publish")) {
            if (extension instanceof InternalApplicationExtension) {
                checkSoftwareComponents(
                        (ApplicationPublishingImpl)
                                ((InternalApplicationExtension) extension).getPublishing());
            }
            if (extension instanceof InternalLibraryExtension) {
                checkSoftwareComponents(
                        (LibraryPublishingImpl)
                                ((InternalLibraryExtension) extension).getPublishing());
            }
        }
    }

    private void checkSoftwareComponents(AbstractPublishing publishing) {
        boolean optIn =
                PublishingUtils.publishingFeatureOptIn(publishing, dslServices.getProjectOptions());
        if (!optIn) {
            String warning =
                    "Software Components will not be created automatically for "
                            + "Maven publishing from Android Gradle Plugin 8.0. To opt-in to the "
                            + "future behavior, set the Gradle property "
                            + "android.disableAutomaticComponentCreation=true in the "
                            + "`gradle.properties` file or use the new publishing DSL.";
            dslServices.getIssueReporter().reportWarning(Type.GENERIC, warning);
        }
    }
}
