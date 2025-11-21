/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS;
import static com.android.build.gradle.internal.utils.InstallApkUtilsKt.getDeviceSpec;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.dsl.Installation;
import com.android.build.api.variant.impl.VariantApiExtensionsKt;
import com.android.build.gradle.internal.BuildToolsExecutableInput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkComponentsKt;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.utils.ApkSources;
import com.android.build.gradle.internal.utils.DefaultDeviceApkOutput;
import com.android.build.gradle.internal.utils.DeviceApkOutput;
import com.android.build.gradle.internal.utils.SdkApkInstallGroup;
import com.android.buildanalyzer.common.TaskCategory;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEPLOYMENT)
public abstract class InstallVariantTask extends NonIncrementalTask {

    private int timeOutInMs = 0;

    private Collection<String> installOptions;

    private String variantName;
    private Set<String> supportedAbis;
    private AndroidVersion minSdkVersion;

    @Inject
    public InstallVariantTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Install task is always run.");
            return false;
        });
    }

    @Override
    protected void doTaskAction() throws DeviceException, ExecutionException {
        final LoggerWrapper iLogger = new LoggerWrapper(getLogger());
        DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(
                        getBuildTools().adbExecutable(),
                        getTimeOutInMs(),
                        iLogger,
                        System.getenv("ANDROID_SERIAL"));

        deviceProvider.use(
                () -> {
                    var deviceApkOutput =
                            new DefaultDeviceApkOutput(
                                    new ApkSources(
                                            getApkDirectory(),
                                            getPrivacySandboxSdksApksFiles(),
                                            getPrivacySandboxSupportedSdkAdditionalSplitApks(),
                                            getPrivacySandboxCompatApks(),
                                            getDexMetadataDirectory()),
                                    supportedAbis,
                                    minSdkVersion,
                                    variantName,
                                    getProjectPath().get(),
                                    iLogger);
                    install(
                            deviceApkOutput,
                            getProjectPath().get(),
                            variantName,
                            deviceProvider,
                            getInstallOptions(),
                            getTimeOutInMs(),
                            getLogger());
                    return null;
                });
    }

    static void install(
            @NonNull DeviceApkOutput deviceApkOutput,
            @NonNull String projectPath,
            @NonNull String variantName,
            @NonNull DeviceProvider deviceProvider,
            @NonNull Collection<String> installOptions,
            int timeOutInMs,
            @NonNull Logger logger)
            throws DeviceException {
        ILogger iLogger = new LoggerWrapper(logger);
        int successfulInstallCount = 0;
        List<? extends DeviceConnector> devices = deviceProvider.getDevices();

        for (final DeviceConnector device : devices) {
            var installGroups = deviceApkOutput.getApks(getDeviceSpec(device));
            final Collection<String> extraArgs =
                    MoreObjects.firstNonNull(installOptions, ImmutableList.of());
            for (var apkInstallGroup : installGroups) {
                var apkFiles =
                        apkInstallGroup.getApks().stream()
                                .map(RegularFile::getAsFile)
                                .collect(Collectors.toList());
                if (apkInstallGroup instanceof SdkApkInstallGroup) {
                    SdkApkInstallGroup sdkApkInstallGroup = (SdkApkInstallGroup) apkInstallGroup;
                    installPrivacySandboxSdkApks(
                            logger,
                            device,
                            projectPath,
                            sdkApkInstallGroup.getSdkFile().getAsFile(),
                            apkFiles,
                            extraArgs,
                            iLogger,
                            variantName,
                            timeOutInMs);
                } else {
                    if (apkFiles.isEmpty()) {
                        logger.lifecycle(
                                "Skipping device '{}' for '{}:{}': Could not find build of variant "
                                        + "which supports density {} and an ABI in {}",
                                device.getName(),
                                projectPath,
                                variantName,
                                device.getDensity(),
                                Joiner.on(", ").join(device.getAbis()));
                    } else {
                        logger.lifecycle(
                                "Installing APK '{}' on '{}' for {}:{}",
                                FileUtils.getNamesAsCommaSeparatedList(apkFiles),
                                device.getName(),
                                projectPath,
                                variantName);
                        installPackages(device, apkFiles, extraArgs, timeOutInMs, iLogger);
                        successfulInstallCount++;
                    }
                }
            }
        }

        if (successfulInstallCount == 0) {
            throw new GradleException("Failed to install on any devices.");
        } else {
            logger.quiet(
                    "Installed on {} {}.",
                    successfulInstallCount,
                    successfulInstallCount == 1 ? "device" : "devices");
        }
    }

    private static void installPackages(
            @NonNull DeviceConnector device,
            @NonNull List<File> apkFiles,
            @NonNull Collection<String> extraArgs,
            @NonNull int timeOutInMs,
            @NonNull ILogger iLogger)
            throws DeviceException {
        if (apkFiles.size() > 1) {
            device.installPackages(apkFiles, extraArgs, timeOutInMs, iLogger);
        } else {
            device.installPackage(apkFiles.get(0), extraArgs, timeOutInMs, iLogger);
        }
    }

    private static void installPrivacySandboxSdkApks(
            @NonNull Logger logger,
            @NonNull DeviceConnector device,
            @NonNull String projectPath,
            @NonNull File file,
            @NonNull List<File> sdkApkFiles,
            @NonNull Collection<String> extraArgs,
            @NonNull ILogger iLogger,
            @NonNull String variantName,
            @NonNull int timeOutInMs) {
        try {
            logger.lifecycle(
                    "Installing Privacy Sandbox APK '{}' on '{}' for {}:{}",
                    FileUtils.getNamesAsCommaSeparatedList(sdkApkFiles),
                    device.getName(),
                    projectPath,
                    variantName);
            installPackages(device, sdkApkFiles, extraArgs, timeOutInMs, iLogger);
        } catch (DeviceException e) {
            logger.error(
                    String.format(
                            "Failed to install privacy sandbox SDK APKs from %s", file.toPath()),
                    e);
            }
    }

    @Input
    public int getTimeOutInMs() {
        return timeOutInMs;
    }

    public void setTimeOutInMs(int timeOutInMs) {
        this.timeOutInMs = timeOutInMs;
    }

    @Input
    @Optional
    public Collection<String> getInstallOptions() {
        return installOptions;
    }

    public void setInstallOptions(Collection<String> installOptions) {
        this.installOptions = installOptions;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApkDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract ConfigurableFileCollection getPrivacySandboxSdksApksFiles();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getPrivacySandboxSupportedSdkAdditionalSplitApks();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getPrivacySandboxCompatApks();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getDexMetadataDirectory();

    @Nested
    public abstract BuildToolsExecutableInput getBuildTools();

    public static class CreationAction
            extends VariantTaskCreationAction<InstallVariantTask, ApkCreationConfig> {

        public CreationAction(@NonNull ApkCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("install");
        }

        @NonNull
        @Override
        public Class<InstallVariantTask> getType() {
            return InstallVariantTask.class;
        }

        @Override
        public void configure(@NonNull InstallVariantTask task) {
            super.configure(task);

            task.variantName = creationConfig.getBaseName();

            if (creationConfig.getNativeBuildCreationConfig() == null) {
                task.supportedAbis = Collections.emptySet();
            } else {
                task.supportedAbis =
                        creationConfig.getNativeBuildCreationConfig().getSupportedAbis();
            }

            task.minSdkVersion =
                    VariantApiExtensionsKt.toSharedAndroidVersion(creationConfig.getMinSdk());

            task.setDescription("Installs the " + creationConfig.getDescription() + ".");
            task.setGroup(TaskManager.INSTALL_GROUP);
            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(
                            SingleArtifact.APK.INSTANCE, task.getApkDirectory());
            if (creationConfig.getPrivacySandboxCreationConfig() != null) {
                task.getPrivacySandboxSdksApksFiles()
                        .setFrom(
                                creationConfig
                                        .getVariantDependencies()
                                        .getArtifactFileCollection(
                                                AndroidArtifacts.ConsumedConfigType
                                                        .RUNTIME_CLASSPATH,
                                                AndroidArtifacts.ArtifactScope.ALL,
                                                ANDROID_PRIVACY_SANDBOX_SDK_APKS));
                task.getPrivacySandboxSupportedSdkAdditionalSplitApks().set(
                                creationConfig.getArtifacts().get(
                                        InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT.INSTANCE
                                )
                );
                task.getPrivacySandboxCompatApks()
                        .set(
                                creationConfig
                                        .getArtifacts()
                                        .get(InternalArtifactType.EXTRACTED_SDK_APKS.INSTANCE));
            }
            task.getPrivacySandboxSdksApksFiles().disallowChanges();
            task.getPrivacySandboxSupportedSdkAdditionalSplitApks().disallowChanges();
            task.getPrivacySandboxCompatApks().disallowChanges();

            Installation installationOptions = creationConfig.getGlobal().getInstallationOptions();
            task.setTimeOutInMs(installationOptions.getTimeOutInMs());
            task.setInstallOptions(installationOptions.getInstallOptions());

            SdkComponentsKt.initialize(task.getBuildTools(), task, creationConfig);

            task.getDexMetadataDirectory()
                    .set(
                            creationConfig
                                    .getArtifacts()
                                    .get(InternalArtifactType.DEX_METADATA_DIRECTORY.INSTANCE));
            task.getDexMetadataDirectory().disallowChanges();
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<InstallVariantTask> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig.getTaskContainer().setInstallTask(taskProvider);
        }
    }
}
