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

import com.android.build.gradle.internal.cxx.logging.LoggingLevel.*
import com.android.utils.ILogger
import org.gradle.api.logging.Logging

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
 * Report an error.
 */
fun errorln(format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedErrorToCurrentLogger(checkedFormat(format, args))

/**
 * Report a warning.
 */
fun warnln(format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedWarningToCurrentLogger(checkedFormat(format, args))

/**
 * Report diagnostic/informational message.
 */
fun infoln(format: String, vararg args: Any) =
    ThreadLoggingEnvironment.reportFormattedInfoToCurrentLogger(checkedFormat(format, args))

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
                    ERROR -> logger.error(message.toString())
                    WARN -> logger.warn(message.toString())
                    INFO -> logger.info(message.toString())
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
        private fun pop() = loggerStack.set(loggerStack.get()?.next)

        /**
         * Get the parent of the current logger.
         */
        @JvmStatic // error: using non-JVM static members protected in the superclass companion
                   // is unsupported yet
        fun parentLogger() = loggerStack.get()?.next?.logger ?: BOTTOM_LOGGING_ENVIRONMENT

        /**
         * Report an error.
         */
        fun reportFormattedErrorToCurrentLogger(message: String) =
            logger.log(errorRecordOf(message))

        /**
         * Report a warning.
         */
        fun reportFormattedWarningToCurrentLogger(message: String) =
            logger.log(warnRecordOf(message))

        /**
         * Report diagnostic/informational message.
         */
        fun reportFormattedInfoToCurrentLogger(message: String) =
            logger.log(infoRecordOf(message))

        /**
         * Produce an ILogger over the current logger.
         */
        fun getILogger() = object : ILogger {
            override fun error(t: Throwable?, format: String?, vararg args: Any) {
                if (t != null) throw t
                logger.log(errorRecordOf(checkedFormat(format!!, args)))
            }
            override fun warning(format: String, vararg args: Any) {
                logger.log(warnRecordOf(checkedFormat(format, args)))
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

