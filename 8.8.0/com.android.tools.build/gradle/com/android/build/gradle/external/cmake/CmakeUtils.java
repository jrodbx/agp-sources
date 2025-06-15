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
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor;
import com.android.repository.Revision;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

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
    public static Revision getVersion(
            @NonNull File cmakeInstallPath, @NonNull Function<File, String> versionExecutor)
            throws IOException {
        final String versionOutput = versionExecutor.apply(new File(cmakeInstallPath, "cmake"));
        if (!versionOutput.startsWith(CMAKE_VERSION_LINE_PREFIX)) {
            throw new RuntimeException(
                    "Did not recognize stdout line as a cmake version: " + versionOutput);
        }
        final String versionString = versionOutput.substring(CMAKE_VERSION_LINE_PREFIX.length());
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

    /** Returns true if currently on Windows. */
    static boolean isWindows() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }
}
