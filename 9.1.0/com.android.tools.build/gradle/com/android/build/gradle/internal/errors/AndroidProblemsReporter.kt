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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter.Severity
import com.android.builder.errors.IssueReporter.Type
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class AndroidProblemReporterProvider @Inject constructor(private val problemsService: Problems) :
  BuildService<AndroidProblemReporterProvider.Parameters> {

  interface Parameters : BuildServiceParameters {

    val enableProblemsApi: Property<Boolean>
  }

  fun reporter(): AndroidProblemsReporter {
    return when {
      parameters.enableProblemsApi.get() -> AndroidProblemsReporterImpl(problemsService.reporter)

      else ->
        object : AndroidProblemsReporter {
          override fun reportSyncIssue(type: Type, severity: Severity, exception: EvalIssueException) = Unit
        }
    }
  }

  class RegistrationAction(project: Project, private val enableProblemsApi: Boolean) :
    ServiceRegistrationAction<AndroidProblemReporterProvider, Parameters>(project, AndroidProblemReporterProvider::class.java) {

    constructor(project: Project, options: ProjectOptions) : this(project, options.get(BooleanOption.ENABLE_PROBLEMS_API))

    override fun configure(parameters: Parameters) {
      parameters.enableProblemsApi.set(enableProblemsApi)
    }
  }
}

interface AndroidProblemsReporter {

  fun reportSyncIssue(type: Type, severity: Severity, exception: EvalIssueException)
}

/**
 * This class defines data provided with the problem. On IDE side it is being read using
 * [com.android.builder.model.v2.ide.SyncIssueDataView]. In case we evolve it in the future we need ensure sure backwards compatibility.
 */
interface SyncIssueData : AdditionalData {
  var data: String?
}

class AndroidProblemsReporterImpl(val problemReporter: ProblemReporter) : AndroidProblemsReporter {

  private val syncIssueProblemGroup = ProblemGroup.create("agp-sync-issues", "Sync Issues")

  override fun reportSyncIssue(type: Type, severity: Severity, exception: EvalIssueException) {
    val id = ProblemId.create(type.type.toString(), type.name, syncIssueProblemGroup)
    val problem = problemReporter.create(id, AndroidSyncIssueProblemBuilder(type, severity, exception))
    problemReporter.report(problem)
  }

  class AndroidSyncIssueProblemBuilder(val type: Type, val severity: Severity, val exception: EvalIssueException) : Action<ProblemSpec> {

    override fun execute(problem: ProblemSpec) {
      val problemSeverity =
        when (severity) {
          Severity.WARNING -> org.gradle.api.problems.Severity.WARNING
          Severity.ERROR -> org.gradle.api.problems.Severity.ERROR
        }
      problem.severity(problemSeverity)
      problem.contextualLabel(exception.message)
      exception.multilineMessage?.let { problem.details(it.joinToString(separator = "\n")) }
      exception.data?.let { problem.additionalData(SyncIssueData::class.java) { data -> data.data = it } }
      problem.withException(exception)
    }
  }
}
