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

import com.android.Version
import com.android.tools.analytics.crash.CrashReport
import com.google.common.base.Throwables
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.lang.IllegalStateException

internal const val REPORT_TYPE = "unexpectedException"

/** Crash report that, in addition to general data, contains exception stacktrace. */
class PluginExceptionReport
private constructor(val exception: Throwable):
    CrashReport(PRODUCT_ID, Version.ANDROID_GRADLE_PLUGIN_VERSION, mapOf(), REPORT_TYPE) {

    companion object {
        fun create(ex: Throwable): PluginExceptionReport? {
            if (ex is ExternalApiUsageException) return null

            val rootCause = Throwables.getRootCause(ex)
            if (isUsefulException(rootCause)) {
                return PluginExceptionReport(rootCause)
            } else {
                return null
            }
        }

        private fun isUsefulException(ex: Throwable): Boolean {
            return ex is NullPointerException
                    || ex is ArrayIndexOutOfBoundsException
                    || ex is IllegalStateException
                    || ex is IllegalArgumentException
        }

    }

    override fun serializeTo(builder: MultipartEntityBuilder) {
        builder.addTextBody("exception_info", getNoPiiStacktrace(exception))
    }

    private fun getNoPiiStacktrace(ex: Throwable): String {
        val sb = StringBuilder(ex::class.java.name)
        sb.append(": <message removed>")
        for (stackTraceElement in ex.stackTrace) {
            sb.append("\n\tat ")
            sb.append(stackTraceElement)
        }
        return sb.toString()
    }
}