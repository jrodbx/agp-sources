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

import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION
import org.gradle.api.logging.Logging

/**
 * A logging environment that will report errors and warnings to an [IssueReporter].
 * Messages are also logger to a standard [org.gradle.api.logging.Logger].
 */
class IssueReporterLoggingEnvironment(
    private val issueReporter : IssueReporter) : PassThroughDeduplicatingLoggingEnvironment() {
    private val logger = Logging.getLogger(IssueReporterLoggingEnvironment::class.java)

    override fun log(message: LoggingMessage) {
        when (message.level) {
            LoggingLevel.INFO -> logger.info(message.toString())
            LoggingLevel.WARN -> {
                issueReporter.reportWarning(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    message.toString())
                logger.warn(message.toString())
            }
            LoggingLevel.ERROR -> {
                issueReporter.reportError(
                    EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    message.toString())
                logger.error(message.toString())
            }
        }
    }
}