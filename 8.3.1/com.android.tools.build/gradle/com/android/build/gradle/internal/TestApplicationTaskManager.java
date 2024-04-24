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


import com.android.annotations.NonNull;
import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.TestVariantBuilder;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.ConsumableCreationConfig;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.component.TestCreationConfig;
import com.android.build.gradle.internal.component.TestFixturesCreationConfig;
import com.android.build.gradle.internal.component.TestVariantCreationConfig;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.AsarsToCompatSplitsTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.tasks.CheckTestedAppObfuscation;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.ComponentType;
import com.google.common.base.Preconditions;
import java.util.Collection;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager
        extends AbstractAppTaskManager<TestVariantBuilder, TestVariantCreationConfig> {

    public TestApplicationTaskManager(
            @NonNull Project project,
            @NonNull
                    Collection<
                                    ? extends
                                            ComponentInfo<
                                                    TestVariantBuilder, TestVariantCreationConfig>>
                            variants,
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

    @Override
    protected void doCreateTasksForVariant(
            @NotNull ComponentInfo<TestVariantBuilder, TestVariantCreationConfig> variantInfo) {
        createCommonTasks(variantInfo);

        TestVariantCreationConfig testVariantProperties = variantInfo.getVariant();

        Provider<Directory> testingApk =
                testVariantProperties.getArtifacts().get(SingleArtifact.APK.INSTANCE);

        boolean privacySandboxEnabled =
                testVariantProperties.getPrivacySandboxCreationConfig() != null;

        FileCollection privacySandboxSdkApks =
                privacySandboxEnabled
                        ? testVariantProperties
                                .getVariantDependencies()
                                .getArtifactFileCollection(
                                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                        AndroidArtifacts.ArtifactScope.ALL,
                                        AndroidArtifacts.ArtifactType
                                                .ANDROID_PRIVACY_SANDBOX_SDK_APKS)
                        : null;

        Provider<Directory> privacySandboxCompatSdkApks =
                privacySandboxEnabled
                        ? testVariantProperties
                                .getArtifacts()
                                .get(InternalArtifactType.SDK_SPLITS_APKS.INSTANCE)
                        : null;

        TestApplicationTestData testData =
                new TestApplicationTestData(
                        testVariantProperties.getNamespace(),
                        testVariantProperties,
                        testingApk,
                        testVariantProperties.getTestedApks(),
                        privacySandboxSdkApks,
                        privacySandboxCompatSdkApks,
                        testVariantProperties
                                .getServices()
                                .getProjectOptions()
                                .getExtraInstrumentationTestRunnerArgs());

        configureTestData(testVariantProperties, testData);

        // create tasks to validate signing and produce signing config versions file.
        createValidateSigningTask(testVariantProperties);
        taskFactory.register(
                new SigningConfigVersionsWriterTask.CreationAction(testVariantProperties));
        taskFactory.register(new AsarsToCompatSplitsTask.CreationAction(testVariantProperties));

        // create the test connected check task.
        TaskProvider<DeviceProviderInstrumentTestTask> instrumentTestTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                testVariantProperties, testData) {
                            @NonNull
                            @Override
                            public String getName() {
                                return super.getName() + ComponentType.ANDROID_TEST_SUFFIX;
                            }
                        });

        taskFactory.configure(CONNECTED_ANDROID_TEST, task -> task.dependsOn(instrumentTestTask));

        createTestDevicesForVariant(
                testVariantProperties,
                testData,
                testVariantProperties.getName(),
                ComponentType.ANDROID_TEST_SUFFIX);
    }

    @Override
    protected void maybeCreateJavaCodeShrinkerTask(
            @NonNull ConsumableCreationConfig creationConfig) {
        if (creationConfig.getOptimizationCreationConfig().getMinifiedEnabled()) {
            doCreateJavaCodeShrinkerTask(creationConfig, true);
        } else {
            TaskProvider<CheckTestedAppObfuscation> checkObfuscation =
                    taskFactory.register(
                            new CheckTestedAppObfuscation.CreationAction(
                                    (TestVariantCreationConfig) creationConfig));
            Preconditions.checkNotNull(creationConfig.getTaskContainer().getJavacTask());
            TaskFactoryUtils.dependsOn(
                    creationConfig.getTaskContainer().getJavacTask(), checkObfuscation);
        }
    }

    /** Creates the merge manifests task. */
    @Override
    @NonNull
    protected TaskProvider<? extends ManifestProcessorTask> createMergeManifestTasks(
            @NonNull ApkCreationConfig creationConfig) {
        return taskFactory.register(
                new ProcessTestManifest.CreationAction((TestCreationConfig) creationConfig));
    }

    @Override
    protected void createVariantPreBuildTask(@NonNull ComponentCreationConfig creationConfig) {
        createDefaultPreBuildTask(creationConfig);
    }
}
