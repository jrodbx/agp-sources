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

package com.android.build.gradle.internal.cxx.json;

import static com.android.build.gradle.internal.cxx.json.LintKt.lint;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getJsonFile;
import static com.android.build.gradle.internal.cxx.model.CxxAbiModelKt.getMiniConfigFile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.model.CxxAbiModel;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Methods for dealing with files and streams of type android_build_gradle.json. */
public class AndroidBuildGradleJsons {
    /**
     * Given a JsonReader that represents an android_build_gradle structure produce a small random
     * access structure called {@link NativeBuildConfigValueMini}
     *
     * @param reader the Json reader
     * @param stats the stats to update
     * @return the mini config
     * @throws IOException if there was an IO problem reading the Json.
     */
    @NonNull
    public static NativeBuildConfigValueMini parseToMiniConfigAndGatherStatistics(
            @NonNull JsonReader reader, @NonNull GradleBuildVariant.Builder stats)
            throws IOException {
        GradleBuildVariant.NativeBuildConfigInfo.Builder config =
                GradleBuildVariant.NativeBuildConfigInfo.newBuilder();
        AndroidBuildGradleJsonStatsBuildingVisitor statsVisitor =
                new AndroidBuildGradleJsonStatsBuildingVisitor(config);
        MiniConfigBuildingVisitor miniConfigVisitor = new MiniConfigBuildingVisitor();
        AndroidBuildGradleJsonCompositeVisitor composite =
                new AndroidBuildGradleJsonCompositeVisitor(statsVisitor, miniConfigVisitor);

        try (AndroidBuildGradleJsonStreamingParser parser =
                new AndroidBuildGradleJsonStreamingParser(reader, composite)) {
            parser.parse();
            stats.addNativeBuildConfig(config);
            return miniConfigVisitor.miniConfig;
        }
    }

    /**
     * Given a JsonReader that represents an android_build_gradle structure produce a small random
     * access structure called {@link NativeBuildConfigValueMini}
     *
     * @param reader the Json reader
     * @return the mini config
     * @throws IOException if there was an IO problem reading the Json.
     */
    @NonNull
    private static NativeBuildConfigValueMini parseToMiniConfig(@NonNull JsonReader reader)
            throws IOException {
        MiniConfigBuildingVisitor miniConfigVisitor = new MiniConfigBuildingVisitor();
        try (AndroidBuildGradleJsonStreamingParser parser =
                new AndroidBuildGradleJsonStreamingParser(reader, miniConfigVisitor)) {
            parser.parse();
            return miniConfigVisitor.miniConfig;
        }
    }

    /**
     * Given a list of Json files and the current variant name produce a list of
     * NativeBuildConfigValueMini. Json parsing is done in a streaming manner so that the entire
     * Json file is not read into memory at once.
     */
    public static List<NativeBuildConfigValueMini> getNativeBuildMiniConfigs(
            @NonNull List<CxxAbiModel> abis, @Nullable GradleBuildVariant.Builder stats)
            throws IOException {
        List<NativeBuildConfigValueMini> miniConfigs = Lists.newArrayList();

        for (CxxAbiModel abi : abis) {
            miniConfigs.add(getNativeBuildMiniConfig(abi, stats));
        }
        return miniConfigs;
    }

    /**
     * Return a {@link NativeBuildConfigValueMini} for this {@link CxxAbiModel}. Generate if missing
     * or out of date with respect to corresponding android_gradle_build.json.
     *
     * <p>Emit lint check errors against the {@link NativeBuildConfigValueMini} if there are any.
     *
     * @param abi the ABI to get mini config fore.
     * @param stats the stats to update
     * @return the mini config
     * @throws IOException if there was an IO problem reading the Json.
     */
    @NonNull
    public static NativeBuildConfigValueMini getNativeBuildMiniConfig(
            @NonNull CxxAbiModel abi, @Nullable GradleBuildVariant.Builder stats)
            throws IOException {
        File persistedMiniConfig = getMiniConfigFile(abi);
        File json = getJsonFile(abi);
        NativeBuildConfigValueMini result;
        if (ExternalNativeBuildTaskUtils.fileIsUpToDate(json, persistedMiniConfig)) {
            // The mini json has already been created for us. Just read it instead of parsing again.
            try (JsonReader reader = new JsonReader(new FileReader(persistedMiniConfig))) {
                result = parseToMiniConfig(reader);
            }
        } else {
            try (JsonReader reader = new JsonReader(new FileReader(json))) {
                result =
                        stats == null
                                ? parseToMiniConfig(reader)
                                : parseToMiniConfigAndGatherStatistics(reader, stats);
            }
            writeNativeBuildMiniConfigValueToJsonFile(persistedMiniConfig, result);
        }

        // Check the resulting mini config for problems. We have the right context here to attribute
        // issues to a specific JSON or mini config JSON file name.
        if (json.isFile()) {
            // Report any lint-check errors against the original full JSON file rather than the mini
            // config file that is derived from it.
            lint(result, json);
        } else {
            // A metadata provider may choose to only supply the mini config (with the rest of the
            // relevant information provided by compile_commands.json). In this case, lint checks
            // should report errors against the mini config file rather than the non-existent full
            // JSON.
            lint(result, persistedMiniConfig);
        }
        return result;
    }

