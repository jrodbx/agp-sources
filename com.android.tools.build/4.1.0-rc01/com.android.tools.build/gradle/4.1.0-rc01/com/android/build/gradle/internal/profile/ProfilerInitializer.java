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
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.ProcessProfileWriterFactory;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;

/**
 * Initialize the {@link ProcessProfileWriterFactory} using a given project.
 *
 * <p>Is separate from {@code ProcessProfileWriterFactory} as {@code ProcessProfileWriterFactory}
 * does not depend on gradle classes.
 */
public final class ProfilerInitializer {

    public static final String PROFILE_DIRECTORY = "android-profile";

    private static final DateTimeFormatter PROFILE_FILE_NAME =
            DateTimeFormatter.ofPattern("'profile-'yyyy-MM-dd-HH-mm-ss-SSS'.rawproto'", Locale.US);

    private static final Object lock = new Object();

    @Nullable private static volatile RecordingBuildListener recordingBuildListener;
    @Nullable private static volatile Gradle gradle;
    @Nullable private static volatile GradleAnalyticsEnvironment gradleAnalyticsEnvironment;

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
            gradleAnalyticsEnvironment = new GradleAnalyticsEnvironment(project.getProviders());
            ProcessProfileWriterFactory.initialize(
                    project.getRootProject().getProjectDir(),
                    project.getGradle().getGradleVersion(),
                    new LoggerWrapper(project.getLogger()),
                    projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON),
                    gradleAnalyticsEnvironment);
            recordingBuildListener =
                    new RecordingBuildListener(project.getName(), ProcessProfileWriter.get());
            gradle = project.getGradle();
            project.getGradle().getTaskGraph().addTaskExecutionListener(recordingBuildListener);
        }

        ProfileCleanupBuildService profileCleanupBuildService =
                new ProfileCleanupBuildService.RegistrationAction(
                                project,
                                projectOptions.get(StringOption.PROFILE_OUTPUT_DIR),
                                projectOptions.get(BooleanOption.ENABLE_PROFILE_JSON))
                        .execute()
                        .get();
        project.getGradle().projectsEvaluated(profileCleanupBuildService::projectEvaluated);

        return recordingBuildListener;
    }

    static void unregister(@Nullable Path profileDir) {
        synchronized (lock) {
            ProfileAgent.INSTANCE.unregister();
            if (recordingBuildListener != null) {
                Objects.requireNonNull(gradle)
                        .getTaskGraph()
                        .removeTaskExecutionListener(
                                Objects.requireNonNull(recordingBuildListener));
                recordingBuildListener = null;
                gradle = null;
                Objects.requireNonNull(gradleAnalyticsEnvironment).releaseProviderFactory();
                gradleAnalyticsEnvironment = null;
                @Nullable
                Path profileFile =
                        profileDir == null
                                ? null
                                : profileDir.resolve(PROFILE_FILE_NAME.format(LocalDateTime.now()));

                // This is deliberately asynchronous, so the build can complete before the
                // analytics are submitted.
                ProcessProfileWriterFactory.shutdownAndMaybeWrite(profileFile);
            }
        }
    }
}

