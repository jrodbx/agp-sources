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


import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

/**
 * Shared utility methods for dealing with external native build tasks.
 */
public class ExternalNativeBuildTaskUtils {
    // Forked CMake version is the one we get when we execute "cmake --version" command.
    public static final String CUSTOM_FORK_CMAKE_VERSION = "3.6.0-rc2";

    /**
     * File 'derived' is consider to depend on the contents of file 'source' this function return
     * true if source is more recent than derived.
     *
     * <p>If derived doesn't exist then it is not consider to be up-to-date with respect to source.
     *
     * @param source -- original file (must exist)
     * @param derived -- derived file
     * @return true if derived is more recent than original
     * @throws IOException if there was a problem reading the timestamp of one of the files
     */
    public static boolean fileIsUpToDate(@NonNull File source, @NonNull File derived)
            throws IOException {
        if (!source.exists()) {
            // Generally shouldn't happen but if it does then let's claim that derived is out of
            // date.
            return false;
        }
        if (!derived.exists()) {
            // Derived file doesn't exist so it is not up-to-date with respect to file 1
            return false;
        }
        long sourceTimestamp = Files.getLastModifiedTime(source.toPath()).toMillis();
        long derivedTimestamp = Files.getLastModifiedTime(derived.toPath()).toMillis();
        return sourceTimestamp <= derivedTimestamp;
    }

    /**
     * The json mini-config file contains a subset of the regular json file that is much smaller and
     * less memory-intensive to read.
     */
    @NonNull
    public static File getJsonMiniConfigFile(@NonNull File originalJson) {
        return new File(originalJson.getParent(), "android_gradle_build_mini.json");
    }

    @NonNull
    public static List<File> getOutputJsons(@NonNull File jsonFolder,
            @NonNull Collection<String> abis) {
        List<File> outputs = Lists.newArrayList();
        for (String abi : abis) {
            outputs.add(new File(new File(jsonFolder, abi), "android_gradle_build.json"));
        }
        return outputs;
    }
}