    /**
     * Writes the given object as JSON to the given json file.
     *
     * @throws IOException I/O failure
     */
    public static void writeNativeBuildConfigValueToJsonFile(
            @NonNull File outputJson, @NonNull NativeBuildConfigValue nativeBuildConfigValue)
            throws IOException {
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();

        try (FileWriter jsonWriter = new FileWriter(outputJson)) {
            gson.toJson(nativeBuildConfigValue, jsonWriter);
        }
    }

    /**
     * Writes the given object as JSON to the given json file.
     *
     * @throws IOException I/O failure
     */
    private static void writeNativeBuildMiniConfigValueToJsonFile(
            @NonNull File outputJson, @NonNull NativeBuildConfigValueMini miniConfig)
            throws IOException {
        String actualResult =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .setPrettyPrinting()
                        .create()
                        .toJson(miniConfig);
        outputJson.getParentFile().mkdirs();
        Files.write(outputJson.toPath(), actualResult.getBytes(Charsets.UTF_8));
    }

    /**
     * Streams over android_build_gradle.json and produces a random-access but small structure
     * called a NativeBuildConfigValueMini.
     */
    private static class MiniConfigBuildingVisitor extends AndroidBuildGradleJsonStreamingVisitor {
        @NonNull private final NativeBuildConfigValueMini miniConfig;
        @Nullable private String libraryName;

        MiniConfigBuildingVisitor() {
            this.miniConfig = new NativeBuildConfigValueMini();
            libraryName = null;
        }

        @Override
        protected void beginLibrary(@NonNull String libraryName) {
            super.beginLibrary(libraryName);
            this.libraryName = libraryName;
            miniConfig.libraries.put(libraryName, new NativeLibraryValueMini());
        }

        @Override
        protected void visitLibraryAbi(@NonNull String abi) {
            super.visitLibraryAbi(abi);
            miniConfig.libraries.get(libraryName).abi = abi;
        }

        @Override
        protected void visitLibraryArtifactName(@NonNull String artifactName) {
            super.visitLibraryArtifactName(artifactName);
            miniConfig.libraries.get(libraryName).artifactName = artifactName;
        }

        @Override
        protected void visitLibraryBuildCommandComponents(
                @NonNull List<String> buildCommandComponents) {
            super.visitLibraryBuildCommandComponents(buildCommandComponents);
            miniConfig.libraries.get(libraryName).buildCommandComponents = buildCommandComponents;
        }

        @Override
        protected void visitCleanCommandsComponents(@NonNull List<String> cleanCommandComponents) {
            super.visitCleanCommandsComponents(cleanCommandComponents);
            miniConfig.cleanCommandsComponents.add(cleanCommandComponents);
        }

        @Override
        protected void visitBuildTargetsCommandComponents(
                @Nullable List<String> buildTargetsCommandComponents) {
            super.visitBuildTargetsCommandComponents(buildTargetsCommandComponents);
            miniConfig.buildTargetsCommandComponents = buildTargetsCommandComponents;
        }

        @Override
        protected void visitLibraryOutput(@Nullable String output) {
            if (output == null) return;
            super.visitLibraryOutput(output);
            miniConfig.libraries.get(libraryName).output = new File(output);
        }

        @Override
        protected void visitBuildFile(@NonNull String buildFile) {
            super.visitBuildFile(buildFile);
            miniConfig.buildFiles.add(new File(buildFile));
        }

        @Override
        protected void visitLibraryRuntimeFile(@NonNull String runtimeFile) {
            super.visitLibraryRuntimeFile(runtimeFile);
            miniConfig.libraries.get(libraryName).runtimeFiles.add(new File(runtimeFile));
        }
    }
}
