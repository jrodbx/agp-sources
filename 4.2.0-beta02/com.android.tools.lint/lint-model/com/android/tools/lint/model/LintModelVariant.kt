/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.model

import com.android.ide.common.gradle.model.IdeVariant
import com.android.sdklib.AndroidVersion
import java.io.File

interface LintModelVariant {
    /** Module containing this variant */
    val module: LintModelModule

    val name: String
    val useSupportLibraryVectorDrawables: Boolean
    val mainArtifact: LintModelAndroidArtifact
    val testArtifact: LintModelJavaArtifact?
    val androidTestArtifact: LintModelAndroidArtifact?
    val mergedManifest: File?
    val manifestMergeReport: File?

    // For temporary backwards compatibility
    val oldVariant: IdeVariant?

    // In builder-model these are coming from the merged flavor, plus buildType merged in
    val `package`: String?
    val minSdkVersion: AndroidVersion?
    val targetSdkVersion: AndroidVersion?
    val resValues: Map<String, LintModelResourceField>
    val manifestPlaceholders: Map<String, String>
    val resourceConfigurations: Collection<String>
    val proguardFiles: Collection<File>
    val consumerProguardFiles: Collection<File>

    val sourceProviders: List<LintModelSourceProvider>
    val testSourceProviders: List<LintModelSourceProvider>

    val debuggable: Boolean
    val shrinkable: Boolean

    /** Build features in effect */
    val buildFeatures: LintModelBuildFeatures

    /**
     * Lookup from artifact address in a [LintModelDependencyGraph] to a [LintModelLibrary].
     * The libraries are shared across modules and variants, only the dependency graphs
     * pointing to the libraries by address are per artifact.
     */
    val libraryResolver: LintModelLibraryResolver
}

class DefaultLintModelVariant(
    override val module: LintModelModule,

    override val name: String,
    override val useSupportLibraryVectorDrawables: Boolean,
    override val mainArtifact: LintModelAndroidArtifact,
    override val testArtifact: LintModelJavaArtifact?,
    override val androidTestArtifact: LintModelAndroidArtifact?,
    override val mergedManifest: File?,
    override val manifestMergeReport: File?,
    override val `package`: String?,
    override val minSdkVersion: AndroidVersion?,
    override val targetSdkVersion: AndroidVersion?,

    /**
     * Resource fields declared in the DSL. Note that unlike the builder-model,
     * this map merges all the values from the mergedFlavor (which includes the defaultConfig)
     * as well as the buildType.
     */
    override val resValues: Map<String, LintModelResourceField>,
    /**
     * Manifest placeholders declared in the DSL. Note that unlike the builder-model,
     * this map merges all the values from the mergedFlavor (which includes the defaultConfig)
     * as well as the buildType.
     */
    override val manifestPlaceholders: Map<String, String>,

    override val resourceConfigurations: Collection<String>,
    override val proguardFiles: Collection<File>,
    override val consumerProguardFiles: Collection<File>,

    override val sourceProviders: List<LintModelSourceProvider>,
    override val testSourceProviders: List<LintModelSourceProvider>,

    override val debuggable: Boolean,
    override val shrinkable: Boolean,
    override val buildFeatures: LintModelBuildFeatures,
    override val libraryResolver: LintModelLibraryResolver,

    // For temporary backwards compatibility
    override val oldVariant: IdeVariant?
) : LintModelVariant {
    override fun toString(): String = name
}
