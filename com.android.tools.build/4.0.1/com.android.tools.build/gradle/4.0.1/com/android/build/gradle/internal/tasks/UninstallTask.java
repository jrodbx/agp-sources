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
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.testing.ConnectedDeviceProvider;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

public abstract class UninstallTask extends NonIncrementalTask {

    private BaseVariantData variant;

    private int mTimeOutInMs = 0;

    private Provider<File> adbExecutableProvider;

    public UninstallTask() {
        this.getOutputs().upToDateWhen(task -> {
            getLogger().debug("Uninstall task is always run.");
            return false;
        });
    }

    @Override
    protected void doTaskAction() throws DeviceException, ExecutionException {
        final Logger logger = getLogger();
        final String applicationId = variant.getVariantDslInfo().getApplicationId();

        logger.info("Uninstalling app: {}", applicationId);

        final ILogger iLogger = new LoggerWrapper(getLogger());
        final DeviceProvider deviceProvider =
                new ConnectedDeviceProvider(adbExecutableProvider.get(), getTimeOutInMs(), iLogger);

        deviceProvider.use(
                () -> {
                    final List<? extends DeviceConnector> devices = deviceProvider.getDevices();

                    for (DeviceConnector device : devices) {
                        device.uninstallPackage(applicationId, getTimeOutInMs(), iLogger);
                        logger.lifecycle(
                                "Uninstalling {} (from {}:{}) from device '{}' ({}).",
                                applicationId,
                                getProject().getName(),
                                variant.getName(),
                                device.getName(),
                                device.getSerialNumber());
                    }

                    int n = devices.size();
                    logger.quiet(
                            "Uninstalled {} from {} device{}.",
                            applicationId,
                            n,
                            n == 1 ? "" : "s");

                    return null;
                });
    }

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public File getAdbExe() {
        return adbExecutableProvider.get();
    }

    @Internal("This task is always executed")
    public BaseVariantData getVariant() {
        return variant;
    }

    public void setVariant(BaseVariantData variant) {
        this.variant = variant;
    }

    @Input
    public int getTimeOutInMs() {
        return mTimeOutInMs;
    }

    public void setTimeOutInMs(int timeoutInMs) {
        mTimeOutInMs = timeoutInMs;
    }

    public static class CreationAction extends VariantTaskCreationAction<UninstallTask> {

        public CreationAction(VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return StringHelper.appendCapitalized("uninstall", getVariantScope().getName());
        }

        @NonNull
        @Override
        public Class<UninstallTask> getType() {
            return UninstallTask.class;
        }

        @Override
        public void configure(@NonNull UninstallTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.setVariant(scope.getVariantData());
            task.setDescription("Uninstalls the " + scope.getVariantData().getDescription() + ".");
            task.setGroup(TaskManager.INSTALL_GROUP);
            task.setTimeOutInMs(
                    scope.getGlobalScope().getExtension().getAdbOptions().getTimeOutInMs());

            task.adbExecutableProvider =
                    scope.getGlobalScope().getSdkComponents().getAdbExecutableProvider();

        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends UninstallTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setUninstallTask(taskProvider);
        }
    }
}
