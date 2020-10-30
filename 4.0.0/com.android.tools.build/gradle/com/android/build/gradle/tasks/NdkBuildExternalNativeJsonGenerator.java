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
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.errorln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.infoln;
import static com.android.build.gradle.internal.cxx.logging.LoggingEnvironmentKt.warnln;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getJsonFile;
import static com.android.build.gradle.internal.cxx.services.CxxProcessServiceKt.createProcessOutputJunction;

import com.android.annotations.NonNull;
import com.android.build.gradle.external.gnumake.NativeBuildConfigValueBuilder;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.internal.cxx.model.CxxAbiModelKt;
import com.android.build.gradle.internal.cxx.model.CxxBuildModel;
import com.android.build.gradle.internal.cxx.model.CxxVariantModel;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.gradle.api.Action;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

/**
 * ndk-build JSON generation logic. This is separated from the corresponding ndk-build task so that
 * JSON can be generated during configuration.
 */
class NdkBuildExternalNativeJsonGenerator extends ExternalNativeJsonGenerator {

    NdkBuildExternalNativeJsonGenerator(
            @NonNull CxxBuildModel build,
            @NonNull CxxVariantModel variant,
            @NonNull List<CxxAbiModel> abis,
            @NonNull GradleBuildVariant.Builder stats) {
        super(build, variant, abis, stats);
        this.stats.setNativeBuildSystemType(
                GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD);

        // Do some basic sync time checks.
        if (getMakefile().isDirectory()) {
            errorln(
                    "Gradle project ndkBuild.path %s is a folder. "
                            + "Only files (like Android.mk) are allowed.",
                    getMakefile());
        } else if (!getMakefile().exists()) {
            errorln(
                    "Gradle project ndkBuild.path is %s but that file doesn't exist",
                    getMakefile());
        }
    }

    @Override
    void processBuildOutput(@NonNull String buildOutput, @NonNull CxxAbiModel abi)
            throws IOException {
        // Discover Application.mk if one exists next to Android.mk
        // If there is an Application.mk file next to Android.mk then pick it up.
        File applicationMk = new File(getMakeFile().getParent(), "Application.mk");

        // Write the captured ndk-build output to a file for diagnostic purposes.
        infoln("parse and convert ndk-build output to build configuration JSON");

        // Tasks, including the Exec task used to execute ndk-build, will execute in the same folder
        // as the module build.gradle. However, parsing of ndk-build output doesn't necessarily
        // happen within a task because it may be done at sync time. The parser needs to create
        // absolute paths for source files that have relative paths in the ndk-build output. For
        // this reason, we need to tell the parser the folder of the module build.gradle. This is
        // 'projectDir'.
        //
        // Example, if a project is set up as follows:
        //
        //   project/build.gradle
        //   project/app/build.gradle
        //
        // Then, right now, the current folder is 'project/' but ndk-build -n was executed in
        // 'project/app/'. For this reason, any relative paths in the ndk-build -n output will be
        // relative to 'project/app/' but a direct call now to getAbsolutePath() would produce a
        // path relative to 'project/' which is wrong.
        //
        // NOTE: CMake doesn't have the same issue because CMake JSON generation happens fully
        // within the Exec call which has 'project/app' as the current directory.

        // TODO(jomof): This NativeBuildConfigValue is probably consuming a lot of memory for large
        // projects. Should be changed to a streaming model where NativeBuildConfigValueBuilder
        // provides a streaming JsonReader rather than a full object.
        NativeBuildConfigValue buildConfig =
                new NativeBuildConfigValueBuilder(
                                getMakeFile(), variant.getModule().getModuleRootFolder())
                        .setCommands(
                                getBuildCommand(abi, applicationMk, false /* removeJobsFlag */),
                                getBuildCommand(abi, applicationMk, true /* removeJobsFlag */)
                                        + " clean",
                                variant.getVariantName(),
                                buildOutput)
                        .build();

        if (applicationMk.exists()) {
            infoln("found application make file %s", applicationMk.getAbsolutePath());
            Preconditions.checkNotNull(buildConfig.buildFiles);
            buildConfig.buildFiles.add(applicationMk);
        }

        String actualResult = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .setPrettyPrinting()
                .create()
                .toJson(buildConfig);

        // Write the captured ndk-build output to JSON file
        Files.write(getJsonFile(abi).toPath(), actualResult.getBytes(Charsets.UTF_8));
    }

