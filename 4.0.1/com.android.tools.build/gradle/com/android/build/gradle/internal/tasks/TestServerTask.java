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
import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.testing.api.TestServer;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Task sending APKs out to a {@link TestServer} */
public abstract class TestServerTask extends NonIncrementalTask {

    TestServer testServer;

    @Override
    protected void doTaskAction() {

        List<File> testedApkFiles =
                getTestedApks().isPresent()
                        ? new BuiltArtifactsLoaderImpl()
                                .load(getTestedApks().get())
                                .getElements()
                                .stream()
                                .map(BuiltArtifact::getOutputFile)
                                .map(Path::toFile)
                                .collect(Collectors.toList())
                        : ImmutableList.of();

        if (testedApkFiles.size() > 1) {
            throw new RuntimeException("Cannot handle split APKs");
        }
        File testedApkFile = testedApkFiles.isEmpty() ? null : testedApkFiles.get(0);
        List<File> testApkFiles =
                new BuiltArtifactsLoaderImpl()
                        .load(getTestApks().get())
                        .getElements()
                        .stream()
                        .map(BuiltArtifact::getOutputFile)
                        .map(Path::toFile)
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
            extends VariantTaskCreationAction<TestServerTask> {

        private final TestServer testServer;

        public TestServerTaskCreationAction(VariantScope scope, TestServer testServer) {
            super(scope);
            this.testServer = testServer;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getVariantDslInfo().hasFlavors()
                    ? getVariantScope().getTaskName(testServer.getName() + "Upload")
                    : testServer.getName() + ("Upload");
        }

        @NonNull
        @Override
        public Class<TestServerTask> getType() {
            return TestServerTask.class;
        }

        @Override
        public void configure(@NonNull TestServerTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            final BaseVariantData testedVariantData = scope.getTestedVariantData();

            final String variantName = scope.getName();
            task.setDescription(
                    "Uploads APKs for Build \'"
                            + variantName
                            + "\' to Test Server \'"
                            + StringHelper.usLocaleCapitalize(testServer.getName())
                            + "\'.");
            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);

            task.setTestServer(testServer);

            if (testedVariantData != null
                    && testedVariantData
                            .getScope()
                            .getArtifacts()
                            .hasFinalProduct(InternalArtifactType.APK.INSTANCE)) {
                testedVariantData
                        .getScope()
                        .getArtifacts()
                        .setTaskInputToFinalProduct(
                                InternalArtifactType.APK.INSTANCE, task.getTestedApks());
            }

            scope.getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.APK.INSTANCE, task.getTestApks());

            if (!testServer.isConfigured()) {
                task.setEnabled(false);
            }
        }
    }
}
