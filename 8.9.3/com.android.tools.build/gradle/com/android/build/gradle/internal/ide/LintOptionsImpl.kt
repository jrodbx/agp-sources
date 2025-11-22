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

import com.android.annotations.concurrency.Immutable
import com.android.builder.model.LintOptions
import java.io.File
import java.io.Serializable

/**
 * Implementation of [LintOptions] that is Serializable.
 *
 * Should only be used for the model.
 */
@Immutable
data class LintOptionsImpl(
    override val disable: Set<String>,
    override val enable: Set<String>,
    override val check: Set<String>,
    override val isAbortOnError: Boolean,
    override val isAbsolutePaths: Boolean,
    override val isNoLines: Boolean,
    override val isQuiet: Boolean,
    override val isCheckAllWarnings: Boolean,
    override val isIgnoreWarnings: Boolean,
    override val isWarningsAsErrors: Boolean,
    override val isCheckTestSources: Boolean,
    override val isIgnoreTestSources: Boolean,
    override val isCheckGeneratedSources: Boolean,
    override val isExplainIssues: Boolean,
    override val isShowAll: Boolean,
    override val lintConfig: File?,
    override val textReport: Boolean,
    override val textOutput: File?,
    override val htmlReport: Boolean,
    override val htmlOutput: File?,
    override val xmlReport: Boolean,
    override val xmlOutput: File?,
    override val sarifReport: Boolean,
    override val sarifOutput: File?,
    override val isCheckReleaseBuilds: Boolean,
    override val isCheckDependencies: Boolean,
    override val baselineFile: File?,
    override val severityOverrides: Map<String, Int>?
) : LintOptions, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID = 1L
    }
}
