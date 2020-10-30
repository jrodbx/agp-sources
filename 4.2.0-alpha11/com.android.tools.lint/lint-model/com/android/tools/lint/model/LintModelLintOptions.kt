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

package com.android.tools.lint.model

import java.io.File
import java.io.Serializable

/**
 * Options for lint. Example:
 *
 * <pre>
 *
 * android {
 * lintOptions {
 * // set to true to turn off analysis progress reporting by lint
 * quiet true
 * // if true, stop the gradle build if errors are found
 * abortOnError false
 * // set to true to have all release builds run lint on issues with severity=fatal
 * // and abort the build (controlled by abortOnError above) if fatal issues are found
 * checkReleaseBuilds true
 * // if true, only report errors
 * ignoreWarnings true
 * // if true, emit full/absolute paths to files with errors (true by default)
 * //absolutePaths true
 * // if true, check all issues, including those that are off by default
 * checkAllWarnings true
 * // if true, treat all warnings as errors
 * warningsAsErrors true
 * // turn off checking the given issue id's
 * disable 'TypographyFractions','TypographyQuotes'
 * // turn on the given issue id's
 * enable 'RtlHardcoded','RtlCompat', 'RtlEnabled'
 * // check *only* the given issue id's
 * check 'NewApi', 'InlinedApi'
 * // if true, don't include source code lines in the error output
 * noLines true
 * // if true, show all locations for an error, do not truncate lists, etc.
 * showAll true
 * // whether lint should include full issue explanations in the text error output
 * explainIssues false
 * // Fallback lint configuration (default severities, etc.)
 * lintConfig file("default-lint.xml")
 * // if true, generate a text report of issues (false by default)
 * textReport true
 * // location to write the output; can be a file or 'stdout' or 'stderr'
 * //textOutput 'stdout'
 * textOutput file("lint-results.txt")
 * // if true, generate an XML report for use by for example Jenkins
 * xmlReport true
 * // file to write report to (if not specified, defaults to lint-results.xml)
 * xmlOutput file("lint-report.xml")
 * // if true, generate an HTML report (with issue explanations, sourcecode, etc)
 * htmlReport true
 * // optional path to report (default will be lint-results.html in the builddir)
 * htmlOutput file("lint-report.html")
 * // Set the severity of the given issues to fatal (which means they will be
 * // checked during release builds (even if the lint target is not included)
 * fatal 'NewApi', 'InlineApi'
 * // Set the severity of the given issues to error
 * error 'Wakelock', 'TextViewEdits'
 * // Set the severity of the given issues to warning
 * warning 'ResourceAsColor'
 * // Set the severity of the given issues to ignore (same as disabling the check)
 * ignore 'TypographyQuotes'
 * // Set the severity of the given issues to informational
 * informational 'StopShip'
 * // Use (or create) a baseline file for issues that should not be reported
 * baseline file("lint-baseline.xml")
 * // Normally most lint checks are not run on test sources (except the checks
 * // dedicated to looking for mistakes in unit or instrumentation tests, unless
 * // ignoreTestSources is true). You can turn on normal lint checking in all
 * // sources with the following flag, false by default:
 * checkTestSources true
 * // Like checkTestSources, but always skips analyzing tests -- meaning that it
 * // also ignores checks that have explicitly asked to look at test sources, such
 * // as the unused resource check.
 * ignoreTestSources true
 * // Normally lint will skip generated sources, but you can turn it on with this flag
 * checkGeneratedSources true
 * // Normally lint will analyze all dependencies along with each module; this ensures
 * // that lint can correctly (for example) determine if a resource declared in a library
 * // is unused; checking only the library in isolation would not be able to identify this
 * // problem. However, this leads to quite a bit of extra computation; a library is
 * // analyzed repeatedly, for each module that it is used in.
 * checkDependencies false
 * }
 * }
</pre> *
 */
interface LintModelLintOptions {
    /**
     * Returns the set of issue id's to suppress. Callers are allowed to modify this collection.
     * To suppress a given issue, add the lint issue id to the returned set.
     */
    val disable: Set<String>

    /**
     * Returns the set of issue id's to enable. Callers are allowed to modify this collection.
     * To enable a given issue, add the lint issue id to the returned set.
     */
    val enable: Set<String>

    /**
     * Returns the exact set of issues to check, or null to run the issues that are enabled
     * by default plus any issues enabled via [.getEnable] and without issues disabled
     * via [.getDisable]. If non-null, callers are allowed to modify this collection.
     */
    val check: Set<String>?

