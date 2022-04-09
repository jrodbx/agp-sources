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

import static com.android.build.gradle.internal.cxx.configure.CxxCreateGradleTasksKt.createCxxVariantBuildTask;

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent.ScopeType;
import com.android.build.api.variant.VariantBuilder;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.component.ApplicationCreationConfig;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.component.TestFixturesCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.feature.BundleAllClasses;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
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
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig;
import com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.ExtractDeepLinksTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.builder.core.ComponentType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/** TaskManager for creating tasks in an Android application project. */
public abstract class AbstractAppTaskManager<
                VariantBuilderT extends VariantBuilder, VariantT extends VariantCreationConfig>
        extends TaskManager<VariantBuilderT, VariantT> {

    protected AbstractAppTaskManager(
            @NonNull Project project,
            @NonNull Collection<? extends ComponentInfo<VariantBuilderT, VariantT>> variants,
            @NonNull Collection<? extends TestComponentCreationConfig> testComponents,
            @NonNull Collection<? extends TestFixturesCreationConfig> testFixturesComponents,
            @NonNull GlobalTaskCreationConfig globalConfig,
            @NonNull TaskManagerConfig localConfig,
            @NonNull BaseExtension extension) {
        super(
                project,
                variants,
                testComponents,
                testFixturesComponents,
                globalConfig,
                localConfig,
                extension);
    }

    protected void createCommonTasks(@NonNull ComponentInfo<VariantBuilderT, VariantT> variant) {
        ApkCreationConfig creationConfig = (ApkCreationConfig) variant.getVariant();

        createAnchorTasks(creationConfig);

        taskFactory.register(new ExtractDeepLinksTask.CreationAction(creationConfig));

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(creationConfig);

        // Add a task to publish the applicationId.
        // TODO remove case once TaskManager's type param is based on BaseCreationConfig
        createApplicationIdWriterTask(creationConfig);

        // Add a task to check the manifest
        taskFactory.register(new CheckManifest.CreationAction(creationConfig));

        // Add a task to process the manifest(s)
        createMergeApkManifestsTask(creationConfig);

        // Add a task to create the res values
        createGenerateResValuesTask(creationConfig);

        // Add a task to compile renderscript files.
        createRenderscriptTask(creationConfig);

        // Add a task to merge the resource folders
        createMergeResourcesTasks(creationConfig);

        // Add tasks to compile shader
        createShaderTask(creationConfig);

        // Add a task to merge the asset folders
        createMergeAssetsTask(creationConfig);

        taskFactory.register(new CompressAssetsTask.CreationAction(creationConfig));

        // Add a task to create the BuildConfig class
        createBuildConfigTask(creationConfig);

        // Add a task to process the Android Resources and generate source files
        createApkProcessResTask(creationConfig);

        // Add a task to process the java resources
        createProcessJavaResTask(creationConfig);

        createAidlTask(creationConfig);

        maybeExtractProfilerDependencies(creationConfig);

        // Set up the C/C++ external native build task
        createCxxVariantBuildTask(
                taskFactory, variant.getVariant(), project.getProviders(), project.getLayout());

        // Add a task to merge the jni libs folders
        createMergeJniLibFoldersTasks(creationConfig);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(creationConfig);

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(creationConfig);

        // Add a compile task
        createCompileTask(creationConfig);

        taskFactory.register(new StripDebugSymbolsTask.CreationAction(variant.getVariant()));

        taskFactory.register(
                new ExtractNativeDebugMetadataTask.FullCreationAction(variant.getVariant()));
        taskFactory.register(
                new ExtractNativeDebugMetadataTask.SymbolTableCreationAction(variant.getVariant()));

        createPackagingTask(creationConfig);

        taskFactory.register(new PackagedDependenciesWriterTask.CreationAction(creationConfig));

        taskFactory.register(new ApkZipPackagingTask.CreationAction(creationConfig));
    }

    private void createCompileTask(@NonNull ApkCreationConfig creationConfig) {
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(creationConfig);
        addJavacClassesStream(creationConfig);
        setJavaCompilerTask(javacTask, creationConfig);
        createPostCompilationTasks(creationConfig);
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentCreationConfig creationConfig) {
        super.postJavacCreation(creationConfig);

        taskFactory.register(
                new BundleAllClasses.CreationAction(
                        creationConfig, AndroidArtifacts.PublishedConfigType.API_ELEMENTS));
        taskFactory.register(
                new BundleAllClasses.CreationAction(
                        creationConfig, AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS));
    }

    @Override
    protected void createVariantPreBuildTask(@NonNull ComponentCreationConfig creationConfig) {
        final ComponentType componentType = creationConfig.getComponentType();

        if (componentType.isApk()) {
            boolean useDependencyConstraints =
                    creationConfig
                            .getServices()
                            .getProjectOptions()
                            .get(BooleanOption.USE_DEPENDENCY_CONSTRAINTS);

            TaskProvider<? extends Task> task;

            if (componentType.isTestComponent()) {
                task =
                        taskFactory.register(
                                new TestPreBuildTask.CreationAction(
                                        (TestComponentCreationConfig) creationConfig));
                if (useDependencyConstraints) {
                    task.configure(t -> t.setEnabled(false));
                }
            } else {
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

            if (componentType.isBaseModule() && globalConfig.getHasDynamicFeatures()) {
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
            @NonNull ComponentCreationConfig creationConfig) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    private void createApplicationIdWriterTask(@NonNull ApkCreationConfig creationConfig) {
        if (creationConfig.getComponentType().isBaseModule()) {
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
        creationConfig.getOldVariantApiLegacySupport().getVariantData().applicationIdTextResource =
                resources.fromFile(applicationIdWriterTask);
    }

    private void createMergeResourcesTasks(@NonNull ApkCreationConfig creationConfig) {
        // The "big merge" of all resources, will merge and compile resources that will later
        // be used for linking.
        createMergeResourcesTask(
                creationConfig,
                true,
                Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES));

        ProjectOptions projectOptions = creationConfig.getServices().getProjectOptions();
        boolean nonTransitiveR = projectOptions.get(BooleanOption.NON_TRANSITIVE_R_CLASS);
        boolean namespaced = creationConfig.getGlobal().getNamespacedAndroidResources();

        // TODO(b/138780301): Also use compile time R class in android tests.
        if ((projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS) || nonTransitiveR)
                && !creationConfig.getComponentType().isForTesting()
                && !namespaced) {
            // The "small merge" of only the app's local resources (can be multiple source-sets, but
            // most of the time it's just one). This is used by the Process for generating the local
            // R-def.txt file containing a list of resources defined in this module.
            basicCreateMergeResourcesTask(
                    creationConfig,
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