    /**
     * Get the process builder with -n flag. This will tell ndk-build to emit the steps that it
     * would do to execute the build.
     */
    @NonNull
    @Override
    ProcessInfoBuilder getProcessBuilder(@NonNull CxxAbiModel abi) {
        // Discover Application.mk if one exists next to Android.mk
        // If there is an Application.mk file next to Android.mk then pick it up.
        File applicationMk = new File(getMakeFile().getParent(), "Application.mk");
        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(getNdkBuild())
                .addArgs(getBaseArgs(abi, applicationMk, false /* removeJobsFlag */))
                // Disable response files so we can parse the command line.
                .addArgs("APP_SHORT_COMMANDS=false")
                .addArgs("LOCAL_SHORT_COMMANDS=false")
                .addArgs("-B") // Build as if clean
                .addArgs("-n");
        return builder;
    }

    @NonNull
    @Override
    String executeProcess(
            @NonNull CxxAbiModel abi,
            @NonNull Function<Action<? super ExecSpec>, ExecResult> execOperation)
            throws ProcessException, IOException {
        return createProcessOutputJunction(
                        abi.getVariant().getModule(),
                        CxxAbiModelKt.getSoFolder(abi),
                        "android_gradle_generate_ndk_build_json_" + abi.getAbi().getTag(),
                        getProcessBuilder(abi),
                        "")
                .logStderrToInfo()
                .executeAndReturnStdoutString(execOperation::apply);
    }

    @NonNull
    @Override
    public NativeBuildSystem getNativeBuildSystem() {
        return NativeBuildSystem.NDK_BUILD;
    }

    @NonNull
    @Override
    Map<Abi, File> getStlSharedObjectFiles() {
        return Maps.newHashMap();
    }

    /** Get the path of the ndk-build script. */
    @NonNull
    private String getNdkBuild() {
        String tool = "ndk-build";
        if (isWindows()) {
            tool += ".cmd";
        }
        File toolFile = new File(getNdkFolder(), tool);

        try {
            // Attempt to shorten ndkFolder which may have segments of "path\.."
            // File#getAbsolutePath doesn't do this.
            return toolFile.getCanonicalPath();
        } catch (IOException e) {
            warnln(
                    "Attempted to get ndkFolder canonical path and failed: %s\n"
                            + "Falling back to absolute path.",
                    e);
            return toolFile.getAbsolutePath();
        }
    }

    /**
     * If the make file is a directory then get the implied file, otherwise return the path.
     */
    @NonNull
    private File getMakeFile() {
        if (getMakefile().isDirectory()) {
            return new File(getMakefile(), "Android.mk");
        }
        return getMakefile();
    }

