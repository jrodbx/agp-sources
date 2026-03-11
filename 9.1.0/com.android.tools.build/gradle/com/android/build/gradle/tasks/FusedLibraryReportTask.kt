/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.getFusedLibraryDependencyModuleVersionIdentifiers
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.File
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task for generating human-readable JSON report of dependencies included in artifacts and what dependencies are dependencies in the Fused
 * Library. Integration tests found in [com.android.build.gradle.integration.library.FusedLibraryClassesVerificationTest].
 */
@DisableCachingByDefault(because = DisabledCachingReason.FAST_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA, secondaryTaskCategories = [TaskCategory.FUSING])
abstract class FusedLibraryReportTask : NonIncrementalGlobalTask() {

  @get:Input abstract val resolvedRuntimeConfiguration: Property<ResolvedComponentResult>

  @get:Input abstract val dependencies: SetProperty<ModuleVersionIdentifier>

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val localJars: ConfigurableFileCollection

  @get:OutputFile abstract val report: RegularFileProperty

  override fun doTaskAction() {
    val includedDependenciesDisplayNames =
      resolvedRuntimeConfiguration
        .get()
        .dependencies
        .filterNot { it.isConstraint }
        .map {
          when (it) {
            is ResolvedDependencyResult -> it.selected.id.displayName
            is UnresolvedDependencyResult -> it.requested.displayName
            else -> error("Unknown type ${it.javaClass.name}")
          }
        }
    val included = includedDependenciesDisplayNames + localJars.files.map { it.name }
    val dependenciesDisplayNames = dependencies.get().map { it.toString() }
    val fusedLibReport = FusedLibraryReport(included, dependenciesDisplayNames)
    fusedLibReport.writeToFile(report.get().asFile)
  }

  class CreationAction(private val creationConfig: FusedLibraryGlobalScope) : GlobalTaskCreationAction<FusedLibraryReportTask>() {

    override val name: String
      get() = "report"

    override val type: Class<FusedLibraryReportTask>
      get() = FusedLibraryReportTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<FusedLibraryReportTask>) {
      super.handleProvider(taskProvider)

      creationConfig.artifacts
        .setInitialProvider(taskProvider, FusedLibraryReportTask::report)
        .withName("report.json")
        .on(FusedLibraryInternalArtifactType.FUSED_LIBRARY_REPORT)
    }

    override fun configure(task: FusedLibraryReportTask) {
      super.configure(task)
      task.description = "Produces a report of dependencies included in the fused aar artifact and the dependencies that must be provided."
      val includeConfiguration = task.project.configurations.getByName(FusedLibraryConstants.INCLUDE_CONFIGURATION_NAME)
      val resolvableRuntimeConfiguration = task.project.configurations.getByName(FusedLibraryConstants.FUSED_RUNTIME_CONFIGURATION_NAME)

      task.resolvedRuntimeConfiguration.setDisallowChanges(resolvableRuntimeConfiguration.incoming.resolutionResult.rootComponent)
      task.dependencies.setDisallowChanges(getFusedLibraryDependencyModuleVersionIdentifiers(includeConfiguration))

      task.localJars.setFrom(creationConfig.getLocalJars())
    }
  }
}

data class FusedLibraryReport(
  @SerializedName("included") val included: List<String>,
  @SerializedName("dependencies") val dependencies: List<String>,
) {

  fun writeToFile(file: File) {
    file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(this))
  }

  companion object {
    fun readFromFile(file: File): FusedLibraryReport {
      return Gson().fromJson(file.readText(), FusedLibraryReport::class.java)
    }
  }
}
