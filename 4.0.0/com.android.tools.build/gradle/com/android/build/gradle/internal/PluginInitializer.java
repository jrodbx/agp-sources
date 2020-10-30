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

package com.android.build.gradle.internal;

import com.android.Version;
import com.android.annotations.NonNull;
import com.android.utils.JvmWideVariable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.Project;

/**
 * Helper class to perform a few initializations when the plugin is applied to a project.
 *
 * <p>To ensure proper usage, the {@link #initialize(Project)} method must be called immediately
 * whenever the plugin is applied to a project.
 */
@ThreadSafe
public final class PluginInitializer {

    /**
     * Map from a project instance to the plugin version that is applied to the project, used to
     * detect if different plugin versions are applied.
     *
     * <p>We use the project instance instead of the project path as the key because, within a
     * build, Gradle might apply the plugin multiple times to different project instances having the
     * same project path. Using project instances as keys helps us tracks this information better.
     *
     * <p>This map will be reset at the end of every build since the scope of the check is per
     * build.
     */
    @NonNull
    private static final ConcurrentMap<Object, String> projectToPluginVersionMap =
            Verify.verifyNotNull(
                    // IMPORTANT: This variable's group, name, and type must not be changed across
                    // plugin versions.
                    new JvmWideVariable<>(
                                    "PLUGIN_VERSION_CHECK",
                                    "PROJECT_TO_PLUGIN_VERSION",
                                    new TypeToken<ConcurrentMap<Object, String>>() {},
                                    ConcurrentHashMap::new)
                            .get());

    /**
     * Performs a few initializations when the plugin is applied to a project. This method must be
     * called immediately whenever the plugin is applied to a project.
     *
     * <p>Currently, the initialization includes:
     *
     * <ol>
     *   <li>Notifying the {@link BuildSessionImpl} singleton object that a new build has started,
     *       as required by that class.
     *   <li>Checking that the same plugin version is applied within a build.
     * </ol>
     *
     * <p>Here, a build refers to the entire Gradle build, which includes included builds in the
     * case of composite builds. Note that the Gradle daemon never executes two builds at the same
     * time, although it may execute sub-builds (for sub-projects) or included builds in parallel.
     *
     * <p>The scope of the above plugin version check is per build. It is okay that different plugin
     * versions are applied across different builds.
     *
     * @param project the project that the plugin is applied to
     * @throws IllegalStateException if the plugin version check failed
     */
    public static void initialize(@NonNull Project project) {
        // Notifying the BuildSessionImpl singleton object must be done first
        BuildSessionImpl.getSingleton().initialize(project.getGradle());

        // The scope of the plugin version check is per build, so we need to reset the variable for
        // this check at the end of every build. We register the action early in case the code that
        // follows throws an exception. Note that if multiple plugin versions are applied, the
        // variable will be reset multiple times since the method below takes effect for only the
        // current plugin version.
        BuildSessionImpl.getSingleton()
                .executeOnceWhenBuildFinished(
                        PluginInitializer.class.getName(),
                        "resetPluginVersionCheckVariable",
                        projectToPluginVersionMap::clear);

        // Check that the same plugin version is applied (the code is synchronized on the shared map
        // to make the method call thread safe across class loaders)
        synchronized (projectToPluginVersionMap) {
            verifySamePluginVersion(
                    projectToPluginVersionMap, project, Version.ANDROID_GRADLE_PLUGIN_VERSION);
        }
    }

    /** Verifies that the same plugin version is applied. */
    @VisibleForTesting
    static void verifySamePluginVersion(
            @NonNull ConcurrentMap<Object, String> projectToPluginVersionMap,
            @NonNull Project project,
            @NonNull String pluginVersion) {
        Preconditions.checkState(
                !projectToPluginVersionMap.containsKey(project),
                String.format(
                        "Android Gradle plugin %1$s must not be applied to project '%2$s'"
                                + " since version %3$s was already applied to this project",
                        pluginVersion,
                        project.getProjectDir().getAbsolutePath(),
                        projectToPluginVersionMap.get(project)));

        projectToPluginVersionMap.put(project, pluginVersion);

        if (projectToPluginVersionMap.values().stream().distinct().count() > 1) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append(
                    "Using multiple versions of the Android Gradle plugin in the same build"
                            + " is not allowed.");
            for (Map.Entry<Object, String> entry : projectToPluginVersionMap.entrySet()) {
                Preconditions.checkState(
                        entry.getKey() instanceof Project,
                        Project.class + " should be loaded only once");
                Project fromProject = (Project) entry.getKey();
                String toPluginVersion = entry.getValue();
                errorMessage.append(
                        String.format(
                                "\n\t'%1$s' is using version %2$s",
                                fromProject.getProjectDir().getAbsolutePath(), toPluginVersion));
            }
            throw new IllegalStateException(errorMessage.toString());
        }
    }
}
