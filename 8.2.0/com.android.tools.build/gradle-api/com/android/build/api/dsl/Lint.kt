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

package com.android.build.api.dsl

import java.io.File

/**
 * DSL object for configuring lint options. Example:
 *
 * ```
 * android {
 *    lint {
 *          // set to true to turn off analysis progress reporting by lint
 *          quiet = true
 *          // if true, stop the gradle build if errors are found
 *          abortOnError = false
 *          // set to true to have all release builds run lint on issues with severity=fatal
 *          // and abort the build (controlled by abortOnError above) if fatal issues are found
 *          checkReleaseBuilds = true
 *          // if true, only report errors
 *          ignoreWarnings = true
 *          // if true, emit full/absolute paths to files with errors (true by default)
 *          absolutePaths = true
 *          // if true, check all issues, including those that are off by default
 *          checkAllWarnings = true
 *          // if true, treat all warnings as errors
 *          warningsAsErrors = true
 *          // turn off checking the given issue id's
 *          disable += ['TypographyFractions', 'TypographyQuotes']
 *          // turn on the given issue id's
 *          enable += ['RtlHardcoded','RtlCompat', 'RtlEnabled']
 *          // check *only* the given issue id's
 *          checkOnly += ['NewApi', 'InlinedApi']
 *          // if true, don't include source code lines in the error output
 *          noLines = true
 *          // if true, show all locations for an error, do not truncate lists, etc.
 *          showAll = true
 *          // whether lint should include full issue explanations in the text error output
 *          explainIssues = false
 *          // Fallback lint configuration (default severities, etc.)
 *          lintConfig = file("default-lint.xml")
 *          // if true, generate a text report of issues (false by default)
 *          textReport = true
 *          // location to write the output; can be a file or 'stdout' or 'stderr'
 *          //textOutput 'stdout'
 *          textOutput file("$buildDir/reports/lint-results.txt")
 *          // if true, generate an XML report for use by for example Jenkins
 *          xmlReport true
 *          // file to write report to (if not specified, defaults to lint-results.xml)
 *          xmlOutput file("$buildDir/reports/lint-report.xml")
 *          // if true, generate an HTML report (with issue explanations, sourcecode, etc)
 *          htmlReport true
 *          // optional path to HTML report (default will be lint-results.html in the builddir)
 *          htmlOutput file("$buildDir/reports/lint-report.html")
 *          // if true, generate a SARIF report (OASIS Static Analysis Results Interchange Format)
 *          sarifReport true
 *          // optional path to SARIF report (default will be lint-results.sarif in the builddir)
 *          sarifOutput file("$buildDir/reports/lint-report.html")
 *          // Set the severity of the given issues to fatal (which means they will be
 *          // checked during release builds (even if the lint target is not included)
 *          fatal 'NewApi', 'InlineApi'
 *          // Set the severity of the given issues to error
 *          error 'Wakelock', 'TextViewEdits'
 *          // Set the severity of the given issues to warning
 *          warning 'ResourceAsColor'
 *          // Set the severity of the given issues to ignore (same as disabling the check)
 *          ignore 'TypographyQuotes'
 *          // Set the severity of the given issues to informational
 *          informational 'StopShip'
 *          // Use (or create) a baseline file for issues that should not be reported
 *          baseline file("lint-baseline.xml")
 *          // Normally most lint checks are not run on test sources (except the checks
 *          // dedicated to looking for mistakes in unit or instrumentation tests, unless
 *          // ignoreTestSources is true). You can turn on normal lint checking in all
 *          // sources with the following flag, false by default:
 *          checkTestSources true
 *          // Like checkTestSources, but always skips analyzing tests -- meaning that it
 *          // also ignores checks that have explicitly asked to look at test sources, such
 *          // as the unused resource check.
 *          ignoreTestSources true
 *          // Normally lint will skip generated sources, but you can turn it on with this flag
 *          checkGeneratedSources true
 *          // Whether lint should check all dependencies too as part of its analysis.
 *          // Default is false.
 *          checkDependencies true
 *          // targetSdk version used when generating a lint report for a library.
 *          // Must be equal or higher than main target SDK. Must be set for libraries only.
 *          targetSdk 33
 *     }
 * }
 * ```
 */
interface Lint {
    /**
     * The set of issue IDs to suppress. Callers are allowed to modify this collection.
     *
     */
    val disable: MutableSet<String>

    /**
     * The set of issue IDs to enable. Callers are allowed to modify this collection.
     */
    val enable: MutableSet<String>

    /**
     * The exact set of issues to check set by [checkOnly].
     *
     * If empty, lint will detect the issues that are enabled by default plus
     * any issues enabled via [enable] and without issues disabled via [disable].
     */
    val checkOnly: MutableSet<String>

    /** Whether lint should set the exit code of the process if errors are found */
    var abortOnError: Boolean

    /**
     * Whether lint should display full paths in the error output. By default the paths are relative
     * to the path lint was invoked from.
     */
    var absolutePaths: Boolean

