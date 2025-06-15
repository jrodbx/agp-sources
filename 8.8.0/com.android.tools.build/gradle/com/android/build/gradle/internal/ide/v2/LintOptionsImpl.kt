/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.builder.model.v2.ide.ApiVersion
import com.android.builder.model.v2.ide.LintOptions
import java.io.File
import java.io.Serializable

/**
 * Implementation of [LintOptions] for serialization via the Tooling API.
 */
data class LintOptionsImpl(
    override val disable: Set<String>,
    override val enable: Set<String>,
    override val informational: Set<String>,
    override val warning: Set<String>,
    override val error: Set<String>,
    override val fatal: Set<String>,
    override val checkOnly: Set<String>,
    override val abortOnError: Boolean,
    override val absolutePaths: Boolean,
    override val noLines: Boolean,
    override val quiet: Boolean,
    override val checkAllWarnings: Boolean,
    override val ignoreWarnings: Boolean,
    override val warningsAsErrors: Boolean,
    override val checkTestSources: Boolean,
    override val ignoreTestSources: Boolean,
    override val ignoreTestFixturesSources: Boolean,
    override val checkGeneratedSources: Boolean,
    override val explainIssues: Boolean,
    override val showAll: Boolean,
    override val lintConfig: File?,
    override val textReport: Boolean,
    override val textOutput: File?,
    override val htmlReport: Boolean,
    override val htmlOutput: File?,
    override val xmlReport: Boolean,
    override val xmlOutput: File?,
    override val sarifReport: Boolean,
    override val sarifOutput: File?,
    override val checkReleaseBuilds: Boolean,
    override val checkDependencies: Boolean,
    override val baseline: File?,
    override val targetSdk: ApiVersion?,
) : LintOptions, Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}
