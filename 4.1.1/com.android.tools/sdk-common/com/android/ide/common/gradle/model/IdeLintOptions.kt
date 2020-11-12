/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model

import com.android.builder.model.LintOptions
import com.android.ide.common.repository.GradleVersion
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.Serializable

data class IdeLintOptions(
    val baselineFile: File? = null,
    val lintConfig: File? = null,
    val severityOverrides: Map<String, Int>? = null,
    val isCheckTestSources: Boolean = false,
    val isCheckDependencies: Boolean = false,
    val disable: Set<String> = mutableSetOf(), // instead of emptySet, because of ModelSerializationTest
    val enable: Set<String> = mutableSetOf(),
    val check: Set<String>? = null,
    val isAbortOnError: Boolean = true,
    val isAbsolutePaths: Boolean = true,
    val isNoLines: Boolean = false,
    val isQuiet: Boolean = false,
    val isCheckAllWarnings: Boolean = false,
    val isIgnoreWarnings: Boolean = false,
    val isWarningsAsErrors: Boolean = false,
    val isIgnoreTestSources: Boolean = false,
    val isCheckGeneratedSources: Boolean = false,
    val isCheckReleaseBuilds: Boolean = true,
    val isExplainIssues: Boolean = true,
    val isShowAll: Boolean = false,
    val textReport: Boolean = false,
    val textOutput: File? = null,
    val htmlReport: Boolean = true,
    val htmlOutput: File? = null,
    val xmlReport: Boolean = true,
    val xmlOutput: File? = null
) : Serializable {

    /** Creates a deep copy of the Gradle tooling API [LintOptions].  */
    constructor(
        options: LintOptions,
        modelVersion: GradleVersion?
    ) : this(
      baselineFile = if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0, "beta", 2, true))
            options.baselineFile
        else
            null,
      lintConfig = IdeModel.copyNewProperty<File>({ options.lintConfig }, null),
      severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
      isCheckTestSources = modelVersion != null &&
                modelVersion.isAtLeast(2, 4, 0) &&
                options.isCheckTestSources,
      isCheckDependencies = IdeModel.copyNewProperty({ options.isCheckDependencies }, false)!!,
      disable = IdeModel.copy(options.disable)!!,
      enable = IdeModel.copy(options.enable)!!,
      check = options.check?.let { ImmutableSet.copyOf(it) },
      isAbortOnError = IdeModel.copyNewProperty({ options.isAbortOnError }, true)!!,
      isAbsolutePaths = IdeModel.copyNewProperty({ options.isAbsolutePaths }, true)!!,
      isNoLines = IdeModel.copyNewProperty({ options.isNoLines }, false)!!,
      isQuiet = IdeModel.copyNewProperty({ options.isQuiet }, false)!!,
      isCheckAllWarnings = IdeModel.copyNewProperty({ options.isCheckAllWarnings }, false)!!,
      isIgnoreWarnings = IdeModel.copyNewProperty({ options.isIgnoreWarnings }, false)!!,
      isWarningsAsErrors = IdeModel.copyNewProperty({ options.isWarningsAsErrors }, false)!!,
      isIgnoreTestSources = IdeModel.copyNewProperty({ options.isIgnoreTestSources }, false)!!,
      isCheckGeneratedSources = IdeModel.copyNewProperty({ options.isCheckGeneratedSources }, false)!!,
      isExplainIssues = IdeModel.copyNewProperty({ options.isExplainIssues }, true)!!,
      isShowAll = IdeModel.copyNewProperty({ options.isShowAll }, false)!!,
      textReport = IdeModel.copyNewProperty({ options.textReport }, false)!!,
      textOutput = IdeModel.copyNewProperty({ options.textOutput }, null),
      htmlReport = IdeModel.copyNewProperty({ options.htmlReport }, true)!!,
      htmlOutput = IdeModel.copyNewProperty({ options.htmlOutput }, null),
      xmlReport = IdeModel.copyNewProperty({ options.xmlReport }, true)!!,
      xmlOutput = IdeModel.copyNewProperty({ options.xmlOutput }, null),
      isCheckReleaseBuilds = IdeModel.copyNewProperty({ options.isCheckReleaseBuilds }, true)!!
    )

    companion object {
        private const val serialVersionUID = 2L
    }
}
