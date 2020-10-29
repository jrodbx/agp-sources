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

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.dependency.ConfigurationVariantMapping;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BundleLibraryClasses;
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.LibraryAarJarsTask;
import com.android.build.gradle.internal.tasks.LibraryDexingTask;
import com.android.build.gradle.internal.tasks.LibraryJniLibsTask;
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction;
import com.android.build.gradle.internal.tasks.PackageRenderscriptTask;
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.BundleAar;
import com.android.build.gradle.tasks.CompileLibraryResourcesTask;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask;
import com.android.build.gradle.tasks.ZipMergingTask;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android library project. */
public class LibraryTaskManager extends TaskManager {

    public LibraryTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                extension,
                variantFactory,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(
            @NonNull final VariantScope variantScope,
            @NonNull List<VariantScope> variantScopesForLint) {
        final VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();

        GlobalScope globalScope = variantScope.getGlobalScope();

        createAnchorTasks(variantScope);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(variantScope));

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        taskFactory.register(
                new BuildArtifactReportTask.BuildArtifactReportCreationAction(variantScope));

        createGenerateResValuesTask(variantScope);

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(variantScope));

        createMergeLibManifestsTask(variantScope);

        createRenderscriptTask(variantScope);

        createMergeResourcesTasks(variantScope);

        createCompileLibraryResourcesTask(variantScope);

        createShaderTask(variantScope);

        // Add tasks to merge the assets folders
        createMergeAssetsTask(variantScope);
        createLibraryAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        createProcessResTask(
                variantScope,
                null,
                // Switch to package where possible so we stop merging resources in
                // libraries
                MergeType.PACKAGE,
                globalScope.getProjectBaseName());

        // Only verify resources if in Release and not namespaced.
        if (!variantScope.getVariantDslInfo().isDebuggable()
                && !variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            createVerifyLibraryResTask(variantScope);
        }
        registerLibraryRClassTransformStream(variantScope);

        // process java resources only, the merge is setup after
        // the task to generate intermediate jars for project to project publishing.
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope);

        // Add a compile task
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        TaskManager.setJavaCompilerTask(javacTask, variantScope);

        taskFactory.register(new MergeGeneratedProguardFilesCreationAction(variantScope));

        // External native build
        createExternalNativeBuildJsonGenerators(variantScope);
        createExternalNativeBuildTasks(variantScope);

        createMergeJniLibFoldersTasks(variantScope);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(variantScope));

        taskFactory.register(new PackageRenderscriptTask.CreationAction(variantScope));

        // merge consumer proguard files from different build types and flavors
        taskFactory.register(new MergeConsumerProguardFilesTask.CreationAction(variantScope));

        taskFactory.register(new ExportConsumerProguardFilesTask.CreationAction(variantScope));

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (projectOptions.get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.register(new ExtractAnnotations.CreationAction(variantScope));
        }

        final boolean instrumented = variantDslInfo.isTestCoverageEnabled();

        TransformManager transformManager = variantScope.getTransformManager();

        // ----- Code Coverage first -----
        if (instrumented) {
            createJacocoTask(variantScope);
        }

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        final IssueReporter issueReporter = globalScope.getDslScope().getIssueReporter();

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
                    variantScope,
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
                                    variantScope.getTaskContainer().getAssembleTask(),
                                    taskProvider);
                        }
                    });
        }

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
                new BundleLibraryClasses.CreationAction(
                        variantScope,
                        AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR,
                        excludeDataBindingClassesIfNecessary(variantScope)));

        // Also create a directory containing the same classes for incremental dexing
        taskFactory.register(
                new BundleLibraryClasses.CreationAction(
                        variantScope,
                        AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS,
                        AndroidArtifacts.ArtifactType.CLASSES_DIR,
                        excludeDataBindingClassesIfNecessary(variantScope)));

        taskFactory.register(new BundleLibraryJavaRes.CreationAction(variantScope));

        taskFactory.register(new LibraryDexingTask.CreationAction(variantScope));

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(new ZipMergingTask.CreationAction(variantScope));

        // now add a task that will take all the native libs and package
        // them into an intermediary folder. This processes only the PROJECT
        // scope.
        taskFactory.register(
                new LibraryJniLibsTask.ProjectOnlyCreationAction(
                        variantScope, InternalArtifactType.LIBRARY_JNI.INSTANCE));

        // Now go back to fill the pipeline with transforms used when
        // publishing the AAR

        // first merge the java resources.
        createMergeJavaResTask(variantScope);

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTask(variantScope);
        maybeCreateResourcesShrinkerTasks(variantScope);

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.

        taskFactory.register(
                new LibraryAarJarsTask.CreationAction(
                        variantScope,
                        excludeDataBindingClassesIfNecessary(variantScope)));


        // now add a task that will take all the native libs and package
        // them into the libs folder of the bundle. This processes both the PROJECT
        // and the LOCAL_PROJECT scopes
        taskFactory.register(
                new LibraryJniLibsTask.ProjectAndLocalJarsCreationAction(
                        variantScope, InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI.INSTANCE));

        createLintTasks(variantScope, variantScopesForLint);
        createBundleTask(variantScope);
    }

    private void registerLibraryRClassTransformStream(@NonNull VariantScope variantScope) {
        InternalArtifactType<RegularFile> rClassJar;

        if (globalScope.getExtension().getAaptOptions().getNamespaced()) {
            rClassJar = InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR.INSTANCE;
        } else {
            rClassJar = InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR.INSTANCE;
        }
        QualifiedContent.ScopeType scopeType;
        if (variantScope.getCodeShrinker() != null) {
            // Add the R class classes as production classes. They are then removed by the library
            // jar transform.
            // TODO(b/115974418): Can we stop adding the compilation-only R class as a local classes?
            scopeType = Scope.PROJECT;
        } else {
            scopeType = Scope.PROVIDED_ONLY;
        }

        FileCollection compileRClass =
                project.files(variantScope.getArtifacts().getFinalProduct(rClassJar));
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "compile-only-r-class")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(scopeType)
                                .setFileCollection(compileRClass)
                                .build());
    }

    private void createBundleTask(@NonNull VariantScope variantScope) {
        TaskProvider<BundleAar> bundle =
                taskFactory.register(new BundleAar.CreationAction(variantScope));

        TaskFactoryUtils.dependsOn(variantScope.getTaskContainer().getAssembleTask(), bundle);

        final VariantDependencies variantDependencies = variantScope.getVariantDependencies();

        AdhocComponentWithVariants component =
                globalScope.getComponentFactory().adhoc(variantScope.getName());

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
        if (extension
                .getDefaultPublishConfig()
                .equals(variantScope.getVariantDslInfo().getComponentIdentity().getName())) {
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
    protected void createDependencyStreams(@NonNull VariantScope variantScope) {
        super.createDependencyStreams(variantScope);

        // add the same jars twice in the same stream as the EXTERNAL_LIB in the task manager
        // so that filtering of duplicates in proguard can work.
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(variantScope.getLocalPackagedJars())
                                .build());
    }

    private static class MergeResourceCallback implements TaskProviderCallback<MergeResources> {
        private final VariantScope variantScope;

        private MergeResourceCallback(VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends MergeResources> taskProvider) {

            variantScope
                    .getArtifacts()
                    .producesFile(
                            InternalArtifactType.PUBLIC_RES.INSTANCE,
                            taskProvider,
                            MergeResources::getPublicFile,
                            FN_PUBLIC_TXT);

        }
    }

    private void createMergeResourcesTasks(@NonNull VariantScope variantScope) {
        ImmutableSet<MergeResources.Flag> flags;
        if (variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            flags =
                    Sets.immutableEnumSet(
                            MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                            MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        } else {
            flags = Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        }

        MergeResourceCallback callback = new MergeResourceCallback(variantScope);

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        basicCreateMergeResourcesTask(
                variantScope,
                MergeType.PACKAGE,
                variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES.INSTANCE),
                false,
                false,
                false,
                flags,
                callback);


        // This task merges all the resources, including the dependencies of this library.
        // This should be unused, except that external libraries might consume it.
        // Also used by the VerifyLibraryResourcesTask (only ran in release builds).
        createMergeResourcesTask(variantScope, false /*processResources*/, ImmutableSet.of());
    }

    private void createCompileLibraryResourcesTask(@NonNull VariantScope variantScope) {
        if (variantScope.isPrecompileDependenciesResourcesEnabled()) {
            taskFactory.register(new CompileLibraryResourcesTask.CreationAction(variantScope));
        }
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                scope.getGlobalScope()
                        .getProject()
                        .files(
                                scope.getArtifacts().getFinalProduct(JAVAC.INSTANCE),
                                scope.getVariantData().getAllPreJavacGeneratedBytecode(),
                                scope.getVariantData().getAllPostJavacGeneratedBytecode());
        scope.getArtifacts().appendToAllClasses(files);

        // Create jar used for publishing to API elements (for other projects to compile against).
        taskFactory.register(
                new BundleLibraryClasses.CreationAction(
                        scope,
                        AndroidArtifacts.PublishedConfigType.API_ELEMENTS,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR,
                        excludeDataBindingClassesIfNecessary(scope)));
    }

    @NonNull
    private Supplier<List<String>> excludeDataBindingClassesIfNecessary(
            @NonNull VariantScope variantScope) {
        if (!globalScope.getBuildFeatures().getDataBinding()) {
            return Collections::emptyList;
        }

        return () -> {
            File excludeFile =
                    variantScope.getVariantData().getType().isExportDataBindingClassList()
                            ? variantScope.getGeneratedClassListOutputFileForDataBinding()
                            : null;
            File dependencyArtifactsDir =
                    variantScope
                            .getArtifacts()
                            .getFinalProduct(
                                    InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS.INSTANCE)
                            .get()
                            .getAsFile();
            return dataBindingBuilder.getJarExcludeList(
                    variantScope.getVariantData().getLayoutXmlProcessor(),
                    excludeFile,
                    dependencyArtifactsDir);
        };
    }

    public void createLibraryAssetsTask(@NonNull VariantScope scope) {
        taskFactory.register(new MergeSourceSetFolders.LibraryAssetCreationAction(scope));
    }

    @NonNull
    @Override
    protected Set<ScopeType> getJavaResMergingScopes(
            @NonNull VariantScope variantScope, @NonNull QualifiedContent.ContentType contentType) {
        Preconditions.checkArgument(
                contentType == RESOURCES || contentType == NATIVE_LIBS,
                "contentType must be RESOURCES or NATIVE_LIBS");
        if (variantScope.getTestedVariantData() != null) {
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

    public void createVerifyLibraryResTask(@NonNull VariantScope scope) {
        TaskProvider<VerifyLibraryResourcesTask> verifyLibraryResources =
                taskFactory.register(new VerifyLibraryResourcesTask.CreationAction(scope));

        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getAssembleTask(), verifyLibraryResources);
    }
}
