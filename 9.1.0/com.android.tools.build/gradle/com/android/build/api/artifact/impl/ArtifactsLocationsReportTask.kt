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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.google.gson.Gson
import java.io.File
import kotlin.reflect.KClass
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.work.DisableCachingByDefault

/**
 * AGP debugging task that dumps all the artifacts location to a json file.
 *
 * The format of the file will be : { "single" : { ... list all single artifacts in the following format ... { "file_type": "Directory",
 * "artifact": "PROJECT_DEX_ARCHIVE", "location": "<some path>/build/intermediates/project_dex_archive/debug/dexBuilderDebug/out" }, },
 * "multiple": { ... list all multiple artifacts in the following format ...
 * * {
 *     * "file_type": "Directory",
 *     * "artifact": "PROJECT_DEX_ARCHIVE",
 *     * "location": [File.pathSeparator] separated list of absolute file path locations.
 *     * }, } }
 *
 * To activate this task, set the [com.android.build.gradle.options.BooleanOption.DUMP_ARTIFACTS_LOCATIONS] property to true.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC)
abstract class ArtifactsLocationsReportTask : NonIncrementalTask() {

  @get:Internal abstract val singleArtifacts: ListProperty<Pair<Artifact.Single<*>, Provider<out FileSystemLocation>>>

  @get:Internal abstract val multipleArtifacts: ListProperty<Pair<Artifact.Multiple<*>, List<Provider<out FileSystemLocation>>>>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  override fun doTaskAction() {
    val gson = Gson()
    val data = mutableMapOf<String, List<Map<String, String>>>()
    data["single"] = singleArtifacts.get().map { pair -> artifactToMap(pair.first, locationToString(pair.second)) }
    data["multiple"] = multipleArtifacts.get().map { pair -> artifactToMap(pair.first, locationToString(pair.second)) }
    val jsonString = gson.toJson(data)
    outputFile.get().asFile.writeText(jsonString)
  }

  private fun locationToString(location: Provider<out FileSystemLocation>) =
    if (location.isPresent) location.get().asFile.absolutePath else "Unknown_location"

  private fun locationToString(location: List<Provider<out FileSystemLocation>>) =
    location.joinToString(File.pathSeparator) { if (it.isPresent) it.get().asFile.absolutePath else "" }

  private fun artifactToMap(artifactType: Artifact<*>, location: String): Map<String, String> =
    mutableMapOf<String, String>()
      .also {
        it["file_type"] = artifactType.kind.dataType().simpleName ?: "Unknown"
        it["artifact"] = artifactType.name()
        it["location"] = location
      }
      .toMap()

  class CreationAction(creationConfig: ComponentCreationConfig) :
    VariantTaskCreationAction<ArtifactsLocationsReportTask, ComponentCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("dump", "ArtifactsLocations")

    override val type: Class<ArtifactsLocationsReportTask>
      get() = ArtifactsLocationsReportTask::class.java

    override fun configure(task: ArtifactsLocationsReportTask) {
      super.configure(task)
      dumpSingleArtifacts(task, SingleArtifact::class)
      dumpSingleArtifacts(task, InternalArtifactType::class)
      dumpMultipleArtifacts(task, MultipleArtifact::class)
      dumpMultipleArtifacts(task, InternalMultipleArtifactType::class)
      task.outputFile.set(
        creationConfig.services.projectInfo.intermediatesFile("agp-debug/${creationConfig.name}/dump-artifacts-locations.json")
      )
    }

    private fun dumpSingleArtifacts(task: ArtifactsLocationsReportTask, artifactsType: KClass<out Artifact.Single<*>>) {
      artifactsType.sealedSubclasses.forEach { artifactTypeClass ->
        val artifactType = artifactTypeClass.objectInstance ?: return
        val artifactContainer = creationConfig.artifacts.getArtifactContainer(artifactType)
        task.singleArtifacts.add(artifactType to artifactContainer.locationOnly())
      }
    }

    private fun dumpMultipleArtifacts(task: ArtifactsLocationsReportTask, artifactsType: KClass<out Artifact.Multiple<*>>) {
      artifactsType.sealedSubclasses.forEach { artifactTypeClass ->
        val artifactType = artifactTypeClass.objectInstance ?: return
        val artifactContainer = creationConfig.artifacts.getArtifactContainer(artifactType)
        task.multipleArtifacts.add(artifactType to artifactContainer.locationOnly())
      }
    }
  }
}
