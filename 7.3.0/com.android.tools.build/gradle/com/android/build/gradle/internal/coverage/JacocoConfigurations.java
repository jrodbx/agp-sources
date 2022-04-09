/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage;

import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/** Class to keep track of Jacoco-specific configurations. */
public final class JacocoConfigurations {
    public static final String ANT_CONFIGURATION_NAME = "androidJacocoAnt";

    /** Version of Jacoco to be used with DX, see https://issuetracker.google.com/37116789. */
    public static final String VERSION_FOR_DX = "0.8.7";

    // See http://b/62623509.
    public static final GradleVersion MIN_WITHOUT_BROKEN_BYTECODE = GradleVersion.parse("0.8.7");

    @NonNull
    public static String getAgentRuntimeDependency(@NonNull String jacocoVersion) {
        return "org.jacoco:org.jacoco.agent:" + jacocoVersion + ":runtime";
    }

    /**
     * Create or retrieve configuration that contains jars necessary for offline instrumentation and
     * the Jacoco report task.
     */
    @NonNull
    public static Configuration getJacocoAntTaskConfiguration(
            @NonNull Project project, @NonNull String jacocoVersion) {
        Configuration configuration =
                project.getConfigurations().findByName(ANT_CONFIGURATION_NAME);
        if (configuration != null) {
            return configuration;
        }

        configuration = project.getConfigurations().create(ANT_CONFIGURATION_NAME);

        configuration.setVisible(false);
        configuration.setTransitive(true);
        configuration.setCanBeConsumed(false);
        configuration.setDescription("The Jacoco agent to use to get coverage data.");

        project.getDependencies()
                .add(ANT_CONFIGURATION_NAME, "org.jacoco:org.jacoco.ant:" + jacocoVersion);
        return configuration;
    }
}
