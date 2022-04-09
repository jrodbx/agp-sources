/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.testing.api.TestServer;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/** Task sending APKs out to a {@link TestServer} */
@DisableCachingByDefault
public abstract class TestServerTask extends NonIncrementalTask {

    TestServer testServer;

    @Override
    protected void doTaskAction() {

        List<File> testedApkFiles =
                getTestedApks().isPresent()
                        ? new BuiltArtifactsLoaderImpl()
                                .load(getTestedApks().get()).getElements().stream()
                                        .map(BuiltArtifact::getOutputFile)
                                        .map(File::new)
                                        .collect(Collectors.toList())
                        : ImmutableList.of();

        if (testedApkFiles.size() > 1) {
            throw new RuntimeException("Cannot handle split APKs");
        }
        File testedApkFile = testedApkFiles.isEmpty() ? null : testedApkFiles.get(0);
        List<File> testApkFiles =
                new BuiltArtifactsLoaderImpl()
                        .load(getTestApks().get()).getElements().stream()
                                .map(BuiltArtifact::getOutputFile)
                                .map(File::new)
                                .collect(Collectors.toList());
        if (testApkFiles.size() > 1) {
            throw new RuntimeException("Cannot handle split APKs in test APKs");
        }
        testServer.uploadApks(getVariantName(), testApkFiles.get(0), testedApkFile);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getTestApks();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    @NonNull
    public abstract DirectoryProperty getTestedApks();

    @NonNull
    @Override
    @Input
    public String getVariantName() {
        return super.getVariantName();
    }

    public void setTestServer(TestServer testServer) {
        this.testServer = testServer;
    }

    /** Configuration Action for a TestServerTask. */
    public static class TestServerTaskCreationAction
            extends VariantTaskCreationAction<TestServerTask, AndroidTestImpl> {
        private final TestServer testServer;
        private final boolean hasFlavors;

        public TestServerTaskCreationAction(
                @NonNull AndroidTestImpl androidTestProperties, TestServer testServer) {
            super(androidTestProperties);
            this.testServer = testServer;
            this.hasFlavors = androidTestProperties.getVariantDslInfo().hasFlavors();
        }

        @NonNull
        @Override
        public String getName() {
            return hasFlavors
                    ? computeTaskName(testServer.getName() + "Upload")
                    : testServer.getName() + ("Upload");
        }

        @NonNull
        @Override
        public Class<TestServerTask> getType() {
            return TestServerTask.class;
        }

        @Override
        public void configure(
                @NonNull TestServerTask task) {
            super.configure(task);
            VariantImpl testedVariant = creationConfig.getTestedVariant();

            final String variantName = creationConfig.getName();
            task.setDescription(
                    "Uploads APKs for Build \'"
                            + variantName
                            + "\' to Test Server \'"
                            + StringHelper.usLocaleCapitalize(testServer.getName())
                            + "\'.");
            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

            task.setTestServer(testServer);

            testedVariant
                    .getArtifacts()
                    .setTaskInputToFinalProduct(SingleArtifact.APK.INSTANCE, task.getTestedApks());

            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(SingleArtifact.APK.INSTANCE, task.getTestApks());

            if (!testServer.isConfigured()) {
                task.setEnabled(false);
            }
        }
    }
}
