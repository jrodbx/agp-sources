/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.logging

import com.android.build.gradle.internal.cxx.logging.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingLevel.LIFECYCLE
import com.android.build.gradle.internal.cxx.logging.LoggingLevel.WARN
import com.android.utils.cxx.CxxDiagnosticCode
import java.io.File

data class LoggingMessage(
    val level: LoggingLevel,
    val message: String,
    val file: File? = null,
    val tag: String? = null,
    val diagnosticCode: CxxDiagnosticCode? = null
) {
    override fun toString(): String {
        val codeHeader = when (diagnosticCode) {
            null -> "C/C++: "
            else -> when (level) {
                WARN -> "[CXX${diagnosticCode.warningCode}] "
                ERROR -> "[CXX${diagnosticCode.errorCode}] "
                else -> throw IllegalStateException("Message at $level should not have diagnostic code.")
            }
        }
        return when {
            (file == null && tag == null) -> "$codeHeader$message"
            (file != null && tag == null) -> "$codeHeader$file : $message"
            (file == null && tag != null) -> "$codeHeader$tag : $message"
            else -> "$codeHeader$file $tag : $message"
        }
    }
}

private fun LoggingLevel.recordOf(message: String, diagnosticCode: CxxDiagnosticCode?) =
    LoggingMessage(this, message, diagnosticCode = diagnosticCode)

fun errorRecordOf(message: String, diagnosticCode: CxxDiagnosticCode?) =
    ERROR.recordOf(message, diagnosticCode)

fun warnRecordOf(message: String, diagnosticCode: CxxDiagnosticCode?) =
    WARN.recordOf(message, diagnosticCode)

fun lifecycleRecordOf(message: String) = LIFECYCLE.recordOf(message, null)
fun infoRecordOf(message: String) = INFO.recordOf(message, null)
