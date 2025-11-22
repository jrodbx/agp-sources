/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.testing

import org.gradle.api.logging.Logging
import org.gradle.api.logging.Logger
import java.util.Timer
import kotlin.concurrent.timer

private const val DEFAULT_LOG_STATUS_DELAY_MS = 10 * 1000L

class TestResultProgressTracker @JvmOverloads constructor(
    deviceName: String,
    val logStatusDelayMS: Long = DEFAULT_LOG_STATUS_DELAY_MS,
    val logger: Logger = Logging.getLogger(TestResultProgressTracker::class.java),
    val timerFactory: (Long, () -> Unit) -> Timer = { delay, action ->
        timer(
            initialDelay = delay,
            period = delay
        ) {
            action()
        }
    }
) {

    private var logTimer: Timer? = null

    val status = TestSuiteStatus(deviceName)

    class TestSuiteStatus(val deviceName: String) {
        private var testsFailedCount = 0
        private var testsSkippedCount = 0
        private var testsScheduledCount = 0
        private var testsCompletedCount = 0
        private var statusChanged = false

        val finishedTests: Int
            get() = synchronized(this) { testsCompletedCount }

        fun hasChanged(): Boolean =
            synchronized(this) {
                val hasChange = statusChanged
                statusChanged = false
                hasChange
            }

        fun addCompletedTest() {
            synchronized(this) {
                statusChanged = true
                ++testsCompletedCount
            }
        }

        fun addFailedTest() {
            synchronized(this) {
                statusChanged = true
                ++testsCompletedCount
                ++testsFailedCount
            }
        }

        fun addSkippedTest() {
            synchronized(this) {
                statusChanged = true
                ++testsCompletedCount
                ++testsSkippedCount
            }
        }

        fun scheduleTests(amount: Int) {
            synchronized(this) {
                statusChanged = true
                testsScheduledCount = amount
            }
        }

        fun getStatus(): String = synchronized (this) {
            "$deviceName Tests $testsCompletedCount/$testsScheduledCount completed. " +
                    "($testsSkippedCount skipped) ($testsFailedCount failed)"
        }
    }

    fun logStatus() {
        if (status.hasChanged()) {
            logger.lifecycle(status.getStatus())
        }
    }

    fun onTestSuiteStarted(scheduledTestCaseCount: Int) {
        status.scheduleTests(scheduledTestCaseCount)
        logTimer = timerFactory(logStatusDelayMS, ::logStatus)
    }

    fun onTestFailed() = status.addFailedTest()

    fun onTestPassed() = status.addCompletedTest()

    fun onTestSkipped() = status.addSkippedTest()

    fun onTestSuiteFinished() {
        logTimer?.cancel()
        logger.lifecycle(
            "Finished ${status.finishedTests} tests on ${status.deviceName}"
        )
    }
}
