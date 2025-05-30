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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task for generating human-readable JSON report of dependencies included in artifacts and
 * what dependencies are dependencies in the Fused Library.
 * Integration tests found in [com.android.build.gradle.integration.library.FusedLibraryClassesVerificationTest].
 */
@DisableCachingByDefault(because = DisabledCachingReason.FAST_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA, secondaryTaskCategories = [TaskCategory.FUSING])
abstract class FusedLibraryReportTask : NonIncrementalGlobalTask() {

    @get:Input
    abstract val includeConfiguration: Property<ResolvedComponentResult>

    @get:Input
    abstract val dependencies: SetProperty<ModuleVersionIdentifier>

    @get:OutputFile
    abstract val report: RegularFileProperty

    override fun doTaskAction() {
        val includedDependenciesDisplayNames =
            includeConfiguration.get().dependencies.map { it.requested.displayName }
        val dependenciesDisplayNames = dependencies.get().map { it.toString() }
        val fusedLibReport = FusedLibraryReport(
            includedDependenciesDisplayNames,
            dependenciesDisplayNames
        )
        fusedLibReport.writeToFile(report.get().asFile)
    }

    class CreationAction(private val creationConfig: FusedLibraryGlobalScope) :
        GlobalTaskCreationAction<FusedLibraryReportTask>() {

        override val name: String
            get() = "report"

        override val type: Class<FusedLibraryReportTask>
            get() = FusedLibraryReportTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryReportTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryReportTask::report
            ).withName("report.json")
                .on(FusedLibraryInternalArtifactType.FUSED_LIBRARY_REPORT)
        }

        override fun configure(task: FusedLibraryReportTask) {
            super.configure(task)
            val includeConfiguration = task.project.configurations
                .getByName(FusedLibraryConstants.INCLUDE_CONFIGURATION_NAME)

            task.includeConfiguration.setDisallowChanges(
                includeConfiguration.incoming.resolutionResult.rootComponent
            )
            task.dependencies.setDisallowChanges(
                getFusedLibraryDependencyModuleVersionIdentifiers(
                    includeConfiguration, creationConfig.services.issueReporter)
            )
        }
    }
}

data class FusedLibraryReport(
    @SerializedName("included") val included: List<String>,
    @SerializedName("dependencies") val dependencies: List<String>) {

    fun writeToFile(file: File) {
        file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(this))
    }

    companion object {
        fun readFromFile(file: File): FusedLibraryReport {
            return Gson().fromJson(file.readText(), FusedLibraryReport::class.java)
        }
    }
}
