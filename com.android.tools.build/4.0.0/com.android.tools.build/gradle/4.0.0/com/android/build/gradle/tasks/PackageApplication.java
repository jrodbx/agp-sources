/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.SingleArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.profile.ProcessProfileWriter;
import com.google.wireless.android.sdk.stats.GradleBuildProjectMetrics;
import java.io.File;
import java.io.IOException;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;

/** Task to package an Android application (APK). */
public abstract class PackageApplication extends PackageAndroidArtifact {

    ArtifactType<Directory> expectedOutputType;

    @Override
    @Internal
    protected ArtifactType<Directory> getInternalArtifactType() {
        return expectedOutputType;
    }

    public static void recordMetrics(String projectPath, File apkOutputFile, File resourcesApFile) {
        long metricsStartTime = System.nanoTime();
        GradleBuildProjectMetrics.Builder metrics = GradleBuildProjectMetrics.newBuilder();

        Long apkSize = getSize(apkOutputFile);
        if (apkSize != null) {
            metrics.setApkSize(apkSize);
        }

        Long resourcesApSize = getSize(resourcesApFile);
        if (resourcesApSize != null) {
            metrics.setResourcesApSize(resourcesApSize);
        }

        metrics.setMetricsTimeNs(System.nanoTime() - metricsStartTime);

        ProcessProfileWriter.getProject(projectPath).setMetrics(metrics);
    }

    @Nullable
    private static Long getSize(@Nullable File file) {
        if (file == null) {
            return null;
        }
        try {
            return java.nio.file.Files.size(file.toPath());
        } catch (IOException e) {
            return null;
        }
    }

    // ----- CreationAction -----

    /**
     * Configures the task to perform the "standard" packaging, including all files that should end
     * up in the APK.
     */
    public static class CreationAction
            extends PackageAndroidArtifact.CreationAction<PackageApplication> {

        private final File outputDirectory;

        public CreationAction(
                @NonNull VariantScope packagingScope,
                @NonNull File outputDirectory,
                @NonNull SingleArtifactType<Directory> inputResourceFilesType,
                @NonNull Provider<Directory> manifests,
                @NonNull ArtifactType<Directory> manifestType,
                boolean packageCustomClassDependencies) {
            super(
                    packagingScope,
                    inputResourceFilesType,
                    manifests,
                    manifestType,
                    packageCustomClassDependencies);
            this.outputDirectory = outputDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends PackageApplication> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setPackageAndroidTask(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.APK.INSTANCE,
                            taskProvider,
                            PackageApplication::getOutputDirectory,
                            outputDirectory.getAbsolutePath(),
                            "");

            getVariantScope()
                    .getArtifacts()
                    .getOperations()
                    .setInitialProvider(taskProvider, PackageApplication::getIdeModelOutputFile)
                    .atLocation(PackageApplication::getOutputDirectory)
                    .withName(ExistingBuildElements.METADATA_FILE_NAME)
                    .on(InternalArtifactType.APK_IDE_MODEL.INSTANCE);
        }

        @Override
        protected void finalConfigure(PackageApplication task) {
            super.finalConfigure(task);
            task.expectedOutputType = InternalArtifactType.APK.INSTANCE;
        }
    }
}
