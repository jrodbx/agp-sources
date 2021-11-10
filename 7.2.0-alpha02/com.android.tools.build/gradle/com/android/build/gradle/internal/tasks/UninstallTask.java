/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.gradle.internal.BuildToolsExecutableInput;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.SdkComponentsKt;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.component.ApkCreationConfig;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.utils.ILogger;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault
public abstract class UninstallTask extends NonIncrementalTask {

    private String variantName;
    private String projectName;
    // these are not inputs so we don't need the task to have its own Property
    private Provider<String> applicationId;

    private int mTimeOutInMs = 0;

    public UninstallTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Uninstall task is always run.");
            return false;
        });
    }

    @Override
    protected void doTaskAction() throws DeviceException, ExecutionException {
        final Logger logger = getLogger();

        logger.info("Uninstalling app: {}", applicationId);

        final ILogger iLogger = new LoggerWrapper(getLogger());
        final DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(
                        getBuildToolsExecutableInput().adbExecutable(),
                        getTimeOutInMs(),
                        iLogger,
                        System.getenv("ANDROID_SERIAL"));

        deviceProvider.use(
                () -> {
                    final List<? extends DeviceConnector> devices = deviceProvider.getDevices();

                    for (DeviceConnector device : devices) {
                        device.uninstallPackage(applicationId.get(), getTimeOutInMs(), iLogger);
                        logger.lifecycle(
                                "Uninstalling {} (from {}:{}) from device '{}' ({}).",
                                applicationId.get(),
                                projectName,
                                variantName,
                                device.getName(),
                                device.getSerialNumber());
                    }

                    int n = devices.size();
                    logger.quiet(
                            "Uninstalled {} from {} device{}.",
                            applicationId.get(),
                            n,
                            n == 1 ? "" : "s");

                    return null;
                });
    }

    @Input
    public int getTimeOutInMs() {
        return mTimeOutInMs;
    }

    @Nested
    public abstract BuildToolsExecutableInput getBuildToolsExecutableInput();

    public void setTimeOutInMs(int timeoutInMs) {
        mTimeOutInMs = timeoutInMs;
    }

    public static class CreationAction
            extends VariantTaskCreationAction<UninstallTask, ApkCreationConfig> {

        public CreationAction(@NonNull ApkCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("uninstall");
        }

        @NonNull
        @Override
        public Class<UninstallTask> getType() {
            return UninstallTask.class;
        }

        @Override
        public void configure(@NonNull UninstallTask task) {
            super.configure(task);

            task.variantName = creationConfig.getName();
            task.projectName = task.getProject().getName();
            task.applicationId = creationConfig.getApplicationId();
            task.setDescription("Uninstalls the " + creationConfig.getDescription() + ".");
            task.setGroup(TaskManager.INSTALL_GROUP);
            task.setTimeOutInMs(
                    creationConfig
                            .getGlobalScope()
                            .getExtension()
                            .getAdbOptions()
                            .getTimeOutInMs());

            SdkComponentsKt.initialize(task.getBuildToolsExecutableInput(), creationConfig);
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<UninstallTask> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig.getTaskContainer().setUninstallTask(taskProvider);
        }
    }
}
