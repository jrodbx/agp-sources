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

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.isCmakeForkVersion;
import static com.android.build.gradle.internal.cxx.configure.ExternalNativeJsonGenerationUtilKt.registerWriteModelAfterJsonGeneration;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.errorln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.infoln;
import static com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironmentKt.toJsonString;
import static com.android.build.gradle.internal.cxx.model.CreateCxxAbiModelKt.createCxxAbiModel;
import static com.android.build.gradle.internal.cxx.model.CreateCxxVariantModelKt.createCxxVariantModel;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getBuildCommandFile;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getBuildOutputFile;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getJsonFile;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getJsonGenerationLoggingRecordFile;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getPrefabConfigFile;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.shouldGeneratePrefabPackages;
import static com.android.build.gradle.internal.cxx.model.GetCxxBuildModelKt.getCxxBuildModel;
import static com.android.build.gradle.internal.cxx.services.CxxBuildModelListenerServiceKt.executeListenersOnceBeforeJsonGeneration;
import static com.android.build.gradle.internal.cxx.services.CxxEvalIssueReporterServiceKt.issueReporter;
import static com.android.build.gradle.internal.cxx.services.CxxModelDependencyServiceKt.jsonGenerationInputDependencyFileCollection;
import static com.android.build.gradle.internal.cxx.services.CxxSyncListenerServiceKt.executeListenersOnceAfterJsonGeneration;
import static com.android.build.gradle.internal.cxx.settings.CxxAbiModelCMakeSettingsRewriterKt.getBuildCommandArguments;
import static com.android.build.gradle.internal.cxx.settings.CxxAbiModelCMakeSettingsRewriterKt.rewriteCxxAbiModelWithCMakeSettings;
import static com.android.build.gradle.tasks.GeneratePrefabPackagesKt.generatePrefabPackages;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationInvalidationState;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini;
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.PassThroughPrefixingLoggingEnvironment;
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxAbiModelKt;
import com.android.build.gradle.internal.cxx.model.CxxBuildModel;
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel;
import com.android.build.gradle.internal.cxx.model.CxxModuleModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModelKt;
import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleBuildVariant.NativeBuildConfigInfo.GenerationOutcome;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

/**
 * Base class for generation of native JSON.
 */
public abstract class ExternalNativeJsonGenerator {

    @NonNull protected final CxxBuildModel build;
    @NonNull protected final CxxVariantModel variant;
    @NonNull protected final List<CxxAbiModel> abis;
    @NonNull protected final GradleBuildVariant.Builder stats;


    ExternalNativeJsonGenerator(
            @NonNull CxxBuildModel build,
            @NonNull CxxVariantModel variant,
            @NonNull List<CxxAbiModel> abis,
            @NonNull GradleBuildVariant.Builder stats) {
        this.build = build;
        this.variant = variant;
        this.abis = abis;
        this.stats = stats;
    }

