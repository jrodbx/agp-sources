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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.variant.impl.TestVariantImpl;
import com.android.build.api.variant.impl.TestVariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.component.TestCreationConfig;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.tasks.CheckTestedAppObfuscation;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.model.CodeShrinker;
import com.android.builder.profile.Recorder;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * TaskManager for standalone test application that lives in a separate module from the tested
 * application.
 */
public class TestApplicationTaskManager
        extends AbstractAppTaskManager<TestVariantImpl, TestVariantPropertiesImpl> {

    public TestApplicationTaskManager(
            @NonNull List<ComponentInfo<TestVariantImpl, TestVariantPropertiesImpl>> variants,
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
            @NonNull ComponentInfo<TestVariantImpl, TestVariantPropertiesImpl> variant,
            @NonNull List<ComponentInfo<TestVariantImpl, TestVariantPropertiesImpl>> allVariants) {
        createCommonTasks(variant, allVariants);

        TestVariantPropertiesImpl testVariantProperties = variant.getProperties();

        Provider<Directory> testingApk =
                testVariantProperties.getArtifacts().get(InternalArtifactType.APK.INSTANCE);

        // The APKs to be tested.
        FileCollection testedApks =
                testVariantProperties
                        .getVariantDependencies()
                        .getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                APK);

        TestApplicationTestData testData =
                new TestApplicationTestData(testVariantProperties, testingApk, testedApks);

        configureTestData(testVariantProperties, testData);

        // create the test connected check task.
        TaskProvider<DeviceProviderInstrumentTestTask> instrumentTestTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                testVariantProperties,
                                new ConnectedDeviceProvider(
                                        globalScope
                                                .getSdkComponents()
                                                .flatMap(
                                                        SdkComponentsBuildService
                                                                ::getAdbExecutableProvider),
                                        extension.getAdbOptions().getTimeOutInMs(),
                                        new LoggerWrapper(getLogger())),
                                DeviceProviderInstrumentTestTask.CreationAction.Type
                                        .INTERNAL_CONNECTED_DEVICE_PROVIDER,
                                testData) {
                            @NonNull
                            @Override
                            public String getName() {
                                return super.getName() + VariantType.ANDROID_TEST_SUFFIX;
                            }
                        });

        Task connectedAndroidTest =
                taskFactory.findByName(
                        BuilderConstants.CONNECTED + VariantType.ANDROID_TEST_SUFFIX);
        if (connectedAndroidTest != null) {
            connectedAndroidTest.dependsOn(instrumentTestTask.getName());
        }
    }

    @Override
    protected void postJavacCreation(@NonNull ComponentPropertiesImpl componentProperties) {
        // do nothing.
    }

    @Override
    public void createLintTasks(
            @NonNull TestVariantPropertiesImpl variantProperties,
            @NonNull List<ComponentInfo<TestVariantImpl, TestVariantPropertiesImpl>> allVariants) {
        // do nothing
    }

    @Override
    public void maybeCreateLintVitalTask(
            @NonNull TestVariantPropertiesImpl variant,
            @NonNull List<ComponentInfo<TestVariantImpl, TestVariantPropertiesImpl>> allVariants) {
        // do nothing
    }

    @Override
    protected void configureGlobalLintTask() {
        // do nothing
    }

    @Override
    protected void maybeCreateJavaCodeShrinkerTask(
            @NonNull ComponentPropertiesImpl componentProperties) {
        final CodeShrinker codeShrinker = componentProperties.getVariantScope().getCodeShrinker();
        if (codeShrinker != null) {
            doCreateJavaCodeShrinkerTask(
                    componentProperties, Objects.requireNonNull(codeShrinker), true);
        } else {
            TaskProvider<CheckTestedAppObfuscation> checkObfuscation =
                    taskFactory.register(
                            new CheckTestedAppObfuscation.CreationAction(componentProperties));
            Preconditions.checkNotNull(componentProperties.getTaskContainer().getJavacTask());
            TaskFactoryUtils.dependsOn(
                    componentProperties.getTaskContainer().getJavacTask(), checkObfuscation);
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
    protected void createVariantPreBuildTask(@NonNull ComponentPropertiesImpl componentProperties) {
        createDefaultPreBuildTask(componentProperties);
    }
}
