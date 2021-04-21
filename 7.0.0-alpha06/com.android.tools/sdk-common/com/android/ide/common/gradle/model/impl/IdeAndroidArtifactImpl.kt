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
package com.android.ide.common.gradle.model.impl

import com.android.builder.model.CodeShrinker
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput
import com.android.ide.common.gradle.model.IdeClassField
import com.android.ide.common.gradle.model.IdeDependencies
import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.ide.common.gradle.model.IdeTestOptions
import java.io.File

data class IdeAndroidArtifactImpl(
  override val name: String,
  override val compileTaskName: String,
  override val assembleTaskName: String,
  override val assembleTaskOutputListingFile: String,
  override val classesFolder: File,
  override val additionalClassesFolders: Collection<File>,
  override val javaResourcesFolder: File?,
  override val variantSourceProvider: IdeSourceProvider?,
  override val multiFlavorSourceProvider: IdeSourceProvider?,
  override val ideSetupTaskNames: Collection<String>,
  private val mutableGeneratedSourceFolders: MutableList<File>,
  override val isTestArtifact: Boolean,
  override val level2Dependencies: IdeDependencies,
  override val applicationId: String,
  override val signingConfigName: String?,
  override val outputs: List<IdeAndroidArtifactOutput>,
  override val isSigned: Boolean,
  override val generatedResourceFolders: Collection<File>,
  override val additionalRuntimeApks: List<File>,
  override val testOptions: IdeTestOptions?,
  override val abiFilters: Set<String>,
  override val bundleTaskName: String?,
  override val bundleTaskOutputListingFile: String?,
  override val apkFromBundleTaskName: String?,
  override val apkFromBundleTaskOutputListingFile: String?,
  override val codeShrinker: CodeShrinker?
) : IdeAndroidArtifact {
  override val generatedSourceFolders: Collection<File> get() = mutableGeneratedSourceFolders

  override fun addGeneratedSourceFolder(generatedSourceFolder: File) {
    mutableGeneratedSourceFolders.add(generatedSourceFolder)
  }

  override val resValues: Map<String, IdeClassField> get() = emptyMap()
}