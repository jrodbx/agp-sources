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
package com.android.ide.common.gradle.model.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** The "build" folder paths per module. */
public class BuildFolderPaths {
    // Key: project build id
    // Value: key - module's Gradle path, value - path of the module's 'build' folder.
    @NonNull
    private final Map<String, Map<String, File>> myBuildFolderPathsByModule = new HashMap<>();

    private String myRootBuildId;

    /**
     * Set the build identifier of root project.
     *
     * @param rootBuildId build identifier of root project.
     */
    public void setRootBuildId(@NonNull String rootBuildId) {
        myRootBuildId = rootBuildId;
    }

    /**
     * Stores the "build" folder path for the given module.
     *
     * @param buildId build identifier of the project.
     * @param moduleGradlePath module's gradle path.
     * @param buildFolder path to the module's build directory.
     */
    public void addBuildFolderMapping(
            @NonNull String buildId, @NonNull String moduleGradlePath, @NonNull File buildFolder) {
        Map<String, File> perBuildMap =
                myBuildFolderPathsByModule.computeIfAbsent(buildId, id -> new HashMap<>());
        perBuildMap.put(moduleGradlePath, buildFolder);
    }

    /**
     * Finds the path of the "build" folder for the given module path and build id.
     *
     * @param moduleGradlePath the gradle path of module.
     * @param buildId build identifier of included project of the module.
     * @return the "build" folder for the given module path from build id; or {@code null} if the
     *     path or build id is not found.
     */
    @Nullable
    public File findBuildFolderPath(@NonNull String moduleGradlePath, @Nullable String buildId) {
        // buildId can be null for root project or for pre-3.1 plugin.
        String projectId = buildId == null ? myRootBuildId : buildId;
        if (myBuildFolderPathsByModule.containsKey(projectId)) {
            return myBuildFolderPathsByModule.get(projectId).get(moduleGradlePath);
        }
        return null;
    }
}
