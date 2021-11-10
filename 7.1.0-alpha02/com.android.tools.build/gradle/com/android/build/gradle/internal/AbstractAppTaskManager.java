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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestFixturesImpl;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.variant.impl.VariantBuilderImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.component.ApplicationCreationConfig;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.ConsumableCreationConfig;
import com.android.build.gradle.internal.feature.BundleAllClasses;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.tasks.AnalyticsRecordingTask;
import com.android.build.gradle.internal.tasks.ApkZipPackagingTask;
import com.android.build.gradle.internal.tasks.AppClasspathCheckTask;
import com.android.build.gradle.internal.tasks.AppPreBuildTask;
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.CheckMultiApkLibrariesTask;
import com.android.build.gradle.internal.tasks.CompressAssetsTask;
import com.android.build.gradle.internal.tasks.ExtractNativeDebugMetadataTask;
import com.android.build.gradle.internal.tasks.ExtractProfilerNativeDependenciesTask;
import com.android.build.gradle.internal.tasks.ModuleMetadataWriterTask;
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask;
import com.android.build.gradle.internal.tasks.TestPreBuildTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.builder.core.VariantType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.gradle.api.Task;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/** TaskManager for creating tasks in an Android application project. */
public abstract class AbstractAppTaskManager<
                VariantBuilderT extends VariantBuilderImpl, VariantT extends VariantImpl>
        extends TaskManager<VariantBuilderT, VariantT> {

    protected AbstractAppTaskManager(
            @NonNull List<ComponentInfo<VariantBuilderT, VariantT>> variants,
            @NonNull List<TestComponentImpl> testComponents,
            @NonNull List<TestFixturesImpl> testFixturesComponents,
            boolean hasFlavors,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ProjectInfo projectInfo) {
        super(
                variants,
                testComponents,
                testFixturesComponents,
                hasFlavors,
                projectOptions,
                globalScope,
                extension,
                projectInfo);
    }

    protected void createCommonTasks(
            @NonNull ComponentInfo<VariantBuilderT, VariantT> variant,
            @NonNull
                    List<? extends ComponentInfo<VariantBuilderT, VariantT>>
                            allComponentsWithLint) {
        VariantT appVariantProperties = variant.getVariant();
        ApkCreationConfig apkCreationConfig = (ApkCreationConfig) appVariantProperties;

        createAnchorTasks(appVariantProperties);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(appVariantProperties));

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(appVariantProperties);

        // Add a task to publish the applicationId.
        // TODO remove case once TaskManager's type param is based on BaseCreationConfig
        createApplicationIdWriterTask(apkCreationConfig);

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(appVariantProperties));

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(appVariantProperties);

        // Add a task to create the res values
        createGenerateResValuesTask(appVariantProperties);

        // Add a task to compile renderscript files.
        createRenderscriptTask(appVariantProperties);

        // Add a task to merge the resource folders
        createMergeResourcesTasks(appVariantProperties);

        // Add tasks to compile shader
        createShaderTask(appVariantProperties);

        // Add a task to merge the asset folders
        createMergeAssetsTask(appVariantProperties);

        taskFactory.register(new CompressAssetsTask.CreationAction(apkCreationConfig));

        // Add a task to create the BuildConfig class
        createBuildConfigTask(appVariantProperties);

        // Add a task to process the Android Resources and generate source files
        createApkProcessResTask(appVariantProperties);

        registerRClassTransformStream(appVariantProperties);

        // Add a task to process the java resources
        createProcessJavaResTask(appVariantProperties);

        createAidlTask(appVariantProperties);

        maybeExtractProfilerDependencies(apkCreationConfig);

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(appVariantProperties);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(appVariantProperties);

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(appVariantProperties);

        // Add a compile task
        createCompileTask(appVariantProperties);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(appVariantProperties));

        taskFactory.register(
                new ExtractNativeDebugMetadataTask.FullCreationAction(appVariantProperties));
        taskFactory.register(
                new ExtractNativeDebugMetadataTask.SymbolTableCreationAction(appVariantProperties));

        createPackagingTask(apkCreationConfig);

        taskFactory.register(
                new PackagedDependenciesWriterTask.CreationAction(appVariantProperties));

        taskFactory.register(new ApkZipPackagingTask.CreationAction(appVariantProperties));
    }

    private void createCompileTask(@NonNull VariantImpl variant) {
        ApkCreationConfig apkCreationConfig = (ApkCreationConfig) variant;

        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variant);
        addJavacClassesStream(variant);
        setJavaCompilerTask(javacTask, variant);
        createPostCompilationTasks(apkCreationConfig);
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentCreationConfig creationConfig) {
        super.postJavacCreation(creationConfig);

        taskFactory.register(new BundleAllClasses.CreationAction(creationConfig));
    }

    @Override
    protected void createVariantPreBuildTask(@NonNull ComponentCreationConfig creationConfig) {
        final VariantType variantType = creationConfig.getVariantType();

        if (variantType.isApk()) {
            boolean useDependencyConstraints =
                    creationConfig
                            .getServices()
                            .getProjectOptions()
                            .get(BooleanOption.USE_DEPENDENCY_CONSTRAINTS);

            TaskProvider<? extends Task> task;

            if (variantType.isTestComponent()) {
                task =
                        taskFactory.register(
                                new TestPreBuildTask.CreationAction(
                                        (TestComponentImpl) creationConfig));
                if (useDependencyConstraints) {
                    task.configure(t -> t.setEnabled(false));
                }
            } else {
                //noinspection unchecked
                task = taskFactory.register(AppPreBuildTask.getCreationAction(creationConfig));
                ApkCreationConfig config = (ApkCreationConfig) creationConfig;
                // Only record application ids for release artifacts
                boolean analyticsEnabled =
                        creationConfig.getServices().getProjectOptions().isAnalyticsEnabled();
                if (!config.getDebuggable() && analyticsEnabled) {
                    TaskProvider<AnalyticsRecordingTask> recordTask =
                            taskFactory.register(new AnalyticsRecordingTask.CreationAction(config));
                    task.configure(it -> it.finalizedBy(recordTask));
                }
            }

            if (!useDependencyConstraints) {
                TaskProvider<AppClasspathCheckTask> classpathCheck =
                        taskFactory.register(
                                new AppClasspathCheckTask.CreationAction(creationConfig));
                TaskFactoryUtils.dependsOn(task, classpathCheck);
            }

            if (variantType.isBaseModule() && globalScope.hasDynamicFeatures()) {
                TaskProvider<CheckMultiApkLibrariesTask> checkMultiApkLibrariesTask =
                        taskFactory.register(
                                new CheckMultiApkLibrariesTask.CreationAction(creationConfig));

                TaskFactoryUtils.dependsOn(task, checkMultiApkLibrariesTask);
            }
            return;
        }

        super.createVariantPreBuildTask(creationConfig);
    }

    @NonNull
    @Override
    protected Set<ScopeType> getJavaResMergingScopes(
            @NonNull ComponentCreationConfig creationConfig,
            @NonNull QualifiedContent.ContentType contentType) {
        if (creationConfig.getVariantScope().consumesFeatureJars()
                && contentType == RESOURCES
                && !(creationConfig instanceof ConsumableCreationConfig)) {
            return TransformManager.SCOPE_FULL_WITH_FEATURES;
        }
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    private void createApplicationIdWriterTask(@NonNull ApkCreationConfig creationConfig) {
        if (creationConfig.getVariantType().isBaseModule()) {
            taskFactory.register(
                    new ModuleMetadataWriterTask.CreationAction(
                            (ApplicationCreationConfig) creationConfig));
        }

        // TODO b/141650037 - Only the base App should create this task once we get rid of
        // getApplicationIdTextResource()
        // Once this is removed, this whole methods can be moved to AppTaskManager
        TaskProvider<? extends Task> applicationIdWriterTask =
                taskFactory.register(new ApplicationIdWriterTask.CreationAction(creationConfig));

        TextResourceFactory resources = project.getResources().getText();
        // this builds the dependencies from the task, and its output is the textResource.
        ((ComponentImpl) creationConfig).getVariantData().applicationIdTextResource =
                resources.fromFile(applicationIdWriterTask);
    }

    private void createMergeResourcesTasks(@NonNull VariantImpl variant) {
        // The "big merge" of all resources, will merge and compile resources that will later
        // be used for linking.
        createMergeResourcesTask(
                variant, true, Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES));

        ProjectOptions projectOptions = variant.getServices().getProjectOptions();
        boolean nonTransitiveR = projectOptions.get(BooleanOption.NON_TRANSITIVE_R_CLASS);
        boolean namespaced =
                variant.getServices()
                        .getProjectInfo()
                        .getExtension()
                        .getAaptOptions()
                        .getNamespaced();

        // TODO(b/138780301): Also use compile time R class in android tests.
        if ((projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS) || nonTransitiveR)
                && !variant.getVariantType().isForTesting()
                && !namespaced) {
            // The "small merge" of only the app's local resources (can be multiple source-sets, but
            // most of the time it's just one). This is used by the Process for generating the local
            // R-def.txt file containing a list of resources defined in this module.
            basicCreateMergeResourcesTask(
                    variant,
                    MergeType.PACKAGE,
                    false,
                    false,
                    false,
                    ImmutableSet.of(),
                    null);
        }
    }

    /** Extract dependencies for profiler supports if needed. */
    private void maybeExtractProfilerDependencies(@NonNull ApkCreationConfig apkCreationConfig) {
        if (apkCreationConfig.getShouldPackageProfilerDependencies()) {
            taskFactory.register(
                    new ExtractProfilerNativeDependenciesTask.CreationAction(apkCreationConfig));
        }
    }
}
