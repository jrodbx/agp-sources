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

import com.android.annotations.NonNull;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.gradle.internal.AdbExecutableInput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.test.BuiltArtifactsSplitOutputMatcher;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.utils.HasConfigurableValuesKt;
import com.android.builder.internal.InstallUtils;
import com.android.builder.testing.api.DeviceConfigProviderImpl;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
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
        final ILogger iLogger = new LoggerWrapper(getLogger());
        DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(
                        getAdbExecutableInput().getAdbExecutable(), getTimeOutInMs(), iLogger);
        deviceProvider.use(
                () -> {
                    BuiltArtifacts builtArtifacts =
                            new BuiltArtifactsLoaderImpl().load(getApkDirectory().get());

                    install(
                            getProjectName(),
                            variantName,
                            deviceProvider,
                            minSdkVersion,
                            builtArtifacts,
                            supportedAbis,
                            getInstallOptions(),
                            getTimeOutInMs(),
                            getLogger());

                    return null;
                });
    }

    static void install(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull DeviceProvider deviceProvider,
            @NonNull AndroidVersion minSkdVersion,
            @NonNull BuiltArtifacts builtArtifacts,
            @NonNull Set<String> supportedAbis,
            @NonNull Collection<String> installOptions,
            int timeOutInMs,
            @NonNull Logger logger)
            throws DeviceException, ProcessException {
        ILogger iLogger = new LoggerWrapper(logger);
        int successfulInstallCount = 0;
        List<? extends DeviceConnector> devices = deviceProvider.getDevices();
        for (final DeviceConnector device : devices) {
            if (InstallUtils.checkDeviceApiLevel(
                    device, minSkdVersion, iLogger, projectName, variantName)) {
                // When InstallUtils.checkDeviceApiLevel returns false, it logs the reason.
                final List<File> apkFiles =
                        BuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(
                                new DeviceConfigProviderImpl(device),
                                builtArtifacts,
                                supportedAbis);

                if (apkFiles.isEmpty()) {
                    logger.lifecycle(
                            "Skipping device '{}' for '{}:{}': Could not find build of variant "
                                    + "which supports density {} and an ABI in {}",
                            device.getName(),
                            projectName,
                            variantName,
                            device.getDensity(),
                            Joiner.on(", ").join(device.getAbis()));
                } else {
                    logger.lifecycle(
                            "Installing APK '{}' on '{}' for {}:{}",
                            FileUtils.getNamesAsCommaSeparatedList(apkFiles),
                            device.getName(),
                            projectName,
                            variantName);

                    final Collection<String> extraArgs =
                            MoreObjects.firstNonNull(installOptions, ImmutableList.of());

                    if (apkFiles.size() > 1) {
                        device.installPackages(apkFiles, extraArgs, timeOutInMs, iLogger);
                        successfulInstallCount++;
                    } else {
                        device.installPackage(apkFiles.get(0), extraArgs, timeOutInMs, iLogger);
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

    @Nested
    public abstract AdbExecutableInput getAdbExecutableInput();

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
            task.supportedAbis = creationConfig.getVariantDslInfo().getSupportedAbis();
            task.minSdkVersion = creationConfig.getMinSdkVersion();

            task.setDescription("Installs the " + creationConfig.getDescription() + ".");
            task.setGroup(TaskManager.INSTALL_GROUP);
            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.APK.INSTANCE, task.getApkDirectory());
            task.setTimeOutInMs(
                    creationConfig
                            .getGlobalScope()
                            .getExtension()
                            .getAdbOptions()
                            .getTimeOutInMs());
            task.setInstallOptions(
                    creationConfig
                            .getGlobalScope()
                            .getExtension()
                            .getAdbOptions()
                            .getInstallOptions());
            HasConfigurableValuesKt.setDisallowChanges(
                    task.getAdbExecutableInput().getSdkBuildService(),
                    BuildServicesKt.getBuildService(
                            creationConfig.getServices().getBuildServiceRegistry(),
                            SdkComponentsBuildService.class));
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<InstallVariantTask> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig.getTaskContainer().setInstallTask(taskProvider);
        }
    }
}