    /** Whether lint should abort the build if errors are found  */
    val abortOnError: Boolean

    /**
     * Whether lint should display full paths in the error output. By default the paths
     * are relative to the path lint was invoked from.
     */
    val absolutePaths: Boolean

    /**
     * Whether lint should include the source lines in the output where errors occurred
     * (true by default)
     */
    val noLines: Boolean

    /**
     * Returns whether lint should be quiet (for example, not write informational messages
     * such as paths to report files written)
     */
    val quiet: Boolean

    /** Returns whether lint should check all warnings, including those off by default  */
    val checkAllWarnings: Boolean

    /** Returns whether lint will only check for errors (ignoring warnings)  */
    val ignoreWarnings: Boolean

    /** Returns whether lint should treat all warnings as errors  */
    val warningsAsErrors: Boolean

    /**
     * Returns whether lint should run all checks on test sources, instead of just the
     * lint checks that have been specifically written to include tests (e.g. checks
     * looking for specific test errors, or checks that need to consider testing code
     * such as the unused resource detector)
     *
     * @return true to check tests, defaults to false
     * @since 2.4
     */
    val checkTestSources: Boolean

    /**
     * Like [.isCheckTestSources], but always skips analyzing tests -- meaning that it also
     * ignores checks that have explicitly asked to look at test sources, such as the unused
     * resource check.
     *
     * @since 3.2.0-alpha14
     */
    val ignoreTestSources: Boolean

    /**
     * Returns whether lint should run checks on generated sources.
     *
     * @return true to check generated sources, defaults to false
     * @since 2.4
     */
    val checkGeneratedSources: Boolean

    /** Returns whether lint should include explanations for issue errors. (Note that
     * HTML and XML reports intentionally do this unconditionally, ignoring this setting.)  */
    val explainIssues: Boolean

    /**
     * Returns whether lint should include all output (e.g. include all alternate
     * locations, not truncating long messages, etc.)
     */
    val showAll: Boolean

    /**
     * Returns an optional path to a lint.xml configuration file
     */
    val lintConfig: File?

    /** Whether we should write an text report. Default false. The location can be
     * controlled by [.getTextOutput].  */
    val textReport: Boolean

    /**
     * The optional path to where a text report should be written. The special value
     * "stdout" can be used to point to standard output.
     */
    val textOutput: File?

    /** Whether we should write an HTML report. Default true. The location can be
     * controlled by [.getHtmlOutput].  */
    val htmlReport: Boolean

    /** The optional path to where an HTML report should be written  */
    val htmlOutput: File?

    /** Whether we should write an XML report. Default true. The location can be
     * controlled by [.getXmlOutput].  */
    val xmlReport: Boolean

    /** The optional path to where an XML report should be written  */
    val xmlOutput: File?

    /**
     * Returns whether lint should check for fatal errors during release builds. Default is true.
     * If issues with severity "fatal" are found, the release build is aborted.
     */
    val checkReleaseBuilds: Boolean

    /**
     * Returns whether lint should check all dependencies too as part of its analysis. Default is
     * false.
     */
    val checkDependencies: Boolean

    /**
     * Returns the baseline file to use, if any. The baseline file is
     * an XML report previously created by lint, and any warnings and
     * errors listed in that report will be ignored from analysis.
     *
     *
     * If you have a project with a large number of existing warnings,
     * this lets you set a baseline and only see newly introduced warnings
     * until you get a chance to go back and address the "technical debt"
     * of the earlier warnings.
     *
     * @return the baseline file, if any
     */
    val baselineFile: File?

    /**
     * An optional map of severity overrides. The map maps from issue id's to the corresponding
     *
     * severity to use, which must be "fatal", "error", "warning", or "ignore".
     */
    val severityOverrides: Map<String, LintModelSeverity>?
}

class DefaultLintModelLintOptions(
    override val disable: Set<String>,
    override val enable: Set<String>,
    override val check: Set<String>?,
    override val abortOnError: Boolean,
    override val absolutePaths: Boolean,
    override val noLines: Boolean,
    override val quiet: Boolean,
    override val checkAllWarnings: Boolean,
    override val ignoreWarnings: Boolean,
    override val warningsAsErrors: Boolean,
    override val checkTestSources: Boolean,
    override val ignoreTestSources: Boolean,
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
    override val checkReleaseBuilds: Boolean,
    override val checkDependencies: Boolean,
    override val baselineFile: File?,
    override val severityOverrides: Map<String, LintModelSeverity>?
) : LintModelLintOptions, Serializable
