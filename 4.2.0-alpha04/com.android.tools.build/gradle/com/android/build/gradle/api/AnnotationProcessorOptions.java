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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import java.util.Map;
import org.gradle.process.CommandLineArgumentProvider;

/** Options for configuring Java annotation processor. */
public interface AnnotationProcessorOptions {

    /**
     * Annotation processors to run.
     *
     * <p>If empty, processors will be automatically discovered.
     */
    @NonNull
    List<String> getClassNames();

    /**
     * Options for the annotation processors provided via key-value pairs.
     *
     * @see #getCompilerArgumentProviders()
     */
    @NonNull
    Map<String, String> getArguments();

    /**
     * Options for the annotation processors provided via {@link CommandLineArgumentProvider}.
     *
     * @see #getArguments()
     */
    @NonNull
    List<CommandLineArgumentProvider> getCompilerArgumentProviders();

    /**
     * Whether to include compile classpath in the processors path.
     */
    @Nullable
    Boolean getIncludeCompileClasspath();
}
