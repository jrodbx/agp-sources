/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.SdkConstants.DIST_URI;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;
import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

import com.android.SdkConstants;
import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.DefaultConfigData;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.errors.SyncIssueReporter;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.ide.dependencies.BuildMappingUtils;
import com.android.build.gradle.internal.ide.dependencies.DependencyGraphBuilder;
import com.android.build.gradle.internal.ide.dependencies.DependencyGraphBuilderKt;
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService;
import com.android.build.gradle.internal.ide.dependencies.LibraryUtils;
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs;
import com.android.build.gradle.internal.ide.level2.GlobalLibraryMapImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.ExtractApksTask;
import com.android.build.gradle.internal.utils.DesugarLibUtils;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.compiling.BuildConfigType;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.dexing.D8DesugaredMethodsGenerator;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidGradlePluginProjectFlags;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.DependenciesInfo;
import com.android.builder.model.InstantRun;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.LintOptions;
import com.android.builder.model.ModelBuilderParameter;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectSyncIssues;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildInformation;
import com.android.builder.model.ViewBindingOptions;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

/** Builder for the custom Android model. */
public class ModelBuilder<Extension extends BaseExtension>
        implements ParameterizedToolingModelBuilder<ModelBuilderParameter> {

    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Extension extension;
    @NonNull private final ExtraModelInfo extraModelInfo;
    @NonNull private final VariantModel variantModel;
    @NonNull private final SyncIssueReporter syncIssueReporter;
    private final int projectType;
    private int modelLevel = AndroidProject.MODEL_LEVEL_0_ORIGINAL;
    private boolean modelWithFullDependency = false;

    /**
     * a map that goes from build name ({@link BuildIdentifier#getName()} to the root dir of the
     * build.
     */
    private ImmutableMap<String, String> buildMapping = null;

    public ModelBuilder(
            @NonNull GlobalScope globalScope,
            @NonNull VariantModel variantModel,
            @NonNull Extension extension,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull SyncIssueReporter syncIssueReporter,
            int projectType) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.extraModelInfo = extraModelInfo;
        this.variantModel = variantModel;
        this.syncIssueReporter = syncIssueReporter;
        this.projectType = projectType;
    }

    @Override
    public boolean canBuild(@NonNull String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName.equals(AndroidProject.class.getName())
                || modelName.equals(GlobalLibraryMap.class.getName())
                || modelName.equals(Variant.class.getName())
                || modelName.equals(ProjectSyncIssues.class.getName());
    }

    @NonNull
    @Override
    public Object buildAll(@NonNull String modelName, @NonNull Project project) {
        // build a map from included build name to rootDir (as rootDir is the only thing
        // that we have access to on the tooling API side).
        initBuildMapping(project);

        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project, true);
        }
        if (modelName.equals(Variant.class.getName())) {
            throw new RuntimeException(
                    "Please use parameterized tooling API to obtain Variant model.");
        }
        return buildNonParameterizedModels(modelName);
    }

    // Build parameterized model. This method is invoked if model is obtained by
    // BuildController::findModel(Model var1, Class<T> var2, Class<P> var3, Action<? super P> var4).
    @NonNull
    @Override
    public Object buildAll(
            @NonNull String modelName,
            @NonNull ModelBuilderParameter parameter,
            @NonNull Project project) {
        // Prevents parameter interface evolution from breaking the model builder.
        parameter = new FailsafeModelBuilderParameter(parameter);

        // build a map from included build name to rootDir (as rootDir is the only thing
        // that we have access to on the tooling API side).
        initBuildMapping(project);
        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project, parameter.getShouldBuildVariant());
        }
        if (modelName.equals(Variant.class.getName())) {
            return buildVariant(
                    project, parameter.getVariantName(), parameter.getShouldGenerateSources());
        }
        return buildNonParameterizedModels(modelName);
    }

    @NonNull
    private Object buildNonParameterizedModels(@NonNull String modelName) {
        if (modelName.equals(GlobalLibraryMap.class.getName())) {
            return buildGlobalLibraryMap(globalScope.getProject().getGradle().getSharedServices());
        } else if (modelName.equals(ProjectSyncIssues.class.getName())) {
            return buildProjectSyncIssuesModel();
        }

        throw new RuntimeException("Invalid model requested: " + modelName);
    }

    @Override
    @NonNull
    public Class<ModelBuilderParameter> getParameterType() {
        return ModelBuilderParameter.class;
    }

    private static Object buildGlobalLibraryMap(
            @NonNull BuildServiceRegistry buildServiceRegistry) {
        LibraryDependencyCacheBuildService libraryDependencyCacheBuildService =
                BuildServicesKt.getBuildService(
                                buildServiceRegistry, LibraryDependencyCacheBuildService.class)
                        .get();
        return new GlobalLibraryMapImpl(libraryDependencyCacheBuildService.getGlobalLibMap());
    }

    private Object buildProjectSyncIssuesModel() {
        syncIssueReporter.lockHandler();

        ImmutableSet.Builder<SyncIssue> allIssues = ImmutableSet.builder();
        allIssues.addAll(syncIssueReporter.getSyncIssues());
        allIssues.addAll(
                BuildServicesKt.getBuildService(
                                globalScope.getProject().getGradle().getSharedServices(),
                                SyncIssueReporterImpl.GlobalSyncIssueService.class)
                        .get()
                        .getAllIssuesAndClear());
        return new DefaultProjectSyncIssues(allIssues.build());
    }

    private Object buildAndroidProject(Project project, boolean shouldBuildVariant) {
        // Cannot be injected, as the project might not be the same as the project used to construct
        // the model builder e.g. when lint explicitly builds the model.
        ProjectOptions projectOptions = new ProjectOptions(project);
        Integer modelLevelInt = SyncOptions.buildModelOnlyVersion(projectOptions);
        if (modelLevelInt != null) {
            modelLevel = modelLevelInt;
        }

        if (modelLevel < AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD) {
            throw new RuntimeException(
                    "This Gradle plugin requires a newer IDE able to request IDE model level 3. For Android Studio this means version 3.0+");
        }

        StudioVersions.verifyIDEIsNotOld(projectOptions);

        modelWithFullDependency =
                projectOptions.get(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES);

        // Get the boot classpath. This will ensure the target is configured.
        List<String> bootClasspath;
        if (globalScope.getSdkComponents().get().getSdkSetupCorrectly().get()) {
            bootClasspath =
                    globalScope.getFilteredBootClasspath().get().stream()
                            .map(it -> it.getAsFile().getAbsolutePath())
                            .collect(Collectors.toList());
        } else {
            // SDK not set up, error will be reported as a sync issue.
            bootClasspath = Collections.emptyList();
        }
        List<File> frameworkSource = Collections.emptyList();

        // List of extra artifacts, with all test variants added.
        List<ArtifactMetaData> artifactMetaDataList = Lists.newArrayList(
                extraModelInfo.getExtraArtifacts());

        for (VariantType variantType : VariantType.Companion.getTestComponents()) {
            artifactMetaDataList.add(new ArtifactMetaDataImpl(
                    variantType.getArtifactName(),
                    true /*isTest*/,
                    variantType.getArtifactType()));
        }

        LintOptions lintOptions =
                com.android.build.gradle.internal.dsl.LintOptions.create(
                        extension.getLintOptions());

        AaptOptions aaptOptions = AaptOptionsImpl.create(extension.getAaptOptions());

        boolean viewBinding =
                variantModel.getVariants().stream()
                        .anyMatch(
                                variantProperties ->
                                        variantProperties.getBuildFeatures().getViewBinding());

        ViewBindingOptions viewBindingOptions = new ViewBindingOptionsImpl(viewBinding);

        DependenciesInfo dependenciesInfo = null;
        if (extension instanceof ApplicationExtension) {
            ApplicationExtension applicationExtension = (ApplicationExtension) extension;
            boolean inApk = applicationExtension.getDependenciesInfo().getIncludeInApk();
            boolean inBundle = applicationExtension.getDependenciesInfo().getIncludeInBundle();
            dependenciesInfo = new DependenciesInfoImpl(inApk, inBundle);
        }

        List<String> flavorDimensionList =
                extension.getFlavorDimensionList() != null
                        ? extension.getFlavorDimensionList()
                        : Lists.newArrayList();

        final VariantInputModel<
                        DefaultConfig,
                        BuildType,
                        ProductFlavor,
                        com.android.build.gradle.internal.dsl.SigningConfig>
                variantInputs = variantModel.getInputs();

        DefaultConfigData<DefaultConfig> defaultConfigData = variantInputs.getDefaultConfigData();
        ProductFlavorContainer defaultConfig =
                ProductFlavorContainerImpl.createProductFlavorContainer(
                        defaultConfigData,
                        defaultConfigData.getDefaultConfig(),
                        extraModelInfo.getExtraFlavorSourceProviders(BuilderConstants.MAIN));

        Collection<BuildTypeContainer> buildTypes = Lists.newArrayList();
        Collection<ProductFlavorContainer> productFlavors = Lists.newArrayList();
        Collection<Variant> variants = Lists.newArrayList();
        Collection<String> variantNames = Lists.newArrayList();

        for (BuildTypeData<BuildType> btData : variantInputs.getBuildTypes().values()) {
            buildTypes.add(BuildTypeContainerImpl.create(
                    btData,
                    extraModelInfo.getExtraBuildTypeSourceProviders(btData.getBuildType().getName())));
        }
        for (ProductFlavorData<ProductFlavor> pfData : variantInputs.getProductFlavors().values()) {
            productFlavors.add(
                    ProductFlavorContainerImpl.createProductFlavorContainer(
                            pfData,
                            pfData.getProductFlavor(),
                            extraModelInfo.getExtraFlavorSourceProviders(
                                    pfData.getProductFlavor().getName())));
        }

        String defaultVariant = variantModel.getDefaultVariant();
        for (VariantPropertiesImpl variantProperties : variantModel.getVariants()) {
            variantNames.add(variantProperties.getName());
            if (shouldBuildVariant) {
                variants.add(createVariant(variantProperties));
            }
        }

        // get groupId/artifactId for project
        String groupId = project.getGroup().toString();

        AndroidGradlePluginProjectFlagsImpl flags = getFlags();

        // Collect all non test variants minimum information.
        Collection<VariantBuildInformation> variantBuildOutputs =
                variantModel.getVariants().stream()
                        .map(this::createBuildInformation)
                        .collect(Collectors.toList());

        return new DefaultAndroidProject(
                project.getName(),
                groupId,
                defaultConfig,
                flavorDimensionList,
                buildTypes,
                productFlavors,
                variants,
                variantNames,
                defaultVariant,
                globalScope.getExtension().getCompileSdkVersion(),
                bootClasspath,
                frameworkSource,
                cloneSigningConfigs(extension.getSigningConfigs()),
                aaptOptions,
                artifactMetaDataList,
                ImmutableList.of(),
                extension.getCompileOptions(),
                lintOptions,
                ImmutableList.copyOf(globalScope.getLocalCustomLintChecks().getFiles()),
                project.getBuildDir(),
                extension.getResourcePrefix(),
                ImmutableList.of(),
                extension.getBuildToolsVersion(),
                extension.getNdkVersion(),
                projectType,
                Version.BUILDER_MODEL_API_VERSION,
                isBaseSplit(),
                getDynamicFeatures(),
                viewBindingOptions,
                dependenciesInfo,
                flags,
                variantBuildOutputs);
    }

    private VariantBuildInformation createBuildInformation(
            ComponentPropertiesImpl componentProperties) {
        return new VariantBuildInformationImp(
                componentProperties.getName(),
                componentProperties.getTaskContainer().assembleTask.getName(),
                toAbsolutePath(
                        componentProperties
                                .getArtifacts()
                                .get(InternalArtifactType.APK_IDE_MODEL.INSTANCE)
                                .getOrNull()),
                componentProperties.getTaskContainer().getBundleTask() == null
                        ? componentProperties.computeTaskName("bundle")
                        : componentProperties.getTaskContainer().getBundleTask().getName(),
                toAbsolutePath(
                        componentProperties
                                .getArtifacts()
                                .get(InternalArtifactType.BUNDLE_IDE_MODEL.INSTANCE)
                                .getOrNull()),
                ExtractApksTask.Companion.getTaskName(componentProperties),
                toAbsolutePath(
                        componentProperties
                                .getArtifacts()
                                .get(InternalArtifactType.APK_FROM_BUNDLE_IDE_MODEL.INSTANCE)
                                .getOrNull()));
    }

    private AndroidGradlePluginProjectFlagsImpl getFlags() {
        ImmutableMap.Builder<AndroidGradlePluginProjectFlags.BooleanFlag, Boolean> flags =
                ImmutableMap.builder();
        boolean finalResIds =
                !globalScope.getProjectOptions().get(BooleanOption.USE_NON_FINAL_RES_IDS);
        flags.put(
                AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS,
                finalResIds);
        flags.put(
                AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS, finalResIds);

        flags.put(
                AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE,
                variantModel.getVariants().stream()
                        .anyMatch(
                                variantProperties ->
                                        variantProperties.getBuildFeatures().getCompose()));

        flags.put(
                AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING,
                variantModel.getVariants().stream()
                        .anyMatch(
                                variantProperties ->
                                        variantProperties.getBuildFeatures().getMlModelBinding()));

        boolean transitiveRClass =
                !globalScope.getProjectOptions().get(BooleanOption.NON_TRANSITIVE_R_CLASS);
        flags.put(AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS, transitiveRClass);

        return new AndroidGradlePluginProjectFlagsImpl(flags.build());
    }

    protected boolean isBaseSplit() {
        return false;
    }

    @Nullable
    private static String toAbsolutePath(@Nullable RegularFile regularFile) {
        return regularFile != null ? regularFile.getAsFile().getAbsolutePath() : null;
    }

    protected boolean inspectManifestForInstantTag(
            @NonNull ComponentPropertiesImpl componentProperties) {
        if (projectType != PROJECT_TYPE_APP && projectType != PROJECT_TYPE_DYNAMIC_FEATURE) {
            return false;
        }

        VariantSources variantSources = componentProperties.getVariantSources();

        List<File> manifests = new ArrayList<>(variantSources.getManifestOverlays());
        File mainManifest = variantSources.getMainManifestIfExists();
        if (mainManifest != null) {
            manifests.add(mainManifest);
        }
        if (manifests.isEmpty()) {
            return false;
        }

        for (File manifest : manifests) {
            try (FileInputStream inputStream = new FileInputStream(manifest)) {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

                while (eventReader.hasNext() && !eventReader.peek().isEndDocument()) {
                    XMLEvent event = eventReader.nextTag();
                    if (event.isStartElement()) {
                        StartElement startElement = event.asStartElement();
                        if (startElement.getName().getNamespaceURI().equals(DIST_URI)
                                && startElement
                                        .getName()
                                        .getLocalPart()
                                        .equalsIgnoreCase("module")) {
                            Attribute instant =
                                    startElement.getAttributeByName(new QName(DIST_URI, "instant"));
                            if (instant != null
                                    && (instant.getValue().equals(SdkConstants.VALUE_TRUE)
                                            || instant.getValue().equals(SdkConstants.VALUE_1))) {
                                eventReader.close();
                                return true;
                            }
                        }
                    } else if (event.isEndElement()
                            && ((EndElement) event)
                                    .getName()
                                    .getLocalPart()
                                    .equalsIgnoreCase("manifest")) {
                        break;
                    }
                }
                eventReader.close();
            } catch (XMLStreamException | IOException e) {
                syncIssueReporter.reportError(
                        Type.GENERIC,
                        "Failed to parse XML in " + manifest.getPath() + "\n" + e.getMessage());
            }
        }
        return false;
    }

    @NonNull
    protected Collection<String> getDynamicFeatures() {
        return ImmutableList.of();
    }

    @NonNull
    private VariantImpl buildVariant(
            @NonNull Project project,
            @Nullable String variantName,
            boolean shouldScheduleSourceGeneration) {
        if (variantName == null) {
            throw new IllegalArgumentException("Variant name cannot be null.");
        }
        for (VariantPropertiesImpl variantProperties : variantModel.getVariants()) {
            if (variantProperties.getName().equals(variantName)) {
                VariantImpl variant = createVariant(variantProperties);
                if (shouldScheduleSourceGeneration) {
                    scheduleSourceGeneration(project, variant);
                }
                return variant;
            }
        }
        throw new IllegalArgumentException(
                String.format("Variant with name '%s' doesn't exist.", variantName));
    }

    /**
     * Used when fetching Android model and generating sources in the same Gradle invocation.
     *
     * <p>As this method modify Gradle tasks, it has to be run before task graph is calculated,
     * which means using {@link org.gradle.tooling.BuildActionExecuter.Builder#projectsLoaded(
     * org.gradle.tooling.BuildAction, org.gradle.tooling.IntermediateResultHandler)} to register
     * the {@link org.gradle.tooling.BuildAction}.
     */
    private static void scheduleSourceGeneration(
            @NonNull Project project, @NonNull Variant variant) {
        List<BaseArtifact> artifacts = Lists.newArrayList(variant.getMainArtifact());
        artifacts.addAll(variant.getExtraAndroidArtifacts());
        artifacts.addAll(variant.getExtraJavaArtifacts());

        Set<String> sourceGenerationTasks =
                artifacts
                        .stream()
                        .map(BaseArtifact::getIdeSetupTaskNames)
                        .flatMap(Collection::stream)
                        .map(taskName -> project.getPath() + ":" + taskName)
                        .collect(Collectors.toSet());

        try {
            StartParameter startParameter = project.getGradle().getStartParameter();
            Set<String> tasks = new HashSet<>(startParameter.getTaskNames());
            tasks.addAll(sourceGenerationTasks);
            startParameter.setTaskNames(tasks);
        } catch (Throwable e) {
            throw new RuntimeException("Can't modify scheduled tasks at current build step", e);
        }
    }

    @NonNull
    private VariantImpl createVariant(@NonNull ComponentPropertiesImpl componentProperties) {
        AndroidArtifact mainArtifact = createAndroidArtifact(ARTIFACT_MAIN, componentProperties);

        // Need access to the merged flavors for the model, so we cast.
        VariantDslInfoImpl variantDslInfo =
                (VariantDslInfoImpl) componentProperties.getVariantDslInfo();

        File manifest = componentProperties.getVariantSources().getMainManifestIfExists();
        if (manifest != null) {
            ManifestAttributeSupplier attributeSupplier =
                    new DefaultManifestParser(
                            manifest,
                            () -> true,
                            componentProperties.getVariantType().getRequiresManifest(),
                            syncIssueReporter);
            try {
                validateMinSdkVersion(attributeSupplier);
                validateTargetSdkVersion(attributeSupplier);
            } catch (Throwable e) {
                syncIssueReporter.reportError(
                        Type.GENERIC,
                        "Failed to parse XML in " + manifest.getPath() + "\n" + e.getMessage());
            }
        }

        String variantName = componentProperties.getName();

        List<AndroidArtifact> extraAndroidArtifacts = Lists.newArrayList(
                extraModelInfo.getExtraAndroidArtifacts(variantName));
        LibraryDependencyCacheBuildService libraryDependencyCache =
                BuildServicesKt.getBuildService(
                                componentProperties.getServices().getBuildServiceRegistry(),
                                LibraryDependencyCacheBuildService.class)
                        .get();
        // Make sure all extra artifacts are serializable.
        List<JavaArtifact> clonedExtraJavaArtifacts =
                extraModelInfo.getExtraJavaArtifacts(variantName).stream()
                        .map(
                                javaArtifact ->
                                        JavaArtifactImpl.clone(
                                                javaArtifact,
                                                modelLevel,
                                                modelWithFullDependency,
                                                libraryDependencyCache))
                        .collect(Collectors.toList());

        if (componentProperties instanceof VariantPropertiesImpl) {
            VariantPropertiesImpl variantProperties = (VariantPropertiesImpl) componentProperties;

            for (VariantType variantType : VariantType.Companion.getTestComponents()) {
                ComponentPropertiesImpl testVariant =
                        variantProperties.getTestComponents().get(variantType);
                if (testVariant != null) {
                    switch ((VariantTypeImpl) variantType) {
                        case ANDROID_TEST:
                            extraAndroidArtifacts.add(
                                    createAndroidArtifact(
                                            variantType.getArtifactName(), testVariant));
                            break;
                        case UNIT_TEST:
                            clonedExtraJavaArtifacts.add(
                                    createUnitTestsJavaArtifact(variantType, testVariant));
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unsupported test variant type ${variantType}.");
                    }
                }
            }
        }

        // used for test only modules
        Collection<TestedTargetVariant> testTargetVariants =
                getTestTargetVariants(componentProperties);

        checkProguardFiles(componentProperties);

        return new VariantImpl(
                variantName,
                componentProperties.getBaseName(),
                componentProperties.getBuildType(),
                getProductFlavorNames(componentProperties),
                new ProductFlavorImpl(
                        variantDslInfo.getMergedFlavor(), variantDslInfo.getApplicationId()),
                mainArtifact,
                extraAndroidArtifacts,
                clonedExtraJavaArtifacts,
                testTargetVariants,
                inspectManifestForInstantTag(componentProperties),
                getDesugaredMethods(componentProperties));
    }

    private void checkProguardFiles(@NonNull ComponentPropertiesImpl componentProperties) {
        final Project project = globalScope.getProject();

        // We check for default files unless it's a base module, which can include default files.
        boolean isBaseModule = componentProperties.getVariantType().isBaseModule();
        boolean isDynamicFeature = componentProperties.getVariantType().isDynamicFeature();

        if (!isBaseModule) {
            List<File> consumerProguardFiles =
                    componentProperties.getVariantScope().getConsumerProguardFilesForFeatures();

            ExportConsumerProguardFilesTask.checkProguardFiles(
                    project.getLayout().getBuildDirectory(),
                    isDynamicFeature,
                    consumerProguardFiles,
                    errorMessage -> syncIssueReporter.reportError(Type.GENERIC, errorMessage));
        }
    }

    @NonNull
    private Collection<TestedTargetVariant> getTestTargetVariants(
            @NonNull ComponentPropertiesImpl componentProperties) {
        if (extension instanceof TestAndroidConfig) {
            TestAndroidConfig testConfig = (TestAndroidConfig) extension;

            // to get the target variant we need to get the result of the dependency resolution
            ArtifactCollection apkArtifacts =
                    componentProperties
                            .getVariantDependencies()
                            .getArtifactCollection(
                                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                                    AndroidArtifacts.ArtifactScope.ALL,
                                    AndroidArtifacts.ArtifactType.MANIFEST_METADATA);

            // while there should be single result, if the variant matching is broken, then
            // we need to support this.
            if (apkArtifacts.getArtifacts().size() == 1) {
                ResolvedArtifactResult result =
                        Iterables.getOnlyElement(apkArtifacts.getArtifacts());
                String variant = LibraryUtils.getVariantName(result);

                return ImmutableList.of(
                        new TestedTargetVariantImpl(testConfig.getTargetProjectPath(), variant));
            } else if (!apkArtifacts.getFailures().isEmpty()) {
                // probably there was an error...
                new DependencyFailureHandler()
                        .addErrors(
                                globalScope.getProject().getPath()
                                        + "@"
                                        + componentProperties.getName()
                                        + "/testTarget",
                                apkArtifacts.getFailures())
                        .registerIssues(syncIssueReporter);
            }
        }

        return ImmutableList.of();
    }

    private JavaArtifactImpl createUnitTestsJavaArtifact(
            @NonNull VariantType variantType,
            @NonNull ComponentPropertiesImpl componentProperties) {
        ArtifactsImpl artifacts = componentProperties.getArtifacts();

        SourceProviders sourceProviders = determineSourceProviders(componentProperties);

        //final VariantScope scope = variantData.getScope();
        Pair<Dependencies, DependencyGraphs> result =
                getDependencies(
                        componentProperties, buildMapping, modelLevel, modelWithFullDependency);

        Set<File> additionalTestClasses = new HashSet<>();
        additionalTestClasses.addAll(
                componentProperties.getVariantData().getAllPreJavacGeneratedBytecode().getFiles());
        additionalTestClasses.addAll(
                componentProperties.getVariantData().getAllPostJavacGeneratedBytecode().getFiles());
        if (componentProperties.getGlobalScope().getExtension().getTestOptions()
                .getUnitTests().isIncludeAndroidResources()) {
            additionalTestClasses.add(
                    artifacts
                            .get(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY.INSTANCE)
                            .get()
                            .getAsFile());
        }
        // The separately compile R class, if applicable.
        if (!globalScope.getExtension().getAaptOptions().getNamespaced()) {
            additionalTestClasses.add(
                    componentProperties.getVariantScope().getRJarForUnitTests().get().getAsFile());
        }

        // No files are possible if the SDK was not configured properly.
        File mockableJar =
                globalScope.getMockableJarArtifact().getFiles().stream().findFirst().orElse(null);

        return new JavaArtifactImpl(
                variantType.getArtifactName(),
                componentProperties.getTaskContainer().getAssembleTask().getName(),
                componentProperties.getTaskContainer().getCompileTask().getName(),
                Sets.newHashSet(TaskManager.CREATE_MOCKABLE_JAR_TASK_NAME),
                getGeneratedSourceFoldersForUnitTests(componentProperties),
                artifacts.get(JAVAC.INSTANCE).get().getAsFile(),
                additionalTestClasses,
                componentProperties.getVariantData().getJavaResourcesForUnitTesting(),
                mockableJar,
                result.getFirst(),
                result.getSecond(),
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider);
    }

    /** Gather the dependency graph for the specified <code>variantScope</code>. */
    @NonNull
    private Pair<Dependencies, DependencyGraphs> getDependencies(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull ImmutableMap<String, String> buildMapping,
            int modelLevel,
            boolean modelWithFullDependency) {
        Pair<Dependencies, DependencyGraphs> result;

        // If there is a missing flavor dimension then we don't even try to resolve dependencies
        // as it may fail due to improperly setup configuration attributes.
        if (syncIssueReporter.hasIssue(Type.UNNAMED_FLAVOR_DIMENSION)) {
            result = Pair.of(DependenciesImpl.EMPTY, EmptyDependencyGraphs.EMPTY);
        } else {
            DependencyGraphBuilder graphBuilder = DependencyGraphBuilderKt.getDependencyGraphBuilder();
            // can't use ProjectOptions as this is likely to change from the initialization of
            // ProjectOptions due to how lint dynamically add/remove this property.

            if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
                result =
                        Pair.of(
                                DependenciesImpl.EMPTY,
                                graphBuilder.createLevel4DependencyGraph(
                                        componentProperties,
                                        modelWithFullDependency,
                                        buildMapping,
                                        syncIssueReporter));
            } else {
                result =
                        Pair.of(
                                graphBuilder.createDependencies(
                                        componentProperties, buildMapping, syncIssueReporter),
                                EmptyDependencyGraphs.EMPTY);
            }
        }

        return result;
    }

    private AndroidArtifact createAndroidArtifact(
            @NonNull String name, @NonNull ComponentPropertiesImpl componentProperties) {
        VariantScope variantScope = componentProperties.getVariantScope();
        VariantDslInfo variantDslInfo = componentProperties.getVariantDslInfo();

        SigningConfig signingConfig = variantDslInfo.getSigningConfig();
        String signingConfigName = null;
        if (signingConfig != null) {
            signingConfigName = signingConfig.getName();
        }

        SourceProviders sourceProviders = determineSourceProviders(componentProperties);

        InstantRunImpl instantRun =
                new InstantRunImpl(
                        globalScope.getProject().file("build_info_removed"),
                        InstantRun.STATUS_REMOVED);

        Pair<Dependencies, DependencyGraphs> dependencies =
                getDependencies(
                        componentProperties, buildMapping, modelLevel, modelWithFullDependency);

        Set<File> additionalClasses = new HashSet<>();
        additionalClasses.addAll(
                componentProperties.getVariantData().getAllPreJavacGeneratedBytecode().getFiles());
        additionalClasses.addAll(
                componentProperties.getVariantData().getAllPostJavacGeneratedBytecode().getFiles());
        additionalClasses.addAll(
                componentProperties
                        .getCompiledRClasses(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH)
                        .getFiles());

        List<File> additionalRuntimeApks = new ArrayList<>();
        TestOptionsImpl testOptions = null;

        if (componentProperties.getVariantType().isTestComponent()) {
            Configuration testHelpers =
                    globalScope
                            .getProject()
                            .getConfigurations()
                            .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION);

            // This may be the case with the experimental plugin.
            if (testHelpers != null) {
                additionalRuntimeApks.addAll(testHelpers.getFiles());
            }

            DeviceProviderInstrumentTestTask.checkForNonApks(
                    additionalRuntimeApks,
                    message -> syncIssueReporter.reportError(Type.GENERIC, message));

            TestOptions testOptionsDsl = globalScope.getExtension().getTestOptions();
            testOptions =
                    new TestOptionsImpl(
                            testOptionsDsl.getAnimationsDisabled(),
                            testOptionsDsl.getExecutionEnum());
        }

        // FIXME: Remove appId from the model
        String applicationId;
        try {
            // This can throw an exception if no package name can be found.
            // Normally, this is fine to throw an exception, but we don't want to crash in sync.
            applicationId = variantDslInfo.getApplicationId().get();
        } catch (RuntimeException e) {
            // don't crash. just throw a sync error.
            applicationId = "";
            syncIssueReporter.reportError(Type.GENERIC, e);
        }

        MutableTaskContainer taskContainer = componentProperties.getTaskContainer();
        ArtifactsImpl artifacts = componentProperties.getArtifacts();

        return new AndroidArtifactImpl(
                name,
                globalScope.getProjectBaseName() + "-" + componentProperties.getBaseName(),
                taskContainer.getAssembleTask().getName(),
                artifacts.get(InternalArtifactType.APK_IDE_MODEL.INSTANCE).getOrNull(),
                variantDslInfo.isSigningReady()
                        || componentProperties.getVariantData().outputsAreSigned,
                signingConfigName,
                applicationId,
                taskContainer.getSourceGenTask().getName(),
                taskContainer.getCompileTask().getName(),
                getGeneratedSourceFolders(componentProperties),
                getGeneratedResourceFolders(componentProperties),
                artifacts.get(JAVAC.INSTANCE).get().getAsFile(),
                additionalClasses,
                componentProperties.getVariantData().getJavaResourcesForUnitTesting(),
                dependencies.getFirst(),
                dependencies.getSecond(),
                additionalRuntimeApks,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider,
                variantDslInfo.getSupportedAbis(),
                instantRun,
                testOptions,
                taskContainer.getConnectedTask() == null
                        ? null
                        : taskContainer.getConnectedTask().getName(),
                taskContainer.getBundleTask() == null
                        ? componentProperties.computeTaskName("bundle")
                        : taskContainer.getBundleTask().getName(),
                artifacts.get(InternalArtifactType.BUNDLE_IDE_MODEL.INSTANCE).getOrNull(),
                ExtractApksTask.Companion.getTaskName(componentProperties),
                artifacts.get(InternalArtifactType.APK_FROM_BUNDLE_IDE_MODEL.INSTANCE).getOrNull(),
                variantScope.getCodeShrinker());
    }

    private void validateMinSdkVersion(@NonNull ManifestAttributeSupplier supplier) {
        if (supplier.getMinSdkVersion() != null) {
            // report an error since min sdk version should not be in the manifest.
            syncIssueReporter.reportError(
                    IssueReporter.Type.MIN_SDK_VERSION_IN_MANIFEST,
                    "The minSdk version should not be declared in the android"
                            + " manifest file. You can move the version from the manifest"
                            + " to the defaultConfig in the build.gradle file.");
        }
    }

    private void validateTargetSdkVersion(@NonNull ManifestAttributeSupplier supplier) {
        if (supplier.getTargetSdkVersion() != null) {
            // report a warning since target sdk version should not be in the manifest.
            syncIssueReporter.reportWarning(
                    IssueReporter.Type.TARGET_SDK_VERSION_IN_MANIFEST,
                    "The targetSdk version should not be declared in the android"
                            + " manifest file. You can move the version from the manifest"
                            + " to the defaultConfig in the build.gradle file.");
        }
    }

    private static SourceProviders determineSourceProviders(
            @NonNull ComponentPropertiesImpl componentProperties) {
        SourceProvider variantSourceProvider =
                componentProperties.getVariantSources().getVariantSourceProvider();
        SourceProvider multiFlavorSourceProvider =
                componentProperties.getVariantSources().getMultiFlavorSourceProvider();

        return new SourceProviders(
                variantSourceProvider != null ?
                        new SourceProviderImpl(variantSourceProvider) :
                        null,
                multiFlavorSourceProvider != null ?
                        new SourceProviderImpl(multiFlavorSourceProvider) :
                        null);
    }

    @NonNull
    private static List<String> getProductFlavorNames(
            @NonNull ComponentPropertiesImpl componentProperties) {
        return componentProperties
                .getProductFlavors()
                .stream()
                .map(kotlin.Pair::getSecond)
                .collect(Collectors.toList());
    }

    @NonNull
    private static List<File> getGeneratedSourceFoldersForUnitTests(
            @Nullable ComponentPropertiesImpl componentProperties) {
        if (componentProperties == null) {
            return Collections.emptyList();
        }

        List<File> folders =
                Lists.newArrayList(
                        componentProperties.getVariantData().getExtraGeneratedSourceFolders());
        folders.add(
                componentProperties
                        .getArtifacts()
                        .get(InternalArtifactType.AP_GENERATED_SOURCES.INSTANCE)
                        .get()
                        .getAsFile());
        return folders;
    }

    @NonNull
    private List<File> getGeneratedSourceFolders(
            @Nullable ComponentPropertiesImpl componentProperties) {
        if (componentProperties == null) {
            return Collections.emptyList();
        }
        ArtifactsImpl operations = componentProperties.getArtifacts();

        boolean isDataBindingEnabled = componentProperties.getBuildFeatures().getDataBinding();
        boolean isViewBindingEnabled = componentProperties.getBuildFeatures().getViewBinding();
        Directory dataBindingSources =
                operations.get(DATA_BINDING_BASE_CLASS_SOURCE_OUT.INSTANCE).getOrNull();
        boolean addBindingSources =
                (isDataBindingEnabled || isViewBindingEnabled) && (dataBindingSources != null);
        List<File> extraFolders = getGeneratedSourceFoldersForUnitTests(componentProperties);

        // Set this to the number of folders you expect to add explicitly in the code below.
        int additionalFolders = 4;
        if (addBindingSources) {
            additionalFolders += 1;
        }
        List<File> folders =
                Lists.newArrayListWithExpectedSize(additionalFolders + extraFolders.size());
        folders.addAll(extraFolders);

        Directory aidlSources =
                operations.get(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR.INSTANCE).getOrNull();

        if (aidlSources != null) {
            folders.add(aidlSources.getAsFile());
        }
        if (componentProperties.getBuildConfigType() == BuildConfigType.JAVA_CLASS) {
            folders.add(componentProperties.getPaths().getBuildConfigSourceOutputDir());
        }
        boolean ndkMode = componentProperties.getVariantDslInfo().getRenderscriptNdkModeEnabled();
        if (!ndkMode) {
            Directory renderscriptSources =
                    operations
                            .get(InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR.INSTANCE)
                            .getOrNull();
            if (renderscriptSources != null) {
                folders.add(renderscriptSources.getAsFile());
            }
        }
        if (addBindingSources) {
            folders.add(dataBindingSources.getAsFile());
        }
        return folders;
    }

    @NonNull
    private static List<File> getGeneratedResourceFolders(
            @Nullable ComponentPropertiesImpl componentProperties) {
        if (componentProperties == null) {
            return Collections.emptyList();
        }

        List<File> result;

        final FileCollection extraResFolders =
                componentProperties.getVariantData().getExtraGeneratedResFolders();
        Set<File> extraFolders = extraResFolders != null ? extraResFolders.getFiles() : null;
        if (extraFolders != null && !extraFolders.isEmpty()) {
            result = Lists.newArrayListWithCapacity(extraFolders.size() + 2);
            result.addAll(extraFolders);
        } else {
            result = Lists.newArrayListWithCapacity(2);
        }

        result.add(componentProperties.getPaths().getRenderscriptResOutputDir());
        result.add(componentProperties.getPaths().getGeneratedResOutputDir());

        return result;
    }

    @NonNull
    private static Collection<SigningConfig> cloneSigningConfigs(
            @NonNull Collection<? extends SigningConfig> signingConfigs) {
        return signingConfigs.stream()
                .map((Function<SigningConfig, SigningConfig>)
                        SigningConfigImpl::createSigningConfig)
                .collect(Collectors.toList());
    }

    private static class SourceProviders {
        protected SourceProviderImpl variantSourceProvider;
        protected SourceProviderImpl multiFlavorSourceProvider;

        public SourceProviders(
                SourceProviderImpl variantSourceProvider,
                SourceProviderImpl multiFlavorSourceProvider) {
            this.variantSourceProvider = variantSourceProvider;
            this.multiFlavorSourceProvider = multiFlavorSourceProvider;
        }
    }

    private void initBuildMapping(@NonNull Project project) {
        if (buildMapping == null) {
            buildMapping = BuildMappingUtils.computeBuildMapping(project.getGradle());
        }
    }

    @NonNull
    private List<String> getDesugaredMethods(@NonNull ComponentPropertiesImpl componentProperties) {
        List<String> desugaredMethodsFromDesugarLib =
                DesugarLibUtils.getDesugaredMethods(
                        componentProperties.getGlobalScope().getProject(),
                        componentProperties.getVariantScope().isCoreLibraryDesugaringEnabled(),
                        componentProperties.getMinSdkVersion(),
                        componentProperties.getGlobalScope().getExtension().getCompileSdkVersion());

        List<String> desugaredMethodsFromD8 = D8DesugaredMethodsGenerator.INSTANCE.generate();

        List<String> desugaredMethods = new ArrayList<>(desugaredMethodsFromDesugarLib);
        desugaredMethods.addAll(desugaredMethodsFromD8);
        return desugaredMethods;
    }
}
