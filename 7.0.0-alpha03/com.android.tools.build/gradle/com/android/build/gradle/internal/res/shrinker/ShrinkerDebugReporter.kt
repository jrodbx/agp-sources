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

package com.android.build.gradle.internal.res.shrinker

import java.io.File
import java.io.PrintWriter
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

interface ShrinkerDebugReporter : AutoCloseable {
    fun debug(f: () -> String) = report(f, LogLevel.DEBUG)
    fun info(f: () -> String) = report(f, LogLevel.INFO)

    fun report(f: () -> String, logLevel: LogLevel)
}

object NoDebugReporter : ShrinkerDebugReporter {
    override fun report(f: () -> String, logLevel: LogLevel) = Unit
    override fun close() = Unit
}

class LoggerAndFileDebugReporter(
    private val logger: Logger,
    reportFile: File?
) : ShrinkerDebugReporter {
    private val writer: PrintWriter? = reportFile?.let { PrintWriter(it) }

    override fun report(f: () -> String, logLevel: LogLevel) {
        if (logger.isEnabled(logLevel) || writer != null) {
            val message = f()
            logger.log(logLevel, message)
            writer?.println(message)
        }
    }

    override fun close() {
        writer?.close()
    }
}
