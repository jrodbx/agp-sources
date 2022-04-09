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

import com.android.utils.ILogger
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxBugDiagnosticCode
import com.google.protobuf.GeneratedMessageV3
import org.gradle.api.logging.Logging
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.INFO
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.LIFECYCLE
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.WARN
import com.android.build.gradle.internal.cxx.string.StringEncoder

/**
 * This file exposes functions for logging where the logger is held in a stack on thread-local
 * storage.
 *
 * Example usage,
 *
 *      MyThreadLoggingEnvironment(...).use {
 *          warnln("falling rocks")
 *       }
 *
 * The purpose is to separate the concerns of other classes and functions from the need to log
 * and warn.
 */

/**
 * Report a bug in Android Gradle Plugin.
 *
 * Unlike [errorln], this function requires a diagnostic code [CxxBugDiagnosticCode] and that
 * diagnostic code is the Issue Tracker bug number.
 */
fun bugln(bugCode: CxxBugDiagnosticCode, format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedBugToCurrentLogger(
        checkedFormat(format, args),
        bugCode
    )

/**
 * Report an error.
 */
fun errorln(format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedErrorToCurrentLogger(
        checkedFormat(format, args), null
    )

fun errorln(diagnosticCode: CxxDiagnosticCode, format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedErrorToCurrentLogger(
        checkedFormat(format, args),
        diagnosticCode
    )

/**
 * Report a warning.
 */
fun warnln(format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedWarningToCurrentLogger(
        checkedFormat(format, args),
        null
    )

fun warnln(diagnosticCode: CxxDiagnosticCode, format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedWarningToCurrentLogger(
        checkedFormat(format, args),
        diagnosticCode
    )

/**
 * Report a non-error/non-warning message that should be displayed during normal gradle build
 * without --info flag.
 */
fun lifecycleln(format: String, vararg args: Any) =
        ThreadLoggingEnvironment.reportFormattedLifecycleToCurrentLogger(checkedFormat(format, args))

/**
 * Report diagnostic/informational message.
 */
fun infoln(format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedInfoToCurrentLogger(checkedFormat(format, args))

/**
 * Log a structured message. The function [message] will only be called if there is a logger
 * that accepts structured messages attached.
 */
fun logStructured(message : (StringEncoder) -> GeneratedMessageV3) =
    ThreadLoggingEnvironment.logStructuredMessageToCurrentLogger(message)

/**
 * If caller from Java side misuses %s-style formatting (too many %s for example), the exception
 * from String.format can be concealed by Gradle's logging system. It will appear as
 * "Invalid format (%s)" with no other indication about the source of the problem. This is not
 * necessarily a code bug (for example a user string might be passed in format and that user
 * string has an invalid %)
 */
private fun checkedFormat(format: String, args: Array<out Any>): String {
    if (args.isEmpty()) {
        return format
    }
    return try {
        String.format(format, *args)
    } catch (e: Throwable) {
        """
        ${e.message}
        format = $format
        args[${args.size}] = ${args.joinToString("\n")}
        stacktrace = ${e.stackTrace.joinToString("\n")}"""
        .trimIndent()
    }
}

/**
 * Interface for logging environment.
 */
interface LoggingEnvironment : AutoCloseable {
    fun log(message : LoggingMessage)
    fun logStructured(message : (StringEncoder) -> GeneratedMessageV3) { }
}

/**
 * Logger base class. When used from Java try-with-resources or Kotlin use() function it will
 * automatically register and deregister with the thread-local stack of loggers.
 */
abstract class ThreadLoggingEnvironment : LoggingEnvironment {
    init {
        // Okay to suppress because push doesn't have knowledge of derived classes.
        @Suppress("LeakingThis")
        push(this)
    }
    override fun close() {
        pop()
    }

    companion object {
        /**
         * Singly-linked list where null is used as empty list.
         * The purpose is that Thread Local has zero allocations when there are no loggers so that
         * the class loader that creates the loggers won't leak.
         */
        private data class LoggerStack(
            val logger : LoggingEnvironment,
            val next : LoggerStack?)
        /**
         * Stack of logger environments.
         */
        private val loggerStack : ThreadLocal<LoggerStack?> =
            ThreadLocal.withInitial { null }

        /**
         * The logger environment to use if there is no other environment. There should always be an
         * intentional logging environment. This logging environment does not register itself on
         * with a thread-local (to avoid leaking class loader). It is stateless the call to close()
         * is a no-op.
         */
        private val BOTTOM_LOGGING_ENVIRONMENT = BottomLoggingEnvironment()

        private class BottomLoggingEnvironment : LoggingEnvironment {
            private val logger = Logging.getLogger(BottomLoggingEnvironment::class.java)

            override fun log(message: LoggingMessage) {
                when(message.level) {
                    ERROR -> logger.error(message.text())
                    WARN -> logger.warn(message.text())
                    LIFECYCLE -> logger.lifecycle(message.text())
                    INFO -> logger.info(message.text())
                }
            }

            override fun close() {
            }
        }

        /**
         * The current logger.
         */
        private val logger : LoggingEnvironment
            get() = loggerStack.get()?.logger ?: BOTTOM_LOGGING_ENVIRONMENT

        /**
         * Push a new logging environment onto the stack of environments.
         */
        private fun push(logger: ThreadLoggingEnvironment) =
            loggerStack.set(LoggerStack(logger, loggerStack.get()))

        /**
         * Pop the top logging environment.
         */
        private fun pop() {
            val next = loggerStack.get()?.next
            if (next != null) loggerStack.set(next) else loggerStack.remove()
        }

        /**
         * Get the parent of the current logger.
         */
        @JvmStatic // error: using non-JVM static members protected in the superclass companion
                   // is unsupported yet
        fun parentLogger() = loggerStack.get()?.next?.logger ?: BOTTOM_LOGGING_ENVIRONMENT

        /**
         * Report a bug.
         */
        fun reportFormattedBugToCurrentLogger(
            message: String,
            bugCode: CxxBugDiagnosticCode
        ) =
            logger.log(bugRecordOf(message, bugCode))

        /**
         * Report an error.
         */
        fun reportFormattedErrorToCurrentLogger(
            message: String,
            diagnosticCode: CxxDiagnosticCode?
        ) =
            logger.log(errorRecordOf(message, diagnosticCode))

        /**
         * Report a warning.
         */
        fun reportFormattedWarningToCurrentLogger(
            message: String,
            diagnosticCode: CxxDiagnosticCode?
        ) =
            logger.log(warnRecordOf(message, diagnosticCode))

        /**
         * Report a non-error/non-warning message that should be displayed during normal gradle build
         * without --info flag.
         */
        fun reportFormattedLifecycleToCurrentLogger(message: String) =
            logger.log(lifecycleRecordOf(message))

        /**
         * Report diagnostic/informational message.
         */
        fun reportFormattedInfoToCurrentLogger(message: String) =
            logger.log(infoRecordOf(message))

        /**
         * Log a structured (ProtoBuf) message
         */
        fun logStructuredMessageToCurrentLogger(message : (StringEncoder) -> GeneratedMessageV3) =
            logger.logStructured(message)

        /**
         * Throw an exception if the currently registered logger is the
         * default one.
         */
        fun requireExplicitLogger() {
            if (logger == BOTTOM_LOGGING_ENVIRONMENT)
                throw Exception("Non-default logger is required")
        }

        /**
         * Produce an ILogger over the current logger.
         */
        fun getILogger(warningDiagnosticCode: CxxDiagnosticCode, errorDiagnosticCode: CxxDiagnosticCode) = object : ILogger {
            override fun error(t: Throwable?, format: String?, vararg args: Any) {
                if (t != null) throw t
                logger.log(errorRecordOf(checkedFormat(format!!, args), errorDiagnosticCode))
            }
            override fun warning(format: String, vararg args: Any) {
                logger.log(warnRecordOf(checkedFormat(format, args), warningDiagnosticCode))
            }
            override fun info(format: String, vararg args: Any) {
                logger.log(infoRecordOf(checkedFormat(format, args)))
            }
            override fun verbose(format: String, vararg args: Any) {
                logger.log(infoRecordOf(checkedFormat(format, args)))
            }
        }
    }
}

