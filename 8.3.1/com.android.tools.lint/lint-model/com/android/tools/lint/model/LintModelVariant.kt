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

import com.android.sdklib.AndroidVersion
import java.io.File

interface LintModelVariant {
  /** Module containing this variant. */
  val module: LintModelModule

  val name: String
  val useSupportLibraryVectorDrawables: Boolean

  /**
   * The single artifact passed to lint for analysis, or the "main" artifact if multiple artifacts
   * are passed to lint for analysis.
   */
  val artifact: LintModelArtifact

  /**
   * The single artifact passed to lint for analysis, or the "main" artifact if multiple artifacts
   * are passed to lint for analysis.
   */
  @Deprecated("This property is deprecated.", ReplaceWith("artifact"))
  val mainArtifact: LintModelAndroidArtifact
  val testArtifact: LintModelJavaArtifact?
  val androidTestArtifact: LintModelAndroidArtifact?
  val testFixturesArtifact: LintModelAndroidArtifact?
  val mergedManifest: File?
  val manifestMergeReport: File?

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
  val testFixturesSourceProviders: List<LintModelSourceProvider>

  val debuggable: Boolean
  val shrinkable: Boolean

  /** Build features in effect. */
  val buildFeatures: LintModelBuildFeatures

  /**
   * Lookup from artifact address in a [LintModelDependencyGraph] to a [LintModelLibrary]. The
   * libraries are shared across modules and variants, only the dependency graphs pointing to the
   * libraries by address are per artifact.
   */
  val libraryResolver: LintModelLibraryResolver

  /** The location of lint's partial results directory, if doing partial analysis. */
  val partialResultsDir: File?

  /** Files listing any D8 backported desugared methods or core library desugared methods. */
  val desugaredMethodsFiles: Collection<File>
}

/** mainArtifactOrNull is the "main" artifact if it being analyzed by lint, or null if not. */
class DefaultLintModelVariant(
  override val module: LintModelModule,
  override val name: String,
  override val useSupportLibraryVectorDrawables: Boolean,
  mainArtifactOrNull: LintModelAndroidArtifact?,
  override val testArtifact: LintModelJavaArtifact?,
  override val androidTestArtifact: LintModelAndroidArtifact?,
  override val testFixturesArtifact: LintModelAndroidArtifact?,
  override val mergedManifest: File?,
  override val manifestMergeReport: File?,
  override val `package`: String?,
  override val minSdkVersion: AndroidVersion?,
  override val targetSdkVersion: AndroidVersion?,

  /**
   * Resource fields declared in the DSL. Note that unlike the builder-model, this map merges all
   * the values from the mergedFlavor (which includes the defaultConfig) as well as the buildType.
   */
  override val resValues: Map<String, LintModelResourceField>,
  /**
   * Manifest placeholders declared in the DSL. Note that unlike the builder-model, this map merges
   * all the values from the mergedFlavor (which includes the defaultConfig) as well as the
   * buildType.
   */
  override val manifestPlaceholders: Map<String, String>,
  override val resourceConfigurations: Collection<String>,
  override val proguardFiles: Collection<File>,
  override val consumerProguardFiles: Collection<File>,
  override val sourceProviders: List<LintModelSourceProvider>,
  override val testSourceProviders: List<LintModelSourceProvider>,
  override val testFixturesSourceProviders: List<LintModelSourceProvider>,
  override val debuggable: Boolean,
  override val shrinkable: Boolean,
  override val buildFeatures: LintModelBuildFeatures,
  override val libraryResolver: LintModelLibraryResolver,
  override val partialResultsDir: File?,
  override val desugaredMethodsFiles: Collection<File>,
) : LintModelVariant {
  override fun toString(): String = name

  override val artifact: LintModelArtifact by lazy {
    return@lazy if (mainArtifactOrNull != null) {
      mainArtifactOrNull
    } else {
      val nonNullArtifacts: List<LintModelArtifact> =
        listOfNotNull(testArtifact, androidTestArtifact, testFixturesArtifact)
      if (nonNullArtifacts.size != 1) {
        throw RuntimeException("Unexpected number of artifacts")
      }
      nonNullArtifacts[0]
    }
  }

  @Deprecated("This property is deprecated.", replaceWith = ReplaceWith("artifact"))
  override val mainArtifact: LintModelAndroidArtifact by lazy {
    return@lazy mainArtifactOrNull
      ?: artifact as? LintModelAndroidArtifact
      ?: DefaultLintModelAndroidArtifact(
        applicationId = "",
        generatedResourceFolders = listOf(),
        generatedSourceFolders = listOf(),
        desugaredMethodsFiles = listOf(),
        dependencies = artifact.dependencies,
        classOutputs = artifact.classOutputs,
        type = artifact.type
      )
  }
}
