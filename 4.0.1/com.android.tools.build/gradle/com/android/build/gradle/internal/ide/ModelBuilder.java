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
import com.android.build.VariantOutput;
import com.android.build.api.artifact.PublicArtifactType;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.errors.SyncIssueReporter;
import com.android.build.gradle.internal.ide.dependencies.BuildMappingUtils;
import com.android.build.gradle.internal.ide.dependencies.DependencyGraphBuilder;
import com.android.build.gradle.internal.ide.dependencies.DependencyGraphBuilderKt;
import com.android.build.gradle.internal.ide.dependencies.LibraryUtils;
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesUtils;
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs;
import com.android.build.gradle.internal.ide.level2.GlobalLibraryMapImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.SingleArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.ExtractApksTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
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
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.ProjectSyncIssues;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.android.builder.model.ViewBindingOptions;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

/** Builder for the custom Android model. */
public class ModelBuilder<Extension extends BaseExtension>
        implements ParameterizedToolingModelBuilder<ModelBuilderParameter> {

    @NonNull protected final GlobalScope globalScope;
    @NonNull protected final Extension extension;
    @NonNull private final ExtraModelInfo extraModelInfo;
    @NonNull private final VariantModel variantModel;
    @NonNull private final TaskManager taskManager;
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
            @NonNull TaskManager taskManager,
            @NonNull Extension extension,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull SyncIssueReporter syncIssueReporter,
            int projectType) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.extraModelInfo = extraModelInfo;
        this.variantModel = variantModel;
        this.taskManager = taskManager;
        this.syncIssueReporter = syncIssueReporter;
        this.projectType = projectType;
    }

    public static void clearCaches() {
        LibraryUtils.clearCaches();
        MavenCoordinatesUtils.clearMavenCaches();
    }

    @Override
    public boolean canBuild(@NonNull String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName.equals(AndroidProject.class.getName())
                || modelName.equals(GlobalLibraryMap.class.getName())
                || modelName.equals(ProjectBuildOutput.class.getName())
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
        if (modelName.equals(ProjectBuildOutput.class.getName())) {
            return buildMinimalisticModel();
        } else if (modelName.equals(GlobalLibraryMap.class.getName())) {
            return buildGlobalLibraryMap();
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

    @VisibleForTesting
    ProjectBuildOutput buildMinimalisticModel() {

        ImmutableList.Builder<VariantBuildOutput> variantsOutput = ImmutableList.builder();

        // gather the testingVariants per testedVariant
        Multimap<VariantScope, VariantScope> sortedVariants = ArrayListMultimap.create();
        for (VariantScope variantScope : variantModel.getVariants()) {
            boolean isTestComponent = variantScope.getVariantData().getType().isTestComponent();

            if (isTestComponent && variantScope.getTestedVariantData() != null) {
                sortedVariants.put(variantScope.getTestedVariantData().getScope(), variantScope);
            }
        }

        for (VariantScope variantScope : variantModel.getVariants()) {
            boolean isTestComponent = variantScope.getType().isTestComponent();

            if (!isTestComponent) {
                Collection<VariantScope> testingVariants = sortedVariants.get(variantScope);
                Collection<TestVariantBuildOutput> testVariantBuildOutputs;
                if (testingVariants == null) {
                    testVariantBuildOutputs = ImmutableList.of();
                } else {
                    testVariantBuildOutputs =
                            testingVariants
                                    .stream()
                                    .map(
                                            testVariantScope ->
                                                    new DefaultTestVariantBuildOutput(
                                                            testVariantScope.getName(),
                                                            getBuildOutputSupplier(
                                                                            testVariantScope
                                                                                    .getVariantData())
                                                                    .get(),
                                                            variantScope.getName(),
                                                            testVariantScope.getType()
                                                                            == VariantTypeImpl
                                                                                    .ANDROID_TEST
                                                                    ? TestVariantBuildOutput
                                                                            .TestType.ANDROID_TEST
                                                                    : TestVariantBuildOutput
                                                                            .TestType.UNIT))
                                    .collect(Collectors.toList());
                }
                variantsOutput.add(
                        new DefaultVariantBuildOutput(
                                variantScope.getName(),
                                getBuildOutputSupplier(variantScope.getVariantData()).get(),
                                testVariantBuildOutputs));
            }
        }

        return new DefaultProjectBuildOutput(variantsOutput.build());
    }

    private static Object buildGlobalLibraryMap() {
        return new GlobalLibraryMapImpl(LibraryUtils.getGlobalLibMap());
    }

    private Object buildProjectSyncIssuesModel() {
        syncIssueReporter.lockHandler();
        return new DefaultProjectSyncIssues(ImmutableSet.copyOf(syncIssueReporter.getSyncIssues()));
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

        StudioVersions.verifyStudioIsNotOld(projectOptions);

        modelWithFullDependency =
                projectOptions.get(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES);

        // Get the boot classpath. This will ensure the target is configured.
        List<String> bootClasspath;
        if (globalScope.getSdkComponents().getSdkSetupCorrectly().get()) {
            bootClasspath =
                    globalScope
                            .getFilteredBootClasspath()
                            .getFiles()
                            .stream()
                            .map(File::getAbsolutePath)
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

        ViewBindingOptions viewBindingOptions =
                new ViewBindingOptionsImpl(globalScope.getBuildFeatures().getViewBinding());

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

        final VariantInputModel variantInputs = variantModel.getInputs();

        ProductFlavorContainer defaultConfig =
                ProductFlavorContainerImpl.createProductFlavorContainer(
                        variantInputs.getDefaultConfig(),
                        extraModelInfo.getExtraFlavorSourceProviders(
                                variantInputs.getDefaultConfig().getProductFlavor().getName()));

        Collection<BuildTypeContainer> buildTypes = Lists.newArrayList();
        Collection<ProductFlavorContainer> productFlavors = Lists.newArrayList();
        Collection<Variant> variants = Lists.newArrayList();
        Collection<String> variantNames = Lists.newArrayList();

        for (BuildTypeData btData : variantInputs.getBuildTypes().values()) {
            buildTypes.add(BuildTypeContainerImpl.create(
                    btData,
                    extraModelInfo.getExtraBuildTypeSourceProviders(btData.getBuildType().getName())));
        }
        for (ProductFlavorData pfData : variantInputs.getProductFlavors().values()) {
            productFlavors.add(ProductFlavorContainerImpl.createProductFlavorContainer(
                    pfData,
                    extraModelInfo.getExtraFlavorSourceProviders(pfData.getProductFlavor().getName())));
        }

        String defaultVariant = variantModel.getDefaultVariant();
        for (VariantScope variantScope : variantModel.getVariants()) {
            if (!variantScope.getVariantData().getType().isTestComponent()) {
                variantNames.add(variantScope.getName());
                if (shouldBuildVariant) {
                    variants.add(createVariant(variantScope.getVariantData()));
                }
            }
        }

        // get groupId/artifactId for project
        String groupId = project.getGroup().toString();

        AndroidGradlePluginProjectFlagsImpl flags = getFlags();

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
                project.getBuildDir(),
                extension.getResourcePrefix(),
                ImmutableList.of(),
                extension.getBuildToolsVersion(),
                projectType,
                Version.BUILDER_MODEL_API_VERSION,
                isBaseSplit(),
                getDynamicFeatures(),
                viewBindingOptions,
                dependenciesInfo,
                flags);
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
                globalScope.getBuildFeatures().getCompose());

        boolean transitiveRClass =
                !globalScope.getProjectOptions().get(BooleanOption.NAMESPACED_R_CLASS);
        flags.put(AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS, transitiveRClass);

        return new AndroidGradlePluginProjectFlagsImpl(flags.build());
    }

    protected boolean isBaseSplit() {
        return false;
    }

    protected boolean inspectManifestForInstantTag(BaseVariantData variantData) {
        if (projectType != PROJECT_TYPE_APP && projectType != PROJECT_TYPE_DYNAMIC_FEATURE) {
            return false;
        }

        VariantSources variantSources = variantData.getVariantSources();

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
        for (VariantScope variantScope : variantModel.getVariants()) {
            if (!variantScope.getVariantData().getType().isTestComponent()
                    && variantScope.getName().equals(variantName)) {
                VariantImpl variant = createVariant(variantScope.getVariantData());
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
    private VariantImpl createVariant(@NonNull BaseVariantData variantData) {
        AndroidArtifact mainArtifact = createAndroidArtifact(ARTIFACT_MAIN, variantData);

        // Need access to the merged flavors for the model, so we cast.
        VariantDslInfoImpl variantDslInfo = (VariantDslInfoImpl) variantData.getVariantDslInfo();

        File manifest = variantData.getVariantSources().getMainManifestIfExists();
        if (manifest != null) {
            ManifestAttributeSupplier attributeSupplier =
                    new DefaultManifestParser(
                            manifest,
                            () -> true,
                            variantDslInfo.getVariantType().getRequiresManifest(),
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

        String variantName = variantData.getName();

        List<AndroidArtifact> extraAndroidArtifacts = Lists.newArrayList(
                extraModelInfo.getExtraAndroidArtifacts(variantName));
        // Make sure all extra artifacts are serializable.
        List<JavaArtifact> clonedExtraJavaArtifacts =
                extraModelInfo
                        .getExtraJavaArtifacts(variantName)
                        .stream()
                        .map(
                                javaArtifact ->
                                        JavaArtifactImpl.clone(
                                                javaArtifact, modelLevel, modelWithFullDependency))
                        .collect(Collectors.toList());

        if (variantData instanceof TestedVariantData) {
            for (VariantType variantType : VariantType.Companion.getTestComponents()) {
                TestVariantData testVariantData = ((TestedVariantData) variantData).getTestVariantData(variantType);
                if (testVariantData != null) {
                    switch ((VariantTypeImpl) variantType) {
                        case ANDROID_TEST:
                            extraAndroidArtifacts.add(
                                    createAndroidArtifact(
                                            variantType.getArtifactName(), testVariantData));
                            break;
                        case UNIT_TEST:
                            clonedExtraJavaArtifacts.add(
                                    createUnitTestsJavaArtifact(variantType, testVariantData));
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unsupported test variant type ${variantType}.");
                    }
                }
            }
        }

        // used for test only modules
        Collection<TestedTargetVariant> testTargetVariants = getTestTargetVariants(variantData);

        checkProguardFiles(variantData.getScope());

        return new VariantImpl(
                variantName,
                variantDslInfo.getBaseName(),
                variantDslInfo.getComponentIdentity().getBuildType(),
                getProductFlavorNames(variantData),
                new ProductFlavorImpl(variantDslInfo.getMergedFlavor()),
                mainArtifact,
                extraAndroidArtifacts,
                clonedExtraJavaArtifacts,
                testTargetVariants,
                inspectManifestForInstantTag(variantData));
    }

    private void checkProguardFiles(@NonNull VariantScope variantScope) {
        final GlobalScope globalScope = variantScope.getGlobalScope();
        final Project project = globalScope.getProject();

        // We check for default files unless it's a base module, which can include default files.
        boolean isBaseModule = variantScope.getType().isBaseModule();
        boolean isDynamicFeature = variantScope.getType().isDynamicFeature();

        if (!isBaseModule) {
            List<File> consumerProguardFiles = variantScope.getConsumerProguardFilesForFeatures();

            ExportConsumerProguardFilesTask.checkProguardFiles(
                    project,
                    isDynamicFeature,
                    consumerProguardFiles,
                    errorMessage -> syncIssueReporter.reportError(Type.GENERIC, errorMessage));
        }
    }

    @NonNull
    private Collection<TestedTargetVariant> getTestTargetVariants(BaseVariantData variantData) {
        if (extension instanceof TestAndroidConfig) {
            TestAndroidConfig testConfig = (TestAndroidConfig) extension;

            // to get the target variant we need to get the result of the dependency resolution
            ArtifactCollection apkArtifacts =
                    variantData
                            .getScope()
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
                VariantScope variantScope = variantData.getScope();

                // probably there was an error...
                new DependencyFailureHandler()
                        .addErrors(
                                variantScope.getGlobalScope().getProject().getPath()
                                        + "@"
                                        + variantScope.getName()
                                        + "/testTarget",
                                apkArtifacts.getFailures())
                        .registerIssues(syncIssueReporter);
            }
        }

        return ImmutableList.of();
    }

    private JavaArtifactImpl createUnitTestsJavaArtifact(
            @NonNull VariantType variantType, @NonNull BaseVariantData variantData) {
        SourceProviders sourceProviders = determineSourceProviders(variantData);

        final VariantScope scope = variantData.getScope();
        Pair<Dependencies, DependencyGraphs> result =
                getDependencies(
                        scope,
                        buildMapping,
                        modelLevel,
                        modelWithFullDependency);

        Set<File> additionalTestClasses = new HashSet<>();
        additionalTestClasses.addAll(variantData.getAllPreJavacGeneratedBytecode().getFiles());
        additionalTestClasses.addAll(variantData.getAllPostJavacGeneratedBytecode().getFiles());
        if (scope.getArtifacts()
                .hasFinalProduct(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY.INSTANCE)) {
            additionalTestClasses.add(
                    scope.getArtifacts()
                            .getFinalProduct(
                                    InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY.INSTANCE)
                            .get()
                            .getAsFile());
        }
        // The separately compile R class, if applicable.
        if (!globalScope.getExtension().getAaptOptions().getNamespaced()
                && !globalScope.getProjectOptions().get(BooleanOption.GENERATE_R_JAVA)) {
            additionalTestClasses.add(scope.getRJarForUnitTests().get().getAsFile());
        }

        // No files are possible if the SDK was not configured properly.
        File mockableJar =
                globalScope.getMockableJarArtifact().getFiles().stream().findFirst().orElse(null);

        return new JavaArtifactImpl(
                variantType.getArtifactName(),
                scope.getTaskContainer().getAssembleTask().getName(),
                scope.getTaskContainer().getCompileTask().getName(),
                Sets.newHashSet(taskManager.createMockableJar.getName()),
                getGeneratedSourceFoldersForUnitTests(variantData),
                scope.getArtifacts().getFinalProduct(JAVAC.INSTANCE).get().getAsFile(),
                additionalTestClasses,
                variantData.getJavaResourcesForUnitTesting(),
                mockableJar,
                result.getFirst(),
                result.getSecond(),
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider);
    }

    /** Gather the dependency graph for the specified <code>variantScope</code>. */
    @NonNull
    private Pair<Dependencies, DependencyGraphs> getDependencies(
            @NonNull VariantScope variantScope,
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
                                        variantScope,
                                        modelWithFullDependency,
                                        buildMapping,
                                        syncIssueReporter));
            } else {
                result =
                        Pair.of(
                                graphBuilder.createDependencies(
                                        variantScope, buildMapping, syncIssueReporter),
                                EmptyDependencyGraphs.EMPTY);
            }
        }

        return result;
    }

    private AndroidArtifact createAndroidArtifact(
            @NonNull String name, @NonNull BaseVariantData variantData) {
        VariantScope scope = variantData.getScope();
        VariantDslInfo variantDslInfo = variantData.getVariantDslInfo();

        SigningConfig signingConfig = variantDslInfo.getSigningConfig();
        String signingConfigName = null;
        if (signingConfig != null) {
            signingConfigName = signingConfig.getName();
        }

        SourceProviders sourceProviders = determineSourceProviders(variantData);

        // get the outputs
        BuildOutputSupplier<Collection<EarlySyncBuildOutput>> splitOutputsProxy =
                getBuildOutputSupplier(variantData);
        BuildOutputSupplier<Collection<EarlySyncBuildOutput>> manifestsProxy =
                getManifestsSupplier(variantData);

        InstantRunImpl instantRun =
                new InstantRunImpl(
                        scope.getGlobalScope().getProject().file("build_info_removed"),
                        InstantRun.STATUS_REMOVED);

        Pair<Dependencies, DependencyGraphs> dependencies =
                getDependencies(
                        scope,
                        buildMapping,
                        modelLevel,
                        modelWithFullDependency);

        Set<File> additionalClasses = new HashSet<>();
        additionalClasses.addAll(variantData.getAllPreJavacGeneratedBytecode().getFiles());
        additionalClasses.addAll(variantData.getAllPostJavacGeneratedBytecode().getFiles());
        additionalClasses.addAll(
                variantData
                        .getScope()
                        .getCompiledRClasses(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH)
                        .getFiles());

        List<File> additionalRuntimeApks = new ArrayList<>();
        TestOptionsImpl testOptions = null;

        if (variantData.getType().isTestComponent()) {
            Configuration testHelpers =
                    scope.getGlobalScope()
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

            TestOptions testOptionsDsl = scope.getGlobalScope().getExtension().getTestOptions();
            testOptions =
                    new TestOptionsImpl(
                            testOptionsDsl.getAnimationsDisabled(),
                            testOptionsDsl.getExecutionEnum());
        }


        String applicationId;
        try {
            // This can throw an exception if no package name can be found.
            // Normally, this is fine to throw an exception, but we don't want to crash in sync.
            applicationId = variantDslInfo.getApplicationId();
        } catch (RuntimeException e) {
            // don't crash. just throw a sync error.
            applicationId = "";
            syncIssueReporter.reportError(Type.GENERIC, e);
        }
        final MutableTaskContainer taskContainer = scope.getTaskContainer();

        return new AndroidArtifactImpl(
                name,
                scope.getGlobalScope().getProjectBaseName() + "-" + variantDslInfo.getBaseName(),
                taskContainer.getAssembleTask().getName(),
                scope.getArtifacts()
                        .getOperations()
                        .get(InternalArtifactType.APK_IDE_MODEL.INSTANCE)
                        .getOrNull(),
                variantDslInfo.isSigningReady() || variantData.outputsAreSigned,
                signingConfigName,
                applicationId,
                taskContainer.getSourceGenTask().getName(),
                taskContainer.getCompileTask().getName(),
                getGeneratedSourceFolders(variantData),
                getGeneratedResourceFolders(variantData),
                scope.getArtifacts().getFinalProduct(JAVAC.INSTANCE).get().getAsFile(),
                additionalClasses,
                scope.getVariantData().getJavaResourcesForUnitTesting(),
                dependencies.getFirst(),
                dependencies.getSecond(),
                additionalRuntimeApks,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider,
                variantDslInfo.getSupportedAbis(),
                variantDslInfo.getMergedBuildConfigFields(),
                variantDslInfo.getMergedResValues(),
                instantRun,
                splitOutputsProxy,
                manifestsProxy,
                testOptions,
                taskContainer.getConnectedTask() == null
                        ? null
                        : taskContainer.getConnectedTask().getName(),
                taskContainer.getBundleTask() == null
                        ? scope.getTaskName("bundle")
                        : taskContainer.getBundleTask().getName(),
                scope.getArtifacts()
                        .getOperations()
                        .get(InternalArtifactType.BUNDLE_IDE_MODEL.INSTANCE)
                        .getOrNull(),
                ExtractApksTask.Companion.getTaskName(scope),
                scope.getArtifacts()
                        .getOperations()
                        .get(InternalArtifactType.APK_FROM_BUNDLE_IDE_MODEL.INSTANCE)
                        .getOrNull(),
                scope.getCodeShrinker());
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

    private BuildOutputSupplier<Collection<EarlySyncBuildOutput>> getBuildOutputSupplier(
            BaseVariantData variantData) {
        final VariantScope variantScope = variantData.getScope();

        VariantTypeImpl variantType = (VariantTypeImpl) variantData.getType();

        switch (variantType) {
            case BASE_APK:
            case OPTIONAL_APK:
            case TEST_APK:
            case ANDROID_TEST:
                return new BuildOutputsSupplier(
                        BuiltArtifacts.METADATA_FILE_VERSION,
                        ImmutableList.of(PublicArtifactType.APK.INSTANCE),
                        ImmutableList.of(variantScope.getApkLocation()));
            case LIBRARY:
                return BuildOutputSupplier.of(
                        ImmutableList.of(
                                new EarlySyncBuildOutput(
                                        InternalArtifactType.AAR.INSTANCE,
                                        VariantOutput.OutputType.MAIN,
                                        ImmutableList.of(),
                                        0,
                                        variantScope
                                                .getArtifacts()
                                                .getFinalProduct(InternalArtifactType.AAR.INSTANCE)
                                                .get()
                                                .getAsFile())));
            case UNIT_TEST:
                return (BuildOutputSupplier<Collection<EarlySyncBuildOutput>>)
                        () -> {
                            final BaseVariantData testedVariantData =
                                    variantScope.getTestedVariantData();
                            //noinspection ConstantConditions
                            final VariantScope testedVariantScope = testedVariantData.getScope();

                            PublishingSpecs.VariantSpec testedSpec =
                                    testedVariantScope
                                            .getPublishingSpec()
                                            .getTestingSpec(
                                                    variantScope
                                                            .getVariantDslInfo()
                                                            .getVariantType());

                            // get the OutputPublishingSpec from the ArtifactType for this
                            // particular variant spec
                            PublishingSpecs.OutputSpec taskOutputSpec =
                                    testedSpec.getSpec(
                                            AndroidArtifacts.ArtifactType.CLASSES_JAR,
                                            AndroidArtifacts.PublishedConfigType.API_ELEMENTS);
                            // now get the output type
                            SingleArtifactType<? extends FileSystemLocation> testedOutputType =
                                    taskOutputSpec.getOutputType();

                            return ImmutableList.of(
                                    new EarlySyncBuildOutput(
                                            JAVAC.INSTANCE,
                                            VariantOutput.OutputType.MAIN,
                                            ImmutableList.of(),
                                            variantData.getVariantDslInfo().getVersionCode(),
                                            variantScope
                                                    .getArtifacts()
                                                    .getFinalProductAsFileCollection(
                                                            testedOutputType)
                                                    // We used to call .getSingleFile() but Kotlin
                                                    // projects currently have 2 output dirs
                                                    // specified for test classes. This supplier is
                                                    // going away in beta3, so this is obsolete in
                                                    // any case.
                                                    .get()
                                                    .iterator()
                                                    .next()));
                        };
            default:
                throw new RuntimeException("Unhandled build type " + variantData.getType());
        }
    }

    // is it still used by IDE ? at this point, it becomes impossible to set this up accurately.
    private BuildOutputSupplier<Collection<EarlySyncBuildOutput>> getManifestsSupplier(
            BaseVariantData variantData) {

        VariantTypeImpl variantType = (VariantTypeImpl) variantData.getType();

        switch (variantType) {
            case BASE_APK:
            case OPTIONAL_APK:
            case ANDROID_TEST:
            case TEST_APK:
                return new BuildOutputsSupplier(
                        BuildElements.METADATA_FILE_VERSION,
                        ImmutableList.of(InternalArtifactType.MERGED_MANIFESTS.INSTANCE),
                        ImmutableList.of(variantData.getScope().getManifestOutputDirectory()));
            case LIBRARY:
                return BuildOutputSupplier.of(
                        ImmutableList.of(
                                new EarlySyncBuildOutput(
                                        InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                                        VariantOutput.OutputType.MAIN,
                                        ImmutableList.of(),
                                        0,
                                        new File(
                                                variantData.getScope().getManifestOutputDirectory(),
                                                SdkConstants.ANDROID_MANIFEST_XML))));
            default:
                throw new RuntimeException("Unhandled build type " + variantData.getType());
        }
    }

    private static SourceProviders determineSourceProviders(@NonNull BaseVariantData variantData) {
        SourceProvider variantSourceProvider =
                variantData.getVariantSources().getVariantSourceProvider();
        SourceProvider multiFlavorSourceProvider =
                variantData.getVariantSources().getMultiFlavorSourceProvider();

        return new SourceProviders(
                variantSourceProvider != null ?
                        new SourceProviderImpl(variantSourceProvider) :
                        null,
                multiFlavorSourceProvider != null ?
                        new SourceProviderImpl(multiFlavorSourceProvider) :
                        null);
    }

    @NonNull
    private static List<String> getProductFlavorNames(@NonNull BaseVariantData variantData) {
        return variantData
                .getVariantDslInfo()
                .getProductFlavorList()
                .stream()
                .map((Function<ProductFlavor, String>) ProductFlavor::getName)
                .collect(Collectors.toList());
    }

    @NonNull
    private static List<File> getGeneratedSourceFoldersForUnitTests(
            @Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }

        List<File> folders = Lists.newArrayList(variantData.getExtraGeneratedSourceFolders());
        folders.add(
                variantData
                        .getScope()
                        .getArtifacts()
                        .getFinalProduct(InternalArtifactType.AP_GENERATED_SOURCES.INSTANCE)
                        .get()
                        .getAsFile());
        return folders;
    }

    @NonNull
    private static List<File> getGeneratedSourceFolders(@Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }
        VariantScope scope = variantData.getScope();
        BuildArtifactsHolder artifacts = scope.getArtifacts();
        GlobalScope globalScope = variantData.getScope().getGlobalScope();

        boolean isDataBindingEnabled = globalScope.getBuildFeatures().getDataBinding();
        boolean isViewBindingEnabled = globalScope.getBuildFeatures().getViewBinding();
        Directory dataBindingSources =
                scope.getArtifacts()
                        .getFinalProduct(DATA_BINDING_BASE_CLASS_SOURCE_OUT.INSTANCE)
                        .getOrNull();
        boolean addBindingSources =
                (isDataBindingEnabled || isViewBindingEnabled) && (dataBindingSources != null);
        List<File> extraFolders = getGeneratedSourceFoldersForUnitTests(variantData);

        // Set this to the number of folders you expect to add explicitly in the code below.
        int additionalFolders = 4;
        if (addBindingSources) {
            additionalFolders += 1;
        }
        List<File> folders =
                Lists.newArrayListWithExpectedSize(additionalFolders + extraFolders.size());
        folders.addAll(extraFolders);

        Directory aidlSources =
                scope.getArtifacts()
                        .getFinalProduct(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR.INSTANCE)
                        .getOrNull();
        if (aidlSources != null) {
            folders.add(aidlSources.getAsFile());
        }
        folders.add(scope.getBuildConfigSourceOutputDir());
        boolean ndkMode = variantData.getVariantDslInfo().getRenderscriptNdkModeEnabled();
        if (!ndkMode) {
            Directory renderscriptSources =
                    scope.getArtifacts()
                            .getFinalProduct(
                                    InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR.INSTANCE)
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
    private static List<File> getGeneratedResourceFolders(@Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }

        List<File> result;

        final FileCollection extraResFolders = variantData.getExtraGeneratedResFolders();
        Set<File> extraFolders = extraResFolders != null ? extraResFolders.getFiles() : null;
        if (extraFolders != null && !extraFolders.isEmpty()) {
            result = Lists.newArrayListWithCapacity(extraFolders.size() + 2);
            result.addAll(extraFolders);
        } else {
            result = Lists.newArrayListWithCapacity(2);
        }

        VariantScope scope = variantData.getScope();

        result.add(scope.getRenderscriptResOutputDir());

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

}
