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

import static com.google.common.base.Preconditions.checkState;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.variant.impl.GradleProperty;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.AndroidBasePlugin;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApiObjectFactory;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.ClasspathVerifier;
import com.android.build.gradle.internal.DependencyConfigurator;
import com.android.build.gradle.internal.DependencyResolutionChecks;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NonFinalPluginExpiry;
import com.android.build.gradle.internal.PluginInitializer;
import com.android.build.gradle.internal.SdkComponents;
import com.android.build.gradle.internal.SdkLocator;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.attribution.AttributionListenerInitializer;
import com.android.build.gradle.internal.crash.CrashReporting;
import com.android.build.gradle.internal.dependency.ConstraintHandler;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.BuildTypeFactory;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.DslVariableFactory;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavorFactory;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.SigningConfigFactory;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.errors.DeprecationReporterImpl;
import com.android.build.gradle.internal.errors.MessageReceiverImpl;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.ide.ModelBuilder;
import com.android.build.gradle.internal.ide.NativeModelBuilder;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.profile.ProfileAgent;
import com.android.build.gradle.internal.profile.ProfilerInitializer;
import com.android.build.gradle.internal.profile.RecordingBuildListener;
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl;
import com.android.build.gradle.internal.scope.DelayedActionsExecutor;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.Aapt2Daemon;
import com.android.build.gradle.internal.services.Aapt2Workers;
import com.android.build.gradle.internal.utils.GradlePluginUtils;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.build.gradle.internal.variant.VariantInputModelImpl;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.internal.variant.VariantModelImpl;
import com.android.build.gradle.internal.variant2.DslScopeImpl;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.options.SyncOptions;
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode;
import com.android.build.gradle.tasks.LintBaseTask;
import com.android.build.gradle.tasks.factory.AbstractCompilesUtil;
import com.android.builder.core.BuilderConstants;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.Main;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.gradle.api.ToolingRegistryProvider;
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
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Base class for all Android plugins */
public abstract class BasePlugin implements Plugin<Project>, ToolingRegistryProvider {

    private BaseExtension extension;

    private VariantManager variantManager;
    private VariantInputModelImpl variantInputModel;

    protected TaskManager taskManager;

    protected Project project;

    protected ProjectOptions projectOptions;

    GlobalScope globalScope;
    protected SyncIssueReporterImpl syncIssueHandler;

    private DataBindingBuilder dataBindingBuilder;

    private VariantFactory variantFactory;

    private SourceSetManager sourceSetManager;

    @NonNull private final ToolingModelBuilderRegistry registry;
    @NonNull private final SoftwareComponentFactory componentFactory;

    private LoggerWrapper loggerWrapper;

    protected ExtraModelInfo extraModelInfo;

    private String creator;

    private Recorder threadRecorder;

    private boolean hasCreatedTasks = false;

    public BasePlugin(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull SoftwareComponentFactory componentFactory) {
        ClasspathVerifier.checkClasspathSanity();
        this.registry = registry;
        this.componentFactory = componentFactory;
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION;
        NonFinalPluginExpiry.verifyRetirementAge();
    }

    @NonNull
    protected abstract BaseExtension createExtension(
            @NonNull DslScope dslScope,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull DefaultConfig defaultConfig,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo);

    @NonNull
    protected abstract GradleBuildProject.PluginType getAnalyticsPluginType();

    @NonNull
    protected abstract VariantFactory createVariantFactory(@NonNull GlobalScope globalScope);

