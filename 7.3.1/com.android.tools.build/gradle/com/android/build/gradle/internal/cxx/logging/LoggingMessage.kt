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

import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.BUG
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.LIFECYCLE
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.UNRECOGNIZED
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.WARN
import com.android.build.gradle.internal.cxx.string.StringDecoder
import com.android.build.gradle.internal.cxx.string.StringEncoder
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxBugDiagnosticCode

/**
 * Helper function to create [LoggingMessage].
 */
fun createLoggingMessage(
    level : LoggingLevel,
    message : String,
    tag : String = "",
    file : String = "",
    diagnosticCode: Int = 0
) : LoggingMessage {
    return LoggingMessage.newBuilder()
        .setLevel(level)
        .setMessage(message)
        .setTag(tag)
        .setFile(file)
        .setDiagnosticCode(diagnosticCode)
        .build()
}

/**
 * Given a [LoggingMessage] use the given [StringEncoder] to convert
 * to an [EncodedLoggingMessage] that has strings encoded as ints.
 */
fun LoggingMessage.encode(encoder : StringEncoder) : EncodedLoggingMessage {
    return EncodedLoggingMessage.newBuilder()
        .setLevel(level)
        .setMessageId(encoder.encode(message))
        .setTagId(encoder.encode(tag))
        .setFileId(encoder.encode(file))
        .setDiagnosticCode(diagnosticCode)
        .build()
}

/**
 * Given an [EncodedLoggingMessage], which has strings represented as ints, use
 * the given [StringDecoder] to convert to a [LoggingMessage] that has
 * instantiated strings.
 */
fun EncodedLoggingMessage.decode(decoder : StringDecoder) : LoggingMessage {
    return LoggingMessage.newBuilder()
        .setLevel(level)
        .setMessage(decoder.decode(messageId))
        .setTag(decoder.decode(tagId))
        .setFile(decoder.decode(fileId))
        .setDiagnosticCode(diagnosticCode)
        .build()
}

fun decodeLoggingMessage(
    encoded : EncodedLoggingMessage,
    decoder : StringDecoder) = encoded.decode(decoder)

fun LoggingMessage.text() : String {
    val codeHeader = when(diagnosticCode) {
        0 -> "C/C++: "
        else -> when(level) {
            BUG -> "[bug $diagnosticCode] "
            else -> "[CXX$diagnosticCode] "
        }
    }
    return when {
        (file.isBlank() && tag.isBlank()) -> "$codeHeader$message"
        (file.isNotBlank() && tag.isBlank()) -> "$codeHeader$file : $message"
        (file.isBlank() && tag.isNotBlank()) -> "$codeHeader$tag : $message"
        else -> "$codeHeader$file $tag : $message"
    }
}

fun bugRecordOf(message: String, diagnosticCode: CxxBugDiagnosticCode) =
    createLoggingMessage(BUG, message, diagnosticCode = diagnosticCode.bugNumber)

fun errorRecordOf(message: String, diagnosticCode: CxxDiagnosticCode?) =
    createLoggingMessage(ERROR, message, diagnosticCode = diagnosticCode?.errorCode?:0)

fun warnRecordOf(message: String, diagnosticCode: CxxDiagnosticCode?) =
    createLoggingMessage(WARN, message, diagnosticCode = diagnosticCode?.warningCode?:0)

fun lifecycleRecordOf(message: String) = createLoggingMessage(LIFECYCLE, message)
fun infoRecordOf(message: String) = createLoggingMessage(INFO, message)

