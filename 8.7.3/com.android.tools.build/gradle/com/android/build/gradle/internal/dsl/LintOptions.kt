/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.dsl.decorator.annotation.NonNullableSetter
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.LintOptions.Companion.SEVERITY_DEFAULT_ENABLED
import com.android.builder.model.LintOptions.Companion.SEVERITY_ERROR
import com.android.builder.model.LintOptions.Companion.SEVERITY_FATAL
import com.android.builder.model.LintOptions.Companion.SEVERITY_IGNORE
import com.android.builder.model.LintOptions.Companion.SEVERITY_INFORMATIONAL
import com.android.builder.model.LintOptions.Companion.SEVERITY_WARNING
import com.android.tools.lint.model.LintModelSeverity
import java.io.File
import javax.inject.Inject

/**
 * Implementation of the (deprecated) lintOptions block ([com.android.build.api.dsl.LintOptions])
 *
 * This now simply delegates to the given delegate lint block [LintImpl], and has no state.
 */
abstract class LintOptions
@Inject
constructor(private val dslServices: DslServices, internal val delegate: LintImpl):
    com.android.builder.model.LintOptions,
    com.android.build.api.dsl.LintOptions {

    @set:NonNullableSetter
    final override var lintConfig: File?
        get() = delegate.lintConfig
        set(value) {
            delegate.lintConfig = value
        }

    final override var disable: MutableSet<String>
        get() = delegate.disable
        set(value) {
            delegate.disable.addAll(value)
        }

    final override var enable: MutableSet<String>
        get() = delegate.enable
        set(value) {
            delegate.enable.addAll(value)
        }

    override val checkOnly: MutableSet<String>
        get() = delegate.checkOnly

    @Deprecated(message = "", replaceWith = ReplaceWith("checkOnly"))
    final override var check: MutableSet<String>
        get() = checkOnly
        set(value) {
            checkOnly.addAll(value)
        }

    final override var isAbortOnError: Boolean
        get() = delegate.abortOnError
        set(value) { delegate.abortOnError = value }
    final override var isAbsolutePaths: Boolean
        get() = delegate.absolutePaths
        set(value) { delegate.absolutePaths = value }
    final override var isNoLines: Boolean
        get() = delegate.noLines
        set(value) { delegate.noLines = value }
    final override var isQuiet: Boolean
        get() = delegate.quiet
        set(value) { delegate.quiet = value }
    final override var isCheckAllWarnings: Boolean
        get() = delegate.checkAllWarnings
        set(value) { delegate.checkAllWarnings = value }

    final override var isIgnoreWarnings: Boolean
        get() = delegate.ignoreWarnings
        set(value) { delegate.ignoreWarnings = value }

    final override var isWarningsAsErrors: Boolean
        get() = delegate.warningsAsErrors
        set(value) { delegate.warningsAsErrors = value }

    final override var isCheckGeneratedSources: Boolean
        get() = delegate.checkGeneratedSources
        set(value) { delegate.checkGeneratedSources = value }

    final override var isExplainIssues: Boolean
        get() = delegate.explainIssues
        set(value) { delegate.explainIssues = value }

    final override var isShowAll: Boolean
        get() = delegate.showAll
        set(value) { delegate.showAll = value }

    final override var textReport: Boolean
        get() = delegate.textReport
        set(value) { delegate.textReport = value }

    final override var htmlReport: Boolean
        get() = delegate.htmlReport
        set(value) { delegate.htmlReport = value }

    final override var xmlReport: Boolean
        get() = delegate.xmlReport
        set(value) { delegate.xmlReport = value }

    final override var sarifReport: Boolean
        get() = delegate.sarifReport
        set(value) { delegate.sarifReport = value }

    final override var isCheckReleaseBuilds: Boolean
        get() = delegate.checkReleaseBuilds
        set(value) { delegate.checkReleaseBuilds = value }

    final override var isCheckDependencies: Boolean
        get() = delegate.checkDependencies
        set(value) { delegate.checkDependencies = value }


    final override var baselineFile: File?
        get() = delegate.baseline
        set(value) { delegate.baseline = value }

    final override var isCheckTestSources: Boolean
        get() = delegate.checkTestSources
        set(value) { delegate.checkTestSources = value }

    final override var isIgnoreTestSources: Boolean
        get() = delegate.ignoreTestSources
        set(value) { delegate.ignoreTestSources = value }

    final override var textOutput: File?
        get() = delegate.textOutput
        set(value) { delegate.textOutput = value }
    final override var htmlOutput: File?
        get() = delegate.htmlOutput
        set(value) { delegate.htmlOutput = value }
    final override var xmlOutput: File?
        get() = delegate.xmlOutput
        set(value) { delegate.xmlOutput = value }
    final override var sarifOutput: File?
        get() = delegate.sarifOutput
        set(value) { delegate.sarifOutput = value }

    override val severityOverrides: Map<String, Int>?
        get() = severityOverridesMap.mapValues { getToolingModelSeverity(it.value) }.ifEmpty { null }

    internal val severityOverridesMap: Map<String, LintModelSeverity>
        get() = delegate.severityOverridesMap

    private fun getToolingModelSeverity(severity: LintModelSeverity): Int =
        when (severity) {
            LintModelSeverity.FATAL -> SEVERITY_FATAL
            LintModelSeverity.ERROR -> SEVERITY_ERROR
            LintModelSeverity.WARNING -> SEVERITY_WARNING
            LintModelSeverity.INFORMATIONAL -> SEVERITY_INFORMATIONAL
            LintModelSeverity.IGNORE -> SEVERITY_IGNORE
            LintModelSeverity.DEFAULT_ENABLED -> SEVERITY_DEFAULT_ENABLED
        }

    // -- DSL Methods.
    override fun baseline(baseline: String) {
        delegate.baseline = dslServices.file(baseline)
    }

    override fun baseline(baselineFile: File) {
        delegate.baseline = baselineFile
    }

    @Suppress("OverridingDeprecatedMember")
    override fun check(id: String) {
        emitCheckWarning()
        checkOnly(id)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun check(vararg ids: String) {
        emitCheckWarning()
        checkOnly(*ids)
    }

    private fun emitCheckWarning() {
        dslServices.deprecationReporter
            .reportDeprecatedUsage(
                "android.lintOptions.checkOnly",
                "android.lintOptions.check",
                DeprecationTarget.LINT_CHECK_ONLY)
    }

    override fun checkOnly(id: String) {
        checkOnly.add(id)
    }

    override fun checkOnly(vararg ids: String) {
        ids.forEach {
            checkOnly(it)
        }
    }

    override fun enable(id: String) {
        enable.add(id)
    }

    override fun enable(vararg ids: String) {
        ids.forEach {
            enable(it)
        }
    }

    override fun disable(id: String) {
        disable.add(id)
    }

    override fun disable(vararg ids: String) {
        ids.forEach {
            disable(it)
        }
    }

    // For textOutput 'stdout' or 'stderr' (normally a file)
    override fun textOutput(textOutput: String) {
        this.textOutput = File(textOutput)
    }

    // For textOutput file()
    override fun textOutput(textOutput: File) {
        this.textOutput = textOutput
    }

    override fun informational(id: String) {
        delegate.informational += id
    }

    override fun informational(vararg ids: String) {
        ids.forEach {
            informational(it)
        }
    }

    override fun ignore(id: String) {
        @Suppress("DEPRECATION")
        delegate.ignore += id
    }

    override fun ignore(vararg ids: String) {
        ids.forEach {
            ignore(it)
        }
    }

    override fun warning(id: String) {
        delegate.warning += id
    }

    override fun warning(vararg ids: String) {
        ids.forEach {
            warning(it)
        }
    }

    override fun error(id: String) {
        delegate.error += id
    }

    override fun error(vararg ids: String) {
        ids.forEach {
            error(it)
        }
    }

    override fun fatal(id: String) {
        delegate.fatal += id
    }

    override fun fatal(vararg ids: String) {
        ids.forEach {
            fatal(it)
        }
    }
}