    /** Get the base list of arguments for invoking ndk-build. */
    @NonNull
    private List<String> getBaseArgs(
            @NonNull CxxAbiModel abi, @NonNull File applicationMk, boolean removeJobsFlag) {
        List<String> result = Lists.newArrayList();
        result.add("NDK_PROJECT_PATH=null");
        result.add("APP_BUILD_SCRIPT=" + getMakeFile());

        if (applicationMk.exists()) {
            // NDK_APPLICATION_MK specifies the Application.mk file.
            result.add("NDK_APPLICATION_MK=" + applicationMk.getAbsolutePath());
        }

        if (!abi.getVariant().getPrefabPackageDirectoryList().isEmpty()) {
            if (abi.getVariant().getModule().getNdkVersion().getMajor() < 21) {
                // These cannot be automatically imported prior to NDK r21 which started handling
                // NDK_GRADLE_INJECTED_IMPORT_PATH, but the user can add that search path explicitly
                // for older releases.
                // TODO(danalbert): Include a link to the docs page when it is published.
                // This can be worked around on older NDKs, but it's too verbose to include in the
                // warning message.
                warnln("Prefab packages cannot be automatically imported until NDK r21.");
            }
            result.add("NDK_GRADLE_INJECTED_IMPORT_PATH=" + abi.getPrefabFolder().toString());
        }

        // APP_ABI and NDK_ALL_ABIS work together. APP_ABI is the specific ABI for this build.
        // NDK_ALL_ABIS is the universe of all ABIs for this build. NDK_ALL_ABIS is set to just the
        // current ABI. If we don't do this, then ndk-build will erase build artifacts for all abis
        // aside from the current.
        result.add("APP_ABI=" + abi.getAbi().getTag());
        result.add("NDK_ALL_ABIS=" + abi.getAbi().getTag());

        if (isDebuggable()) {
            result.add("NDK_DEBUG=1");
        } else {
            result.add("NDK_DEBUG=0");
        }

        result.add("APP_PLATFORM=android-" + abi.getAbiPlatformVersion());

        // getObjFolder is set to the "local" subfolder in the user specified directory, therefore,
        // NDK_OUT should be set to getObjFolder().getParent() instead of getObjFolder().
        String ndkOut = new File(getObjFolder()).getParent();
        if (CURRENT_PLATFORM == PLATFORM_WINDOWS) {
            // Due to b.android.com/219225, NDK_OUT on Windows requires forward slashes.
            // ndk-build.cmd is supposed to escape the back-slashes but it doesn't happen.
            // Workaround here by replacing back slash with forward.
            // ndk-build will have a fix for this bug in r14 but this gradle fix will make it
            // work back to r13, r12, r11, and r10.
            ndkOut = ndkOut.replace('\\', '/');
        }
        result.add("NDK_OUT=" + ndkOut);

        result.add("NDK_LIBS_OUT=" + getSoFolder());

        // Related to issuetracker.google.com/69110338. Semantics of APP_CFLAGS and APP_CPPFLAGS
        // is that the flag(s) are unquoted. User may place quotes if it is appropriate for the
        // target compiler. User in this case is build.gradle author of
        // externalNativeBuild.ndkBuild.cppFlags or the author of Android.mk.
        for (String flag : getcFlags()) {
            result.add(String.format("APP_CFLAGS+=%s", flag));
        }

        for (String flag : getCppFlags()) {
            result.add(String.format("APP_CPPFLAGS+=%s", flag));
        }

        boolean skipNextArgument = false;
        for (String argument : getBuildArguments()) {
            // Jobs flag is removed for clean command because Make has issues running
            // cleans in parallel. See b.android.com/214558
            if (removeJobsFlag && argument.equals("-j")) {
                // This is the arguments "-j" "4" case. We need to skip the current argument
                // which is "-j" as well as the next argument, "4".
                skipNextArgument = true;
                continue;
            }
            if (removeJobsFlag && argument.equals("--jobs")) {
                // This is the arguments "--jobs" "4" case. We need to skip the current argument
                // which is "--jobs" as well as the next argument, "4".
                skipNextArgument = true;
                continue;
            }
            if (skipNextArgument) {
                // Skip the argument following "--jobs" or "-j"
                skipNextArgument = false;
                continue;
            }
            if (removeJobsFlag && (argument.startsWith("-j") || argument.startsWith("--jobs="))) {
                // This is the "-j4" or "--jobs=4" case.
                continue;
            }

            result.add(argument);
        }

        return result;
    }

    /** Get the build command */
    @NonNull
    private String getBuildCommand(
            @NonNull CxxAbiModel abi, @NonNull File applicationMk, boolean removeJobsFlag) {
        return getNdkBuild()
                + " "
                + Joiner.on(" ").join(getBaseArgs(abi, applicationMk, removeJobsFlag));
    }
}
