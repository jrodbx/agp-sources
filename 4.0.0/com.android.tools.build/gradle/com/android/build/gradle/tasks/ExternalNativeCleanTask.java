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

import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.infoln;
import static com.android.build.gradle.internal.cxx.model.CreateCxxAbiModelKt.createCxxAbiModel;
import static com.android.build.gradle.internal.cxx.model.CreateCxxVariantModelKt.createCxxVariantModel;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getJsonFile;
import static com.android.build.gradle.internal.cxx.process.ProcessOutputJunctionKt.createProcessOutputJunction;
import static com.android.build.gradle.internal.cxx.settings.CxxAbiModelCMakeSettingsRewriterKt.rewriteCxxAbiModelWithCMakeSettings;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxModuleModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.errors.DefaultIssueReporter;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.process.ExecOperations;

/**
 * Task that takes set of JSON files of type NativeBuildConfigValue and does clean steps with them.
 *
 * <p>It declares no inputs or outputs, as it's supposed to always run when invoked. Incrementality
 * is left to the underlying build system.
 */
public abstract class ExternalNativeCleanTask extends NonIncrementalTask {
    private CxxVariantModel variant;
    private List<CxxAbiModel> abis;
    @NonNull private final ExecOperations execOperations;

    @Inject
    public ExternalNativeCleanTask(@NonNull ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Override
    protected void doTaskAction() throws ProcessException, IOException {
        try (ThreadLoggingEnvironment ignore =
                new IssueReporterLoggingEnvironment(
                        new DefaultIssueReporter(new LoggerWrapper(getLogger())))) {
            infoln("starting clean");
            infoln("finding existing JSONs");

            List<File> existingJsons = Lists.newArrayList();
            for (CxxAbiModel abi : abis) {
                if (getJsonFile(abi).isFile()) {
                    existingJsons.add(getJsonFile(abi));
                } else {
                    // This is infoln instead of warnln because clean considers all possible
                    // ABIs while cleaning
                    infoln(
                            "Json file not found so contents couldn't be cleaned %s",
                            getJsonFile(abi));
                }
            }

            List<NativeBuildConfigValueMini> configValueList =
                    AndroidBuildGradleJsons.getNativeBuildMiniConfigs(existingJsons, null);
            List<String> cleanCommands = Lists.newArrayList();
            List<String> targetNames = Lists.newArrayList();
            for (NativeBuildConfigValueMini config : configValueList) {
                cleanCommands.addAll(config.cleanCommands);
                Set<String> targets = Sets.newHashSet();
                for (NativeLibraryValueMini library : config.libraries.values()) {
                    targets.add(String.format("%s %s", library.artifactName, library.abi));
                }
                targetNames.add(Joiner.on(",").join(targets));
            }
            infoln("about to execute %s clean commands", cleanCommands.size());
            executeProcessBatch(cleanCommands, targetNames);
            infoln("clean complete");
        }
    }

    /**
     * Given a list of build commands, execute each. If there is a failure, processing is stopped at
     * that point.
     */
    private void executeProcessBatch(
            @NonNull List<String> commands, @NonNull List<String> targetNames)
            throws ProcessException, IOException {
        for (int commandIndex = 0; commandIndex < commands.size(); ++commandIndex) {
            String command = commands.get(commandIndex);
            String target = targetNames.get(commandIndex);
            getLogger().lifecycle(String.format("Clean %s", target));
            List<String> tokens = StringHelper.tokenizeCommandLineToEscaped(command);
            ProcessInfoBuilder processBuilder = new ProcessInfoBuilder();
            processBuilder.setExecutable(tokens.get(0));
            for (int i = 1; i < tokens.size(); ++i) {
                processBuilder.addArgs(tokens.get(i));
            }
            infoln("%s", processBuilder);
            createProcessOutputJunction(
                            variant.getObjFolder(),
                            "android_gradle_clean_" + commandIndex,
                            processBuilder,
                            getLogger(),
                            new GradleProcessExecutor(execOperations::exec),
                            "")
                    .logStderrToInfo()
                    .logStdoutToInfo()
                    .execute(execOperations::exec);
        }
    }

    public static class CreationAction extends VariantTaskCreationAction<ExternalNativeCleanTask> {
        @NonNull private final CxxVariantModel variant;
        @NonNull private final List<CxxAbiModel> abis = Lists.newArrayList();

        public CreationAction(@NonNull CxxModuleModel module, @NonNull VariantScope scope) {
            super(scope);
            this.variant = createCxxVariantModel(module, scope);
            // Attempt to clean every possible ABI even those that aren't currently built.
            // This covers cases where user has changed abiFilters or platform. We don't want
            // to leave stale results hanging around.
            for (Abi abi : Abi.values()) {
                abis.add(
                        rewriteCxxAbiModelWithCMakeSettings(
                                createCxxAbiModel(
                                        variant,
                                        abi,
                                        scope.getGlobalScope(),
                                        scope.getVariantData())));
            }

        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("externalNativeBuildClean");
        }

        @NonNull
        @Override
        public Class<ExternalNativeCleanTask> getType() {
            return ExternalNativeCleanTask.class;
        }

        @Override
        public void configure(@NonNull ExternalNativeCleanTask task) {
            super.configure(task);
            task.variant = variant;
            task.abis = abis;
        }
    }
}
