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
import com.android.build.api.artifact.ArtifactType;
import com.android.build.api.component.impl.TestComponentBuilderImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.variant.impl.TestVariantBuilderImpl;
import com.android.build.api.variant.impl.TestVariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.component.*;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.test.TestApplicationTestData;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.tasks.CheckTestedAppObfuscation;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.model.CodeShrinker;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;
import org.gradle.api.Task;
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
        extends AbstractAppTaskManager<TestVariantBuilderImpl, TestVariantImpl> {

    public TestApplicationTaskManager(
            @NonNull List<ComponentInfo<TestVariantBuilderImpl, TestVariantImpl>> variants,
            @NonNull
                    List<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension) {
        super(variants, testComponents, hasFlavors, globalScope, extension);
    }

    @Override
    protected void doCreateTasksForVariant(
            @NotNull ComponentInfo<TestVariantBuilderImpl, TestVariantImpl> variantInfo,
            @NotNull
                    List<? extends ComponentInfo<TestVariantBuilderImpl, TestVariantImpl>>
                            allVariants) {
        createCommonTasks(variantInfo, allVariants);

        TestVariantImpl testVariantProperties = variantInfo.getVariant();

        Provider<Directory> testingApk =
                testVariantProperties.getArtifacts().get(ArtifactType.APK.INSTANCE);

        // The APKs to be tested.
        FileCollection testedApks =
                testVariantProperties
                        .getVariantDependencies()
                        .getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                APK);

        TestApplicationTestData testData =
                new TestApplicationTestData(
                        project.getProviders(),
                        testVariantProperties,
                        testVariantProperties,
                        testingApk,
                        testedApks);

        configureTestData(testVariantProperties, testData);

        // create tasks to validate signing and produce signing config versions file.
        createValidateSigningTask(testVariantProperties);
        taskFactory.register(
                new SigningConfigVersionsWriterTask.CreationAction(testVariantProperties));

        // create the test connected check task.
        TaskProvider<DeviceProviderInstrumentTestTask> instrumentTestTask =
                taskFactory.register(
                        new DeviceProviderInstrumentTestTask.CreationAction(
                                testVariantProperties, testData) {
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
    protected void postJavacCreation(@NonNull ComponentCreationConfig creationConfig) {
        creationConfig
                .getArtifacts()
                .appendToAllClasses(
                        creationConfig
                                .getServices()
                                .fileCollection(
                                        creationConfig
                                                .getArtifacts()
                                                .get(InternalArtifactType.JAVAC.INSTANCE),
                                        creationConfig
                                                .getVariantData()
                                                .getAllPreJavacGeneratedBytecode(),
                                        creationConfig
                                                .getVariantData()
                                                .getAllPostJavacGeneratedBytecode()));
    }

    @Override
    protected void maybeCreateJavaCodeShrinkerTask(
            @NonNull ConsumableCreationConfig creationConfig) {
        final CodeShrinker codeShrinker = creationConfig.getCodeShrinker();
        if (codeShrinker != null) {
            doCreateJavaCodeShrinkerTask(
                    creationConfig, Objects.requireNonNull(codeShrinker), true);
        } else {
            TaskProvider<CheckTestedAppObfuscation> checkObfuscation =
                    taskFactory.register(
                            new CheckTestedAppObfuscation.CreationAction(creationConfig));
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
