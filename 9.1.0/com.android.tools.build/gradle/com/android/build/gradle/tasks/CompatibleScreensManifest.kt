/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task to generate a manifest snippet that just contains a compatible-screens node with the given density and the given list of screen
 * sizes.
 *
 * Caching disabled by default for this task because the task does very little work. Input files are written to a minimal XML file and no
 * computation is required. Calculating cache hit/miss and fetching results is likely more expensive than simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST, secondaryTaskCategories = [TaskCategory.SOURCE_GENERATION])
abstract class CompatibleScreensManifest : NonIncrementalTask() {

  @get:Input abstract val applicationId: Property<String>

  @get:Input abstract val screenSizes: SetProperty<String>

  @get:OutputDirectory abstract val outputFolder: DirectoryProperty

  @get:Nested abstract val variantOutputs: ListProperty<VariantOutputImpl>

  @get:Input @get:Optional abstract val minSdkVersion: Property<String>

  override fun doTaskAction() {
    BuiltArtifactsImpl(
        artifactType = COMPATIBLE_SCREEN_MANIFEST,
        applicationId = applicationId.get(),
        variantName = variantName,
        elements = emptyList(),
      )
      .save(outputFolder.get())
  }

  class CreationAction(creationConfig: ApplicationCreationConfig, private val screenSizes: Set<String>) :
    VariantTaskCreationAction<CompatibleScreensManifest, ApplicationCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("create", "CompatibleScreenManifests")

    override val type: Class<CompatibleScreensManifest>
      get() = CompatibleScreensManifest::class.java

    override fun handleProvider(taskProvider: TaskProvider<CompatibleScreensManifest>) {
      super.handleProvider(taskProvider)
      creationConfig.artifacts.setInitialProvider(taskProvider, CompatibleScreensManifest::outputFolder).on(COMPATIBLE_SCREEN_MANIFEST)
    }

    override fun configure(task: CompatibleScreensManifest) {
      super.configure(task)

      task.screenSizes.setDisallowChanges(screenSizes)
      task.applicationId.setDisallowChanges(creationConfig.applicationId)

      task.variantOutputs.setDisallowChanges(creationConfig.outputs.getEnabledVariantOutputs())

      task.minSdkVersion.setDisallowChanges(task.project.provider { creationConfig.minSdk.getApiString() })
    }
  }
}
