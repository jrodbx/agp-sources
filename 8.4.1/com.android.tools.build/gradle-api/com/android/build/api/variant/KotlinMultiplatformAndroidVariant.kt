/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.api.variant

import com.android.build.api.artifact.Artifacts
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection

/**
 * Properties for the main Variant of a kotlin multiplatform android library
 */
@Suppress("DEPRECATION")
@Incubating
interface KotlinMultiplatformAndroidVariant: HasDeviceTests, HasAndroidTest, HasUnitTest {
    /**
     * The name of the variant
     */
    @get:Incubating
    val name: String

    /**
     * Access to the variant's buildable artifacts for build customization.
     */
    @get:Incubating
    val artifacts: Artifacts

    /**
     * Access to the variant's instrumentation options.
     */
    @get:Incubating
    val instrumentation: Instrumentation

    /**
     * Access to the variant's compile classpath.
     *
     * The returned [FileCollection] should not be resolved until execution time.
     */
    @get:Incubating
    val compileClasspath: FileCollection

    /**
     * List of the components nested in the main variant, the returned list may contain:
     *
     * * [UnitTest] component if the unit tests for this variant are enabled,
     * * [AndroidTest] component if this variant [HasDeviceTests] and android tests for this variant
     * are enabled,
     *
     * Use this list to do operations on all nested components of this variant without having to
     * manually check whether the variant has each component.
     *
     * Example:
     *
     * ```kotlin
     *  androidComponents {
     *     onVariant { variant ->
     *         variant.nestedComponents.forEach { component ->
     *             component.instrumentation.transformClassesWith(
     *                 AsmClassVisitorFactoryImpl.class,
     *                 InstrumentationScope.Project) { params -> params.x = "value" }
     *             instrumentation.setAsmFramesComputationMode(COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
     *         }
     *     }
     *  }
     *  ```
     */
    @get:Incubating
    val nestedComponents: List<Component>

    /**
     * Provides access to the [LifecycleTasks] created for this component.
     */
    @get:Incubating
    val lifecycleTasks: LifecycleTasks
}