    @NonNull
    protected abstract TaskManager createTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder);

    protected abstract int getProjectType();

    @VisibleForTesting
    public VariantManager getVariantManager() {
        return variantManager;
    }

    @VisibleForTesting
    public VariantInputModel getVariantInputModel() {
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
                });
    }

    private void basePluginApply(@NonNull Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;
        this.projectOptions = new ProjectOptions(project);
        DependencyResolutionChecks.registerDependencyCheck(project, projectOptions);

        project.getPluginManager().apply(AndroidBasePlugin.class);

        checkPathForErrors();
        checkModulesForErrors();

        AttributionListenerInitializer.INSTANCE.init(
                project, projectOptions.get(StringOption.IDE_ATTRIBUTION_FILE_LOCATION));

        PluginInitializer.initialize(project);
        RecordingBuildListener buildListener = ProfilerInitializer.init(project, projectOptions);
        ProfileAgent.INSTANCE.register(project.getName(), buildListener);
        threadRecorder = ThreadRecorder.get();

        Aapt2Workers.registerAapt2WorkersBuildService(project, projectOptions);
        Aapt2Daemon.registerAapt2DaemonBuildService(project);

        ProcessProfileWriter.getProject(project.getPath())
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                .setOptions(AnalyticsUtil.toProto(projectOptions));

        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.getPath(),
                null,
                this::configureProject);

        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                project.getPath(),
                null,
                this::configureExtension);

        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                project.getPath(),
                null,
                this::createTasks);
    }

    protected abstract void pluginSpecificApply(@NonNull Project project);

    private void configureProject() {
        final Gradle gradle = project.getGradle();
        ObjectFactory objectFactory = project.getObjects();
        final Logger logger = project.getLogger();
        final String projectPath = project.getPath();

        syncIssueHandler =
                new SyncIssueReporterImpl(SyncOptions.getModelQueryMode(projectOptions), logger);

        DeprecationReporterImpl deprecationReporter =
                new DeprecationReporterImpl(syncIssueHandler, projectOptions, projectPath);

        extraModelInfo = new ExtraModelInfo();

        SdkComponents sdkComponents =
                SdkComponents.Companion.createSdkComponents(
                        project,
                        projectOptions,
                        // We pass a supplier here because extension will only be set later.
                        this::getExtension,
                        getLogger(),
                        syncIssueHandler);

        dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(
                SyncOptions.getErrorFormatMode(projectOptions) == ErrorFormatMode.MACHINE_PARSABLE);

        projectOptions.getAllOptions().forEach(deprecationReporter::reportOptionIssuesIfAny);

        // Enforce minimum versions of certain plugins
        GradlePluginUtils.enforceMinimumVersionsOfPlugins(project, syncIssueHandler);

        // Apply the Java plugin
        project.getPlugins().apply(JavaBasePlugin.class);

        DslScopeImpl dslScope =
                new DslScopeImpl(
                        syncIssueHandler,
                        deprecationReporter,
                        objectFactory,
                        project.getLogger(),
                        new BuildFeatureValuesImpl(projectOptions),
                        project.getProviders(),
                        new DslVariableFactory(syncIssueHandler),
                        project.getLayout(),
                        project::file);

        @Nullable
        FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

        MessageReceiverImpl messageReceiver =
                new MessageReceiverImpl(SyncOptions.getErrorFormatMode(projectOptions), logger);

        globalScope =
                new GlobalScope(
                        project,
                        creator,
                        projectOptions,
                        dslScope,
                        sdkComponents,
                        registry,
                        buildCache,
                        messageReceiver,
                        componentFactory);

        project.getTasks()
                .named("assemble")
                .configure(
                        task ->
                                task.setDescription(
                                        "Assembles all variants of all applications and secondary packages."));

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        gradle.addBuildListener(
                new BuildAdapter() {
                    @Override
                    public void buildFinished(@NonNull BuildResult buildResult) {
                        // Do not run buildFinished for included project in composite build.
                        if (buildResult.getGradle().getParent() != null) {
                            return;
                        }
                        ModelBuilder.clearCaches();
                        sdkComponents.unload();
                        SdkLocator.resetCache();
                        ConstraintHandler.clearCache();
                        threadRecorder.record(
                                ExecutionType.BASE_PLUGIN_BUILD_FINISHED,
                                projectPath,
                                null,
                                Main::clearInternTables);
                        DeprecationReporterImpl.Companion.clean();
                    }
                });

        createLintClasspathConfiguration(project);
    }

    /** Creates a lint class path Configuration for the given project */
    public static void createLintClasspathConfiguration(@NonNull Project project) {
        Configuration config = project.getConfigurations().create(LintBaseTask.LINT_CLASS_PATH);
        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The lint embedded classpath");

        project.getDependencies().add(config.getName(), "com.android.tools.lint:lint-gradle:" +
                Version.ANDROID_TOOLS_BASE_VERSION);
    }

    private void configureExtension() {
        DslScope dslScope = globalScope.getDslScope();

        final NamedDomainObjectContainer<BuildType> buildTypeContainer =
                project.container(BuildType.class, new BuildTypeFactory(dslScope));
        final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer =
                project.container(ProductFlavor.class, new ProductFlavorFactory(dslScope));
        final NamedDomainObjectContainer<SigningConfig> signingConfigContainer =
                project.container(
                        SigningConfig.class,
                        new SigningConfigFactory(
                                dslScope.getObjectFactory(),
                                GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));

        final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs =
                project.container(BaseVariantOutput.class);

        project.getExtensions().add("buildOutputs", buildOutputs);

        sourceSetManager =
                new SourceSetManager(
                        project, isPackagePublished(), dslScope, new DelayedActionsExecutor());

        DefaultConfig defaultConfig =
                dslScope.getObjectFactory()
                        .newInstance(DefaultConfig.class, BuilderConstants.MAIN, dslScope);

        extension =
                createExtension(
                        dslScope,
                        projectOptions,
                        globalScope,
                        buildTypeContainer,
                        defaultConfig,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo);

        // link the extension buildFeature to the BuildFeatureValues in DslScope
        ((BuildFeatureValuesImpl) dslScope.getBuildFeatures())
                .setDslBuildFeatures(((CommonExtension) extension).getBuildFeatures());

        globalScope.setExtension(extension);

        variantFactory = createVariantFactory(globalScope);

        taskManager =
                createTaskManager(
                        globalScope,
                        project,
                        projectOptions,
                        dataBindingBuilder,
                        extension,
                        variantFactory,
                        registry,
                        threadRecorder);

        variantInputModel =
                new VariantInputModelImpl(globalScope, extension, variantFactory, sourceSetManager);
        variantManager =
                new VariantManager(
                        globalScope,
                        project,
                        projectOptions,
                        extension,
                        variantFactory,
                        variantInputModel,
                        taskManager,
                        sourceSetManager,
                        threadRecorder);

        registerModels(
                registry,
                globalScope,
                variantInputModel,
                variantManager,
                extension,
                extraModelInfo);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded(variantInputModel::addSigningConfig);

        buildTypeContainer.whenObjectAdded(
                buildType -> {
                    if (!this.getClass().isAssignableFrom(DynamicFeaturePlugin.class)) {
                        SigningConfig signingConfig =
                                signingConfigContainer.findByName(BuilderConstants.DEBUG);
                        buildType.init(signingConfig);
                    } else {
                        // initialize it without the signingConfig for dynamic-features.
                        buildType.init();
                    }
                    variantInputModel.addBuildType(buildType);
                });

        productFlavorContainer.whenObjectAdded(variantInputModel::addProductFlavor);

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved(
                new UnsupportedAction("Removing signingConfigs is not supported."));
        buildTypeContainer.whenObjectRemoved(
                new UnsupportedAction("Removing build types is not supported."));
        productFlavorContainer.whenObjectRemoved(
                new UnsupportedAction("Removing product flavors is not supported."));

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(
                buildTypeContainer, productFlavorContainer, signingConfigContainer);

        createAndroidTestUtilConfiguration();
    }

    protected void registerModels(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantInputModel variantInputModel,
            @NonNull VariantManager variantManager,
            @NonNull BaseExtension extension,
            @NonNull ExtraModelInfo extraModelInfo) {
        // Register a builder for the custom tooling model
        VariantModel variantModel =
                new VariantModelImpl(
                        variantInputModel,
                        extension::getTestBuildType,
                        variantManager,
                        globalScope.getDslScope().getIssueReporter());

        registerModelBuilder(registry, globalScope, variantModel, extension, extraModelInfo);

        // Register a builder for the native tooling model
        NativeModelBuilder nativeModelBuilder = new NativeModelBuilder(globalScope, variantManager);
        registry.register(nativeModelBuilder);
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
                        taskManager,
                        extension,
                        extraModelInfo,
                        syncIssueHandler,
                        getProjectType()));
    }

    private static class UnsupportedAction implements Action<Object> {

        private final String message;

        UnsupportedAction(String message) {
            this.message = message;
        }

        @Override
        public void execute(@NonNull Object o) {
            throw new UnsupportedOperationException(message);
        }
    }

    private void createTasks() {
        threadRecorder.record(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () -> taskManager.createTasksBeforeEvaluate());

        project.afterEvaluate(
                CrashReporting.afterEvaluate(
                        p -> {
                            sourceSetManager.runBuildableArtifactsActions();

                            threadRecorder.record(
                                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                    project.getPath(),
                                    null,
                                    this::createAndroidTasks);
                        }));
    }

    @VisibleForTesting
    final void createAndroidTasks() {

        if (extension.getCompileSdkVersion() == null) {
            if (SyncOptions.getModelQueryMode(projectOptions)
                    .equals(SyncOptions.EvaluationMode.IDE)) {
                String newCompileSdkVersion = findHighestSdkInstalled();
                if (newCompileSdkVersion == null) {
                    newCompileSdkVersion = "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
                }
                extension.setCompileSdkVersion(newCompileSdkVersion);
            }

            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportError(
                            Type.COMPILE_SDK_VERSION_NOT_SET,
                            "compileSdkVersion is not specified. Please add it to build.gradle");
        }

        // Make sure unit tests set the required fields.
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");
        extension
                .getCompileOptions()
                .setDefaultJavaVersion(
                        AbstractCompilesUtil.getDefaultJavaVersion(
                                extension.getCompileSdkVersion()));

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
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportWarning(IssueReporter.Type.GENERIC, warningMsg);
        }

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

        extension.disableWrite();
        globalScope.getDslScope().getVariableFactory().disableWrite();

        taskManager.configureCustomLintChecks();

        ProcessProfileWriter.getProject(project.getPath())
                .setCompileSdk(extension.getCompileSdkVersion())
                .setBuildToolsVersion(extension.getBuildToolsRevision().toString())
                .setSplits(AnalyticsUtil.toProto(extension.getSplits()));

        String kotlinPluginVersion = getKotlinPluginVersion();
        if (kotlinPluginVersion != null) {
            ProcessProfileWriter.getProject(project.getPath())
                    .setKotlinPluginVersion(kotlinPluginVersion);
        }
        AnalyticsUtil.recordFirebasePerformancePluginVersion(project);

        List<VariantScope> variantScopes = variantManager.createVariantsAndTasks();

        new DependencyConfigurator(project, project.getName(), globalScope, variantInputModel)
                .configureDependencies();

        // Run the old Variant API, after the variants and tasks have been created.
        ApiObjectFactory apiObjectFactory =
                new ApiObjectFactory(extension, variantFactory, project.getObjects());
        for (VariantScope variantScope : variantScopes) {
            BaseVariantData variantData = variantScope.getVariantData();
            apiObjectFactory.create(variantData);
        }

        // Make sure no SourceSets were added through the DSL without being properly configured
        sourceSetManager.checkForUnconfiguredSourceSets();

        // must run this after scopes are created so that we can configure kotlin
        // kapt tasks
        taskManager.addBindingDependenciesIfNecessary(
                globalScope.getBuildFeatures().getViewBinding(),
                globalScope.getBuildFeatures().getDataBinding(),
                extension.getDataBinding(),
                variantManager.getVariantScopes());


        // configure compose related tasks.
        taskManager.configureKotlinPluginTasksForComposeIfNecessary(
                globalScope, variantManager.getVariantScopes());

        // create the global lint task that depends on all the variants
        taskManager.configureGlobalLintTask(variantManager.getVariantScopes());

        int flavorDimensionCount = 0;
        if (extension.getFlavorDimensionList() != null) {
            flavorDimensionCount = extension.getFlavorDimensionList().size();
        }

        taskManager.createAnchorAssembleTasks(
                variantScopes, extension.getProductFlavors().size(), flavorDimensionCount);

        // now publish all variant artifacts.
        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            variantManager.publishBuildArtifacts(variantScope);
        }

        checkSplitConfiguration();
        variantManager.setHasCreatedTasks(true);
        // notify our properties that configuration is over for us.
        GradleProperty.Companion.endOfEvaluation();
    }

    private String findHighestSdkInstalled() {
        String highestSdk = null;
        File folder = new File(globalScope.getSdkComponents().getSdkDirectory(), "platforms");
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
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportWarning(
                            Type.GENERIC,
                            "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false. For more information, go to "
                                    + configApkUrl);
        }

        if (!generatePureSplits && splits.getLanguage().isEnable()) {
            globalScope
                    .getDslScope()
                    .getIssueReporter()
                    .reportWarning(
                            Type.GENERIC,
                            "Per-language APKs are supported only when building Android Instant Apps. For more information, go to "
                                    + configApkUrl);
        }
    }

    /**
     * Check the sub-projects structure :
     * So far, checks that 2 modules do not have the same identification (group+name).
     */
    private void checkModulesForErrors() {
        Project rootProject = project.getRootProject();
        Map<String, Project> subProjectsById = new HashMap<>();
        for (Project subProject : rootProject.getAllprojects()) {
            String id = subProject.getGroup().toString() + ":" + subProject.getName();
            if (subProjectsById.containsKey(id)) {
                String message = String.format(
                        "Your project contains 2 or more modules with the same " +
                                "identification %1$s\n" +
                                "at \"%2$s\" and \"%3$s\".\n" +
                                "You must use different identification (either name or group) for " +
                                "each modules.",
                        id,
                        subProjectsById.get(id).getPath(),
                        subProject.getPath() );
                throw new StopExecutionException(message);
            } else {
                subProjectsById.put(id, subProject);
            }
        }
    }

    private void checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            return;
        }

        // See if the user disabled the check:
        if (projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)) {
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

    @NonNull
    @Override
    public ToolingModelBuilderRegistry getModelBuilderRegistry() {
        return registry;
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
        getLogger()
                .info(
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
}
