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

import org.gradle.api.Incubating
import java.io.File

/**
 * DSL object for configuring lint options. Example:
 *
 * ```
 * android {
 *    lintOptions {
 *          // set to true to turn off analysis progress reporting by lint
 *          quiet true
 *          // if true, stop the gradle build if errors are found
 *          abortOnError false
 *          // set to true to have all release builds run lint on issues with severity=fatal
 *          // and abort the build (controlled by abortOnError above) if fatal issues are found
 *          checkReleaseBuilds true
 *          // if true, only report errors
 *          ignoreWarnings true
 *          // if true, emit full/absolute paths to files with errors (true by default)
 *          //absolutePaths true
 *          // if true, check all issues, including those that are off by default
 *          checkAllWarnings true
 *          // if true, treat all warnings as errors
 *          warningsAsErrors true
 *          // turn off checking the given issue id's
 *          disable 'TypographyFractions','TypographyQuotes'
 *          // turn on the given issue id's
 *          enable 'RtlHardcoded','RtlCompat', 'RtlEnabled'
 *          // check *only* the given issue id's
 *          check 'NewApi', 'InlinedApi'
 *          // if true, don't include source code lines in the error output
 *          noLines true
 *          // if true, show all locations for an error, do not truncate lists, etc.
 *          showAll true
 *          // whether lint should include full issue explanations in the text error output
 *          explainIssues false
 *          // Fallback lint configuration (default severities, etc.)
 *          lintConfig file("default-lint.xml")
 *          // if true, generate a text report of issues (false by default)
 *          textReport true
 *          // location to write the output; can be a file or 'stdout' or 'stderr'
 *          //textOutput 'stdout'
 *          textOutput file("$reportsDir/lint-results.txt")
 *          // if true, generate an XML report for use by for example Jenkins
 *          xmlReport true
 *          // file to write report to (if not specified, defaults to lint-results.xml)
 *          xmlOutput file("$reportsDir/lint-report.xml")
 *          // if true, generate an HTML report (with issue explanations, sourcecode, etc)
 *          htmlReport true
 *          // optional path to HTML report (default will be lint-results.html in the builddir)
 *          htmlOutput file("$reportsDir/lint-report.html")
 *          // if true, generate a SARIF report (OASIS Static Analysis Results Interchange Format)
 *          sarifReport true
 *          // optional path to SARIF report (default will be lint-results.sarif in the builddir)
 *          sarifOutput file("$reportsDir/lint-report.html")
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
 *          // Normally lint will analyze all dependencies along with each module; this ensures
 *          // that lint can correctly (for example) determine if a resource declared in a library
 *          // is unused; checking only the library in isolation would not be able to identify this
 *          // problem. However, this leads to quite a bit of extra computation; a library is
 *          // analyzed repeatedly, for each module that it is used in.
 *          checkDependencies false
 *     }
 * }
 * ```
 */
@Incubating
@Deprecated("Renamed to Lint", replaceWith = ReplaceWith("Lint"))
interface LintOptions : Lint
