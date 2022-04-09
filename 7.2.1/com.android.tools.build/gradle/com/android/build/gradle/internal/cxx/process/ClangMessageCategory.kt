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

package com.android.build.gradle.internal.cxx.process

/**
 * Detect common diagnostic message formats so that they can be passed to lifecycle instead of info.
 * Only lifecycle lines will be seen by the user at the command-line (and by Android Studio build
 * logging).
 */
private val diagnosticTypes = listOf("ignored", "note", "remark", "warning", "error", "fatal error")
private val diagnosticTypeOrPattern = diagnosticTypes.joinToString("|")
private val clangLinkerErrorPattern =
    Regex("clang(\\+\\+)?(\\.exe)?: error: linker command failed with exit code 1.*")
private val clangFileInclusionPattern = Regex("In file included from (.+):(\\d+):")
private val clangDiagnosticMessagePattern =
    Regex("((?:[A-Z]:)?[^\\s][^:]+):(\\d+):(\\d+): ($diagnosticTypeOrPattern): (.*)")
private val clangLinkerErrorDiagnosticPattern =
    Regex("((?:[A-Z]:)?[^\\s][^:]+)(?::(\\d+))?: ($diagnosticTypeOrPattern)?: (.+)")
private val msvcDiagnosticMessagePattern =
    Regex("((?:[A-Z]:)?[^\\s][^:]+)\\((\\d+),(\\d+)\\): ($diagnosticTypeOrPattern): (.*)")
private val allPatterns = listOf(
    clangLinkerErrorPattern,
    clangFileInclusionPattern,
    clangDiagnosticMessagePattern,
    clangLinkerErrorDiagnosticPattern,
    msvcDiagnosticMessagePattern
)

fun isNinjaWorkingDirectoryLine(message: String): Boolean {
    return message.startsWith("ninja: Entering directory")
}

fun shouldElevateToLifeCycle(message: String): Boolean {
    // Quick short-circuit to avoid checking all regex against all lines
    if (!message.contains(':')) return false
    for (pattern in allPatterns) {
        if (pattern.matches(message)) return true
    }
    return false
}
