/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;
import static com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_API_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_RUNTIME_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_PUBLICATION;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.dsl.PrefabPackagingOptions;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.impl.LibraryVariantImpl;
import com.android.build.api.variant.impl.LibraryVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.internal.cxx.gradle.generator.ExternalNativeJsonGenerator;
import com.android.build.gradle.internal.dependency.ConfigurationVariantMapping;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.AarMetadataTask;
import com.android.build.gradle.internal.tasks.BundleLibraryClassesDir;
import com.android.build.gradle.internal.tasks.BundleLibraryClassesJar;
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.LibraryAarJarsTask;
import com.android.build.gradle.internal.tasks.LibraryJniLibsTask;
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction;
import com.android.build.gradle.internal.tasks.PackageRenderscriptTask;
import com.android.build.gradle.internal.tasks.PrefabModuleTaskData;
import com.android.build.gradle.internal.tasks.PrefabPackageTask;
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.BundleAar;
import com.android.build.gradle.tasks.CompileLibraryResourcesTask;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessLibraryManifest;
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask;
import com.android.build.gradle.tasks.ZipMergingTask;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/** TaskManager for creating tasks in an Android library project. */
public class LibraryTaskManager
        extends TaskManager<LibraryVariantImpl, LibraryVariantPropertiesImpl> {

    public LibraryTaskManager(
            @NonNull List<ComponentInfo<LibraryVariantImpl, LibraryVariantPropertiesImpl>> variants,
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
        super(variants, testComponents, hasFlavors, globalScope, extension, recorder);
    }

    @Override
    protected void doCreateTasksForVariant(
            @NonNull ComponentInfo<LibraryVariantImpl, LibraryVariantPropertiesImpl> variant,
            @NonNull
                    List<ComponentInfo<LibraryVariantImpl, LibraryVariantPropertiesImpl>>
                            allVariants) {

        LibraryVariantPropertiesImpl libVariantProperties = variant.getProperties();
        BuildFeatureValues buildFeatures = libVariantProperties.getBuildFeatures();

        createAnchorTasks(libVariantProperties);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(libVariantProperties));

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(libVariantProperties);

        if (buildFeatures.getAndroidResources()) {
            createGenerateResValuesTask(libVariantProperties);
        } else { // Resource processing is disabled.
            // TODO(b/147579629): add a warning for manifests containing resource references.
            if (globalScope.getExtension().getAaptOptions().getNamespaced()) {
                getLogger()
                        .error(
                                "Disabling resource processing in resource namespace aware "
                                        + "modules is not supported currently.");
            }

            // Create a task to generate empty/mock required resource artifacts.
            taskFactory.register(
                    new GenerateEmptyResourceFilesTask.CreateAction(libVariantProperties));
        }

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(libVariantProperties));

        taskFactory.register(new ProcessLibraryManifest.CreationAction(libVariantProperties));

        createRenderscriptTask(libVariantProperties);

        if (buildFeatures.getAndroidResources()) {
            createMergeResourcesTasks(libVariantProperties);

            createCompileLibraryResourcesTask(libVariantProperties);
        }

        createShaderTask(libVariantProperties);

        // Add tasks to merge the assets folders
        createMergeAssetsTask(libVariantProperties);
        createLibraryAssetsTask(libVariantProperties);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(libVariantProperties);

        if (buildFeatures.getAndroidResources()) {
            // Add a task to generate resource source files, directing the location
            // of the r.txt file to be directly in the bundle.
            createProcessResTask(
                    libVariantProperties,
                    null,
                    // Switch to package where possible so we stop merging resources in
                    // libraries
                    MergeType.PACKAGE,
                    globalScope.getProjectBaseName());

            // Only verify resources if in Release and not namespaced.
            if (!libVariantProperties.getVariantDslInfo().isDebuggable()
                    && !globalScope.getExtension().getAaptOptions().getNamespaced()) {
                createVerifyLibraryResTask(libVariantProperties);
            }

            registerLibraryRClassTransformStream(libVariantProperties);
        }

        // process java resources only, the merge is setup after
        // the task to generate intermediate jars for project to project publishing.
        createProcessJavaResTask(libVariantProperties);

        createAidlTask(libVariantProperties);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(libVariantProperties);

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(libVariantProperties);

        // Add a compile task
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(libVariantProperties);
        addJavacClassesStream(libVariantProperties);
        TaskManager.setJavaCompilerTask(javacTask, libVariantProperties);

        taskFactory.register(new MergeGeneratedProguardFilesCreationAction(libVariantProperties));

        // External native build
        createExternalNativeBuildJsonGenerators(libVariantProperties);
        createExternalNativeBuildTasks(libVariantProperties);

        createMergeJniLibFoldersTasks(libVariantProperties);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(libVariantProperties));

        taskFactory.register(new PackageRenderscriptTask.CreationAction(libVariantProperties));

        // merge consumer proguard files from different build types and flavors
        taskFactory.register(
                new MergeConsumerProguardFilesTask.CreationAction(libVariantProperties));

        taskFactory.register(
                new ExportConsumerProguardFilesTask.CreationAction(libVariantProperties));

        createPrefabTasks(libVariantProperties);

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (libVariantProperties
                .getServices()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.register(new ExtractAnnotations.CreationAction(libVariantProperties));
        }

        final boolean instrumented =
                libVariantProperties.getVariantDslInfo().isTestCoverageEnabled();

        TransformManager transformManager = libVariantProperties.getTransformManager();

        // ----- Code Coverage first -----
        if (instrumented) {
            createJacocoTask(libVariantProperties);
        }

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        final IssueReporter issueReporter = libVariantProperties.getServices().getIssueReporter();

        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            // Check the transform only applies to supported scopes for libraries:
            // We cannot transform scopes that are not packaged in the library
            // itself.
            Sets.SetView<? super Scope> difference =
                    Sets.difference(transform.getScopes(), TransformManager.PROJECT_ONLY);
            if (!difference.isEmpty()) {
                String scopes = difference.toString();
                issueReporter.reportError(
                        Type.GENERIC,
                        String.format(
                                "Transforms with scopes '%s' cannot be applied to library projects.",
                                scopes));
            }

            List<Object> deps = customTransformsDependencies.get(i);
            transformManager.addTransform(
                    taskFactory,
                    libVariantProperties,
                    transform,
                    null,
                    task -> {
                        if (!deps.isEmpty()) {
                            task.dependsOn(deps);
                        }
                    },
                    taskProvider -> {
                        // if the task is a no-op then we make assemble task
                        // depend on it.
                        if (transform.getScopes().isEmpty()) {
                            TaskFactoryUtils.dependsOn(
                                    libVariantProperties.getTaskContainer().getAssembleTask(),
                                    taskProvider);
                        }
                    });
        }

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
                new BundleLibraryClassesJar.CreationAction(
                        libVariantProperties,
                        AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS));

        // Also create a directory containing the same classes for incremental dexing
        taskFactory.register(new BundleLibraryClassesDir.CreationAction(libVariantProperties));

        taskFactory.register(new BundleLibraryJavaRes.CreationAction(libVariantProperties));

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(new ZipMergingTask.CreationAction(libVariantProperties));

        // now add a task that will take all the native libs and package
        // them into an intermediary folder. This processes only the PROJECT
        // scope.
        taskFactory.register(
                new LibraryJniLibsTask.ProjectOnlyCreationAction(
                        libVariantProperties, InternalArtifactType.LIBRARY_JNI.INSTANCE));

        // Now go back to fill the pipeline with transforms used when
        // publishing the AAR

        // first merge the java resources.
        createMergeJavaResTask(libVariantProperties);

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTask(libVariantProperties);

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.

        taskFactory.register(new LibraryAarJarsTask.CreationAction(libVariantProperties));

        // now add a task that will take all the native libs and package
        // them into the libs folder of the bundle. This processes both the PROJECT
        // and the LOCAL_PROJECT scopes
        taskFactory.register(
                new LibraryJniLibsTask.ProjectAndLocalJarsCreationAction(
                        libVariantProperties,
                        InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI.INSTANCE));

        // Add a task to create the AAR metadata file
        taskFactory.register(new AarMetadataTask.CreationAction(libVariantProperties));

        createLintTasks(libVariantProperties, allVariants);
        createBundleTask(libVariantProperties);
    }

    private void registerLibraryRClassTransformStream(
            @NonNull VariantPropertiesImpl variantProperties) {

        if (!variantProperties.getBuildFeatures().getAndroidResources()) {
            return;
        }

        QualifiedContent.ScopeType scopeType;
        if (variantProperties.getVariantScope().getCodeShrinker() != null) {
            // Add the R class classes as production classes. They are then removed by the library
            // jar transform.
            // TODO(b/115974418): Can we stop adding the compilation-only R class as a local classes?
            scopeType = Scope.PROJECT;
        } else {
            scopeType = Scope.PROVIDED_ONLY;
        }

        FileCollection compileRClass =
                project.files(
                        variantProperties
                                .getArtifacts()
                                .get(InternalArtifactType.COMPILE_R_CLASS_JAR.INSTANCE));
        variantProperties
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "compile-only-r-class")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(scopeType)
                                .setFileCollection(compileRClass)
                                .build());
    }

    private void createBundleTask(@NonNull VariantPropertiesImpl variantProperties) {
        TaskProvider<BundleAar> bundle =
                taskFactory.register(new BundleAar.CreationAction(variantProperties));

        TaskFactoryUtils.dependsOn(variantProperties.getTaskContainer().getAssembleTask(), bundle);

        final VariantDependencies variantDependencies = variantProperties.getVariantDependencies();

        AdhocComponentWithVariants component =
                globalScope.getComponentFactory().adhoc(variantProperties.getName());

        final Configuration apiPub = variantDependencies.getElements(API_PUBLICATION);
        final Configuration runtimePub = variantDependencies.getElements(RUNTIME_PUBLICATION);

        component.addVariantsFromConfiguration(
                apiPub, new ConfigurationVariantMapping("compile", false));
        component.addVariantsFromConfiguration(
                runtimePub, new ConfigurationVariantMapping("runtime", false));
        project.getComponents().add(component);

        AdhocComponentWithVariants allVariants =
                (AdhocComponentWithVariants) project.getComponents().findByName("all");
        if (allVariants == null) {
            allVariants = globalScope.getComponentFactory().adhoc("all");
            project.getComponents().add(allVariants);
        }
        final Configuration allApiPub = variantDependencies.getElements(ALL_API_PUBLICATION);
        allVariants.addVariantsFromConfiguration(
                allApiPub, new ConfigurationVariantMapping("compile", true));
        final Configuration allRuntimePub =
                variantDependencies.getElements(ALL_RUNTIME_PUBLICATION);
        allVariants.addVariantsFromConfiguration(
                allRuntimePub, new ConfigurationVariantMapping("runtime", true));

        // Old style publishing. This is likely to go away at some point.
        if (extension.getDefaultPublishConfig().equals(variantProperties.getName())) {
            VariantHelper.setupArchivesConfig(project, variantDependencies.getRuntimeClasspath());

            // add the artifact that will be published.
            // it must be default so that it can be found by other library modules during
            // publishing to a maven repo. Adding it to "archives" only allows the current
            // module to be published by not to be found by consumer who are themselves published
            // (leading to their pom not containing dependencies).
            project.getArtifacts().add("default", bundle);
        }
    }

    @Override
    protected void createDependencyStreams(@NonNull ComponentPropertiesImpl componentProperties) {
        super.createDependencyStreams(componentProperties);

        // add the same jars twice in the same stream as the EXTERNAL_LIB in the task manager
        // so that filtering of duplicates in proguard can work.
        componentProperties
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(
                                        componentProperties
                                                .getVariantScope()
                                                .getLocalPackagedJars())
                                .build());
    }

    private static class MergeResourceCallback implements TaskProviderCallback<MergeResources> {
        @NonNull private final VariantPropertiesImpl variantProperties;

        private MergeResourceCallback(@NonNull VariantPropertiesImpl variantProperties) {
            this.variantProperties = variantProperties;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<MergeResources> taskProvider) {
            variantProperties
                    .getArtifacts()
                    .setInitialProvider(taskProvider, MergeResources::getPublicFile)
                    .withName(FN_PUBLIC_TXT)
                    .on(InternalArtifactType.PUBLIC_RES.INSTANCE);
        }
    }

    private void createMergeResourcesTasks(@NonNull VariantPropertiesImpl variantProperties) {
        ImmutableSet<MergeResources.Flag> flags;
        if (variantProperties.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            flags =
                    Sets.immutableEnumSet(
                            MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                            MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        } else {
            flags = Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        }

        MergeResourceCallback callback = new MergeResourceCallback(variantProperties);

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        basicCreateMergeResourcesTask(
                variantProperties,
                MergeType.PACKAGE,
                variantProperties
                        .getPaths()
                        .getIntermediateDir(InternalArtifactType.PACKAGED_RES.INSTANCE),
                false,
                false,
                false,
                flags,
                callback);

        // This task merges all the resources, including the dependencies of this library.
        // This should be unused, except that external libraries might consume it.
        // Also used by the VerifyLibraryResourcesTask (only ran in release builds).
        createMergeResourcesTask(variantProperties, false /*processResources*/, ImmutableSet.of());
    }

    private void createCompileLibraryResourcesTask(
            @NonNull VariantPropertiesImpl variantProperties) {
        if (variantProperties.getVariantScope().isPrecompileDependenciesResourcesEnabled()) {
            taskFactory.register(new CompileLibraryResourcesTask.CreationAction(variantProperties));
        }
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentPropertiesImpl componentProperties) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                componentProperties
                        .getServices()
                        .fileCollection(
                                componentProperties.getArtifacts().get(JAVAC.INSTANCE),
                                componentProperties
                                        .getVariantData()
                                        .getAllPreJavacGeneratedBytecode(),
                                componentProperties
                                        .getVariantData()
                                        .getAllPostJavacGeneratedBytecode());
        componentProperties.getArtifacts().appendToAllClasses(files);

        // Create jar used for publishing to API elements (for other projects to compile against).
        taskFactory.register(
                new BundleLibraryClassesJar.CreationAction(
                        componentProperties, AndroidArtifacts.PublishedConfigType.API_ELEMENTS));
    }

    public void createLibraryAssetsTask(@NonNull VariantPropertiesImpl variantProperties) {
        taskFactory.register(
                new MergeSourceSetFolders.LibraryAssetCreationAction(variantProperties));
    }

    public void createPrefabTasks(@NonNull LibraryVariantPropertiesImpl variantProperties) {
        if (!variantProperties.getBuildFeatures().getPrefabPublishing()) {
            return;
        }

        Provider<ExternalNativeJsonGenerator> generator =
                variantProperties.getTaskContainer().getExternalNativeJsonGenerator();
        if (generator == null) {
            // No external native build, so definitely no prefab tasks.
            return;
        }

        LibraryExtension extension = (LibraryExtension) globalScope.getExtension();
        List<PrefabModuleTaskData> modules = Lists.newArrayList();
        for (PrefabPackagingOptions options : extension.getPrefab()) {
            String name = options.getName();
            if (name == null) {
                throw new InvalidUserDataException("prefab modules must specify a name");
            }
            File headers = null;
            if (options.getHeaders() != null) {
                headers =
                        project.getLayout()
                                .getProjectDirectory()
                                .dir(options.getHeaders())
                                .getAsFile();
            }
            modules.add(new PrefabModuleTaskData(name, headers, options.getLibraryName()));
        }

        if (!modules.isEmpty()) {
            TaskProvider<PrefabPackageTask> packageTask =
                    taskFactory.register(
                            new PrefabPackageTask.CreationAction(
                                    modules,
                                    generator.get().getVariant(),
                                    generator.get().getAbis(),
                                    variantProperties));
            packageTask
                    .get()
                    .dependsOn(variantProperties.getTaskContainer().getExternalNativeBuildTask());
        }
    }

    @NonNull
    @Override
    protected Set<ScopeType> getJavaResMergingScopes(
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull QualifiedContent.ContentType contentType) {
        Preconditions.checkArgument(
                contentType == RESOURCES || contentType == NATIVE_LIBS,
                "contentType must be RESOURCES or NATIVE_LIBS");
        if (componentProperties.getVariantType().isTestComponent()) {
            if (contentType == RESOURCES) {
                return TransformManager.SCOPE_FULL_PROJECT_WITH_LOCAL_JARS;
            }
            // contentType is NATIVE_LIBS
            return TransformManager.SCOPE_FULL_PROJECT;
        }
        if (contentType == RESOURCES) {
            return TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS;
        }
        // contentType is NATIVE_LIBS
        return TransformManager.PROJECT_ONLY;
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    public void createVerifyLibraryResTask(@NonNull VariantPropertiesImpl variantProperties) {
        TaskProvider<VerifyLibraryResourcesTask> verifyLibraryResources =
                taskFactory.register(
                        new VerifyLibraryResourcesTask.CreationAction(variantProperties));

        TaskFactoryUtils.dependsOn(
                variantProperties.getTaskContainer().getAssembleTask(), verifyLibraryResources);
    }

    @Override
    protected void configureGlobalLintTask() {
        super.configureGlobalLintTask();

        // publish the local lint.jar to all the variants.
        // This takes the global jar (output of PrepareLintJar) and publishes to each variants
        // as we don't have variant-free publishing at the moment.
        for (LibraryVariantPropertiesImpl variant : variantPropertiesList) {
            variant.getArtifacts()
                    .copy(
                            InternalArtifactType.LINT_PUBLISH_JAR.INSTANCE,
                            globalScope.getGlobalArtifacts());
        }
    }
}
