/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ide.common.attribution

/**
 * Enum class for Category-Based Task Analyzer for Build Analyzer.
 * Each field corresponds to a specific atomic function/execution of a task.
 * All task categories can be in secondaryTaskCategories, but not
 * primaryTaskCategory in [BuildAnalyzer.kt]
 **/
enum class TaskCategory {
    /**
     * Tasks that involves compilation of files/sources.
     */
    COMPILATION,
    /**
     * This is a primary category for tasks that perform test execution.
     */
    TEST,
    /**
     * This is a primary category for tasks that perform
     * Android manifest merging and compiling
     */
    MANIFEST,
    /**
     * This is a primary category for tasks that perform
     * Android resources compilation, processing, linking
     * and merging.
     */
    ANDROID_RESOURCES,
    /**
     * This is a primary category for tasks that are related to
     * Native build compilatoin, linking and packaging.
     */
    NATIVE,
    /**
     * This is a primary category for tasks that are related to
     * Java source compilation, processing and merging.
     */
    JAVA,
    /**
     * Primary task category for tasks related to Java resources
     * merging and packaging.
     */
    JAVA_RESOURCES,
    /**
     * Primary task category for tasks related to Java Doc generation
     * and processing.
     */
    JAVA_DOC,
    /**
     * Marks a task that is related to the Kotlin plugin,
     * including processing or compiling Kotlin sources.
     * This is a primary category for tasks that belong to the Kotlin plugin.
     */
    KOTLIN,
    /**
     * Primary task category for tasks that perform AIDL source
     * compilation and processing.
     */
    AIDL,
    /**
     * Primary task category for tasks related to Renderscript source
     * compilation and processing.
     */
    RENDERSCRIPT,
    /**
     * Primary task category for tasks related to Shader sources compilation
     * and processing.
     */
    SHADER,
    /**
     * This is a primary category for tasks whose main function is
     * generating Dex files.
     */
    DEXING,
    /**
     * Primary task category for ART profile tasks.
     */
    ART_PROFILE,
    /**
     * Primary task category for tasks related to the
     * Lint tool.
     */
    LINT,
    /**
     * Primary task category for tasks related to data binding.
     */
    DATA_BINDING,
    /**
     * Primary task category for tasks whose main function is to
     * read/write metadata.
     */
    METADATA,
    /**
     * Primary task category for tasks whose main function is to
     * verify the validity of the project and dependencies setup.
     */
    VERIFICATION,
    /**
     * Primary task category for tasks that sync IDE sources with Gradle.
     */
    SYNC,
    /**
     * Primary task category for tasks related to device deployment.
     */
    DEPLOYMENT,
    /**
     * This is a primary category for tasks that would not be
     * executed as part of the build, but rather users would
     * invoke them to check something.
     */
    HELP,
    /**
     * This is a primary category for tasks whose main function
     * is packaging APKs.
     */
    APK_PACKAGING,
    /**
     * This is a primary category for tasks whose main function
     * is packaging AARs.
     */
    AAR_PACKAGING,
    /**
     * This is a primary category for tasks whose main function
     * is packaging bundles.
     */
    BUNDLE_PACKAGING,
    /**
     * This is a primary category for tasks whose main function
     * is optimization of the project (eg. shrinking files).
     */
    OPTIMIZATION,
    /**
     * Tasks related to generating sources.
     */
    SOURCE_GENERATION,
    /**
     * Tasks related to processing sources.
     */
    SOURCE_PROCESSING,
    /**
     * Tasks related to packaging artifacts.
     */
    ZIPPING,
    /**
     * Tasks related to linking artifacts.
     */
    LINKING,
    /**
     * Tasks related to merging artifacts.
     */
    MERGING,
    /**
     * Tasks part of fusing library.
     */
    FUSING,
    /**
     * Primary task category for tasks that perform their
     * execution on compiles classes (eg. bytecode).
     */
    COMPILED_CLASSES,
    /**
     * Task category for Gradle plugins. No task in AGP should use this.
     */
    GRADLE,
    /**
     * Primary task category for general AGP tasks.
     */
    MISC,
    /**
     * Task category for third-party plugins. No task in AGP should use this.
     */
    UNKNOWN
}
