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
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_API_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_RUNTIME_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_PUBLICATION;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.api.component.impl.TestComponentBuilderImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.dsl.PrefabPackagingOptions;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.impl.LibraryVariantBuilderImpl;
import com.android.build.api.variant.impl.LibraryVariantImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel;
import com.android.build.gradle.internal.dependency.ConfigurationVariantMapping;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.res.GenerateApiPublicTxtTask;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.NotNull;

/** TaskManager for creating tasks in an Android library project. */
public class LibraryTaskManager extends TaskManager<LibraryVariantBuilderImpl, LibraryVariantImpl> {

    public LibraryTaskManager(
            @NonNull List<ComponentInfo<LibraryVariantBuilderImpl, LibraryVariantImpl>> variants,
            @NonNull
                    List<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension) {
        super(variants, testComponents, hasFlavors, globalScope, extension);
    }

    @Override
    protected void doCreateTasksForVariant(
            @NotNull ComponentInfo<LibraryVariantBuilderImpl, LibraryVariantImpl> variantInfo,
            @NotNull
                    List<? extends ComponentInfo<LibraryVariantBuilderImpl, LibraryVariantImpl>>
                            allVariants) {

        LibraryVariantImpl libraryVariant = variantInfo.getVariant();
        BuildFeatureValues buildFeatures = libraryVariant.getBuildFeatures();

        createAnchorTasks(libraryVariant);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(libraryVariant));

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(libraryVariant);

