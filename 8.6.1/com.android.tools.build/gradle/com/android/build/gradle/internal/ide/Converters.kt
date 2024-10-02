/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.ide

import com.android.build.gradle.internal.dsl.LintOptions

internal fun convertLintOptions(source: LintOptions): LintOptionsImpl {
    return LintOptionsImpl(
        disable = source.disable.toSet(),
        enable = source.enable.toSet(),
        check = source.checkOnly.toSet(),
        isAbortOnError = source.isAbortOnError,
        isAbsolutePaths = source.isAbsolutePaths,
        isNoLines = source.isNoLines,
        isQuiet = source.isQuiet,
        isCheckAllWarnings = source.isCheckAllWarnings,
        isIgnoreWarnings = source.isIgnoreWarnings,
        isWarningsAsErrors = source.isWarningsAsErrors,
        isCheckTestSources = source.isCheckTestSources,
        isIgnoreTestSources = source.isIgnoreTestSources,
        isCheckGeneratedSources = source.isCheckGeneratedSources,
        isExplainIssues = source.isExplainIssues,
        isShowAll = source.isShowAll,
        lintConfig = source.lintConfig,
        textReport = source.textReport,
        textOutput = source.textOutput,
        htmlReport = source.htmlReport,
        htmlOutput = source.htmlOutput,
        xmlReport = source.xmlReport,
        xmlOutput = source.xmlOutput,
        sarifReport = source.sarifReport,
        sarifOutput = source.sarifOutput,
        isCheckReleaseBuilds = source.isCheckReleaseBuilds,
        isCheckDependencies = source.isCheckDependencies,
        baselineFile = source.baselineFile,
        severityOverrides = source.severityOverrides?.toMap()
    )
}
