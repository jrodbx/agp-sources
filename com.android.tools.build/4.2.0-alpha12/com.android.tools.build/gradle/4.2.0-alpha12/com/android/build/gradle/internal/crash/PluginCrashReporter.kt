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

package com.android.build.gradle.internal.crash

import com.google.common.annotations.VisibleForTesting
import com.android.build.gradle.internal.LoggerWrapper
import com.android.Version
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.crash.CrashReporter
import com.android.tools.analytics.crash.GoogleCrashReporter

/** This cannot be changed without updating the go/crash configuration. */
internal const val PRODUCT_ID = "AndroidGradlePlugin"

/** Reporter used to upload crashes in AGP, in order to help debug user issues. */
object PluginCrashReporter {

    private val reporter: CrashReporter?

    init {
        reporter = getCrashReporter()
    }

    /**
     * Reports the exception if it is one of the types we are interested in reporting. Returns true
     * if reporting is enabled, and exception type should be reported, false otherwise.
     */
    @JvmStatic
    fun maybeReportException(ex: Throwable): Boolean = maybeReportExceptionImpl(reporter, ex)

    @VisibleForTesting
    fun maybeReportExceptionForTest(ex: Throwable): Boolean {
        val crashReporter = getCrashReporter(forTest = true)
        return maybeReportExceptionImpl(crashReporter, ex)
    }

    private fun getCrashReporter(forTest: Boolean = false): CrashReporter? {
        AnalyticsSettings.initialize(LoggerWrapper.getLogger(PluginCrashReporter::class.java))
        return if (AnalyticsSettings.optedIn) {
            val isDebugBuild = Version.ANDROID_GRADLE_PLUGIN_VERSION.endsWith("-dev")
            GoogleCrashReporter(false, isDebugBuild || forTest)
        } else {
            null
        }
    }

    private fun maybeReportExceptionImpl(reporter: CrashReporter?, ex: Throwable): Boolean {
        if (reporter == null) {
            return false
        }

        return PluginExceptionReport.create(ex)?.let {
            reporter.submit(it)
            return true
        } ?: false
    }
}