        if (buildFeatures.getAndroidResources()) {
            createGenerateResValuesTask(libraryVariant);
        } else { // Resource processing is disabled.
            // TODO(b/147579629): add a warning for manifests containing resource references.
            if (globalScope.getExtension().getAaptOptions().getNamespaced()) {
                getLogger()
                        .error(
                                "Disabling resource processing in resource namespace aware "
                                        + "modules is not supported currently.");
            }

            // Create a task to generate empty/mock required resource artifacts.
            taskFactory.register(new GenerateEmptyResourceFilesTask.CreateAction(libraryVariant));
        }

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(libraryVariant));

        taskFactory.register(new ProcessLibraryManifest.CreationAction(libraryVariant));

        createRenderscriptTask(libraryVariant);

        if (buildFeatures.getAndroidResources()) {
            createMergeResourcesTasks(libraryVariant);

            createCompileLibraryResourcesTask(libraryVariant);
        }

        createShaderTask(libraryVariant);

        // Add tasks to merge the assets folders
        createMergeAssetsTask(libraryVariant);
        createLibraryAssetsTask(libraryVariant);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(libraryVariant);

        if (buildFeatures.getAndroidResources()) {
            // Add a task to generate resource source files, directing the location
            // of the r.txt file to be directly in the bundle.
            createProcessResTask(
                    libraryVariant,
                    null,
                    // Switch to package where possible so we stop merging resources in
                    // libraries
                    MergeType.PACKAGE,
                    globalScope.getProjectBaseName());

            // Only verify resources if in Release and not namespaced.
            if (!libraryVariant.getDebuggable()
                    && !globalScope.getExtension().getAaptOptions().getNamespaced()) {
                createVerifyLibraryResTask(libraryVariant);
            }

            registerLibraryRClassTransformStream(libraryVariant);
        }

        // process java resources only, the merge is setup after
        // the task to generate intermediate jars for project to project publishing.
        createProcessJavaResTask(libraryVariant);

        createAidlTask(libraryVariant);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(libraryVariant);

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(libraryVariant);

        // Add a compile task
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(libraryVariant);
        addJavacClassesStream(libraryVariant);
        TaskManager.setJavaCompilerTask(javacTask, libraryVariant);

        taskFactory.register(new MergeGeneratedProguardFilesCreationAction(libraryVariant));

        createMergeJniLibFoldersTasks(libraryVariant);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(libraryVariant));

        taskFactory.register(new PackageRenderscriptTask.CreationAction(libraryVariant));

        // merge consumer proguard files from different build types and flavors
        taskFactory.register(new MergeConsumerProguardFilesTask.CreationAction(libraryVariant));

        taskFactory.register(new ExportConsumerProguardFilesTask.CreationAction(libraryVariant));

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (libraryVariant
                .getServices()
                .getProjectOptions()
                .get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.register(new ExtractAnnotations.CreationAction(libraryVariant));
        }

        final boolean instrumented = libraryVariant.getVariantDslInfo().isTestCoverageEnabled();

        TransformManager transformManager = libraryVariant.getTransformManager();

        // ----- Code Coverage first -----
        if (instrumented) {
            createJacocoTask(libraryVariant);
        }

        maybeCreateTransformClassesWithAsmTask(libraryVariant, instrumented);

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        final IssueReporter issueReporter = libraryVariant.getServices().getIssueReporter();

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
                    libraryVariant,
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
                                    libraryVariant.getTaskContainer().getAssembleTask(),
                                    taskProvider);
                        }
                    });
        }

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
                new BundleLibraryClassesJar.CreationAction(
                        libraryVariant, AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS));

        // Also create a directory containing the same classes for incremental dexing
        taskFactory.register(new BundleLibraryClassesDir.CreationAction(libraryVariant));

        taskFactory.register(new BundleLibraryJavaRes.CreationAction(libraryVariant));

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(new ZipMergingTask.CreationAction(libraryVariant));

        // now add a task that will take all the native libs and package
        // them into an intermediary folder. This processes only the PROJECT
        // scope.
        taskFactory.register(
                new LibraryJniLibsTask.ProjectOnlyCreationAction(
                        libraryVariant, InternalArtifactType.LIBRARY_JNI.INSTANCE));

        // Now go back to fill the pipeline with transforms used when
        // publishing the AAR

        // first merge the java resources.
        createMergeJavaResTask(libraryVariant);

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTask(libraryVariant);

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.

        taskFactory.register(new LibraryAarJarsTask.CreationAction(libraryVariant));

        // now add a task that will take all the native libs and package
        // them into the libs folder of the bundle. This processes both the PROJECT
        // and the LOCAL_PROJECT scopes
        taskFactory.register(
                new LibraryJniLibsTask.ProjectAndLocalJarsCreationAction(
                        libraryVariant, InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI.INSTANCE));

        // Add a task to create the AAR metadata file
        taskFactory.register(new AarMetadataTask.CreationAction(libraryVariant));

        createLintTasks(libraryVariant, allVariants);
        createBundleTask(libraryVariant);
    }

    private void registerLibraryRClassTransformStream(@NonNull VariantImpl variant) {
        if (!variant.getBuildFeatures().getAndroidResources()) {
            return;
        }
        FileCollection compileRClass =
                project.files(
                        variant.getArtifacts()
                                .get(InternalArtifactType.COMPILE_R_CLASS_JAR.INSTANCE));
        variant.getTransformManager()
                .addStream(
                        OriginalStream.builder("compile-only-r-class")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(Scope.PROVIDED_ONLY)
                                .setFileCollection(compileRClass)
                                .build());
    }

    private void createBundleTask(@NonNull VariantImpl variant) {
        TaskProvider<BundleAar> bundle =
                taskFactory.register(new BundleAar.CreationAction(variant));

        variant.getTaskContainer()
                .getAssembleTask()
                .configure(
                        task -> {
                            task.dependsOn(variant.getArtifacts().get(ArtifactType.AAR.INSTANCE));
                        });

        final VariantDependencies variantDependencies = variant.getVariantDependencies();

        AdhocComponentWithVariants component =
                globalScope.getComponentFactory().adhoc(variant.getName());

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
        if (extension.getDefaultPublishConfig().equals(variant.getName())) {
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
    protected void createDependencyStreams(@NonNull ComponentCreationConfig creationConfig) {
        super.createDependencyStreams(creationConfig);

        // add the same jars twice in the same stream as the EXTERNAL_LIB in the task manager
        // so that filtering of duplicates in proguard can work.
        creationConfig
                .getTransformManager()
                .addStream(
                        OriginalStream.builder("local-deps-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(
                                        creationConfig.getVariantScope().getLocalPackagedJars())
                                .build());
    }

    private static class MergeResourceCallback implements TaskProviderCallback<MergeResources> {
        @NonNull private final VariantImpl variant;

        private MergeResourceCallback(@NonNull VariantImpl variant) {
            this.variant = variant;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<MergeResources> taskProvider) {
            variant.getArtifacts()
                    .setInitialProvider(taskProvider, MergeResources::getPublicFile)
                    .withName(FN_PUBLIC_TXT)
                    .on(InternalArtifactType.PUBLIC_RES.INSTANCE);
        }
    }

    private void createMergeResourcesTasks(@NonNull VariantImpl variant) {
        ImmutableSet<MergeResources.Flag> flags;
        if (variant.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            flags =
                    Sets.immutableEnumSet(
                            MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                            MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        } else {
            flags = Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        }

        MergeResourceCallback callback = new MergeResourceCallback(variant);

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        basicCreateMergeResourcesTask(
                variant,
                MergeType.PACKAGE,
                variant.getPaths().getIntermediateDir(InternalArtifactType.PACKAGED_RES.INSTANCE),
                false,
                false,
                false,
                flags,
                callback);

        // This task merges all the resources, including the dependencies of this library.
        // This should be unused, except that external libraries might consume it.
        // Also used by the VerifyLibraryResourcesTask (only ran in release builds).
        createMergeResourcesTask(variant, false /*processResources*/, ImmutableSet.of());

        // Task to generate the public.txt for the API that always exists
        // Unlike the internal one which is packaged in the AAR which only exists if the
        // developer has explicitly marked resources as public.
        taskFactory.register(new GenerateApiPublicTxtTask.CreationAction(variant));
    }

    private void createCompileLibraryResourcesTask(@NonNull VariantImpl variant) {
        if (variant.isPrecompileDependenciesResourcesEnabled()) {
            taskFactory.register(new CompileLibraryResourcesTask.CreationAction(variant));
        }
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentCreationConfig creationConfig) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                creationConfig
                        .getServices()
                        .fileCollection(
                                creationConfig.getArtifacts().get(JAVAC.INSTANCE),
                                creationConfig.getVariantData().getAllPreJavacGeneratedBytecode(),
                                creationConfig.getVariantData().getAllPostJavacGeneratedBytecode());
        creationConfig.getArtifacts().appendToAllClasses(files);

        // Create jar used for publishing to API elements (for other projects to compile against).
        taskFactory.register(
                new BundleLibraryClassesJar.CreationAction(
                        creationConfig, AndroidArtifacts.PublishedConfigType.API_ELEMENTS));
    }

    public void createLibraryAssetsTask(@NonNull VariantImpl variant) {
        taskFactory.register(new MergeSourceSetFolders.LibraryAssetCreationAction(variant));
    }

    @NonNull
    @Override
    protected Set<ScopeType> getJavaResMergingScopes(
            @NonNull ComponentCreationConfig creationConfig,
            @NonNull QualifiedContent.ContentType contentType) {
        Preconditions.checkArgument(contentType == RESOURCES, "contentType must be RESOURCES");
        if (creationConfig.getVariantType().isTestComponent()) {
            return TransformManager.SCOPE_FULL_PROJECT_WITH_LOCAL_JARS;
        }
        return TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS;
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    public void createVerifyLibraryResTask(@NonNull VariantImpl variant) {
        TaskProvider<VerifyLibraryResourcesTask> verifyLibraryResources =
                taskFactory.register(new VerifyLibraryResourcesTask.CreationAction(variant));

        variant.getTaskContainer()
                .getAssembleTask()
                .configure(
                        task -> {
                            task.dependsOn(
                                    variant.getArtifacts()
                                            .get(
                                                    InternalArtifactType.VERIFIED_LIBRARY_RESOURCES
                                                            .INSTANCE));
                        });
    }

    @Override
    protected void configureGlobalLintTask() {
        super.configureGlobalLintTask();

        // publish the local lint.jar to all the variants.
        // This takes the global jar (output of PrepareLintJar) and publishes to each variants
        // as we don't have variant-free publishing at the moment.
        for (LibraryVariantImpl variant : variantPropertiesList) {
            variant.getArtifacts()
                    .copy(
                            InternalArtifactType.LINT_PUBLISH_JAR.INSTANCE,
                            globalScope.getGlobalArtifacts());
        }
    }
}
