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

package com.android.build.gradle.internal.coverage.tasks

import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.TaskCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.builder.core.ComponentTypeImpl
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

interface CodeCoverageReportCreationConfig : TaskCreationConfig {

  /** Kotlin source folders. */
  val kotlin: FlatSourceDirectoriesImpl?

  /** Java sources folders. */
  val java: FlatSourceDirectoriesImpl?

  /** runs [action] passing the [Sources.java] internal representation if not null. If null, action is not run. */
  fun java(action: (FlatSourceDirectoriesImpl) -> Unit)

  /** runs [action] passing the [Sources.kotlin] internal representation if not null. If null, action is not run. */
  fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit)

  /** Access to the global artifacts */
  val globalArtifacts: Artifacts

  /** Collection of coverage reports from dependent modules. */
  val dependantModulesReports: FileCollection

  /** Provider for the unit test coverage file. */
  val unitTestCoverageFile: Provider<RegularFile>?

  /** Provider for the connected test coverage directory. */
  val connectedTestCoverageDirectory: Provider<Directory>?
}

class CodeCoverageReportCreationConfigImpl(
  private val variantCreationConfig: VariantCreationConfig,
  private val testComponents: Collection<TestComponentCreationConfig>,
) : CodeCoverageReportCreationConfig {

  override val kotlin: FlatSourceDirectoriesImpl?
    get() = variantCreationConfig.sources.kotlin

  override val java: FlatSourceDirectoriesImpl?
    get() = variantCreationConfig.sources.java

  override fun java(action: (FlatSourceDirectoriesImpl) -> Unit) {
    variantCreationConfig.sources.java(action)
  }

  override fun kotlin(action: (FlatSourceDirectoriesImpl) -> Unit) {
    variantCreationConfig.sources.kotlin(action)
  }

  override val globalArtifacts: Artifacts
    get() = variantCreationConfig.global.globalArtifacts

  override val dependantModulesReports: FileCollection
    get() =
      variantCreationConfig.variantDependencies.getArtifactFileCollection(
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
        AndroidArtifacts.ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.CODE_COVERAGE_DATA,
      )

  override val name: String
    get() = variantCreationConfig.name

  override val services: TaskCreationServices
    get() = variantCreationConfig.services

  override val taskContainer: MutableTaskContainer
    get() = variantCreationConfig.taskContainer

  override val artifacts: ArtifactsImpl
    get() = variantCreationConfig.artifacts

  override val unitTestCoverageFile: Provider<RegularFile>?
    get() =
      testComponents
        .firstOrNull {
          it.mainVariant.name == name &&
            it is HostTestCreationConfig &&
            it.codeCoverageEnabled &&
            it.componentType == ComponentTypeImpl.UNIT_TEST
        }
        ?.artifacts
        ?.get(InternalArtifactType.UNIT_TEST_CODE_COVERAGE)

  override val connectedTestCoverageDirectory: Provider<Directory>?
    get() =
      testComponents
        .firstOrNull { it.mainVariant.name == name && it is DeviceTestCreationConfig && it.codeCoverageEnabled && it.componentType.isApk }
        ?.artifacts
        ?.get(InternalArtifactType.CODE_COVERAGE)
}
