/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.BytecodeTransformation
import com.android.builder.model.v2.ide.TestSuiteArtifact
import com.android.builder.model.v2.ide.TestSuiteTestInfo
import java.io.File
import java.io.Serializable

data class TestSuiteArtifactImpl(
  override val testInfo: TestSuiteTestInfo,
  override val compileTaskName: String?,
  override val assembleTaskName: String?,
  override val ideSetupTaskNames: Set<String>,
  override val generatedSourceFolders: Collection<File>,
  override val classesFolders: Set<File>,
  override val generatedClassPaths: Map<String, File>,
  override val bytecodeTransformations: Collection<BytecodeTransformation>,
) : TestSuiteArtifact, Serializable {
  companion object {
    @JvmStatic private val serialVersionUID: Long = 2L
  }

  @Deprecated("Was never used, removed in AGP 8.3")
  override val modelSyncFiles: Collection<Void>
    get() = emptyList()
}