    /**
     * Returns true if platform is windows
     */
    protected static boolean isWindows() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }

    @NonNull
    private List<File> getDependentBuildFiles(@NonNull File json) throws IOException {
        List<File> result = Lists.newArrayList();
        if (!json.exists()) {
            return result;
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        NativeBuildConfigValueMini config =
                AndroidBuildGradleJsons.getNativeBuildMiniConfig(json, stats);

        // If anything in the prefab package changes, re-run. Note that this also depends on the
        // directories, so added/removed files will also trigger a re-run.
        for (File pkgDir : variant.getPrefabPackageDirectoryList()) {
            Files.walk(pkgDir.toPath()).forEach(it -> result.add(it.toFile()));
        }
        result.addAll(config.buildFiles);
        return result;
    }

    public void build(
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation)
            throws IOException, ProcessException {
        buildAndPropagateException(false, execOperation, javaExecOperation);
    }

    public void build(
            boolean forceJsonGeneration,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation) {
        try {
            infoln("building json with force flag %s", forceJsonGeneration);
            buildAndPropagateException(forceJsonGeneration, execOperation, javaExecOperation);
        } catch (@NonNull IOException | GradleException e) {
            errorln("exception while building Json $%s", e.getMessage());
        } catch (ProcessException e) {
            errorln(
                    "executing external native build for %s %s",
                    getNativeBuildSystem().getTag(), variant.getModule().getMakeFile());
        }
    }

    public List<Callable<Void>> parallelBuild(
            boolean forceJsonGeneration,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation) {
        List<Callable<Void>> buildSteps = new ArrayList<>(abis.size());
        // These are lazily initialized values that can only be computed from a Gradle managed
        // thread. Compute now so that we don't in the worker threads that we'll be running as.
        variant.getPrefabPackageDirectoryList();
        variant.getModule().getProject().getPrefabClassPath();
        for (CxxAbiModel abi : abis) {
            buildSteps.add(
                    () ->
                            buildForOneConfigurationConvertExceptions(
                                    forceJsonGeneration, abi, execOperation, javaExecOperation));
        }
        return buildSteps;
    }

    @Nullable
    private Void buildForOneConfigurationConvertExceptions(
            boolean forceJsonGeneration,
            CxxAbiModel abi,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation) {
        try (ThreadLoggingEnvironment ignore =
                new IssueReporterLoggingEnvironment(issueReporter(abi.getVariant().getModule()))) {
            try {
                buildForOneConfiguration(
                        forceJsonGeneration, abi, execOperation, javaExecOperation);
            } catch (@NonNull IOException | GradleException e) {
                errorln("exception while building Json %s", e.getMessage());
            } catch (ProcessException e) {
                errorln(
                        "executing external native build for %s %s",
                        getNativeBuildSystem().getTag(), variant.getModule().getMakeFile());
            }
            return null;
        }
    }

    @NonNull
    private static String getPreviousBuildCommand(@NonNull File commandFile) throws IOException {
        if (!commandFile.exists()) {
            return "";
        }
        return new String(Files.readAllBytes(commandFile.toPath()), Charsets.UTF_8);
    }

    @NonNull
    private static PrefabConfigurationState getPreviousPrefabConfigurationState(
            @NonNull File prefabStateFile) throws IOException {
        if (!prefabStateFile.exists()) {
            return new PrefabConfigurationState(false, null, Collections.emptyList());
        }

        return PrefabConfigurationState.fromJson(
                new String(Files.readAllBytes(prefabStateFile.toPath()), Charsets.UTF_8));
    }

    protected void checkPrefabConfig() {}

    private void buildAndPropagateException(
            boolean forceJsonGeneration,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation)
            throws IOException, ProcessException {
        Exception firstException = null;
        for (CxxAbiModel abi : abis) {
            try {
                buildForOneConfiguration(
                        forceJsonGeneration, abi, execOperation, javaExecOperation);
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }

        if (firstException != null) {
            if (firstException instanceof GradleException) {
                throw (GradleException) firstException;
            }

            if (firstException instanceof IOException) {
                throw (IOException) firstException;
            }

            throw (ProcessException) firstException;
        }
    }

    public void buildForOneAbiName(
            boolean forceJsonGeneration,
            String abiName,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation) {
        int built = 0;
        for (CxxAbiModel abi : abis) {
            if (!abi.getAbi().getTag().equals(abiName)) {
                continue;
            }
            built++;
            buildForOneConfigurationConvertExceptions(
                    forceJsonGeneration, abi, execOperation, javaExecOperation);
        }
        assert (built == 1);
    }

    private void buildForOneConfiguration(
            boolean forceJsonGeneration,
            CxxAbiModel abi,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation,
            @NonNull Function<Action<? super JavaExecSpec>, ExecResult> javaExecOperation)
            throws GradleException, IOException, ProcessException {
        try (PassThroughPrefixingLoggingEnvironment recorder =
                new PassThroughPrefixingLoggingEnvironment(
                        abi.getVariant().getModule().getMakeFile(),
                        abi.getVariant().getVariantName() + "|" + abi.getAbi().getTag())) {

            GradleBuildVariant.NativeBuildConfigInfo.Builder variantStats =
                    GradleBuildVariant.NativeBuildConfigInfo.newBuilder();
            variantStats.setAbi(AnalyticsUtil.getAbi(abi.getAbi().getTag()));
            variantStats.setDebuggable(variant.isDebuggableEnabled());
            long startTime = System.currentTimeMillis();
            variantStats.setGenerationStartMs(startTime);
            try {
                infoln(
                        "Start JSON generation. Platform version: %s min SDK version: %s",
                        abi.getAbiPlatformVersion(),
                        abi.getAbi().getTag(),
                        abi.getAbiPlatformVersion());
                if (!executeListenersOnceBeforeJsonGeneration(build)) {
                    infoln("Errors seen in validation before JSON generation started");
                    return;
                }
                ProcessInfoBuilder processBuilder = getProcessBuilder(abi);

                // See whether the current build command matches a previously written build command.
                String currentBuildCommand =
                        processBuilder.toString()
                                + "Build command args:"
                                + getBuildCommandArguments(abi)
                                + "\n";

                PrefabConfigurationState prefabState =
                        new PrefabConfigurationState(
                                abi.getVariant().getModule().getProject().isPrefabEnabled(),
                                abi.getVariant().getModule().getProject().getPrefabClassPath(),
                                variant.getPrefabPackageDirectoryList());

                PrefabConfigurationState previousPrefabState =
                        getPreviousPrefabConfigurationState(getPrefabConfigFile(abi));

                JsonGenerationInvalidationState invalidationState =
                        new JsonGenerationInvalidationState(
                                forceJsonGeneration,
                                getJsonFile(abi),
                                getBuildCommandFile(abi),
                                currentBuildCommand,
                                getPreviousBuildCommand(getBuildCommandFile(abi)),
                                getDependentBuildFiles(getJsonFile(abi)),
                                prefabState,
                                previousPrefabState);

                if (invalidationState.getRebuild()) {
                    infoln("rebuilding JSON %s due to:", getJsonFile(abi));
                    for (String reason : invalidationState.getRebuildReasons()) {
                        infoln(reason);
                    }

                    if (shouldGeneratePrefabPackages(abi)) {
                        checkPrefabConfig();
                        generatePrefabPackages(
                                getNativeBuildSystem(),
                                variant.getModule(),
                                abi,
                                variant.getPrefabPackageDirectoryList(),
                                javaExecOperation);
                    }

                    // Related to https://issuetracker.google.com/69408798
                    // Something has changed so we need to clean up some build intermediates and
                    // outputs.
                    // - If only a build file has changed then we try to keep .o files and,
                    // in the case of CMake, the generated Ninja project. In this case we must
                    // remove .so files because they are automatically packaged in the APK on a
                    // *.so basis.
                    // - If there is some other cause to recreate the JSon, such as command-line
                    // changed then wipe out the whole JSon folder.
                    if (abi.getCxxBuildFolder().getParentFile().exists()) {
                        if (invalidationState.getSoftRegeneration()) {
                            infoln(
                                    "keeping json folder '%s' but regenerating project",
                                    abi.getCxxBuildFolder());

                        } else {
                            infoln("removing stale contents from '%s'", abi.getCxxBuildFolder());
                            FileUtils.deletePath(abi.getCxxBuildFolder());
                        }
                    }

                    if (abi.getCxxBuildFolder().mkdirs()) {
                        infoln("created folder '%s'", abi.getCxxBuildFolder());
                    }

                    infoln("executing %s %s", getNativeBuildSystem().getTag(), processBuilder);
                    String buildOutput = executeProcess(abi, execOperation);
                    infoln("done executing %s", getNativeBuildSystem().getTag());

                    // Write the captured process output to a file for diagnostic purposes.
                    infoln("write build output %s", getBuildOutputFile(abi).getAbsolutePath());
                    Files.write(
                            getBuildOutputFile(abi).toPath(), buildOutput.getBytes(Charsets.UTF_8));
                    processBuildOutput(buildOutput, abi);

                    if (!getJsonFile(abi).exists()) {
                        throw new GradleException(
                                String.format(
                                        "Expected json generation to create '%s' but it didn't",
                                        getJsonFile(abi)));
                    }

                    synchronized (stats) {
                        // Related to https://issuetracker.google.com/69408798
                        // Targets may have been removed or there could be other orphaned extra .so
                        // files. Remove these and rely on the build step to replace them if they are
                        // legitimate. This is to prevent unexpected .so files from being packaged in
                        // the APK.
                        removeUnexpectedSoFiles(
                                CxxAbiModelKt.getSoFolder(abi),
                                AndroidBuildGradleJsons.getNativeBuildMiniConfig(
                                        getJsonFile(abi), stats));
                    }

                    // Write the ProcessInfo to a file, this has all the flags used to generate the
                    // JSON. If any of these change later the JSON will be regenerated.
                    infoln("write command file %s", getBuildCommandFile(abi).getAbsolutePath());
                    Files.write(
                            getBuildCommandFile(abi).toPath(),
                            currentBuildCommand.getBytes(Charsets.UTF_8));

                    // Persist the prefab configuration as well.
                    Files.write(
                            getPrefabConfigFile(abi).toPath(),
                            prefabState.toJsonString().getBytes(Charsets.UTF_8));

                    // Record the outcome. JSON was built.
                    variantStats.setOutcome(GenerationOutcome.SUCCESS_BUILT);
                } else {
                    infoln("JSON '%s' was up-to-date", getJsonFile(abi));
                    variantStats.setOutcome(GenerationOutcome.SUCCESS_UP_TO_DATE);
                }
                infoln("JSON generation completed without problems");
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                variantStats.setOutcome(GenerationOutcome.FAILED);
                infoln("JSON generation completed with problem. Exception: " + e.toString());
                throw e;
            } finally {
                variantStats.setGenerationDurationMs(System.currentTimeMillis() - startTime);
                synchronized (stats) {
                    stats.addNativeBuildConfig(variantStats);
                }
                getJsonGenerationLoggingRecordFile(abi).getParentFile().mkdirs();
                Files.write(
                        getJsonGenerationLoggingRecordFile(abi).toPath(),
                        toJsonString(recorder.getRecord()).getBytes(Charsets.UTF_8));
                executeListenersOnceAfterJsonGeneration(abi);
            }
        }
    }

    /**
     * This function removes unexpected so files from disk. Unexpected means they exist on disk but
     * are not present as a build output from the json.
     *
     * <p>It is generally valid for there to be extra .so files because the build system may copy
     * libraries to the output folder. This function is meant to be used in cases where we suspect
     * the .so may have been orphaned by the build system due to a change in build files.
     *
     * @param expectedOutputFolder the expected location of output .so files
     * @param config the existing miniconfig
     * @throws IOException in the case that json is missing or can't be read or some other IO
     *     problem.
     */
    private static void removeUnexpectedSoFiles(
            @NonNull File expectedOutputFolder, @NonNull NativeBuildConfigValueMini config)
            throws IOException {
        if (!expectedOutputFolder.isDirectory()) {
            // Nothing to clean
            return;
        }

        // Gather all expected build outputs
        List<Path> expectedSoFiles = Lists.newArrayList();
        for (NativeLibraryValueMini library : config.libraries.values()) {
            assert library.output != null;
            expectedSoFiles.add(library.output.toPath());
        }

        try (Stream<Path> paths = Files.walk(expectedOutputFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".so"))
                    .filter(path -> !expectedSoFiles.contains(path))
                    .forEach(
                            path -> {
                                if (path.toFile().delete()) {
                                    infoln(
                                            "deleted unexpected build output %s in incremental "
                                                    + "regenerate",
                                            path);
                                }
                            });
        }
    }

    /**
     * Derived class implements this method to post-process build output. NdkPlatform-build uses
     * this to capture and analyze the compile and link commands that were written to stdout.
     */
    abstract void processBuildOutput(@NonNull String buildOutput, @NonNull CxxAbiModel abiConfig)
            throws IOException;

    @NonNull
    abstract ProcessInfoBuilder getProcessBuilder(@NonNull CxxAbiModel abi);

    /**
     * Executes the JSON generation process. Return the combination of STDIO and STDERR from running
     * the process.
     *
     * @return Returns the combination of STDIO and STDERR from running the process.
     */
    abstract String executeProcess(
            @NonNull CxxAbiModel abi,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation)
            throws ProcessException, IOException;

    /** @return the native build system that is used to generate the JSON. */
    @Input
    @NonNull
    public abstract NativeBuildSystem getNativeBuildSystem();

    /** @return a map of Abi to STL shared object (.so files) that should be copied. */
    @Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    @NonNull
    abstract Map<Abi, File> getStlSharedObjectFiles();

    /** @return the variant name for this generator */
    @Input
    @NonNull
    public String getVariantName() {
        return variant.getVariantName();
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull CxxModuleModel module, @NonNull VariantScope scope) {
        try (ThreadLoggingEnvironment ignore =
                new IssueReporterLoggingEnvironment(issueReporter(module))) {
            return createImpl(module, scope);
        }
    }

    @NonNull
    private static ExternalNativeJsonGenerator createImpl(
            @NonNull CxxModuleModel module, @NonNull VariantScope scope) {
        CxxVariantModel variant = createCxxVariantModel(module, scope);
        List<CxxAbiModel> abis = Lists.newArrayList();

        CxxBuildModel cxxBuildModel =
                getCxxBuildModel(scope.getGlobalScope().getProject().getGradle());
        for (Abi abi : variant.getValidAbiList()) {
            CxxAbiModel model =
                    rewriteCxxAbiModelWithCMakeSettings(
                            createCxxAbiModel(
                                    variant, abi, scope.getGlobalScope(), scope.getVariantData()));
            abis.add(model);

            // Register callback to write Json after generation finishes.
            // We don't write it now because sync configuration is executing. We want to defer
            // until model building.
            registerWriteModelAfterJsonGeneration(model);
        }

        GradleBuildVariant.Builder stats =
                ProcessProfileWriter.getOrCreateVariant(
                        module.getGradleModulePathName(), scope.getName());

        switch (module.getBuildSystem()) {
            case NDK_BUILD:
                return new NdkBuildExternalNativeJsonGenerator(cxxBuildModel, variant, abis, stats);
            case CMAKE:
                CxxCmakeModuleModel cmake = Objects.requireNonNull(variant.getModule().getCmake());
                Revision cmakeRevision = cmake.getMinimumCmakeVersion();
                stats.setNativeCmakeVersion(cmakeRevision.toString());
                if (isCmakeForkVersion(cmakeRevision)) {
                    return new CmakeAndroidNinjaExternalNativeJsonGenerator(
                            cxxBuildModel, variant, abis, stats);
                }

                if (cmakeRevision.getMajor() < 3
                        || (cmakeRevision.getMajor() == 3 && cmakeRevision.getMinor() <= 6)) {
                    throw new RuntimeException(
                            "Unexpected/unsupported CMake version "
                                    + cmakeRevision.toString()
                                    + ". Try 3.7.0 or later.");
                }

                return new CmakeServerExternalNativeJsonGenerator(
                        cxxBuildModel, variant, abis, stats);
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
    }


    public void forEachNativeBuildConfiguration(@NonNull Consumer<JsonReader> callback)
            throws IOException {
        try (ThreadLoggingEnvironment ignore =
                new IssueReporterLoggingEnvironment(issueReporter(variant.getModule()))) {
            List<File> files = getNativeBuildConfigurationsJsons();
            infoln("streaming %s JSON files", files.size());
            for (File file : getNativeBuildConfigurationsJsons()) {
                if (file.exists()) {
                    infoln("string JSON file %s", file.getAbsolutePath());
                    try (JsonReader reader = new JsonReader(new FileReader(file))) {
                        callback.accept(reader);
                    } catch (Throwable e) {
                        infoln(
                                "Error parsing: %s",
                                String.join("\r\n", Files.readAllLines(file.toPath())));
                        throw e;
                    }
                } else {
                    // If the tool didn't create the JSON file then create fallback with the
                    // information we have so the user can see partial information in the UI.
                    infoln("streaming fallback JSON for %s", file.getAbsolutePath());
                    NativeBuildConfigValueMini fallback = new NativeBuildConfigValueMini();
                    fallback.buildFiles = Lists.newArrayList(variant.getModule().getMakeFile());
                    try (JsonReader reader =
                            new JsonReader(new StringReader(new Gson().toJson(fallback)))) {
                        callback.accept(reader);
                    }
                }
            }
        }
    }

    @Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    @NonNull
    public CxxVariantModel getVariant() {
        return this.variant;
    }

    @NonNull
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public File getMakefile() {
        return variant.getModule().getMakeFile();
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public String getObjFolder() {
        return variant.getObjFolder().getPath();
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public String getNdkFolder() {
        return variant.getModule().getNdkFolder().getPath();
    }

    @Input
    public boolean isDebuggable() {
        return variant.isDebuggableEnabled();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @NonNull
    public FileCollection getJsonGenerationDependencyFiles() {
        return jsonGenerationInputDependencyFileCollection(variant.getModule(), abis);
    }

    @NonNull
    @Optional
    @Input
    public List<String> getBuildArguments() {
        return variant.getBuildSystemArgumentList();
    }

    @NonNull
    @Optional
    @Input
    public List<String> getcFlags() {
        return variant.getCFlagsList();
    }

    @NonNull
    @Optional
    @Input
    public List<String> getCppFlags() {
        return variant.getCppFlagsList();
    }

    @NonNull
    @OutputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        List<File> generatedJsonFiles = new ArrayList<>();
        for (CxxAbiModel abi : abis) {
            generatedJsonFiles.add(getJsonFile(abi));
        }
        return generatedJsonFiles;
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public String getSoFolder() {
        return CxxVariantModelKt.getSoFolder(variant).getPath();
    }

    @NonNull
    @Input // We don't need contents of the files in the generated JSON, just the path.
    public String getSdkFolder() {
        return variant.getModule().getProject().getSdkFolder().getPath();
    }

    @Input
    @NonNull
    public Collection<Abi> getAbis() {
        List<Abi> result = Lists.newArrayList();
        for (CxxAbiModel abi : abis) {
            result.add(abi.getAbi());
        }
        return result;
    }
}
