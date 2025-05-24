/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

interface BootClasspathConfig {

    /**
     * The boot classpath to be used during compilation with all available additional jars
     * including all optional libraries.
     */
    val fullBootClasspath: FileCollection
    val fullBootClasspathProvider: Provider<List<RegularFile>>

    /**
     * The boot classpath to be used during compilation with all available additional jars
     * but only the requested optional ones.
     *
     * <p>Requested libraries not found will be reported to the issue handler.
     *
     * @return a {@link FileCollection} that forms the filtered classpath.
     */
    val filteredBootClasspath: Provider<List<RegularFile>>

    /**
     * The boot classpath to be used during compilation with the core lambdas stubs.
     */
    val bootClasspath: Provider<List<RegularFile>>

    /**
     * Queries the given configuration for mockable version of the jar(s) in it.
     *
     * This is designed to mock android.jar from a configuration that contains it, via Artifact
     * Transforms.
     */
    val mockableJarArtifact: FileCollection
}
