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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.utils.FileCache;
import java.io.File;
import org.gradle.api.Project;

/**
 * Global scope for TransformManager and Transform implementations.
 */
public interface TransformGlobalScope {

    /**
     * Returns the {@link Project}
     */
    Project getProject();

    /**
     * Returns the {@link org.gradle.api.Project} output folder.
     * @return the project's output folder.
     */
    @NonNull
    File getBuildDir();

    /**
     * Returns true if the passed {@link OptionalCompilationStep} was specified when invoking
     * gradle.
     *
     * @param step an optional compilation step.
     * @return true if the step was specified in the command line arguments, false otherwise,
     */
    boolean isActive(OptionalCompilationStep step);

    /** Get the options specified as project properties. */
    @NonNull
    ProjectOptions getProjectOptions();

    /**
     * Returns a {@link FileCache} instance representing the build cache if the build cache is
     * enabled, or null if it is disabled.
     */
    @Nullable
    FileCache getBuildCache();
}
