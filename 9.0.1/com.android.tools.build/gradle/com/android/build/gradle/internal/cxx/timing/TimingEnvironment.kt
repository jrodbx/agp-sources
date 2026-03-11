/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.timing

import java.io.File

/**
 * Time an operation. The result is written to a file defined by the
 * current [TimingEnvironment] scope.
 * Usage,
 *   time("operation") {
 *     // Long running operation
 *   }
 */
fun <T:Any?> time(operation : String, action:()->T) : T {
    TimingEnvironment.openTimingScope(operation)
    try {
        return action()
    } finally {
        TimingEnvironment.closeTimingScope()
    }
}

/**
 * Events below this threshold are not reported in the output file.
 */
private const val CXX_TIMING_MIN_REPORT_THRESHOLD_MS = 10

/**
 * Scope that defines the output location for performance timings.
 *
 * Usage,
 * TimingEnvironment(folder, "generate-cxx-metadata").use {
 *     time("run-cmake") {
 *        // Log to run CMake for this configuration.
 *     }
 * }
 *
 * Timing environments can be nested and the inner-most timing environment
 * determines where the result of 'time()' calls goes.
 *
 * @param timingFolder is the output folder for the timings file.
 * @param outerTimingOperation is the opening timing scope inside the timings file and also
 *   the prefix used for the timings file name.
 * @param currentTimeMillis is a lambda used to return the current time in milliseconds. It's
 *   a lambda so that tests can replace it with deterministic values.
 */
internal class TimingEnvironment(
    private val timingFolder : File,
    private val outerTimingOperation : String,
    private val currentTimeMillis : () -> Long = { System.currentTimeMillis() }
) : AutoCloseable {

    /**
     * Scope defined by a single 'time()' call.
     */
    private class TimingScope(
        val operation : String,
        val start : Long
    ) {
        var openHasBeenReported = false
        var lastChildTimeReportedMs = start
        var childTimings = 0
    }

    /**
     * List of time scopes. Each corresponds to a call to time {...}
     */
    private val timingScopeList = mutableListOf<TimingScope>()

    init {
        // Okay to suppress because push doesn't have knowledge of derived classes.
        @Suppress("LeakingThis")
        push(this)

        // Create empty timing file
        val timingFile = getTimingFile()
        timingFile.parentFile.mkdirs()
        timingFile.appendText("# C/C++ build system timings")

        // Push a timer for this scope
        openTimingScope(outerTimingOperation) // Matched by closeTimingScope in close()
    }

    /**
     * Close this [TimingEnvironment]. This closes the implicit 'time {...}' call started
     * in the constructor.
     * At the end, it pops this [TimingEnvironment] from the stack of environments.
     */
    override fun close() {
        closeTimingScope() // Match openTimingScope in init { }
        getTimingFile().appendText("\n\n")
        pop()
    }

    /**
     * The name of the current output file.
     * Because [TimingEnvironment] is thread-local, the name of file needs to be unique for
     * all current threads.
     */
    internal fun getTimingFile() =
            timingFolder.resolve(
                    "${outerTimingOperation}_${Thread.currentThread().id}_timing.txt")

    /**
     * When a time() { } call is first made, we shouldn't yet output the name of the operation
     * because we don't know yet whether it's going to be a parent or leaf timing.
     * We defer opening until the point this is known.
     */
    private fun openParentsIfNeeded(index : Int) {
        if (index >= timingScopeList.size ||
                timingScopeList[index].openHasBeenReported) return
        openParentsIfNeeded(index + 1)
        val timingFile = getTimingFile()
        val indent = " ".repeat((timingScopeList.size - index - 1) * 2)
        val parent = timingScopeList[index]
        timingFile.appendText("\n$indent${parent.operation}")
        parent.openHasBeenReported = true
    }

    companion object {
        /**
         * Singly-linked list where null is used as empty list.
         * The purpose of using this structure rather than, say, MutableList<TimingEnvironment> is
         * that we need to leave zero allocated memory when the last environment scope is exited.
         * If we leave memory allocated it will have a GC root in thread local storage and this
         * will be reported as a memory leak for tools that check whether all instances allocated
         * by a class loader are destroyed.
         */
        private data class TimingStack(
                val environment: TimingEnvironment,
                val next: TimingStack?
        )

        private val stack: ThreadLocal<TimingStack?> =
                ThreadLocal.withInitial { null }

        /**
         * Push a new timing environment onto the stack of environments.
         */
        private fun push(timing: TimingEnvironment) =
                stack.set(
                        TimingStack(
                                timing,
                                stack.get()
                        )
                )

        /**
         * Pop the top timing environment.
         */
        private fun pop() {
            val next = stack.get()?.next
            if (next != null) stack.set(next) else stack.remove()
        }

        /**
         * Return the depth of the current timer stack. This because the indentation level
         * of the output report.
         */
        private val timerStackDepth : Int get() {
            val environment = stack.get()?.environment ?: error("No active timing environment")
            return environment.timingScopeList.size
        }

        /**
         * Begin a 'time' scope.
         */
        internal fun openTimingScope(operation : String) {
            val environment = stack.get()?.environment ?: return
            environment.timingScopeList.add(0,
                    TimingScope(operation, environment.currentTimeMillis()))
        }

        /**
         * Close a 'time' scope.
         */
        internal fun closeTimingScope() {
            val environment = stack.get()?.environment ?: return
            val timing = environment.timingScopeList.first()
            val timingFile = environment.getTimingFile()
            val end = environment.currentTimeMillis()
            val elapsed = end - timing.start
            val indent = " ".repeat((timerStackDepth - 1) * 2)
            val parent = environment.timingScopeList.drop(1).firstOrNull()

            if (timing.childTimings == 0) {
                if (elapsed >= CXX_TIMING_MIN_REPORT_THRESHOLD_MS) {
                    environment.openParentsIfNeeded(1)
                    // Check for untracked gap since the last child was reported
                    if (parent != null) {
                        val gapElapsed = timing.start - parent.lastChildTimeReportedMs
                        if (gapElapsed >= CXX_TIMING_MIN_REPORT_THRESHOLD_MS) {
                            timingFile.appendText("\n$indent[gap of ${gapElapsed}ms]")
                        }
                        parent.lastChildTimeReportedMs = end
                        parent.childTimings++
                    }
                    timing.openHasBeenReported = true
                    timingFile.appendText("\n$indent${timing.operation} ${elapsed}ms")
                }
            } else {
                environment.openParentsIfNeeded(2)
                val gapElapsed = end - timing.lastChildTimeReportedMs
                if (gapElapsed >= CXX_TIMING_MIN_REPORT_THRESHOLD_MS) {
                    timingFile.appendText("\n$indent  [gap of ${gapElapsed}ms]")
                }
                timingFile.appendText("\n${indent}${timing.operation} completed in ${elapsed}ms")
                if (parent != null) {
                    parent.lastChildTimeReportedMs = end
                    parent.childTimings++
                }
            }

            // Pop this timing from the list
            environment.timingScopeList.removeAt(0)
        }
    }
}
