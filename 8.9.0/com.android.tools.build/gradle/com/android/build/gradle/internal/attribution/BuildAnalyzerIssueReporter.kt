/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.attribution

import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.buildanalyzer.common.TaskCategoryIssue
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry

class BuildAnalyzerIssueReporter(
    projectOptions: ProjectOptions,
    buildServiceRegistry: BuildServiceRegistry
) {

    private val buildAnalyzerConfiguratorService =
        getBuildService<BuildAnalyzerConfiguratorService, BuildServiceParameters.None>(
            buildServiceRegistry
        ).get()

    companion object {
        private val booleanOptionBasedIssues = mapOf(
            TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED to BooleanOption.NON_TRANSITIVE_R_CLASS,
            TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED to BooleanOption.USE_NON_FINAL_RES_IDS,
        )
    }

    init {
        booleanOptionBasedIssues.mapNotNull { (issue, booleanOption) ->
            issue.takeIf { !projectOptions.get(booleanOption) }
        }.forEach(::reportIssue)
    }

    fun reportIssue(issue: TaskCategoryIssue) {
        buildAnalyzerConfiguratorService.reportBuildAnalyzerIssue(issue)
    }
}
