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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.profile.ChromeTracingProfileConverter;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.ProcessProfileWriterFactory;
import com.android.utils.PathUtils;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.gradle.BuildAdapter;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCompletionListener;

/**
 * Initialize the {@link ProcessProfileWriterFactory} using a given project.
 *
 * <p>Is separate from {@code ProcessProfileWriterFactory} as {@code ProcessProfileWriterFactory}
 * does not depend on gradle classes.
 */
public final class ProfilerInitializer {

    public static final String PROFILE_DIRECTORY = "android-profile";

    private static final DateTimeFormatter PROFILE_FILE_NAME =
            DateTimeFormatter.ofPattern("'profile-'YYYY-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US);

    private static final Object lock = new Object();

    @Nullable private static volatile RecordingBuildListener recordingBuildListener;

    private ProfilerInitializer() {
        //Static singleton class.
    }

    @Nullable
    public static RecordingBuildListener getListener() {
        return recordingBuildListener;
    }

    /**
     * Initialize the {@link ProcessProfileWriterFactory}. Idempotent.
     *
     * @param project the current Gradle {@link Project}.
     * @param projectOptions the options
     */
    public static RecordingBuildListener init(
            @NonNull Project project, @NonNull ProjectOptions projectOptions) {
        synchronized (lock) {
            //noinspection VariableNotUsedInsideIf
            if (recordingBuildListener != null) {
                return recordingBuildListener;
            }
            ProcessProfileWriterFactory.initialize(
                    project.getRootProject().getProjectDir(),
                    project.getGradle().getGradleVersion(),
                    new LoggerWrapper(project.getLogger()),
                    projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON));
            recordingBuildListener =
                    new RecordingBuildListener(project.getName(), ProcessProfileWriter.get());
            project.getGradle().addListener(recordingBuildListener);
        }

        project.getGradle()
                .addListener(
                        new ProfileShutdownListener(
                                project.getGradle(),
                                projectOptions.get(StringOption.PROFILE_OUTPUT_DIR),
                                projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON)));

        return recordingBuildListener;
    }

    private static final class ProfileShutdownListener extends BuildAdapter
            implements BuildCompletionListener {

        private static final Logger logger = Logging.getLogger(ProfileShutdownListener.class);
        private final Gradle gradle;
        @Nullable private String profileDirProperty;
        @Nullable private Path profileDir = null;
        private boolean enableProfileJson;

        ProfileShutdownListener(
                @NonNull Gradle gradle,
                @Nullable String profileDirProperty,
                boolean enableProfileJson) {
            this.gradle = gradle;
            this.profileDirProperty = profileDirProperty;
            this.enableProfileJson = enableProfileJson;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            gradle.allprojects(this::collectProjectInfo);
            if (profileDirProperty != null) {
                this.profileDir = gradle.getRootProject().file(profileDirProperty).toPath();
            } else if (enableProfileJson) {
                // If profile json is enabled but no directory is given for the profile outputs, default to build/android-profile
                this.profileDir =
                        gradle.getRootProject().getBuildDir().toPath().resolve(PROFILE_DIRECTORY);
            }
            if (this.profileDir != null) {
                // Proactively delete the folder containing extra chrome traces to be merged.
                Path extraChromeTracePath =
                        this.profileDir.resolve(
                                ChromeTracingProfileConverter.EXTRA_CHROME_TRACE_DIRECTORY);
                try {
                    PathUtils.deleteRecursivelyIfExists(extraChromeTracePath);
                } catch (IOException e) {
                    logger.warn(
                            String.format(
                                    "Cannot extra Chrome trace directory %s. The generated Chrome trace "
                                            + "file may contain stale data.",
                                    extraChromeTracePath),
                            e);
                }
            }
        }

        private void collectProjectInfo(Project project) {
            GradleBuildProject.Builder analyticsProject =
                    ProcessProfileWriter.getProject(project.getPath());
            project.getPlugins()
                    .all((plugin) -> analyticsProject.addPlugin(AnalyticsUtil.toProto(plugin)));
        }

        @Override
        public void completed() {
            synchronized (lock) {
                ProfileAgent.INSTANCE.unregister();
                if (recordingBuildListener != null) {
                    gradle.removeListener(Objects.requireNonNull(recordingBuildListener));
                    recordingBuildListener = null;
                    @Nullable
                    Path profileFile =
                            profileDir == null
                                    ? null
                                    : profileDir.resolve(
                                            PROFILE_FILE_NAME.format(LocalDateTime.now()));

                    // This is deliberately asynchronous, so the build can complete before the
                    // analytics are submitted.
                    ProcessProfileWriterFactory.shutdownAndMaybeWrite(profileFile);
                }
            }
        }
    }
}

