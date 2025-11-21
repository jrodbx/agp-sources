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

package com.android.buildanalyzer.common

/**
 * Enum class for Category-Based Task Analyzer for Build Analyzer.
 * Each field corresponds to a specific atomic function/execution of a task.
 *
 * @param isPrimary indicates whether the category would be the primary category of some task
 * @param description the description of the category which is shown to the user, all primary
 *  categories should have descriptions.
 **/
enum class TaskCategory(val isPrimary: Boolean = false, val description: String = "") {
    // ---------------------------------------------------------------------------------------------
    // PRIMARY CATEGORIES
    // ---------------------------------------------------------------------------------------------
    TEST(isPrimary = true, description = "Tasks related to test execution."),
    MANIFEST(isPrimary = true, description = "Tasks related to Android Manifest merging and compiling."),
    ANDROID_RESOURCES(isPrimary = true, description = "Tasks related to Android resources compilation, processing, linking and merging."),
    NATIVE(isPrimary = true, description = "Tasks related to Native build compilation, linking and packaging."),
    JAVA(isPrimary = true, description = "Tasks related to Java source compilation, processing and merging."),
    JAVA_RESOURCES(isPrimary = true, description = "Tasks related to Java resources merging and packaging."),
    JAVA_DOC(isPrimary = true, description = "Tasks related to Java Doc generation and processing."),
    KOTLIN(isPrimary = true, description = "Tasks related to Kotlin source compilation, processing and merging."),
    AIDL(isPrimary = true, description = "Tasks related to AIDL source compilation and processing."),
    RENDERSCRIPT(isPrimary = true, description = "Tasks related to Renderscript sources compilation and processing."),
    SHADER(isPrimary = true, description = "Tasks related to Shader sources compilation and processing."),
    DEXING(isPrimary = true, description = "Tasks related to generating Dex files."),
    ART_PROFILE(isPrimary = true, description = "Tasks related to ART optimization profiles compilation and processing"),
    LINT(isPrimary = true, description = "Tasks created by the Lint tool."),
    DATA_BINDING(isPrimary = true, description = "Tasks related to data binding."),
    METADATA(isPrimary = true, description = "Tasks related to Metadata generation and processing"),
    VERIFICATION(isPrimary = true, description = "Tasks that verify the project and dependencies setup."),
    SYNC(isPrimary = true, description = "Tasks related to syncing IDE sources with Gradle."),
    DEPLOYMENT(isPrimary = true, description = "Tasks related to device deployment."),
    HELP(isPrimary = true, description = "Tasks that provide helpful information on the project"),
    APK_PACKAGING(isPrimary = true, description = "Tasks related to packaging APKs."),
    AAR_PACKAGING(isPrimary = true, description = "Tasks related to packaging AARs."),
    BUNDLE_PACKAGING(isPrimary = true, description = "Tasks related to packaging bundles."),
    OPTIMIZATION(isPrimary = true, description = "Tasks related to shrinking sources and resources."),
    COMPILED_CLASSES(isPrimary = true, description = "Tasks that process the output of compilation tasks."),
    GRADLE(isPrimary = true, description = "Core tasks created by Gradle."),
    MISC(isPrimary = true, description = "Miscellaneous Android Gradle Plugin tasks."),
    BUILD_SCRIPT(isPrimary = true, description = "Tasks defined in the build script."),
    BUILD_SOURCE(isPrimary = true, description = "Tasks created by plugins defined in the buildSrc."),
    UNCATEGORIZED(isPrimary = true, description = "Uncategorized tasks."),

    // ---------------------------------------------------------------------------------------------
    // SECONDARY CATEGORIES
    // ---------------------------------------------------------------------------------------------

    /**
     * Tasks that involves compilation of files/sources.
     */
    COMPILATION,
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
}
