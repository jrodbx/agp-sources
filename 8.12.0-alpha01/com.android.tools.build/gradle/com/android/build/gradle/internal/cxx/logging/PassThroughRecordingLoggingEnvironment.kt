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

import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.LIFECYCLE
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.WARN
import com.android.build.gradle.internal.cxx.string.StringEncoder
import com.google.gson.GsonBuilder
import com.google.protobuf.GeneratedMessageV3
import java.io.File

/**
 * Cap the number of log records to keep in order to limit memory consumption.
 */
private const val MAX_LOG_RECORDS_TO_KEEP = 200

/**
 * [ThreadLoggingEnvironment] that will record messages and then forward to a parent
 * logger. A maximum of [MAX_LOG_RECORDS_TO_KEEP] messages are kept.
 */
open class PassThroughRecordingLoggingEnvironment : ThreadLoggingEnvironment() {
    private val messages = ArrayDeque<LoggingMessage>(MAX_LOG_RECORDS_TO_KEEP + 1)
    private val parent = parentLogger()

    override fun log(message: LoggingMessage) {
        parent.log(message)
        messages.add(message)
        if (messages.size > MAX_LOG_RECORDS_TO_KEEP) {
            messages.removeFirst();
        }
    }

    override fun logStructured(message: (StringEncoder) -> GeneratedMessageV3) {
        parent.logStructured(message)
    }

    /**
     * true if there was at least one error.
     */
    fun hadErrors() = messages.any { it.level == ERROR }

    /**
     * The error messages that been seen so far.
     */
    val errorMessages get() = messages.filter { it.level == ERROR }

    /**
     * The text errors that have been seen so far.
     */
    val errors get() = errorMessages.map { it.text() }

    /**
     * The warning messages that been seen so far.
     */
    val warningMessages get() = messages.filter { it.level == WARN }

    /**
     * The warnings that have been seen so far.
     */
    val warnings get() = warningMessages.map { it.text() }

    /**
     * The lifecycle messages that have been seen so far.
     */
    val lifecycles get() = messages.filter { it.level == LIFECYCLE }.map { it.text() }

    /**
     * The infos that have been seen so far.
     */
    val infos get() = messages.filter { it.level == INFO }.map { it.text() }

    /**
     * Total message count so far.
     */
    val messageCount get() = messages.size

    /**
     * The logging record so far. Returns an immutable copy.
     */
    val record get() = messages.toList()
}

/**
 * Render a list of [LoggingMessage] as a JSON string.
 */
fun List<LoggingMessage>.toJsonString() = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .setPrettyPrinting()
    .create()
    .toJson(this)!!
