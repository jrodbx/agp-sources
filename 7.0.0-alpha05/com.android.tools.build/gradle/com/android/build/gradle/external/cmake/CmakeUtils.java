/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.external.cmake;

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.cmake.server.CodeModel;
import com.android.build.gradle.external.cmake.server.Configuration;
import com.android.build.gradle.external.cmake.server.FileGroup;
import com.android.build.gradle.external.cmake.server.Project;
import com.android.build.gradle.external.cmake.server.Target;
import com.android.build.gradle.internal.cxx.build.CxxRegularBuilder;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor;
import com.android.repository.Revision;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cmake utility class. */
public class CmakeUtils {
    private static final String CMAKE_VERSION_LINE_PREFIX = "cmake version ";

    /**
     * Parses the Cmake (from the given install path) version string into a structure.
     *
     * @return Revision for the version string.
     * @throws IOException I/O failure
     */
    @NonNull
    public static Revision getVersion(@NonNull File cmakeInstallPath) throws IOException {
        final String versionString = getVersionString(cmakeInstallPath);
        // Custom built CMake (like the Google-built one in the SDK) started having a version
        // number containing hash of something. For example,
        //   3.17.0-gc5272a5
        // This isn't parsable by Revision.parseRevision(). It's also fairly non-standard so
        // it didn't make sense to make Revision understand it. Instead, we just strip the
        // preview version first.
        return Revision.parseRevision(keepWhileNumbersAndDots(versionString));
    }

    @NonNull
    public static String keepWhileNumbersAndDots(String versionString) {
        String stripped = "";
        for (char c : versionString.toCharArray()) {
            if ((c < '0' || c > '9') && c != '.') {
                break;
            }
            stripped += c;
        }
        return stripped;
    }

    @NonNull
    public static Revision getVersion(@NonNull String cmakeVersionString) {
        return Revision.parseRevision(cmakeVersionString);
    }

    /**
     * Returns the build command for the given target (given the output folder and cmake
     * executable).
     */
    @NonNull
    public static List<String> getBuildCommand(
            @NonNull File cmakeExecutable, @NonNull File outputFolder, @NonNull String targetName) {
        return ImmutableList.of(
                getNinjaExecutable(cmakeExecutable),
                "-C",
                outputFolder.getAbsolutePath(),
                targetName);
    }

    /**
     * Returns the command to clean up for the given target (given the output folder and cmake
     * executable).
     */
    @NonNull
    public static List<String> getCleanCommand(
            @NonNull File cmakeExecutable, @NonNull File outputFolder) {
        return ImmutableList.of(
                getNinjaExecutable(cmakeExecutable), "-C", outputFolder.getAbsolutePath(), "clean");
    }

    /**
     * Returns the command to build multiple targets. Before executing the returned command, the
     * targets to build must be substituted using the substituteBuildTargetsCommand method.
     */
    public static List<String> getBuildTargetsCommand(
            @NonNull File cmakeExecutable,
            @NonNull File outputFolder,
            @NonNull List<String> buildCommandArgs) {
        return ImmutableList.<String>builder()
                .add(getNinjaExecutable(cmakeExecutable))
                .addAll(buildCommandArgs)
                .add("-C")
                .add(outputFolder.getAbsolutePath())
                .add(CxxRegularBuilder.BUILD_TARGETS_PLACEHOLDER)
                .build();
    }

    /** Returns the C++ file extensions for the given code model. */
    @NonNull
    public static Set<String> getCppExtensionSet(@NonNull CodeModel codeModel) {
        return getLangExtensions(codeModel, "CXX");
    }

    /** Returns the C file extensions for the given code model. */
    @NonNull
    public static Set<String> getCExtensionSet(CodeModel codeModel) {
        return getLangExtensions(codeModel, "C");
    }

    /**
     * Returns the toolchain hash for the given toolchain. If the contents of the toolchain are
     * null, the functions returns 0.
     */
    public static int getToolchainHash(@NonNull NativeToolchainValue toolchainValue) {
        StringBuilder toolchainString = new StringBuilder();
        if (toolchainValue.cppCompilerExecutable != null) {
            toolchainString =
                    toolchainString
                            .append(toolchainValue.cppCompilerExecutable.getAbsolutePath())
                            .append(" ");
        }
        if (toolchainValue.cCompilerExecutable != null) {
            toolchainString =
                    toolchainString.append(toolchainValue.cCompilerExecutable.getAbsolutePath());
        }

        return toolchainString.toString().hashCode();
    }

