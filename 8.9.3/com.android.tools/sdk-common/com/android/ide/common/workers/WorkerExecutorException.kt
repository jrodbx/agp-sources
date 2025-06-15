/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.workers

import java.io.PrintWriter

/**
 * An exception that wraps multiple worker tasks exceptions.
 */
class WorkerExecutorException : RuntimeException {
    override val cause: Throwable? get() = causes.firstOrNull()

    val causes: List<Throwable>

    companion object {
        private fun getMessage(causes: List<Throwable>): String {
            var message =
                causes.size.toString() +
                        (if (causes.size == 1) " exception was" else " exceptions were") +
                        " raised by workers:\n"
            causes.forEach { message += it.message + "\n" }
            return message
        }
    }

    constructor(causes: Iterable<Throwable>) : this(getMessage(causes.toList()), causes)

    constructor(message: String, causes: Iterable<Throwable>) : super(message) {
        this.causes = causes.toList()
    }

    override fun printStackTrace(writer: PrintWriter) {
        for ((i, cause) in causes.withIndex()) {
            writer.format("Cause %d: ", i + 1)
            cause.printStackTrace(writer)
        }
    }
}