    /**
     * Whether lint should include the source lines in the output where errors occurred (true by
     * default)
     */
    var noLines: Boolean

    /**
     * Whether lint should be quiet (for example, not write informational messages such as paths to
     * report files written)
     */
    var quiet: Boolean

    /** Whether lint should check all warnings, including those off by default */
    var checkAllWarnings: Boolean

    /** Returns whether lint will only check for errors (ignoring warnings) */
    var ignoreWarnings: Boolean

    /** Whether lint should treat all warnings as errors */
    var warningsAsErrors: Boolean

    /**
     * Whether lint should run all checks on test sources, instead of just the lint checks
     * that have been specifically written to include tests (e.g. checks looking for specific test
     * errors, or checks that need to consider testing code such as the unused resource detector)
     */
    var checkTestSources: Boolean

    /**
     * Whether lint should ignore all test sources. This is like [checkTestSources], but always
     * skips analyzing tests -- meaning that it also ignores checks that have explicitly asked to
     * look at test sources, such as the unused resource check.
     */
    var ignoreTestSources: Boolean

    /**
     * Whether lint should ignore all testFixtures sources.
     */
    var ignoreTestFixturesSources: Boolean

    /** Returns whether lint should run checks on generated sources. */
    var checkGeneratedSources: Boolean

    /** Whether lint should check all dependencies too as part of its analysis. Default is false. */
    var checkDependencies: Boolean

    /**
     * Whether lint should include explanations for issue errors. (Note that HTML and XML reports
     * intentionally do this unconditionally, ignoring this setting.)
     */
    var explainIssues: Boolean

    /**
     * Whether lint should include all output (e.g. include all alternate locations, not truncating
     * long messages, etc.)
     */
    var showAll: Boolean

    /**
     * Whether lint should check for fatal errors during release builds. Default is true. If issues
     * with severity "fatal" are found, the release build is aborted.
     */
    var checkReleaseBuilds: Boolean

    /**
     * The default config file to use as a fallback. This corresponds to a `lint.xml` file with
     * severities etc to use when a project does not have more specific information.
     */
    var lintConfig: File?

    /**
     * Whether lint should write a text report.
     *
     * With the default setting ([textReport]`=false`),
     * the lint task will print a summary to `stdout` if there are any lint warnings or errors
     * including a link to the full text report.
     *
     * When [textReport]`=true` and [textOutput] is unset, the full report will be printed by the
     * lint task, rather than just a summary. When [textOutput] is set, the full text report
     * will be copied to the specified location.
     */
    var textReport: Boolean

    /**
     * Whether we should write an HTML report. Default is true. The location can be controlled by
     * [htmlOutput].
     */
    var htmlReport: Boolean

    /**
     * Whether we should write a SARIF (OASIS Static Analysis Results Interchange Format) report.
     * Default is false. The location can be controlled by [sarifOutput].
     */
    var sarifReport: Boolean

    /**
     * Whether we should write an XML report. Default is true. The location can be controlled by
     * [xmlOutput].
     */
    var xmlReport: Boolean

    /**
     * The optional path to where a text report should be written.
     *
     * To output the lint report to `stdout` set [textReport]`=true`, and leave [textOutput] unset.
     */
    var textOutput: File?

    /**
     * The optional path to where an HTML report should be written.
     * Setting this property will also turn on [htmlReport].
     */
    var htmlOutput: File?

    /**
     * The optional path to where an XML report should be written.
     * Setting this property will also turn on [xmlReport].
     */
    var xmlOutput: File?

    /**
     * The optional path to where a SARIF report (OASIS Static
     * Analysis Results Interchange Format) should be written.
     * Setting this property will also turn on [sarifReport].
     */
    var sarifOutput: File?

    /**
     * The baseline file to use, if any. The baseline file is an XML report previously created by
     * lint, and any warnings and errors listed in that report will be ignored from analysis.
     *
     * If you have a project with a large number of existing warnings, this lets you set a baseline
     * and only see newly introduced warnings until you get a chance to go back and address the
     * "technical debt" of the earlier warnings.
     */
    var baseline: File?

    /** Issues that have severity overridden to 'informational' */
    val informational: MutableSet<String>

    /** Issues that have severity overridden to 'ignore' */
    @Deprecated("Ignore and disable are synonyms", ReplaceWith("disable"))
    val ignore: MutableSet<String>

    /** Issues that have severity overridden to 'warning' */
    val warning: MutableSet<String>

    /** Issues that have severity overridden to 'error' */
    val error: MutableSet<String>

    /** Issues that have severity overridden to 'fatal' */
    val fatal: MutableSet<String>

    /**
     * The target sdk version used when generating a lint report for a library.
     * Must be equal or higher than main target SDK. Must be set for libraries only.
     */
    var targetSdk: Int?

    /**
     * The target sdk preview version used when generating a lint report for a library.
     * Must be set for libraries only.
     */
    var targetSdkPreview: String?
}