    /**
     * Returns a JSON string representation of the given object. This is used instead of 'toString'
     * when printing an object's contents.
     */
    @Nullable
    public static <ContentType> String getObjectToString(@Nullable ContentType content) {
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();
        return gson.toJson(content);
    }

    /**
     * Reads the first line of the version output for the current Cmake and returns the version
     * string. For the version output 'cmake version 3.8.0-rc2' the function return '3.8.0-rc2'
     *
     * @return Current Cmake version as a string
     * @throws IOException I/O failure
     */
    @NonNull
    private static String getVersionString(@NonNull File cmakeInstallPath) throws IOException {
        final String versionOutput = getCmakeVersionLinePrefix(cmakeInstallPath);
        if (!versionOutput.startsWith(CMAKE_VERSION_LINE_PREFIX)) {
            throw new RuntimeException(
                    "Did not recognize stdout line as a cmake version: " + versionOutput);
        }
        return versionOutput.substring(CMAKE_VERSION_LINE_PREFIX.length());
    }

    /**
     * Reads the version output for the current Cmake and returns the first line read. Example:
     *
     * <p>$ ./cmake --version
     *
     * <p>cmake version 3.8.0-rc2
     *
     * <p>CMake suite maintained and supported by Kitware (kitware.com/cmake).
     *
     * <p>This function for the above example would return 'cmake version 3.8.0-rc2'
     *
     * @return Current Cmake version output as string
     * @throws IOException I/O failure
     */
    private static String getCmakeVersionLinePrefix(@NonNull File cmakeInstallPath)
            throws IOException {
        File cmakeExecutable = new File(cmakeInstallPath, "cmake");
        ProcessBuilder processBuilder =
                new ProcessBuilder(cmakeExecutable.getAbsolutePath(), "--version");
        processBuilder.redirectErrorStream();
        Process process = processBuilder.start();
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(process.getInputStream());
            try {
                bufferedReader = new BufferedReader(inputStreamReader);
                return bufferedReader.readLine();
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
        } finally {
            if (inputStreamReader != null) {
                inputStreamReader.close();
            }
        }
    }

    /**
     * Returns the set of the language extensions from the given code model. For C++, Cmake server
     * sets the language to CXX, in which case, the language param would be set to "CXX".
     *
     * @param codeModel - code model
     * @param language - language for which we need the extensions
     * @return set of language extensions
     */
    @NonNull
    private static Set<String> getLangExtensions(
            @NonNull CodeModel codeModel, @NonNull String language) {
        Set<String> languageSet = new HashSet<>();
        if (codeModel.configurations == null) {
            return languageSet;
        }
        for (Configuration configuration : codeModel.configurations) {
            if (configuration.projects == null) {
                continue;
            }
            for (Project project : configuration.projects) {
                if (project.targets == null) {
                    continue;
                }
                for (Target target : project.targets) {
                    if (target.fileGroups == null) {
                        continue;
                    }
                    for (FileGroup fileGroup : target.fileGroups) {
                        if (fileGroup.sources == null
                                || fileGroup.language == null
                                || !fileGroup.language.equals(language)) {
                            continue;
                        }
                        for (String source : fileGroup.sources) {
                            String extension = source.substring(source.lastIndexOf('.') + 1).trim();
                            languageSet.add(extension);
                        } // sources
                    } // FileGroup
                } // Target
            } // Project
        } // Configuration

        return languageSet;
    }

    /**
     * Returns the path to the Ninja executable if it exists next to the cmakeExecutable; otherwise
     * assumes Ninja exists exist on $PATH and returns just the platform specific Ninja binary name.
     */
    @VisibleForTesting
    @NonNull
    static String getNinjaExecutable(@NonNull File cmakeExecutable) {
        File cmakeBinFolder = cmakeExecutable.getParentFile();
        File possibleNinja =
                isWindows()
                        ? new File(cmakeBinFolder, "ninja.exe")
                        : new File(cmakeBinFolder, "ninja");
        if (possibleNinja.isFile()) {
            return possibleNinja.getPath();
        }

        return isWindows() ? "ninja.exe" : "ninja";
    }

    /** Returns true if currently on Windows. */
    static boolean isWindows() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }
}
